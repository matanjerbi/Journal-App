package com.matan.journal.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine

// Days are epoch days (LocalDate.toEpochDay()).

/** One page of the journal. */
@Entity(tableName = "entries")
data class Entry(
    @PrimaryKey val day: Long,
    val did: String = "",
    val good: String = "",
    val learned: String = "",
    val free: String = "",
    /** what I want to do tomorrow */
    val intention: String = "",
    /** did yesterday's intention happen? -1 unknown, 0 no, 1 yes */
    val intentionDone: Int = -1,
    /** 1..5, 0 = not set */
    val mood: Int = 0,
    val energy: Int = 0,
    val starred: Boolean = false,
    val starTitle: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val hasContent get() = did.isNotBlank() || good.isNotBlank() || learned.isNotBlank() || free.isNotBlank() || mood > 0
}

enum class LinkKind { ACTIVITY, PERSON, PLACE }
enum class PersonCategory { FAMILY, FRIENDS, PARTNER, WORK, OTHER }

@Entity(tableName = "activities")
data class Activity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val emoji: String = "", val sort: Int = 0)

@Entity(tableName = "people")
data class Person(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val category: PersonCategory = PersonCategory.FRIENDS)

@Entity(tableName = "places")
data class Place(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String)

/** A chip on a day: an activity, a person or a place. */
@Entity(tableName = "links", primaryKeys = ["day", "kind", "refId"], indices = [Index("refId")])
data class Link(val day: Long, val kind: LinkKind, val refId: Long)

@Entity(tableName = "photos", indices = [Index("day")])
data class Photo(@PrimaryKey(autoGenerate = true) val id: Long = 0, val day: Long, val fileName: String)

@Entity(tableName = "recordings", indices = [Index("day")])
data class Recording(@PrimaryKey(autoGenerate = true) val id: Long = 0, val day: Long, val fileName: String, val seconds: Int)

/** Yearly event (birthday, anniversary). */
@Entity(tableName = "birthdays")
data class Birthday(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val month: Int, val dayOfMonth: Int, val year: Int? = null)

@Entity(tableName = "reviews")
data class Review(@PrimaryKey val weekStart: Long, val takeaway: String)

@Dao
interface JournalDao {
    @Query("SELECT * FROM entries ORDER BY day DESC") fun entries(): Flow<List<Entry>>
    @Query("SELECT * FROM activities ORDER BY sort, id") fun activities(): Flow<List<Activity>>
    @Query("SELECT * FROM people ORDER BY name") fun people(): Flow<List<Person>>
    @Query("SELECT * FROM places ORDER BY name") fun places(): Flow<List<Place>>
    @Query("SELECT * FROM links") fun links(): Flow<List<Link>>
    @Query("SELECT * FROM photos ORDER BY id") fun photos(): Flow<List<Photo>>
    @Query("SELECT * FROM recordings ORDER BY id") fun recordings(): Flow<List<Recording>>
    @Query("SELECT * FROM birthdays ORDER BY month, dayOfMonth") fun birthdays(): Flow<List<Birthday>>
    @Query("SELECT * FROM reviews ORDER BY weekStart DESC") fun reviews(): Flow<List<Review>>

