package com.matan.journal.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.matan.journal.data.Activity
import com.matan.journal.data.Entry
import com.matan.journal.data.Journal
import com.matan.journal.data.LinkKind
import com.matan.journal.data.Media
import com.matan.journal.data.Person
import com.matan.journal.data.PersonCategory
import com.matan.journal.data.Photo
import com.matan.journal.data.Place
import com.matan.journal.logic.Echo
import com.matan.journal.logic.energies
import com.matan.journal.logic.moods
import com.matan.journal.ui.AppViewModel
import com.matan.journal.ui.ColorChip
import com.matan.journal.ui.categoryColor
import com.matan.journal.ui.chipColor
import com.matan.journal.ui.Loading
import com.matan.journal.ui.MoodColors
import com.matan.journal.ui.NotebookField
import com.matan.journal.ui.PaperCard
import com.matan.journal.ui.Routes
import com.matan.journal.ui.Screen
import com.matan.journal.ui.SectionTitle
import com.matan.journal.ui.Thumb
import com.matan.journal.ui.duration
import com.matan.journal.ui.fmtDay
import com.matan.journal.ui.rememberBitmap
import com.matan.journal.ui.toast
import com.matan.journal.ui.today
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** How far back you can still write. */
const val EDIT_DAYS = 7

fun PersonCategory.label() = when (this) {
    PersonCategory.FAMILY -> "משפחה"
    PersonCategory.FRIENDS -> "חברים"
    PersonCategory.PARTNER -> "זוגיות"
    PersonCategory.WORK -> "עבודה"
    PersonCategory.OTHER -> "אחר"
}

// ======================= a single day (any date) =======================

@Composable
fun EntryScreen(vm: AppViewModel, nav: NavController, day: Long) {
    val j = vm.journal.collectAsStateWithLifecycle().value ?: return Loading()
    val t = today()
    Screen(fmtDay(day), nav, actions = {
        IconButton(onClick = { nav.navigate(Routes.entry(day - 1)) { popUpTo(Routes.entry(day)) { inclusive = true } } }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "יום קודם")
        }
        IconButton(enabled = day < t, onClick = {
            if (day + 1 == t) nav.popBackStack() else nav.navigate(Routes.entry(day + 1)) { popUpTo(Routes.entry(day)) { inclusive = true } }
        }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "יום הבא") }
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            EntryEditor(vm, j, day, editable = t - day <= EDIT_DAYS) { nav.navigate(Routes.entry(it)) }
        }
    }
}

