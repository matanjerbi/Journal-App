# Journal (יומן)

A personal life journal for Android. Every evening: mood and energy, what you did (chips), who you were with and where, three short questions (what I did, what was good, what I learned) plus free text without limits, up to 5 photos, voice notes, and an intention for tomorrow.

**Install:** [Journal.apk](https://github.com/matanjerbi/Journal-App/releases/latest/download/Journal.apk). Every push to `main` builds a new APK (GitHub Actions); it installs as an update and keeps your data.

- **Today** – the page autosaves; "photos from today" from the gallery; record when you're too tired to type; fill in up to 7 days back.
- **Journal** – timeline by month with full-text search across text, people, places and activities.
- **Insights** – streak, 30-day mood line, what goes with your better days (people / places / activities vs your average), weekdays, intention rate, what fills your days.
- **Growth** – yesterday's intention check, weekly review with a takeaway for next week, a book of everything you learned (filter by activity).
- **Memories** – on this day in past years, monthly photo album, starred special days, birthdays.
- **Privacy** – everything stays on the phone, fingerprint/PIN lock, encrypted backup (AES-256 + HMAC, PBKDF2 password) including photos and recordings.
- **Look** – warm notebook paper with ruled lines; three fonts: handwriting (Amatic SC), formal (Frank Ruhl Libre), flowing (Varela Round) – all OFL from Google Fonts.

Kotlin, Jetpack Compose (Material 3), Room, WorkManager, androidx.biometric. Logic + tests: `logic/Insights.kt`.
