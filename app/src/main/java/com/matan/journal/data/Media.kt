package com.matan.journal.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.InputStream
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Photos and voice notes live in the app's private "media" folder (copied, so the journal never loses them). */
object Media {
    fun dir(context: Context): File = File(context.filesDir, "media").apply { mkdirs() }
    fun file(context: Context, name: String) = File(dir(context), name)

    // ---------- photos ----------

    /** Copies a photo into the journal, downscaled to [maxPx] and rotated upright. Returns the file name. */
    fun importPhoto(context: Context, uri: Uri, maxPx: Int = 1600): String? = runCatching {
        val cr = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxPx || bounds.outHeight / (sample * 2) >= maxPx) sample *= 2
        val raw = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: return null
        val rotation = cr.openInputStream(uri)?.use { exifRotation(it) } ?: 0
        val scaled = scaleDown(rotate(raw, rotation), maxPx)
        val name = "${UUID.randomUUID()}.jpg"
        file(context, name).outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        name
    }.getOrNull()

    private fun exifRotation(input: InputStream): Int = when (ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }

    private fun rotate(b: Bitmap, deg: Int): Bitmap =
        if (deg == 0) b else Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(deg.toFloat()) }, true)

    private fun scaleDown(b: Bitmap, maxPx: Int): Bitmap {
        val m = maxOf(b.width, b.height)
        if (m <= maxPx) return b
        val f = maxPx.toFloat() / m
        return Bitmap.createScaledBitmap(b, (b.width * f).toInt(), (b.height * f).toInt(), true)
    }

    /** Small bitmap of a journal photo. */
    fun thumb(context: Context, fileName: String, px: Int): Bitmap? {
        val f = file(context, fileName)
        if (!f.exists()) return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var s = 1
        while (o.outWidth / (s * 2) >= px && o.outHeight / (s * 2) >= px) s *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })
    }

    /** Small bitmap of a gallery photo. */
    fun thumb(context: Context, uri: Uri, px: Int): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= 29) {
            context.contentResolver.loadThumbnail(uri, android.util.Size(px, px), null)
        } else {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
            var s = 1
            while (o.outWidth / (s * 2) >= px && o.outHeight / (s * 2) >= px) s *= 2
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) }
        }
    }.getOrNull()

    /** Photos taken on [day] in the phone's gallery (needs the photos permission). */
    fun galleryPhotosOf(context: Context, day: Long): List<Uri> = runCatching {
        val zone = ZoneId.systemDefault()
        val start = LocalDate.ofEpochDay(day).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = LocalDate.ofEpochDay(day + 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val list = mutableListOf<Uri>()
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media.DATE_TAKEN} >= ? AND ${MediaStore.Images.Media.DATE_TAKEN} < ?",
            arrayOf(start.toString(), end.toString()),
            "${MediaStore.Images.Media.DATE_TAKEN} DESC",
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (c.moveToNext() && list.size < 40) {
                list += ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(idCol))
            }
        }
        list
    }.getOrDefault(emptyList())

    // ---------- voice ----------

    class Recorder(private val context: Context) {
        private var recorder: MediaRecorder? = null
        private var startedAt = 0L
        var fileName: String? = null
            private set

        fun start(): Boolean = runCatching {
            val name = "${UUID.randomUUID()}.m4a"
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(64_000)
            r.setAudioSamplingRate(44_100)
            r.setOutputFile(file(context, name).path)
            r.prepare()
            r.start()
            recorder = r; fileName = name; startedAt = System.currentTimeMillis()
            true
        }.getOrDefault(false)

        /** Stops and returns (fileName, seconds), or null if nothing usable was recorded. */
        fun stop(): Pair<String, Int>? {
            val r = recorder ?: return null
            val name = fileName ?: return null
            val ok = runCatching { r.stop() }.isSuccess
            r.release(); recorder = null
            val secs = ((System.currentTimeMillis() - startedAt) / 1000).toInt()
            if (!ok || secs < 1) { file(context, name).delete(); return null }
            return name to secs
        }

        fun cancel() {
            recorder?.let { runCatching { it.stop() }; it.release() }
            recorder = null
            fileName?.let { file(context, it).delete() }
        }
    }

    class Player {
        private var player: MediaPlayer? = null
        var playing: String? = null
            private set

        fun play(context: Context, fileName: String, onDone: () -> Unit) {
            stop()
            val p = MediaPlayer()
            runCatching {
                p.setDataSource(file(context, fileName).path)
                p.setOnCompletionListener { stop(); onDone() }
                p.prepare(); p.start()
                player = p; playing = fileName
            }.onFailure { p.release(); onDone() }
        }

        fun stop() {
            player?.let { runCatching { it.stop() }; it.release() }
            player = null; playing = null
        }
    }
}