// ======================= the editor =======================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntryEditor(vm: AppViewModel, j: Journal, day: Long, editable: Boolean, onOpenDay: (Long) -> Unit = {}) {
    val existing = j.entry(day)
    val yesterdayIntention = j.entry(day - 1)?.intention?.takeIf { it.isNotBlank() }

    // ---- page state (saved automatically) ----
    var did by remember(day) { mutableStateOf(existing?.did ?: "") }
    var good by remember(day) { mutableStateOf(existing?.good ?: "") }
    var learned by remember(day) { mutableStateOf(existing?.learned ?: "") }
    var free by remember(day) { mutableStateOf(existing?.free ?: "") }
    var intention by remember(day) { mutableStateOf(existing?.intention ?: "") }
    var intentionDone by remember(day) { mutableIntStateOf(existing?.intentionDone ?: -1) }
    var mood by remember(day) { mutableIntStateOf(existing?.mood ?: 0) }
    var energy by remember(day) { mutableIntStateOf(existing?.energy ?: 0) }
    var starred by remember(day) { mutableStateOf(existing?.starred ?: false) }
    var starTitle by remember(day) { mutableStateOf(existing?.starTitle ?: "") }

    fun current() = Entry(day, did, good, learned, free, intention, intentionDone, mood, energy, starred, starTitle)
    val initial = remember(day) { existing ?: Entry(day) }
    var saved by remember(day) { mutableStateOf(initial) }
    fun dirty() = current().copy(updatedAt = 0) != saved.copy(updatedAt = 0)
    fun saveNow() { if (dirty()) { val e = current(); saved = e; vm.saveEntry(e) } }
    /** chips/photos/voice need the page to exist so the day shows in the timeline */
    fun ensurePage() { if (j.entry(day) == null) { val e = current(); saved = e; vm.saveEntry(e) } }

    LaunchedEffect(did, good, learned, free, intention, intentionDone, mood, energy, starred, starTitle) {
        delay(700); saveNow()
    }
    DisposableEffect(day) { onDispose { saveNow() } }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(bottom = 48.dp)) {
        // ---- yesterday's intention ----
        if (yesterdayIntention != null && editable) {
            PaperCard(color = MaterialTheme.colorScheme.tertiaryContainer) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("אתמול רצית:", style = MaterialTheme.typography.labelLarge)
                    Text(yesterdayIntention, style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(intentionDone == 1, { intentionDone = if (intentionDone == 1) -1 else 1 }, { Text("✓ קרה") })
                        FilterChip(intentionDone == 0, { intentionDone = if (intentionDone == 0) -1 else 0 }, { Text("לא הפעם") })
                    }
                }
            }
        }

        // ---- mood & energy ----
        Scale("איך היה?", moods, mood, editable) { mood = it }
        Scale("אנרגיה", energies, energy, editable) { energy = it }

        // ---- chips ----
        ChipSection(vm, j, day, LinkKind.ACTIVITY, "מה עשיתי", editable, ::ensurePage, onOpenDay)
        ChipSection(vm, j, day, LinkKind.PERSON, "עם מי", editable, ::ensurePage, onOpenDay)
        ChipSection(vm, j, day, LinkKind.PLACE, "איפה", editable, ::ensurePage, onOpenDay)

        // ---- writing ----
        NotebookField("מה עשיתי היום", "כמה מילים על היום...", did, { did = it }, !editable)
        NotebookField("מה היה טוב", "רגע אחד קטן שעשה לך טוב", good, { good = it }, !editable)
        NotebookField("מה למדתי", "על עצמי, על אחרים, על משהו שעשיתי", learned, { learned = it }, !editable)
        NotebookField("עוד משהו", "כל מה שבא לך – בלי הגבלה", free, { free = it }, !editable, minLines = 3)

        // ---- photos & voice ----
        PhotosSection(vm, j, day, editable, ::ensurePage)
        VoiceSection(vm, j, day, editable, ::ensurePage)

        // ---- tomorrow ----
        if (editable) NotebookField("מחר אני רוצה...", "כוונה אחת קטנה למחר", intention, { intention = it }, minLines = 1)

        // ---- special day ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(enabled = editable, onClick = { starred = !starred }) {
                Icon(Icons.Default.Star, "יום מיוחד", tint = if (starred) Color(0xFFE2A93B) else MaterialTheme.colorScheme.outline)
            }
            if (starred) {
                OutlinedTextField(starTitle, { starTitle = it }, Modifier.weight(1f), readOnly = !editable, singleLine = true,
                    label = { Text("מה מיוחד היום? (יום הולדת, טיול...)") })
            } else Text("לסמן כיום מיוחד", color = MaterialTheme.colorScheme.outline)
        }
        if (!editable) Text("אפשר לכתוב עד $EDIT_DAYS ימים אחורה – הדף הזה לקריאה בלבד", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline)
        if (day == today() && j.written(day)) {
            val reactions = Echo.reactions(j, day)
            if (reactions.isNotEmpty()) {
                PaperCard(color = MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("📔 היומן שם לב", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        reactions.forEach { Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer) }
                    }
                }
            }
        }
        if (editable && existing == null && !dirty()) Text("נשמר אוטומטית ✨", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun Scale(title: String, faces: List<String>, value: Int, editable: Boolean, onChange: (Int) -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            faces.forEachIndexed { i, f ->
                val n = i + 1
                Box(
                    Modifier.size(52.dp)
                        .background(if (value == n) MoodColors[i].copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceContainer, CircleShape)
                        .clickable(enabled = editable) { onChange(if (value == n) 0 else n) },
                    contentAlignment = Alignment.Center,
                ) { Text(f, style = MaterialTheme.typography.headlineSmall) }
            }
        }
    }
}

