package com.matan.journal.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.matan.journal.data.Entry
import com.matan.journal.data.Journal
import com.matan.journal.data.LinkKind
import com.matan.journal.data.Media
import com.matan.journal.logic.Insights
import com.matan.journal.logic.MoodLink
import com.matan.journal.ui.AppViewModel
import com.matan.journal.ui.Loading
import com.matan.journal.ui.MoodColors
import com.matan.journal.ui.MoodDot
import com.matan.journal.ui.PaperCard
import com.matan.journal.ui.Routes
import com.matan.journal.ui.Screen
import com.matan.journal.ui.SectionTitle
import com.matan.journal.ui.Thumb
import com.matan.journal.ui.fmtDay
import com.matan.journal.ui.fmtMonth
import com.matan.journal.ui.rememberBitmap
import com.matan.journal.ui.today
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

@Composable
internal fun SettingsAction(nav: NavController) {
    IconButton(onClick = { nav.navigate(Routes.SETTINGS) }) { Icon(Icons.Default.Settings, "הגדרות") }
}

// ======================= Today =======================

@Composable
fun TodayScreen(vm: AppViewModel, nav: NavController) {
    val j = vm.journal.collectAsStateWithLifecycle().value ?: return Loading()
    val date = LocalDate.now()
    val t = date.toEpochDay()
    val streak = Insights.streak(j, t)
    val memories = Insights.memories(j, date)
    val birthdays = j.birthdays.filter { it.month == date.monthValue && it.dayOfMonth == date.dayOfMonth }
    val missing = (1..EDIT_DAYS).map { t - it }.filter { d -> !j.written(d) && j.entries.any { it.day < d } }
    val hello = when (java.time.LocalTime.now().hour) { in 5..11 -> "בוקר טוב"; in 12..16 -> "צהריים טובים"; in 17..21 -> "ערב טוב"; else -> "לילה טוב" }

    Screen("", tab = true, actions = { SettingsAction(nav) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text("$hello,", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(fmtDay(t), style = MaterialTheme.typography.headlineMedium)
                if (streak.current > 0) Text("🔥 ${streak.current} ימים ברצף" + if (streak.best > streak.current) " · שיא: ${streak.best}" else " · השיא שלך!",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            birthdays.forEach {
                PaperCard(color = MaterialTheme.colorScheme.primaryContainer) {
                    Text("🎂 היום יום ההולדת של ${it.name}" + (it.year?.let { y -> " (${date.year - y})" } ?: ""), Modifier.padding(14.dp),
                        style = MaterialTheme.typography.titleMedium)
                }
            }
            if (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) {
                val ws = if (date.dayOfWeek == DayOfWeek.SATURDAY) Insights.weekStart(date) else Insights.weekStart(date) - 7
                if (j.reviews.none { it.weekStart == ws }) {
                    PaperCard(onClick = { nav.navigate(Routes.week(ws)) }, color = MaterialTheme.colorScheme.tertiaryContainer) {
                        Column(Modifier.padding(14.dp)) {
                            Text("🗓️ הסקירה השבועית מחכה", style = MaterialTheme.typography.titleMedium)
                            Text("מה למדת השבוע ומה אתה לוקח לשבוע הבא?", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            // lessons coming back on a spaced schedule
            com.matan.journal.logic.Echo.lessons(j, t).forEach { l ->
                PaperCard(onClick = { nav.navigate(Routes.entry(l.entry.day)) }, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Column(Modifier.padding(14.dp)) {
                        Text("📒 ${l.label}:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(l.entry.learned, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
            // memories: a week / months / years ago, and one good day from the past
            (memories.take(2) + listOfNotNull(Insights.goodDay(j, date)))
                .distinctBy { it.entry.day }
                .forEach { m -> MemoryCard(j, m.entry, m.label) { nav.navigate(Routes.entry(m.entry.day)) } }
            if (missing.isNotEmpty()) {
                PaperCard(color = MaterialTheme.colorScheme.secondaryContainer) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("✍️ ימים שעוד אפשר להשלים", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            missing.take(4).forEach { d -> OutlinedButton(onClick = { nav.navigate(Routes.entry(d)) }) { Text(com.matan.journal.ui.weekdayShort(d)) } }
                        }
                    }
                }
            }
            EntryEditor(vm, j, t, editable = true) { nav.navigate(Routes.entry(it)) }
        }
    }
}

@Composable
fun MemoryCard(j: Journal, e: Entry, label: String, onClick: () -> Unit) {
    val context = LocalContext.current
    val photo = j.photosOf(e.day).firstOrNull()
    PaperCard(onClick = onClick, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (photo != null) {
                val bmp = rememberBitmap(photo.fileName) { Media.thumb(context, photo.fileName, 300) }
                Thumb(bmp, 72.dp)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("✨ $label", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(fmtDay(e.day), style = MaterialTheme.typography.titleSmall)
                val text = listOf(e.good, e.did, e.free).firstOrNull { it.isNotBlank() }
                if (text != null) Text(text, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            }
            MoodDot(e.mood)
        }
    }
}

// ======================= Timeline =======================

@Composable
fun TimelineScreen(vm: AppViewModel, nav: NavController) {
    val j = vm.journal.collectAsStateWithLifecycle().value ?: return Loading()
    val context = LocalContext.current
    var q by rememberSaveable { mutableStateOf("") }
    val list = Insights.search(j, q).filter { j.written(it.day) || it.starred }.sortedByDescending { it.day }
    val byMonth = list.groupBy { YearMonth.from(LocalDate.ofEpochDay(it.day)) }

    Screen("היומן שלי", tab = true, actions = { SettingsAction(nav) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(16.dp),
                    label = { Text("חיפוש – מילה, אדם, מקום או פעילות") })
            }
            if (list.isEmpty()) item {
                Text(if (q.isBlank()) "היומן עוד ריק. הדף הראשון מחכה לך בלשונית \"היום\" ✍️" else "לא נמצא", Modifier.padding(24.dp))
            }
            byMonth.forEach { (ym, entries) ->
                item(key = "m_$ym") { SectionTitle("${fmtMonth(ym.atDay(1))} · ${entries.size} ימים") }
                items(entries, key = { it.day }) { e ->
                    val photo = j.photosOf(e.day).firstOrNull()
                    val chips = j.links.filter { it.day == e.day }.map { j.name(it.kind, it.refId) }.filter { it.isNotBlank() }
                    PaperCard(onClick = { nav.navigate(Routes.entry(e.day)) }) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(fmtDay(e.day), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                    if (e.starred) Text("⭐")
                                    if (j.recordingsOf(e.day).isNotEmpty()) Text("🎙️")
                                }
                                if (e.starred && e.starTitle.isNotBlank()) Text(e.starTitle, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                                val text = listOf(e.did, e.good, e.free).firstOrNull { it.isNotBlank() }
                                if (text != null) Text(text, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                if (chips.isNotEmpty()) Text(chips.take(6).joinToString(" · "), style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(start = 10.dp)) {
                                MoodDot(e.mood)
                                if (photo != null) {
                                    val bmp = rememberBitmap(photo.fileName) { Media.thumb(context, photo.fileName, 300) }
                                    Thumb(bmp, 76.dp, Modifier.padding(top = 6.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ======================= Insights =======================

@Composable
fun InsightsScreen(vm: AppViewModel, nav: NavController) {
    val j = vm.journal.collectAsStateWithLifecycle().value ?: return Loading()
    val t = today()
    val streak = Insights.streak(j, t)
    val written = j.entries.count { j.written(it.day) }
    val overall = Insights.overallMood(j)
    val links = Insights.moodLinks(j, LinkKind.ACTIVITY) + Insights.moodLinks(j, LinkKind.PERSON) + Insights.moodLinks(j, LinkKind.PLACE)
    val best = links.filter { it.delta > 0.2 }.sortedByDescending { it.delta }.take(6)
    val worst = links.filter { it.delta < -0.2 }.sortedBy { it.delta }.take(3)
    val series = Insights.moodSeries(j, t)
    val weekday = Insights.weekdayMood(j)
    val (done, known) = Insights.intentionRate(j)

    Screen("תובנות", tab = true, actions = { SettingsAction(nav) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat("🔥 ${streak.current}", "ימים ברצף", Modifier.weight(1f))
                    Stat("$written", "ימים ביומן", Modifier.weight(1f))
                    Stat(overall?.let { "%.1f".format(it) } ?: "–", "מצב רוח ממוצע", Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PaperCard(Modifier.weight(1f), onClick = { nav.navigate(Routes.week(Insights.weekStart(LocalDate.now()))) }, color = MaterialTheme.colorScheme.tertiaryContainer) {
                        Column(Modifier.padding(14.dp)) { Text("🗓️ השבוע שלי", style = MaterialTheme.typography.titleMedium); Text("סקירה ולקח לשבוע הבא", style = MaterialTheme.typography.bodySmall) }
                    }
                    PaperCard(Modifier.weight(1f), onClick = { nav.navigate(Routes.LESSONS) }, color = MaterialTheme.colorScheme.secondaryContainer) {
                        Column(Modifier.padding(14.dp)) { Text("📒 ספר הלקחים", style = MaterialTheme.typography.titleMedium); Text("כל מה שלמדת", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            if (j.entries.count { it.mood > 0 } < Insights.MIN_DAYS) item {
                PaperCard { Text("אחרי כמה ימים עם דירוג מצב רוח יופיעו כאן דפוסים – עם מי, איפה ומה עושה לך טוב 🌱", Modifier.padding(16.dp)) }
            } else {
                item { SectionTitle("30 הימים האחרונים") }
                item { PaperCard { MoodChart(series, Modifier.padding(14.dp)) } }
                if (best.isNotEmpty()) {
                    item { SectionTitle("מה עושה לך טוב") }
                    items(best, key = { "b_${it.kind}_${it.refId}" }) { LinkRow(it) }
                }
                if (worst.isNotEmpty()) {
                    item { SectionTitle("ימים פחות טובים") }
                    items(worst, key = { "w_${it.kind}_${it.refId}" }) { LinkRow(it) }
                }
                if (weekday.size >= 3) {
                    item { SectionTitle("לפי ימי השבוע") }
                    item { PaperCard { WeekdayBars(weekday, Modifier.padding(14.dp)) } }
                }
            }
            if (known > 0) item {
                PaperCard {
                    Column(Modifier.padding(14.dp)) {
                        Text("🎯 כוונות למחר", style = MaterialTheme.typography.titleMedium)
                        Text("$done מתוך $known קרו (${done * 100 / known}%)")
                    }
                }
            }
            val top = Insights.counts(j, LinkKind.ACTIVITY).take(8)
            if (top.isNotEmpty()) {
                item { SectionTitle("מה ממלא לך את הימים") }
                item {
                    PaperCard {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val max = top.first().second.toFloat()
                            top.forEach { (name, n) ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(name, Modifier.weight(0.4f), maxLines = 1)
                                    Box(Modifier.weight(0.5f).height(10.dp)) {
                                        Box(Modifier.fillMaxWidth(n / max).height(10.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), RoundedCornerShape(5.dp)))
                                    }
                                    Text(" $n", Modifier.weight(0.1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier.background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(18.dp)).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LinkRow(l: MoodLink) {
    val kind = when (l.kind) { LinkKind.ACTIVITY -> ""; LinkKind.PERSON -> "👤 "; LinkKind.PLACE -> "📍 " }
    PaperCard {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(kind + l.name, style = MaterialTheme.typography.titleMedium)
                Text("${l.days} ימים · ממוצע ${"%.1f".format(l.avg)}", style = MaterialTheme.typography.bodySmall)
            }
            val up = l.delta > 0
            Text((if (up) "+" else "") + "%.1f".format(l.delta), color = if (up) MoodColors[4] else MoodColors[0], style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun MoodChart(series: List<Int>, modifier: Modifier) {
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier.fillMaxWidth().height(120.dp)) {
        val step = size.width / (series.size - 1).coerceAtLeast(1)
        fun y(m: Int) = size.height - (m - 1) / 4f * size.height
        (1..5).forEach { m -> drawLine(grid, Offset(0f, y(m)), Offset(size.width, y(m)), 1f) }
        val path = Path()
        var started = false
        // newest on the left side reads naturally in RTL? keep time flowing right-to-left: today at the left edge
        series.forEachIndexed { i, m ->
            if (m <= 0) return@forEachIndexed
            val x = size.width - i * step
            if (!started) { path.moveTo(x, y(m)); started = true } else path.lineTo(x, y(m))
        }
        drawPath(path, line, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        series.forEachIndexed { i, m -> if (m > 0) drawCircle(MoodColors[m - 1], 4.dp.toPx(), Offset(size.width - i * step, y(m))) }
    }
}

@Composable
private fun WeekdayBars(data: Map<DayOfWeek, Double>, modifier: Modifier) {
    val order = listOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)
    val names = listOf("א", "ב", "ג", "ד", "ה", "ו", "ש")
    Row(modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
        order.forEachIndexed { i, d ->
            val v = data[d]
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (v != null) {
                    Text("%.1f".format(v), style = MaterialTheme.typography.labelSmall)
                    val c = MoodColors[(v.toInt() - 1).coerceIn(0, 4)]
                    Box(Modifier.padding(top = 2.dp).width(22.dp).height((v / 5.0 * 80).dp)
                        .background(Brush.verticalGradient(listOf(c, c.copy(alpha = 0.4f))), RoundedCornerShape(6.dp)))
                }
                Text(names[i], style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

