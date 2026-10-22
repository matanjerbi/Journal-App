package com.matan.journal.ui.screens

import android.app.TimePickerDialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.matan.journal.Reminders
import com.matan.journal.data.Backup
import com.matan.journal.data.Birthday
import com.matan.journal.data.FontChoice
import com.matan.journal.data.LinkKind
import com.matan.journal.data.Media
import com.matan.journal.data.Review
import com.matan.journal.logic.Insights
import com.matan.journal.ui.AppViewModel
import com.matan.journal.ui.ConfirmDialog
import com.matan.journal.ui.Loading
import com.matan.journal.ui.NotebookField
import com.matan.journal.ui.PaperCard
import com.matan.journal.ui.Routes
import com.matan.journal.ui.Screen
import com.matan.journal.ui.SectionTitle
import com.matan.journal.ui.SwitchRow
import com.matan.journal.ui.Thumb
import com.matan.journal.ui.fmtDay
import com.matan.journal.ui.fmtMonth
import com.matan.journal.ui.fmtShort
import com.matan.journal.ui.fontFamily
import com.matan.journal.ui.fontLabel
import com.matan.journal.ui.rememberBitmap
import com.matan.journal.ui.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

// ======================= Lessons =======================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LessonsScreen(vm: AppViewModel, nav: NavController) {
    val j = vm.journal.collectAsStateWithLifecycle().value ?: return Loading()
    var filter by rememberSaveable { mutableLongStateOf(0L) }
    val lessons = Insights.lessons(j, filter.takeIf { it > 0 })
    val withLessons = j.activities.filter { a -> Insights.lessons(j, a.id).isNotEmpty() }

    Screen("📒 ספר הלקחים", nav) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (withLessons.isNotEmpty()) item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(filter == 0L, { filter = 0 }, { Text("הכל") })
                    withLessons.forEach { a -> FilterChip(filter == a.id, { filter = a.id }, { Text("${a.emoji} ${a.name}".trim()) }) }
                }
            }
            if (lessons.isEmpty()) item { Text("עוד אין לקחים. כל תשובה ל\"מה למדתי\" נאספת לכאן.", Modifier.padding(16.dp)) }
            lessons.groupBy { YearMonth.from(LocalDate.ofEpochDay(it.day)) }.forEach { (ym, list) ->
                item(key = "h_$ym") { SectionTitle(fmtMonth(ym.atDay(1))) }
                items(list, key = { it.day }) { e ->
                    PaperCard(onClick = { nav.navigate(Routes.entry(e.day)) }) {
                        Column(Modifier.padding(14.dp)) {
                            Text(fmtShort(e.day), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Text(e.learned, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}

// ======================= Weekly review =======================

@Composable
fun WeekScreen(vm: AppViewModel, nav: NavController, start: Long) {
    val j = vm.journal.collectAsStateWithLifecycle().value ?: return Loading()
    val w = Insights.week(j, start)
    val saved = j.reviews.find { it.weekStart == start }?.takeaway ?: ""
    var takeaway by remember(start) { mutableStateOf(saved) }
    val prev = j.reviews.find { it.weekStart == start - 7 }?.takeaway
    androidx.compose.runtime.LaunchedEffect(takeaway) { delay(700); if (takeaway != saved) vm.launch { vm.repo.saveReview(Review(start, takeaway)) } }

    Screen("השבוע ${fmtShort(start)}–${fmtShort(start + 6)}", nav, actions = {
        IconButton(onClick = { nav.navigate(Routes.week(start - 7)) { popUpTo(Routes.week(start)) { inclusive = true } } }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "שבוע קודם") }
        IconButton(enabled = start + 7 <= LocalDate.now().toEpochDay(), onClick = { nav.navigate(Routes.week(start + 7)) { popUpTo(Routes.week(start)) { inclusive = true } } }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "שבוע הבא")
        }
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PaperCard(color = MaterialTheme.colorScheme.tertiaryContainer) {
                Column(Modifier.padding(16.dp)) {
                    Text("כתבת ${w.written} מתוך 7 ימים" + (w.avgMood?.let { " · מצב רוח ממוצע ${"%.1f".format(it)}" } ?: ""), style = MaterialTheme.typography.titleMedium)
                    if (w.topActivities.isNotEmpty()) Text(w.topActivities.joinToString(" · ") { "${it.first} ×${it.second}" }, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (prev != null && prev.isNotBlank()) {
                PaperCard { Column(Modifier.padding(14.dp)) { Text("בשבוע שעבר לקחת:", style = MaterialTheme.typography.labelLarge); Text(prev) } }
            }
            if (w.good.isNotEmpty()) {
                SectionTitle("מה היה טוב")
                w.good.forEach { e -> Text("• ${com.matan.journal.ui.weekdayShort(e.day)}: ${e.good}", Modifier.clickable { nav.navigate(Routes.entry(e.day)) }) }
            }
            if (w.lessons.isNotEmpty()) {
                SectionTitle("מה למדתי")
                w.lessons.forEach { e -> Text("• ${e.learned}") }
            }
            NotebookField("מה אני לוקח לשבוע הבא?", "דבר אחד שאני רוצה לשמור או לשנות", takeaway, { takeaway = it })
        }
    }
}

// ======================= Memories =======================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemoriesScreen(vm: AppViewModel, nav: NavController) {
    val j = vm.journal.collectAsStateWithLifecycle().value ?: return Loading()
    val context = LocalContext.current
    val date = LocalDate.now()
    var month by remember { mutableStateOf(YearMonth.from(date)) }
    var addBirthday by remember { mutableStateOf(false) }
    var deleteBirthday by remember { mutableStateOf<Birthday?>(null) }
    val memories = Insights.memories(j, date)
    val starred = j.entries.filter { it.starred }.sortedByDescending { it.day }
    val album = Insights.monthPhotos(j, month)

    Screen("זיכרונות", tab = true, actions = { SettingsAction(nav) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { SectionTitle("היום, בשנים קודמות") }
            if (memories.isEmpty()) item { Text("עוד אין זיכרונות מהתאריך הזה. בשנה הבאה הדף של היום יופיע כאן ✨", style = MaterialTheme.typography.bodyMedium) }
            items(memories, key = { "m_${it.entry.day}" }) { m -> MemoryCard(j, m.entry, m.label) { nav.navigate(Routes.entry(m.entry.day)) } }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("האלבום של ${fmtMonth(month.atDay(1))}", Modifier.weight(1f))
                    IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "חודש קודם") }
                    IconButton(enabled = month < YearMonth.from(date), onClick = { month = month.plusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "חודש הבא") }
                }
            }
            item {
                if (album.isEmpty()) Text("אין תמונות בחודש הזה", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    album.forEach { p ->
                        val bmp = rememberBitmap(p.fileName) { Media.thumb(context, p.fileName, 300) }
                        Thumb(bmp, 104.dp, Modifier.clickable { nav.navigate(Routes.entry(p.day)) })
                    }
                }
            }

            if (starred.isNotEmpty()) {
                item { SectionTitle("⭐ ימים מיוחדים") }
                items(starred, key = { "s_${it.day}" }) { e ->
                    PaperCard(onClick = { nav.navigate(Routes.entry(e.day)) }) {
                        Column(Modifier.padding(12.dp)) {
                            Text(e.starTitle.ifBlank { "יום מיוחד" }, style = MaterialTheme.typography.titleMedium)
                            Text(fmtDay(e.day), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            item { SectionTitle("🎂 ימי הולדת") }
            items(j.birthdays, key = { "b_${it.id}" }) { b ->
                PaperCard(onClick = { deleteBirthday = b }) {
                    Row(Modifier.padding(12.dp)) {
                        Text(b.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Text("${b.dayOfMonth}.${b.month}" + (b.year?.let { ".$it" } ?: ""))
                    }
                }
            }
            item { OutlinedButton(onClick = { addBirthday = true }) { Text("+ יום הולדת") } }
        }
    }

    if (addBirthday) BirthdayDialog(onDismiss = { addBirthday = false }) { vm.launch { vm.repo.saveBirthday(it) } }
    deleteBirthday?.let { b ->
        ConfirmDialog("למחוק את יום ההולדת של ${b.name}?", onConfirm = { vm.launch { vm.repo.deleteBirthday(b.id) } }, onDismiss = { deleteBirthday = null })
    }
}

@Composable
private fun BirthdayDialog(onDismiss: () -> Unit, onSave: (Birthday) -> Unit) {
    var name by remember { mutableStateOf("") }
    var d by remember { mutableStateOf("") }
    var m by remember { mutableStateOf("") }
    var y by remember { mutableStateOf("") }
    val day = d.toIntOrNull(); val month = m.toIntOrNull()
    val ok = name.isNotBlank() && day != null && month != null && month in 1..12 && day in 1..31
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("יום הולדת") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("של מי") })
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val num = KeyboardOptions(keyboardType = KeyboardType.Number)
                    OutlinedTextField(d, { d = it }, Modifier.weight(1f), singleLine = true, label = { Text("יום") }, keyboardOptions = num)
                    OutlinedTextField(m, { m = it }, Modifier.weight(1f), singleLine = true, label = { Text("חודש") }, keyboardOptions = num)
                    OutlinedTextField(y, { y = it }, Modifier.weight(1.3f), singleLine = true, label = { Text("שנה") }, keyboardOptions = num)
                }
            }
        },
        confirmButton = { TextButton(enabled = ok, onClick = { onSave(Birthday(name = name.trim(), month = month!!, dayOfMonth = day!!, year = y.toIntOrNull())); onDismiss() }) { Text("שמור") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}

// ======================= Settings =======================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: AppViewModel, nav: NavController) {
    val j = vm.journal.collectAsStateWithLifecycle().value ?: return Loading()
    val s by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var passwordFor by remember { mutableStateOf<String?>(null) } // "export" / "import"
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var removing by remember { mutableStateOf<Pair<LinkKind, Long>?>(null) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) { pendingUri = uri; passwordFor = "export" }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { pendingUri = uri; passwordFor = "import" }
    }

    Screen("הגדרות", nav) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("✒️ גופן")
            FontChoice.entries.forEach { f ->
                PaperCard(onClick = { vm.settingsStore.update(s.copy(font = f)) },
                    color = if (s.font == f) MaterialTheme.colorScheme.primaryContainer else null) {
                    Column(Modifier.padding(14.dp)) {
                        Text(fontLabel(f) + if (s.font == f) " ✓" else "", style = MaterialTheme.typography.labelLarge)
                        Text("היום היה יום טוב, למדתי משהו חדש",
                            style = TextStyle(fontFamily = fontFamily(f), fontSize = if (f == FontChoice.HAND) 26.sp else 19.sp))
                    }
                }
            }

            SectionTitle("🔔 תזכורת ערב")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("שעה: %02d:%02d".format(s.reminderHour, s.reminderMinute), Modifier.weight(1f))
                OutlinedButton(onClick = {
                    TimePickerDialog(context, { _, h, m ->
                        val ns = s.copy(reminderHour = h, reminderMinute = m); vm.settingsStore.update(ns); Reminders.schedule(context, ns, true)
                    }, s.reminderHour, s.reminderMinute, true).show()
                }) { Text("שנה") }
            }
            OutlinedButton(onClick = { Reminders.runNow(context); context.toast("שולח תזכורת לבדיקה") }) { Text("שלח תזכורת עכשיו") }

            SectionTitle("🔒 פרטיות")
            SwitchRow("נעילה בטביעת אצבע / קוד", s.lock) { vm.settingsStore.update(s.copy(lock = it)) }

            SectionTitle("🏷️ הצ'יפים שלי")
            Text("לחיצה על צ'יפ מוחקת אותו. מוסיפים חדשים ישר מדף היום.", style = MaterialTheme.typography.bodySmall)
            Text("פעילויות", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                j.activities.forEach { a -> FilterChip(false, { removing = LinkKind.ACTIVITY to a.id }, { Text("${a.emoji} ${a.name}".trim()) }) }
            }
            if (j.people.isNotEmpty()) {
                Text("אנשים", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    j.people.forEach { p -> FilterChip(false, { removing = LinkKind.PERSON to p.id }, { Text("${p.name} · ${p.category.label()}") }) }
                }
            }
            if (j.places.isNotEmpty()) {
                Text("מקומות", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    j.places.forEach { p -> FilterChip(false, { removing = LinkKind.PLACE to p.id }, { Text(p.name) }) }
                }
            }

            SectionTitle("💾 גיבוי מוצפן")
            Text("קובץ אחד עם כל הדפים, התמונות וההקלטות, מוצפן בסיסמה שתבחר. בלי הסיסמה אי אפשר לפתוח אותו – גם לא אני.",
                style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { exporter.launch("journal-${LocalDate.now()}.jrnl") }) { Text("ייצוא גיבוי") }
                OutlinedButton(onClick = { importer.launch(arrayOf("*/*")) }) { Text("שחזור") }
            }
        }
    }

    removing?.let { (kind, id) ->
        ConfirmDialog("למחוק את \"${j.name(kind, id)}\"? הוא יוסר מכל הימים.", onConfirm = {
            vm.launch { when (kind) { LinkKind.ACTIVITY -> vm.repo.deleteActivity(id); LinkKind.PERSON -> vm.repo.deletePerson(id); LinkKind.PLACE -> vm.repo.deletePlace(id) } }
        }, onDismiss = { removing = null })
    }

    val mode = passwordFor
    val uri = pendingUri
    if (mode != null && uri != null) {
        var pw by remember { mutableStateOf("") }
        var pw2 by remember { mutableStateOf("") }
        val exporting = mode == "export"
        AlertDialog(
            onDismissRequest = { passwordFor = null },
            title = { Text(if (exporting) "סיסמה לגיבוי" else "סיסמת הגיבוי") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(pw, { pw = it }, singleLine = true, label = { Text("סיסמה") }, visualTransformation = PasswordVisualTransformation())
                    if (exporting) {
                        OutlinedTextField(pw2, { pw2 = it }, singleLine = true, label = { Text("שוב") }, visualTransformation = PasswordVisualTransformation())
                        Text("שמור את הסיסמה במקום בטוח – אין דרך לשחזר אותה", style = MaterialTheme.typography.bodySmall)
                    } else Text("השחזור יחליף את כל היומן הנוכחי", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(enabled = pw.length >= 4 && (!exporting || pw == pw2), onClick = {
                    passwordFor = null
                    context.toast(if (exporting) "מצפין ושומר..." else "בודק ומשחזר...")
                    vm.launch {
                        val r = withContext(Dispatchers.IO) {
                            runCatching {
                                if (exporting) context.contentResolver.openOutputStream(uri)!!.use { Backup.export(context, vm.repo, pw, it) }
                                else Backup.import(context, vm.repo, pw) { context.contentResolver.openInputStream(uri)!! }
                            }
                        }
                        withContext(Dispatchers.Main) {
                            context.toast(
                                when {
                                    r.isSuccess -> if (exporting) "הגיבוי נשמר 🔒" else "היומן שוחזר"
                                    r.exceptionOrNull() is Backup.WrongPassword -> "סיסמה שגויה או קובץ פגום"
                                    else -> "הפעולה נכשלה"
                                }
                            )
                        }
                    }
                }) { Text(if (exporting) "הצפן ושמור" else "שחזר") }
            },
            dismissButton = { TextButton(onClick = { passwordFor = null }) { Text("ביטול") } },
        )
    }
}

