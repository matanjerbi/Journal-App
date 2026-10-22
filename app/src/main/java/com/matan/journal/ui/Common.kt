package com.matan.journal.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.matan.journal.R
import com.matan.journal.data.FontChoice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// ======================= theme =======================

private val Paper = lightColorScheme(
    primary = Color(0xFFC0643A), onPrimary = Color.White,
    primaryContainer = Color(0xFFF6DCC8), onPrimaryContainer = Color(0xFF4A2410),
    secondary = Color(0xFF5F7F52), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE8D3), onSecondaryContainer = Color(0xFF1E3316),
    tertiary = Color(0xFF3D5A80), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD9E3F0), onTertiaryContainer = Color(0xFF13253D),
    background = Color(0xFFF7F1E3), onBackground = Color(0xFF3A2F26),
    surface = Color(0xFFF7F1E3), onSurface = Color(0xFF3A2F26),
    surfaceVariant = Color(0xFFEDE3CF), onSurfaceVariant = Color(0xFF6B5D4F),
    surfaceContainerLowest = Color(0xFFFFFDF7), surfaceContainerLow = Color(0xFFFCF7EC),
    surfaceContainer = Color(0xFFFFFBF2), surfaceContainerHigh = Color(0xFFF3EBDA), surfaceContainerHighest = Color(0xFFEDE3CF),
    outline = Color(0xFF9C8D7C), outlineVariant = Color(0xFFE3D6BF),
)

private val Night = darkColorScheme(
    primary = Color(0xFFEFA27A), onPrimary = Color(0xFF4A2410),
    primaryContainer = Color(0xFF6B3A20), onPrimaryContainer = Color(0xFFF6DCC8),
    secondary = Color(0xFFA8C69A), onSecondary = Color(0xFF1E3316),
    secondaryContainer = Color(0xFF3A5230), onSecondaryContainer = Color(0xFFDDE8D3),
    tertiary = Color(0xFFA9C2E3), onTertiary = Color(0xFF13253D),
    tertiaryContainer = Color(0xFF2C4262), onTertiaryContainer = Color(0xFFD9E3F0),
    background = Color(0xFF1C1814), onBackground = Color(0xFFEFE5D6),
    surface = Color(0xFF1C1814), onSurface = Color(0xFFEFE5D6),
    surfaceVariant = Color(0xFF332B24), onSurfaceVariant = Color(0xFFCDBFAF),
    surfaceContainerLowest = Color(0xFF15120F), surfaceContainerLow = Color(0xFF211C17),
    surfaceContainer = Color(0xFF26201B), surfaceContainerHigh = Color(0xFF2E2721), surfaceContainerHighest = Color(0xFF383029),
    outline = Color(0xFF9C8D7C), outlineVariant = Color(0xFF4A4037),
)

fun fontFamily(f: FontChoice): FontFamily = when (f) {
    FontChoice.HAND -> FontFamily(Font(R.font.hand_regular, FontWeight.Normal), Font(R.font.hand_bold, FontWeight.Bold))
    FontChoice.FORMAL -> FontFamily(Font(R.font.formal))
    FontChoice.FLOW -> FontFamily(Font(R.font.flow))
}

fun fontLabel(f: FontChoice) = when (f) {
    FontChoice.HAND -> "כתב יד"
    FontChoice.FORMAL -> "רשמי"
    FontChoice.FLOW -> "זורם"
}

/** The handwriting font is narrow, so everything is set a little bigger with it. */
private fun scale(f: FontChoice) = if (f == FontChoice.HAND) 1.3f else 1f

private fun typography(f: FontChoice): Typography {
    val fam = fontFamily(f)
    val k = scale(f)
    val t = Typography()
    fun TextStyle.x(bold: Boolean = false) = copy(
        fontFamily = fam, fontSize = fontSize * k, lineHeight = lineHeight * k,
        fontWeight = if (bold) FontWeight.Bold else fontWeight,
    )
    return Typography(
        displayLarge = t.displayLarge.x(true), displayMedium = t.displayMedium.x(true), displaySmall = t.displaySmall.x(true),
        headlineLarge = t.headlineLarge.x(true), headlineMedium = t.headlineMedium.x(true), headlineSmall = t.headlineSmall.x(true),
        titleLarge = t.titleLarge.x(true), titleMedium = t.titleMedium.x(true), titleSmall = t.titleSmall.x(true),
        bodyLarge = t.bodyLarge.x(), bodyMedium = t.bodyMedium.x(), bodySmall = t.bodySmall.x(),
        labelLarge = t.labelLarge.x(), labelMedium = t.labelMedium.x(), labelSmall = t.labelSmall.x(),
    )
}

