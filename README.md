# যারা এআই (Jara AI)

Kotlin + Jetpack Compose + Material 3 + MVVM. Gemini REST chat, Gemini Live API voice (WebSocket), real permission status, opt-in Accessibility device control.

## Build the APK from a phone only (Termux + GitHub Actions)
1. Termux: `pkg install git unzip` then `termux-setup-storage`
2. `cd ~ && unzip ~/storage/downloads/JaraAI.zip && cd JaraAI`
3. `git init && git add -A && git commit -m "Jara AI"`
4. Create an empty repo on github.com (e.g. `JaraAI`), then:
   `git branch -M main && git remote add origin https://github.com/blackhearttv09/JaraAI.git && git push -u origin main`
   (use a Personal Access Token as the password; scope: repo + workflow)
5. GitHub app/website → Actions → "Build APK" → open the finished run → download artifact `JaraAI-debug-apk` → unzip → install `app-debug.apk` (allow "install unknown apps").

## Build with Android Studio (if you ever have a PC)
Open the folder, let it sync, Build > Build APK(s). Or: `gradle :app:assembleDebug` (Gradle 8.9, JDK 17).

## First run
Menu → API কী সেটিংস → paste a Gemini key (validated live) → tap the mic. For phone control: Permissions → enable the Accessibility service in Android Settings, then turn on "AI-কে স্ক্রিন নিয়ন্ত্রণের অনুমতি" in App Settings.