    @Query("SELECT * FROM entries") suspend fun allEntries(): List<Entry>
    @Query("SELECT * FROM activities") suspend fun allActivities(): List<Activity>
    @Query("SELECT * FROM people") suspend fun allPeople(): List<Person>
    @Query("SELECT * FROM places") suspend fun allPlaces(): List<Place>
    @Query("SELECT * FROM links") suspend fun allLinks(): List<Link>
    @Query("SELECT * FROM photos") suspend fun allPhotos(): List<Photo>
    @Query("SELECT * FROM recordings") suspend fun allRecordings(): List<Recording>
    @Query("SELECT * FROM birthdays") suspend fun allBirthdays(): List<Birthday>
    @Query("SELECT * FROM reviews") suspend fun allReviews(): List<Review>
    @Query("SELECT COUNT(*) FROM activities") suspend fun activityCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveEntry(e: Entry)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveActivity(a: Activity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun savePerson(p: Person): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun savePlace(p: Place): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveLink(l: Link)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun savePhoto(p: Photo): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveRecording(r: Recording): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveBirthday(b: Birthday): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveReview(r: Review)

    @Query("DELETE FROM links WHERE day = :day AND kind = :kind AND refId = :refId") suspend fun deleteLink(day: Long, kind: String, refId: Long)
    @Query("DELETE FROM links WHERE kind = :kind AND refId = :refId") suspend fun deleteLinksTo(kind: String, refId: Long)
    @Query("DELETE FROM activities WHERE id = :id") suspend fun deleteActivity(id: Long)
    @Query("DELETE FROM people WHERE id = :id") suspend fun deletePerson(id: Long)
    @Query("DELETE FROM places WHERE id = :id") suspend fun deletePlace(id: Long)
    @Query("DELETE FROM photos WHERE id = :id") suspend fun deletePhoto(id: Long)
    @Query("DELETE FROM recordings WHERE id = :id") suspend fun deleteRecording(id: Long)
    @Query("DELETE FROM birthdays WHERE id = :id") suspend fun deleteBirthday(id: Long)

    @Insert suspend fun insertEntries(l: List<Entry>)
    @Insert suspend fun insertActivities(l: List<Activity>)
    @Insert suspend fun insertPeople(l: List<Person>)
    @Insert suspend fun insertPlaces(l: List<Place>)
    @Insert suspend fun insertLinks(l: List<Link>)
    @Insert suspend fun insertPhotos(l: List<Photo>)
    @Insert suspend fun insertRecordings(l: List<Recording>)
    @Insert suspend fun insertBirthdays(l: List<Birthday>)
    @Insert suspend fun insertReviews(l: List<Review>)
    @Query("DELETE FROM entries") suspend fun clearEntries()
    @Query("DELETE FROM activities") suspend fun clearActivities()
    @Query("DELETE FROM people") suspend fun clearPeople()
    @Query("DELETE FROM places") suspend fun clearPlaces()
    @Query("DELETE FROM links") suspend fun clearLinks()
    @Query("DELETE FROM photos") suspend fun clearPhotos()
    @Query("DELETE FROM recordings") suspend fun clearRecordings()
    @Query("DELETE FROM birthdays") suspend fun clearBirthdays()
    @Query("DELETE FROM reviews") suspend fun clearReviews()
}

@Database(
    entities = [Entry::class, Activity::class, Person::class, Place::class, Link::class, Photo::class, Recording::class, Birthday::class, Review::class],
    version = 1, exportSchema = false,
)
abstract class JournalDatabase : RoomDatabase() {
    abstract fun dao(): JournalDao
}

/** The whole journal in memory – small enough, and makes insights simple. */
data class Journal(
    val entries: List<Entry> = emptyList(),
    val activities: List<Activity> = emptyList(),
    val people: List<Person> = emptyList(),
    val places: List<Place> = emptyList(),
    val links: List<Link> = emptyList(),
    val photos: List<Photo> = emptyList(),
    val recordings: List<Recording> = emptyList(),
    val birthdays: List<Birthday> = emptyList(),
    val reviews: List<Review> = emptyList(),
) {
    private val byDay by lazy { entries.associateBy { it.day } }
    fun entry(day: Long) = byDay[day]
    fun linksOf(day: Long, kind: LinkKind) = links.filter { it.day == day && it.kind == kind }.map { it.refId }.toSet()
    fun photosOf(day: Long) = photos.filter { it.day == day }
    fun recordingsOf(day: Long) = recordings.filter { it.day == day }
    fun activity(id: Long) = activities.find { it.id == id }
    fun person(id: Long) = people.find { it.id == id }
    fun place(id: Long) = places.find { it.id == id }
    fun name(kind: LinkKind, id: Long) = when (kind) {
        LinkKind.ACTIVITY -> activity(id)?.let { "${it.emoji} ${it.name}".trim() }
        LinkKind.PERSON -> person(id)?.name
        LinkKind.PLACE -> place(id)?.name
    } ?: ""
    /** a day with anything written, a mood, a photo or a recording */
    fun written(day: Long) = entry(day)?.hasContent == true || photos.any { it.day == day } || recordings.any { it.day == day }
}

object DefaultActivities {
    val list = listOf(
        "עבודה" to "💼", "לימודים" to "📚", "מכון" to "🏋️", "טניס" to "🎾", "כדורגל" to "⚽", "רכיבה" to "🏍️",
        "זוגיות" to "❤️", "משפחה" to "👨‍👩‍👦", "חברים" to "🍻", "בילוי" to "🎉", "בישול" to "🍳", "קריאה" to "📖",
        "סרט / סדרה" to "🎬", "טיול" to "🥾", "מנוחה" to "😴", "סידורים" to "🧾",
    )
}

class Repo(val db: JournalDatabase) {
    val dao = db.dao()

