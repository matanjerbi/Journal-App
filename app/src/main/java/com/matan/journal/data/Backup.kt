package com.matan.journal.data

import android.content.Context
import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypted backup: "JRNL1" | salt(16) | iv(16) | AES-256-CTR( zip(data.json + media files) ) | HMAC-SHA256(32).
 * Keys come from the password with PBKDF2 (120k rounds). Encrypt-then-MAC; the MAC is checked before anything is restored.
 */
object Backup {
    private val MAGIC = "JRNL1".toByteArray()
    private const val ROUNDS = 120_000

    class WrongPassword : Exception("סיסמה שגויה או קובץ פגום")

    private fun keys(password: String, salt: ByteArray): Pair<SecretKeySpec, SecretKeySpec> {
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val bytes = f.generateSecret(PBEKeySpec(password.toCharArray(), salt, ROUNDS, 512)).encoded
        return SecretKeySpec(bytes.copyOfRange(0, 32), "AES") to SecretKeySpec(bytes.copyOfRange(32, 64), "HmacSHA256")
    }

    /** Writes to [out] and updates [mac] with every byte. Doesn't close the target. */
    private class MacOut(out: OutputStream, private val mac: Mac) : FilterOutputStream(out) {
        override fun write(b: Int) { out.write(b); mac.update(b.toByte()) }
        override fun write(b: ByteArray, off: Int, len: Int) { out.write(b, off, len); mac.update(b, off, len) }
        override fun close() { flush() }
    }