val LocalFont = staticCompositionLocalOf { FontChoice.HAND }

@Composable
fun JournalTheme(content: @Composable () -> Unit) {
    val vm: AppViewModel = viewModel()
    val s by vm.settings.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalFont provides s.font) {
        MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Night else Paper, typography = typography(s.font), content = content)
    }
}

val MoodColors = listOf(Color(0xFFB9675A), Color(0xFFD08A5C), Color(0xFFC9A75A), Color(0xFF8FA65E), Color(0xFF5E9A6A))

// ======================= formatting =======================

private val he = Locale.forLanguageTag("he")
private val dayFmt = DateTimeFormatter.ofPattern("EEEE, d 'ב'MMMM", he)
private val shortFmt = DateTimeFormatter.ofPattern("d.M", he)
private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", he)
fun fmtDay(day: Long): String = LocalDate.ofEpochDay(day).format(dayFmt)
fun fmtShort(day: Long): String = LocalDate.ofEpochDay(day).format(shortFmt)
fun fmtMonth(d: LocalDate): String = d.format(monthFmt)
fun weekdayShort(day: Long): String = LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("EEE", he))
fun today() = LocalDate.now().toEpochDay()
fun duration(sec: Int) = "%d:%02d".format(sec / 60, sec % 60)

fun Context.toast(msg: String) = android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()

// ======================= components =======================

@Composable
fun Loading() { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }

@Composable
fun PaperCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, color: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    val colors = CardDefaults.cardColors(containerColor = color ?: MaterialTheme.colorScheme.surfaceContainer)
    val shape = RoundedCornerShape(20.dp)
    val el = CardDefaults.cardElevation(1.dp)
    if (onClick != null) Card(onClick, modifier.fillMaxWidth(), shape = shape, colors = colors, elevation = el, content = content)
    else Card(modifier.fillMaxWidth(), shape = shape, colors = colors, elevation = el, content = content)
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = modifier.padding(top = 12.dp, bottom = 2.dp))
}

/** Text area on ruled notebook lines. No length limit. */
@Composable
fun NotebookField(title: String, hint: String, value: String, onChange: (String) -> Unit, readOnly: Boolean = false, minLines: Int = 2) {
    val style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)
    val lineH = style.lineHeight.takeIf { it.isSp } ?: 28.sp
    val rule = MaterialTheme.colorScheme.outlineVariant
    val margin = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    Column(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        BasicTextField(
            value = value, onValueChange = onChange, readOnly = readOnly, textStyle = style.copy(lineHeight = lineH), minLines = minLines,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            decorationBox = { inner ->
                Box(
                    Modifier.fillMaxWidth().drawBehind {
                        val step = lineH.toPx()
                        var y = step - 2.dp.toPx()
                        while (y < size.height + 1) { drawLine(rule, Offset(0f, y), Offset(size.width, y), 1.dp.toPx()); y += step }
                        // the red margin line of a notebook (on the right in Hebrew)
                        drawLine(margin, Offset(size.width - 2.dp.toPx(), 0f), Offset(size.width - 2.dp.toPx(), size.height), 1.dp.toPx())
                    }.padding(end = 10.dp),
                ) {
                    if (value.isEmpty() && !readOnly) Text(hint, style = style.copy(lineHeight = lineH, color = MaterialTheme.colorScheme.outline))
                    inner()
                }
            },
        )
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f)); Switch(checked, onChange)
    }
}

@Composable
fun ConfirmDialog(text: String, confirm: String = "מחק", onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, text = { Text(text) },
        confirmButton = { TextButton(onClick = { onDismiss(); onConfirm() }) { Text(confirm, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}

/** Loads a bitmap off the main thread. */
@Composable
fun rememberBitmap(key: Any, load: () -> Bitmap?): ImageBitmap? {
    val v by produceState<ImageBitmap?>(null, key) { value = withContext(Dispatchers.IO) { load()?.asImageBitmap() } }
    return v
}

@Composable
fun Thumb(bitmap: ImageBitmap?, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (bitmap != null) Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
fun MoodDot(mood: Int, size: Dp = 34.dp) {
    if (mood <= 0) return
    Box(Modifier.size(size).background(MoodColors[mood - 1].copy(alpha = 0.22f), RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
        Text(com.matan.journal.logic.moods[mood - 1])
    }
}

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(32.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("📔", style = MaterialTheme.typography.displayLarge)
        Text("היומן שלך", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onUnlock) { Text("פתח") }
    }
}