// ======================= chips =======================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipSection(vm: AppViewModel, j: Journal, day: Long, kind: LinkKind, title: String, editable: Boolean, ensurePage: () -> Unit, onOpenDay: (Long) -> Unit) {
    val on = j.linksOf(day, kind)
    val usage = j.links.filter { it.kind == kind }.groupingBy { it.refId }.eachCount()
    val items: List<Pair<Long, String>> = when (kind) {
        LinkKind.ACTIVITY -> j.activities.sortedWith(compareByDescending<Activity> { usage[it.id] ?: 0 }.thenBy { it.sort }).map { it.id to "${it.emoji} ${it.name}".trim() }
        LinkKind.PERSON -> j.people.sortedByDescending { usage[it.id] ?: 0 }.map { it.id to it.name }
        LinkKind.PLACE -> j.places.sortedByDescending { usage[it.id] ?: 0 }.map { it.id to it.name }
    }
    var adding by remember { mutableStateOf(false) }
    var showAll by remember { mutableStateOf(false) }
    val visible = if (showAll || !editable) items else items.take(12)
    val shown = if (editable) visible else items.filter { it.first in on }
    if (!editable && shown.isEmpty()) return

    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            (shown + items.filter { it.first in on && it !in shown }).forEach { (id, label) ->
                ColorChip(
                    selected = id in on, enabled = editable,
                    onClick = { ensurePage(); vm.launch { vm.repo.toggleLink(day, kind, id, id !in on) } },
                    label = label, color = chipColor(j, kind, id),
                )
            }
            if (editable) {
                if (items.size > 12) AssistChip(onClick = { showAll = !showAll }, label = { Text(if (showAll) "פחות" else "עוד ${items.size - 12}") })
                AssistChip(onClick = { adding = true }, label = { Text(when (kind) { LinkKind.ACTIVITY -> "+ פעילות"; LinkKind.PERSON -> "+ אדם"; LinkKind.PLACE -> "+ מקום" }) })
            }
        }
        // "the last time..." under what you picked
        on.take(3).forEach { id -> EchoLine(j, kind, id, day, onOpenDay) }
    }
    if (adding) AddChipDialog(kind, onDismiss = { adding = false }) { name, extra, category ->
        ensurePage()
        vm.launch {
            val id = when (kind) {
                LinkKind.ACTIVITY -> vm.repo.saveActivity(Activity(name = name, emoji = extra, sort = 1000))
                LinkKind.PERSON -> vm.repo.savePerson(Person(name = name, category = category))
                LinkKind.PLACE -> vm.repo.savePlace(Place(name = name))
            }
            vm.repo.toggleLink(day, kind, id, true)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddChipDialog(kind: LinkKind, onDismiss: () -> Unit, onSave: (String, String, PersonCategory) -> Unit) {
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf(PersonCategory.FRIENDS) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(when (kind) { LinkKind.ACTIVITY -> "פעילות חדשה"; LinkKind.PERSON -> "אדם חדש"; LinkKind.PLACE -> "מקום חדש" }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("שם") })
                when (kind) {
                    LinkKind.ACTIVITY -> {
                        OutlinedTextField(emoji, { emoji = it.take(4) }, singleLine = true, label = { Text("אימוג'י (לא חובה)") })
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("🏃", "🧘", "🏊", "🎸", "🎮", "🧑‍💻", "🛒", "🍽️", "☕", "🌊", "🐕", "✈️").forEach { e ->
                                Text(e, Modifier.clickable { emoji = e }.padding(6.dp), style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                    LinkKind.PERSON -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PersonCategory.entries.forEach { c -> ColorChip(c == cat, { cat = c }, c.label(), categoryColor(c)) }
                    }
                    LinkKind.PLACE -> {}
                }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim(), emoji.trim(), cat); onDismiss() }) { Text("הוסף") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}

// ======================= photos =======================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotosSection(vm: AppViewModel, j: Journal, day: Long, editable: Boolean, ensurePage: () -> Unit) {
    val context = LocalContext.current
    val photos = j.photosOf(day)
    var viewing by remember { mutableStateOf<Photo?>(null) }
    var galleryOpen by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    val added = remember(day) { mutableStateOf(setOf<Uri>()) }
    if (!editable && photos.isEmpty()) return

    fun addPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        ensurePage()
        val room = (5 - photos.size).coerceAtLeast(0)
        if (room == 0) { context.toast("עד 5 תמונות ליום"); return }
        vm.launch {
            uris.take(room).forEach { u -> withContext(Dispatchers.IO) { Media.importPhoto(context, u) }?.let { vm.repo.addPhoto(day, it) } }
        }
        added.value = added.value + uris
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(5)) { addPhotos(it) }
    val perms = if (Build.VERSION.SDK_INT >= 34) arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    else if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_IMAGES) else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) { galleryOpen = true; refresh++ } else context.toast("בלי הרשאה אפשר לבחור תמונות דרך \"מהגלריה\"")
    }
    val hasPerm = perms.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("תמונות", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        if (photos.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(photos, key = { it.id }) { p ->
                    val bmp = rememberBitmap(p.fileName) { Media.thumb(context, p.fileName, 400) }
                    Thumb(bmp, 110.dp, Modifier.clickable { viewing = p })
                }
            }
        }
        if (editable && photos.size < 5) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { if (hasPerm) { galleryOpen = !galleryOpen; refresh++ } else permLauncher.launch(perms) }) {
                    Text(if (day == today()) "📷 מהיום" else "📷 מאותו יום")
                }
                OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("מהגלריה") }
            }
            if (galleryOpen) {
                val uris by produceState(emptyList<Uri>(), day, refresh) { value = withContext(Dispatchers.IO) { Media.galleryPhotosOf(context, day) } }
                if (uris.isEmpty()) Text("לא מצאתי תמונות שצולמו ביום הזה", style = MaterialTheme.typography.bodySmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(uris, key = { it.toString() }) { u ->
                        val bmp = rememberBitmap(u) { Media.thumb(context, u, 256) }
                        val done = u in added.value
                        Box {
                            Thumb(bmp, 84.dp, Modifier.clickable(enabled = !done) { addPhotos(listOf(u)) })
                            if (done) Text("✓", Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.5f), CircleShape).padding(8.dp), color = Color.White)
                        }
                    }
                }
            }
        }
    }

    viewing?.let { p ->
        Dialog(onDismissRequest = { viewing = null }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val big = rememberBitmap("big_" + p.fileName) { Media.thumb(context, p.fileName, 1600) }
                if (big != null) Image(big, null, Modifier.fillMaxWidth().background(Color.Black, RoundedCornerShape(16.dp)), contentScale = ContentScale.Fit)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewing = null }) { Text("סגור") }
                    if (editable) OutlinedButton(onClick = { vm.launch { vm.repo.deletePhoto(p, context) }; viewing = null }) { Text("הסר מהיומן") }
                }
            }
        }
    }
}