    suspend fun export(context: Context, repo: Repo, password: String, out: OutputStream) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val (enc, macKey) = keys(password, salt)
        val mac = Mac.getInstance("HmacSHA256").apply { init(macKey) }
        out.write(MAGIC); out.write(salt); out.write(iv)
        mac.update(MAGIC); mac.update(salt); mac.update(iv)
        val cipher = Cipher.getInstance("AES/CTR/NoPadding").apply { init(Cipher.ENCRYPT_MODE, enc, IvParameterSpec(iv)) }
        val json = dataJson(repo).toString().toByteArray(Charsets.UTF_8)
        ZipOutputStream(CipherOutputStream(MacOut(out, mac), cipher)).use { zip ->
            zip.putNextEntry(ZipEntry("data.json")); zip.write(json); zip.closeEntry()
            Media.dir(context).listFiles()?.forEach { f ->
                zip.putNextEntry(ZipEntry("media/${f.name}")); f.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
            }
        }
        out.write(mac.doFinal())
        out.flush()
    }

    /**
     * [open] is called twice: once to verify the password/MAC, once to decrypt.
     * Replaces everything only after both steps succeed.
     */
    suspend fun import(context: Context, repo: Repo, password: String, open: () -> InputStream) {
        // pass 1: read header + verify MAC over everything except the last 32 bytes
        val header = ByteArray(MAGIC.size + 32)
        var length = 0L
        val expected = ByteArray(32)
        open().use { input ->
            readFully(input, header)
            if (!header.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) throw WrongPassword()
            val salt = header.copyOfRange(MAGIC.size, MAGIC.size + 16)
            val (_, macKey) = keys(password, salt)
            val mac = Mac.getInstance("HmacSHA256").apply { init(macKey); update(header) }
            // stream, holding back the last 32 bytes (the stored MAC)
            val buf = ByteArray(64 * 1024)
            var tail = ByteArray(0)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                val chunk = tail + buf.copyOf(n)
                val emit = chunk.size - 32
                if (emit > 0) { mac.update(chunk, 0, emit); length += emit; tail = chunk.copyOfRange(emit, chunk.size) } else tail = chunk
            }
            if (tail.size != 32) throw WrongPassword()
            tail.copyInto(expected)
            if (!MessageDigest.isEqual(mac.doFinal(), expected)) throw WrongPassword()
        }

        // pass 2: decrypt into a temp folder
        val salt = header.copyOfRange(MAGIC.size, MAGIC.size + 16)
        val iv = header.copyOfRange(MAGIC.size + 16, MAGIC.size + 32)
        val (enc, _) = keys(password, salt)
        val tmp = File(context.filesDir, "media_import").apply { deleteRecursively(); mkdirs() }
        var json: String? = null
        open().use { input ->
            readFully(input, ByteArray(header.size))
            val cipher = Cipher.getInstance("AES/CTR/NoPadding").apply { init(Cipher.DECRYPT_MODE, enc, IvParameterSpec(iv)) }
            ZipInputStream(CipherInputStream(Limited(input, length), cipher)).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    when {
                        e.name == "data.json" -> json = zip.readBytes().toString(Charsets.UTF_8)
                        e.name.startsWith("media/") && !e.isDirectory -> File(tmp, File(e.name).name).outputStream().use { zip.copyTo(it) }
                    }
                }
            }
        }
        val data = JSONObject(json ?: throw WrongPassword())
        restore(repo, data)
        val media = Media.dir(context)
        media.deleteRecursively()
        if (!tmp.renameTo(media)) {
            media.mkdirs(); tmp.listFiles()?.forEach { it.copyTo(File(media, it.name), true) }; tmp.deleteRecursively()
        }
    }

    private fun readFully(input: InputStream, b: ByteArray) {
        var off = 0
        while (off < b.size) { val n = input.read(b, off, b.size - off); if (n < 0) throw WrongPassword(); off += n }
    }

    private class Limited(private val input: InputStream, private var left: Long) : InputStream() {
        override fun read(): Int = if (left <= 0) -1 else input.read().also { if (it >= 0) left-- }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (left <= 0) return -1
            val n = input.read(b, off, minOf(len.toLong(), left).toInt())
            if (n > 0) left -= n
            return n
        }
    }

    // ---------- JSON ----------

    private fun JSONObject.n(k: String, v: Any?) = put(k, v ?: JSONObject.NULL)
    private fun arr(k: String, j: JSONObject) = j.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } } ?: emptyList()

    private suspend fun dataJson(repo: Repo): JSONObject {
        val d = repo.dao
        fun <T> list(items: List<T>, f: (T) -> JSONObject) = JSONArray().apply { items.forEach { put(f(it)) } }
        return JSONObject().put("version", 1)
            .put("entries", list(d.allEntries()) {
                JSONObject().put("day", it.day).put("did", it.did).put("good", it.good).put("learned", it.learned).put("free", it.free)
                    .put("intention", it.intention).put("intentionDone", it.intentionDone).put("mood", it.mood).put("energy", it.energy)
                    .put("starred", it.starred).put("starTitle", it.starTitle).put("updatedAt", it.updatedAt)
            })
            .put("activities", list(d.allActivities()) { JSONObject().put("id", it.id).put("name", it.name).put("emoji", it.emoji).put("sort", it.sort) })
            .put("people", list(d.allPeople()) { JSONObject().put("id", it.id).put("name", it.name).put("category", it.category.name) })
            .put("places", list(d.allPlaces()) { JSONObject().put("id", it.id).put("name", it.name) })
            .put("links", list(d.allLinks()) { JSONObject().put("day", it.day).put("kind", it.kind.name).put("refId", it.refId) })
            .put("photos", list(d.allPhotos()) { JSONObject().put("id", it.id).put("day", it.day).put("fileName", it.fileName) })
            .put("recordings", list(d.allRecordings()) { JSONObject().put("id", it.id).put("day", it.day).put("fileName", it.fileName).put("seconds", it.seconds) })
            .put("birthdays", list(d.allBirthdays()) { JSONObject().put("id", it.id).put("name", it.name).put("month", it.month).put("dayOfMonth", it.dayOfMonth).n("year", it.year) })
            .put("reviews", list(d.allReviews()) { JSONObject().put("weekStart", it.weekStart).put("takeaway", it.takeaway) })
    }

    private suspend fun restore(repo: Repo, j: JSONObject) {
        val d = repo.dao
        val entries = arr("entries", j).map {
            Entry(it.getLong("day"), it.optString("did"), it.optString("good"), it.optString("learned"), it.optString("free"),
                it.optString("intention"), it.optInt("intentionDone", -1), it.optInt("mood"), it.optInt("energy"),
                it.optBoolean("starred"), it.optString("starTitle"), it.optLong("updatedAt"))
        }
        val activities = arr("activities", j).map { Activity(it.getLong("id"), it.getString("name"), it.optString("emoji"), it.optInt("sort")) }
        val people = arr("people", j).map { Person(it.getLong("id"), it.getString("name"), PersonCategory.valueOf(it.optString("category", "FRIENDS"))) }
        val places = arr("places", j).map { Place(it.getLong("id"), it.getString("name")) }
        val links = arr("links", j).map { Link(it.getLong("day"), LinkKind.valueOf(it.getString("kind")), it.getLong("refId")) }
        val photos = arr("photos", j).map { Photo(it.getLong("id"), it.getLong("day"), it.getString("fileName")) }
        val recs = arr("recordings", j).map { Recording(it.getLong("id"), it.getLong("day"), it.getString("fileName"), it.optInt("seconds")) }
        val bdays = arr("birthdays", j).map {
            Birthday(it.getLong("id"), it.getString("name"), it.getInt("month"), it.getInt("dayOfMonth"), if (it.isNull("year")) null else it.getInt("year"))
        }
        val reviews = arr("reviews", j).map { Review(it.getLong("weekStart"), it.optString("takeaway")) }
        repo.db.withTransaction {
            d.clearLinks(); d.clearPhotos(); d.clearRecordings(); d.clearEntries(); d.clearActivities(); d.clearPeople()
            d.clearPlaces(); d.clearBirthdays(); d.clearReviews()
            d.insertEntries(entries); d.insertActivities(activities); d.insertPeople(people); d.insertPlaces(places)
            d.insertLinks(links); d.insertPhotos(photos); d.insertRecordings(recs); d.insertBirthdays(bdays); d.insertReviews(reviews)
        }
    }
}
