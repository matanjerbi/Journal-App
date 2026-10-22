package com.matan.journal.logic

import com.matan.journal.data.Entry
import com.matan.journal.data.Journal
import com.matan.journal.data.LinkKind
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** Average mood on days with something (a person, place or activity), compared to all days. */
data class MoodLink(val kind: LinkKind, val refId: Long, val name: String, val avg: Double, val days: Int, val delta: Double)

data class Streak(val current: Int, val best: Int)

data class Memory(val entry: Entry, val label: String)

object Insights {
    const val MIN_DAYS = 3

    fun overallMood(j: Journal): Double? = j.entries.filter { it.mood > 0 }.map { it.mood }.average().takeIf { !it.isNaN() }

    /** Things that go with better (or worse) days. Needs at least [MIN_DAYS] rated days each. */
    fun moodLinks(j: Journal, kind: LinkKind, min: Int = MIN_DAYS): List<MoodLink> {
        val overall = overallMood(j) ?: return emptyList()
        val moodByDay = j.entries.filter { it.mood > 0 }.associate { it.day to it.mood }
        return j.links.filter { it.kind == kind }.groupBy { it.refId }.mapNotNull { (ref, links) ->
            val moods = links.mapNotNull { moodByDay[it.day] }
            if (moods.size < min) return@mapNotNull null
            val avg = moods.average()
            MoodLink(kind, ref, j.name(kind, ref), avg, moods.size, avg - overall)
        }.sortedByDescending { it.avg }
    }

    /** How often each chip appears (all time), most common first. */
    fun counts(j: Journal, kind: LinkKind): List<Pair<String, Int>> =
        j.links.filter { it.kind == kind }.groupingBy { it.refId }.eachCount()
            .map { (id, n) -> j.name(kind, id) to n }.sortedByDescending { it.second }

    fun weekdayMood(j: Journal): Map<DayOfWeek, Double> =
        j.entries.filter { it.mood > 0 }.groupBy { LocalDate.ofEpochDay(it.day).dayOfWeek }.mapValues { e -> e.value.map { it.mood }.average() }

    /** Consecutive written days. A streak is still "current" if today isn't written yet but yesterday is. */
    fun streak(j: Journal, today: Long): Streak {
        val days = (j.entries.filter { it.hasContent }.map { it.day } + j.photos.map { it.day } + j.recordings.map { it.day }).toSortedSet()
        var best = 0; var run = 0; var prev: Long? = null
        for (d in days) {
            run = if (prev != null && d == prev + 1) run + 1 else 1
            best = maxOf(best, run); prev = d
        }
        var cur = 0
        var d = if (today in days) today else today - 1
        while (d in days) { cur++; d-- }
        return Streak(cur, best)
    }

    /** Mood per day for the last [n] days (0 = no rating), oldest first. */
    fun moodSeries(j: Journal, today: Long, n: Int = 30): List<Int> = (n - 1 downTo 0).map { j.entry(today - it)?.mood ?: 0 }

    /** Share of intentions that happened (yesterday's intention marked on the next day). */
    fun intentionRate(j: Journal): Pair<Int, Int> {
        val known = j.entries.filter { it.intentionDone >= 0 }
        return known.count { it.intentionDone == 1 } to known.size
    }

    /** "On this day": a week, 1/3/6 months ago, and the same date in earlier years. */
    fun memories(j: Journal, today: LocalDate): List<Memory> {
        val out = mutableListOf<Memory>()
        fun add(d: LocalDate, label: String) {
            j.entry(d.toEpochDay())?.takeIf { it.hasContent || j.photosOf(it.day).isNotEmpty() }?.let { out += Memory(it, label) }
        }
        add(today.minusWeeks(1), "לפני שבוע בדיוק")
        add(today.minusMonths(1), "לפני חודש")
        add(today.minusMonths(3), "לפני שלושה חודשים")
        add(today.minusMonths(6), "לפני חצי שנה")
        (1..10).forEach { y -> add(today.minusYears(y.toLong()), if (y == 1) "לפני שנה" else "לפני $y שנים") }
        return out
    }

    /** One good day from the past (rated 5, starred or with a photo), a different one each day. */
    fun goodDay(j: Journal, today: LocalDate): Memory? {
        val t = today.toEpochDay()
        val candidates = j.entries.filter { it.day <= t - 14 && (it.mood == 5 || it.starred || (it.mood >= 4 && j.photosOf(it.day).isNotEmpty())) }
            .sortedBy { it.day }
        if (candidates.isEmpty()) return null
        return Memory(candidates[(t % candidates.size).toInt()], "יום טוב מהעבר")
    }

    /** Lessons ("what I learned") newest first, optionally only days with a given activity. */
    fun lessons(j: Journal, activityId: Long? = null): List<Entry> = j.entries
        .filter { it.learned.isNotBlank() && (activityId == null || activityId in j.linksOf(it.day, LinkKind.ACTIVITY)) }
        .sortedByDescending { it.day }

    fun weekStart(d: LocalDate): Long = d.toEpochDay() - (d.dayOfWeek.value % 7) // Sunday

    data class Week(val start: Long, val written: Int, val avgMood: Double?, val good: List<Entry>, val lessons: List<Entry>, val topActivities: List<Pair<String, Int>>)

    fun week(j: Journal, start: Long): Week {
        val days = (start until start + 7)
        val es = days.mapNotNull { j.entry(it) }
        val acts = j.links.filter { it.kind == LinkKind.ACTIVITY && it.day in days }.groupingBy { it.refId }.eachCount()
            .map { (id, n) -> j.name(LinkKind.ACTIVITY, id) to n }.sortedByDescending { it.second }.take(5)
        return Week(
            start = start,
            written = days.count { j.written(it) },
            avgMood = es.filter { it.mood > 0 }.map { it.mood }.average().takeIf { !it.isNaN() },
            good = es.filter { it.good.isNotBlank() },
            lessons = es.filter { it.learned.isNotBlank() },
            topActivities = acts,
        )
    }

    fun monthPhotos(j: Journal, ym: YearMonth) = j.photos.filter { YearMonth.from(LocalDate.ofEpochDay(it.day)) == ym }.sortedBy { it.day }

    /** Full-text search across the page and its chips. */
    fun search(j: Journal, q: String): List<Entry> {
        val s = q.trim()
        if (s.isEmpty()) return j.entries
        return j.entries.filter { e ->
            listOf(e.did, e.good, e.learned, e.free, e.intention, e.starTitle).any { it.contains(s, true) } ||
                j.links.any { it.day == e.day && j.name(it.kind, it.refId).contains(s, true) }
        }
    }
}

val moods = listOf("😞", "🙁", "😐", "🙂", "😄")
val energies = listOf("🪫", "😮‍💨", "🙂", "⚡", "🔥")
