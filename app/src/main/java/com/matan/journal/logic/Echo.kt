package com.matan.journal.logic

import com.matan.journal.data.Entry
import com.matan.journal.data.Journal
import com.matan.journal.data.LinkKind
import com.matan.journal.data.Photo
import java.time.LocalDate
import java.time.YearMonth

/** The journal "answering back": links today's page to what you wrote before. */
object Echo {

    fun ago(days: Long) = when {
        days <= 0 -> "היום"
        days == 1L -> "אתמול"
        days < 7 -> "לפני $days ימים"
        days < 14 -> "לפני שבוע"
        days < 30 -> "לפני ${days / 7} שבועות"
        days < 60 -> "לפני חודש"
        days < 365 -> "לפני ${days / 30} חודשים"
        days < 730 -> "לפני שנה"
        else -> "לפני ${days / 365} שנים"
    }

    private fun snippet(e: Entry?): String? =
        e?.let { listOf(it.learned, it.good, it.did, it.free).firstOrNull { s -> s.isNotBlank() } }
            ?.trim()?.let { if (it.length > 90) it.take(88).trimEnd() + "…" else it }

    data class Last(val day: Long, val daysAgo: Long, val snippet: String?, val photo: Photo?)

    /** The previous day (before [day]) with this chip. */
    fun last(j: Journal, kind: LinkKind, id: Long, day: Long): Last? {
        val prev = j.links.filter { it.kind == kind && it.refId == id && it.day < day }.maxOfOrNull { it.day } ?: return null
        return Last(prev, day - prev, snippet(j.entry(prev)), j.photosOf(prev).firstOrNull())
    }

    fun countInMonth(j: Journal, kind: LinkKind, id: Long, day: Long): Int {
        val ym = YearMonth.from(LocalDate.ofEpochDay(day))
        return j.links.count { it.kind == kind && it.refId == id && it.day <= day && YearMonth.from(LocalDate.ofEpochDay(it.day)) == ym }
    }

    fun countTotal(j: Journal, kind: LinkKind, id: Long, day: Long) =
        j.links.count { it.kind == kind && it.refId == id && it.day <= day }

    /** One line under a chip you just picked. */
    fun line(j: Journal, kind: LinkKind, id: Long, day: Long): String {
        val name = j.name(kind, id)
        val last = last(j, kind, id, day)
            ?: return when (kind) {
                LinkKind.ACTIVITY -> "✨ פעם ראשונה ביומן"
                LinkKind.PERSON -> "✨ $name מופיע ביומן לראשונה"
                LinkKind.PLACE -> "✨ פעם ראשונה כאן ביומן"
            }
        val month = countInMonth(j, kind, id, day)
        return when (kind) {
            LinkKind.ACTIVITY -> "בפעם הקודמת: ${ago(last.daysAgo)}" + if (month > 1) " · $month פעמים החודש" else ""
            LinkKind.PERSON -> (if (month > 1) "$month פעמים החודש · " else "") + "בפעם הקודמת: ${ago(last.daysAgo)}"
            LinkKind.PLACE -> "היית כאן לאחרונה ${ago(last.daysAgo)}"
        }
    }

    // ---------- reactions after writing ----------

    private val streakMarks = setOf(3, 7, 14, 21, 30, 50, 75, 100, 150, 200, 300, 365, 500)
    private val monthMarks = setOf(5, 10, 15, 20, 25)
    private val totalMarks = setOf(10, 25, 50, 100, 200, 365)
    private val pageMarks = setOf(1, 7, 30, 50, 100, 200, 365, 500, 1000)

    /** Up to three small "the journal noticed" lines for [day]. */
    fun reactions(j: Journal, day: Long): List<String> {
        val out = mutableListOf<String>()
        val e = j.entry(day)

        val streak = Insights.streak(j, day).current
        if (j.written(day) && streak in streakMarks) out += "🔥 $streak ימים ברצף!"

        val pages = j.entries.count { it.day <= day && j.written(it.day) }
        if (j.written(day) && pages in pageMarks) out += if (pages == 1) "📔 הדף הראשון ביומן. ברוך הבא!" else "📔 הדף ה-$pages שלך ביומן"

        j.linksOf(day, LinkKind.ACTIVITY).forEach { id ->
            val name = j.name(LinkKind.ACTIVITY, id)
            val total = countTotal(j, LinkKind.ACTIVITY, id, day)
            val month = countInMonth(j, LinkKind.ACTIVITY, id, day)
            when {
                total == 1 && pages > 1 -> out += "✨ פעם ראשונה שכתבת על $name"
                month in monthMarks -> out += "💪 פעם ה-$month החודש: $name"
                total in totalMarks -> out += "🏅 $name – בפעם ה-$total ביומן"
            }
        }
        j.linksOf(day, LinkKind.PERSON).forEach { id ->
            val name = j.name(LinkKind.PERSON, id)
            val month = countInMonth(j, LinkKind.PERSON, id, day)
            if (month in monthMarks) out += "🤝 כבר $month פעמים עם $name החודש"
        }

        if (e != null && e.mood == 5) {
            val ws = day - (LocalDate.ofEpochDay(day).dayOfWeek.value % 7)
            val greatThisWeek = (ws..day).count { j.entry(it)?.mood == 5 }
            val bestWeekBefore = j.entries.filter { it.mood == 5 && it.day < ws }
                .groupingBy { it.day - (LocalDate.ofEpochDay(it.day).dayOfWeek.value % 7) }.eachCount().values.maxOrNull() ?: 0
            if (greatThisWeek >= 2 && greatThisWeek > bestWeekBefore) out += "😄 $greatThisWeek ימים מצוינים השבוע – הכי הרבה מאז שהתחלת"
        }

        if (e != null && e.intentionDone == 1) {
            val n = j.entries.count { it.intentionDone == 1 && it.day <= day }
            out += if (n == 1) "🎯 עמדת בכוונה של אתמול" else "🎯 עמדת בכוונה של אתמול – כבר $n פעמים"
        }
        return out.distinct().take(3)
    }

    // ---------- lessons coming back ----------

    data class LessonBack(val entry: Entry, val label: String)

    private val intervals = listOf(7L to "לפני שבוע", 30L to "לפני חודש", 90L to "לפני שלושה חודשים", 180L to "לפני חצי שנה", 365L to "לפני שנה")

    /**
     * Lessons resurface on a spaced schedule (1 week, 1, 3, 6, 12 months),
     * and one lesson from a past day with the same activity as today.
     */
    fun lessons(j: Journal, day: Long): List<LessonBack> {
        val out = mutableListOf<LessonBack>()
        intervals.forEach { (n, label) ->
            j.entry(day - n)?.takeIf { it.learned.isNotBlank() }?.let { out += LessonBack(it, "$label כתבת") }
        }
        j.linksOf(day, LinkKind.ACTIVITY).forEach { act ->
            val candidates = j.entries.filter {
                it.learned.isNotBlank() && it.day < day - 6 && act in j.linksOf(it.day, LinkKind.ACTIVITY) && out.none { o -> o.entry.day == it.day }
            }.sortedBy { it.day }
            if (candidates.isNotEmpty()) {
                val pick = candidates[(day % candidates.size).toInt()]
                out += LessonBack(pick, "גם ב${j.activity(act)?.name ?: "פעילות הזו"}, ${ago(day - pick.day)}")
            }
        }
        return out.distinctBy { it.entry.day }.take(2)
    }
}