// ======================= voice =======================

@Composable
private fun VoiceSection(vm: AppViewModel, j: Journal, day: Long, editable: Boolean, ensurePage: () -> Unit) {
    val context = LocalContext.current
    val recs = j.recordingsOf(day)
    val recorder = remember { Media.Recorder(context) }
    val player = remember { Media.Player() }
    var recording by remember { mutableStateOf(false) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) { onDispose { player.stop(); if (recording) recorder.cancel() } }
    if (!editable && recs.isEmpty()) return

    fun start() {
        if (recorder.start()) { recording = true; startedAt = System.currentTimeMillis(); now = startedAt } else context.toast("לא הצלחתי להתחיל הקלטה")
    }
    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) start() else context.toast("צריך הרשאת מיקרופון כדי להקליט") }
    if (recording) LaunchedEffect(startedAt) { while (true) { now = System.currentTimeMillis(); delay(500) } }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("הקלטות", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        recs.forEach { r ->
            PaperCard {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        if (playing == r.fileName) { player.stop(); playing = null }
                        else { player.play(context, r.fileName) { playing = null }; playing = r.fileName }
                    }) { Text(if (playing == r.fileName) "■" else "▶", style = MaterialTheme.typography.titleLarge) }
                    Text("🎙️ הקלטה · ${duration(r.seconds)}", Modifier.weight(1f))
                    if (editable) TextButton(onClick = { if (playing == r.fileName) player.stop(); vm.launch { vm.repo.deleteRecording(r, context) } }) {
                        Text("מחק", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        if (editable) {
            if (recording) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("🔴 ${duration(((now - startedAt) / 1000).toInt())}", style = MaterialTheme.typography.titleLarge)
                    Button(onClick = {
                        recording = false
                        recorder.stop()?.let { (name, secs) -> ensurePage(); vm.launch { vm.repo.addRecording(day, name, secs) } }
                    }) { Text("סיום ושמירה") }
                    TextButton(onClick = { recording = false; recorder.cancel() }) { Text("בטל") }
                }
            } else {
                OutlinedButton(onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) start()
                    else mic.launch(Manifest.permission.RECORD_AUDIO)
                }) { Text("🎙️ אין כוח לכתוב? הקלט את היום") }
            }
        }
    }
}

@Composable
private fun EchoLine(j: Journal, kind: LinkKind, id: Long, day: Long, onOpenDay: (Long) -> Unit) {
    val context = LocalContext.current
    val last = Echo.last(j, kind, id, day)
    val color = chipColor(j, kind, id)
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp)
            .background(color.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .clickable(enabled = last != null) { last?.let { onOpenDay(it.day) } }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(j.name(kind, id) + " · " + Echo.line(j, kind, id, day), style = MaterialTheme.typography.labelLarge)
            last?.snippet?.let { Text("\u201C$it\u201D", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        last?.photo?.let { p ->
            val bmp = rememberBitmap(p.fileName) { Media.thumb(context, p.fileName, 200) }
            Thumb(bmp, 44.dp)
        }
    }
}
