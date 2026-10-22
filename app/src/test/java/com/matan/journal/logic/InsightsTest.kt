package com.matan.journal.logic

import com.matan.journal.data.Activity
import com.matan.journal.data.Entry
import com.matan.journal.data.Journal
import com.matan.journal.data.Link
import com.matan.journal.data.LinkKind
import com.matan.journal.data.Person
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class InsightsTest {
    private val today = LocalDate.of(2026, 10, 22)
    private val t = today.toEpochDay()

    @Test fun moodLinks_findBetterDays() {
        val entries = (0L until 10L).map { Entry(day = t - it, did = "x", mood = if (it < 4) 5 else 2) }
        val links = (0L until 4L).map { Link(t - it, LinkKind.PERSON, 1) } + listOf(Link(t - 8, LinkKind.PERSON, 2))
        val j = Journal(entries = entries, people = listOf(Person(1, "דני"), Person(2, "רון")), links = links)
        val r = Insights.moodLinks(j, LinkKind.PERSON)
        assertEquals(1, r.size) // רון has only one day
        assertEquals("דני", r[0].name)
        assertEquals(5.0, r[0].avg, 0.001)
        assertTrue(r[0].delta > 1.5)
    }

    @Test fun streak_countsUntilYesterday() {
        val entries = listOf(1L, 2L, 3L, 6L, 7L).map { Entry(day = t - it, did = "x") }
        val s = Insights.streak(Journal(entries = entries), t)
        assertEquals(3, s.current) // yesterday, 2 and 3 days ago
        assertEquals(3, s.best)
    }

    @Test fun memories_sameDateLastYear() {
        val e = Entry(day = today.minusYears(1).toEpochDay(), good = "טיול")
        val m = Insights.memories(Journal(entries = listOf(e)), today)
        assertEquals("לפני שנה", m.single().label)
    }

    @Test fun lessons_filterByActivity() {
        val entries = listOf(Entry(day = t, learned = "סרב טוב יותר"), Entry(day = t - 1, learned = "משהו אחר"))
        val j = Journal(entries = entries, activities = listOf(Activity(1, "טניס")), links = listOf(Link(t, LinkKind.ACTIVITY, 1)))
        assertEquals(listOf(t), Insights.lessons(j, 1).map { it.day })
        assertEquals(2, Insights.lessons(j).size)
    }

    @Test fun search_includesChips() {
        val j = Journal(entries = listOf(Entry(day = t, did = "עבודה")), people = listOf(Person(1, "נועה")), links = listOf(Link(t, LinkKind.PERSON, 1)))
        assertEquals(1, Insights.search(j, "נועה").size)
        assertEquals(0, Insights.search(j, "ים").size)
    }

    @Test fun weekStartsOnSunday() {
        assertEquals(LocalDate.of(2026, 10, 18).toEpochDay(), Insights.weekStart(today)) // Thursday -> Sunday 18/10
    }

    @Test fun echo_lastTimeAndFirstTime() {
        val j = Journal(
            entries = listOf(Entry(day = t - 9, learned = "הסרב נכנס"), Entry(day = t)),
            activities = listOf(Activity(1, "טניס")),
            links = listOf(Link(t - 9, LinkKind.ACTIVITY, 1), Link(t, LinkKind.ACTIVITY, 1)),
        )
        val last = Echo.last(j, LinkKind.ACTIVITY, 1, t)!!
        assertEquals(9L, last.daysAgo)
        assertEquals("הסרב נכנס", last.snippet)
        assertTrue(Echo.line(j, LinkKind.ACTIVITY, 1, t - 9).contains("פעם ראשונה"))
    }

    @Test fun reactions_monthMilestone() {
        val days = (0L until 5L).map { t - it }.filter { LocalDate.ofEpochDay(it).month == today.month }
        val j = Journal(
            entries = days.map { Entry(day = it, did = "x") },
            activities = listOf(Activity(1, "מכון")),
            links = days.map { Link(it, LinkKind.ACTIVITY, 1) },
        )
        assertTrue(Echo.reactions(j, t).any { it.contains("פעם ה-5") })
    }

    @Test fun lessons_comeBackAfterAWeek() {
        val j = Journal(entries = listOf(Entry(day = t - 7, learned = "לנשום לפני שעונים")))
        val l = Echo.lessons(j, t)
        assertEquals("לפני שבוע כתבת", l.single().label)
    }

    @Test fun memories_shortIntervals() {
        val j = Journal(entries = listOf(Entry(day = today.minusWeeks(1).toEpochDay(), good = "ים")))
        assertEquals("לפני שבוע בדיוק", Insights.memories(j, today).single().label)
    }
}