    private data class A(val e: List<Entry>, val a: List<Activity>, val p: List<Person>, val pl: List<Place>, val l: List<Link>)

    fun journal(): Flow<Journal> = combine(
        combine(dao.entries(), dao.activities(), dao.people(), dao.places(), dao.links()) { e, a, p, pl, l -> A(e, a, p, pl, l) },
        dao.photos(), dao.recordings(), dao.birthdays(), dao.reviews(),
    ) { x, ph, r, b, rv -> Journal(x.e, x.a, x.p, x.pl, x.l, ph, r, b, rv) }

    suspend fun seedActivities() {
        if (dao.activityCount() == 0) {
            DefaultActivities.list.forEachIndexed { i, (n, e) -> dao.saveActivity(Activity(name = n, emoji = e, sort = i)) }
        }
    }

    suspend fun saveEntry(e: Entry) = dao.saveEntry(e.copy(updatedAt = System.currentTimeMillis()))
    suspend fun toggleLink(day: Long, kind: LinkKind, refId: Long, on: Boolean) {
        if (on) dao.saveLink(Link(day, kind, refId)) else dao.deleteLink(day, kind.name, refId)
    }
    suspend fun saveActivity(a: Activity) = dao.saveActivity(a)
    suspend fun deleteActivity(id: Long) = db.withTransaction { dao.deleteLinksTo(LinkKind.ACTIVITY.name, id); dao.deleteActivity(id) }
    suspend fun savePerson(p: Person) = dao.savePerson(p)
    suspend fun deletePerson(id: Long) = db.withTransaction { dao.deleteLinksTo(LinkKind.PERSON.name, id); dao.deletePerson(id) }
    suspend fun savePlace(p: Place) = dao.savePlace(p)
    suspend fun deletePlace(id: Long) = db.withTransaction { dao.deleteLinksTo(LinkKind.PLACE.name, id); dao.deletePlace(id) }
    suspend fun addPhoto(day: Long, fileName: String) = dao.savePhoto(Photo(day = day, fileName = fileName))
    suspend fun deletePhoto(p: Photo, context: Context) { Media.file(context, p.fileName).delete(); dao.deletePhoto(p.id) }
    suspend fun addRecording(day: Long, fileName: String, seconds: Int) = dao.saveRecording(Recording(day = day, fileName = fileName, seconds = seconds))
    suspend fun deleteRecording(r: Recording, context: Context) { Media.file(context, r.fileName).delete(); dao.deleteRecording(r.id) }
    suspend fun saveBirthday(b: Birthday) = dao.saveBirthday(b)
    suspend fun deleteBirthday(id: Long) = dao.deleteBirthday(id)
    suspend fun saveReview(r: Review) = dao.saveReview(r)
}

// ---------- settings ----------

enum class FontChoice { HAND, FORMAL, FLOW }

data class Settings(
    val font: FontChoice = FontChoice.HAND,
    val reminderHour: Int = 21,
    val reminderMinute: Int = 30,
    val lock: Boolean = true,
)

class SettingsStore(context: Context) {
    private val p = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(
        Settings(
            font = runCatching { FontChoice.valueOf(p.getString("font", "HAND")!!) }.getOrDefault(FontChoice.HAND),
            reminderHour = p.getInt("reminderHour", 21),
            reminderMinute = p.getInt("reminderMinute", 30),
            lock = p.getBoolean("lock", true),
        )
    )
    val flow: StateFlow<Settings> = state
    fun update(s: Settings) {
        p.edit().putString("font", s.font.name).putInt("reminderHour", s.reminderHour).putInt("reminderMinute", s.reminderMinute)
            .putBoolean("lock", s.lock).apply()
        state.value = s
    }
}
