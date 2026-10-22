package com.matan.journal

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.matan.journal.data.Journal
import com.matan.journal.data.JournalDatabase
import com.matan.journal.data.Repo
import com.matan.journal.data.Settings
import com.matan.journal.data.SettingsStore
import com.matan.journal.logic.Insights
import com.matan.journal.ui.AppNav
import com.matan.journal.ui.JournalTheme
import com.matan.journal.ui.LockScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

class JournalApp : Application() {
    lateinit var repo: Repo
        private set
    lateinit var settings: SettingsStore
        private set

    override fun onCreate() {
        super.onCreate()
        repo = Repo(Room.databaseBuilder(this, JournalDatabase::class.java, "journal.db").build())
        settings = SettingsStore(this)
        CoroutineScope(Dispatchers.IO).launch { repo.seedActivities() }
        Reminders.createChannel(this)
        Reminders.schedule(this, settings.flow.value, replace = false)
    }
}

class MainActivity : FragmentActivity() {
    private var locked by mutableStateOf(true)
    private var lastStop = 0L
    private var prompting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JournalTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    if (locked) LockScreen { unlock() } else AppNav()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val s = (application as JournalApp).settings.flow.value
        if (!s.lock || !canLock()) { locked = false; return }
        // lock again after a minute in the background
        if (lastStop == 0L || System.currentTimeMillis() - lastStop > 60_000) { locked = true; unlock() }
    }

    override fun onStop() {
        super.onStop()
        lastStop = System.currentTimeMillis()
    }

    private fun canLock() = BiometricManager.from(this).canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS

    private fun unlock() {
        if (!locked || prompting) return
        prompting = true
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { prompting = false; locked = false }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { prompting = false }
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("היומן שלך")
                .setSubtitle("פתיחה בטביעת אצבע")
                .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
                .build()
        )
    }
}

object Reminders {
    private const val CHANNEL = "journal"
    private const val WORK = "daily-journal"

    fun createChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "תזכורת ליומן", NotificationManager.IMPORTANCE_DEFAULT))
    }

    fun schedule(context: Context, s: Settings, replace: Boolean) {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(s.reminderHour, s.reminderMinute)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val req = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK, if (replace) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP, req,
        )
    }

    fun runNow(context: Context) {
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<ReminderWorker>().setInputData(workDataOf("force" to true)).build())
    }

    /** Evening reminder text, or null if today is already written (unless forced). */
    fun evening(j: Journal, date: LocalDate, force: Boolean): List<String>? {
        val t = date.toEpochDay()
        val lines = mutableListOf<String>()
        val bdToday = j.birthdays.filter { it.month == date.monthValue && it.dayOfMonth == date.dayOfMonth }
        val tomorrow = date.plusDays(1)
        val bdTomorrow = j.birthdays.filter { it.month == tomorrow.monthValue && it.dayOfMonth == tomorrow.dayOfMonth }
        bdToday.forEach { lines += "🎂 היום: ${it.name}" }
        bdTomorrow.forEach { lines += "🎂 מחר: ${it.name}" }
        if (date.dayOfWeek == DayOfWeek.SATURDAY) lines += "🗓️ הסקירה השבועית מחכה לך"
        val written = j.written(t)
        if (!written) {
            val streak = Insights.streak(j, t).current
            lines.add(0, if (streak > 1) "איך היה היום? רצף של $streak ימים 🔥" else "איך היה היום? ✍️ דקה אחת וזה שם")
        }
        if (written && lines.isEmpty() && !force) return null
        if (lines.isEmpty()) lines += "כבר כתבת היום – כל הכבוד 🌙"
        return lines
    }

    suspend fun check(context: Context, force: Boolean) {
        val app = context.applicationContext as JournalApp
        val prefs = context.getSharedPreferences("notif", Context.MODE_PRIVATE)
        val date = LocalDate.now()
        if (!force && prefs.getLong("lastRun", -1) == date.toEpochDay()) return
        prefs.edit().putLong("lastRun", date.toEpochDay()).apply()
        val j = app.repo.journal().first()
        evening(j, date, force)?.let { notify(context, 1, "היומן שלך", it) }
        // a year ago, only when there's a photo to remember
        val yearAgo = date.minusYears(1).toEpochDay()
        if (j.photosOf(yearAgo).isNotEmpty()) {
            notify(context, 2, "📸 היום לפני שנה", listOf(j.entry(yearAgo)?.good?.takeIf { it.isNotBlank() } ?: "יש לך תמונה מהיום הזה לפני שנה"))
        }
    }

    private fun notify(context: Context, id: Int, title: String, lines: List<String>) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val pi = PendingIntent.getActivity(
            context, id, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val style = NotificationCompat.InboxStyle().also { s -> lines.forEach { s.addLine(it) } }
        NotificationManagerCompat.from(context).notify(
            id,
            NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle(title)
                .setContentText(lines.first()).setStyle(style).setContentIntent(pi).setAutoCancel(true).build(),
        )
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Reminders.check(applicationContext, inputData.getBoolean("force", false))
        return Result.success()
    }
}
