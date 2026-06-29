# Stay Focused - Per-App Time Limiter

A robust Android parental control and productivity application designed to enforce daily time limits on specific apps. Specially engineered to survive aggressive battery optimization on ColorOS/Realme UI devices.

## 🚀 Features

- **Per-App Time Limits:** Set custom daily allowances for any installed application.
- **Aggressive Persistence:** Built with multiple fallback mechanisms to ensure the service survives OS-level battery optimizations (ColorOS/Realme UI).
- **Beautiful Hindi Blocking Overlay:** A clear, aesthetically pleasing full-screen block when the time limit is reached, with customizable messaging.
- **PIN-Protected Settings:** Secure the app's settings behind a custom 4-digit PIN (hashed via SHA-256) to prevent unauthorized changes.
- **Secret Extra Time:** A hidden 10-tap gesture on the blocking screen grants an emergency time extension.

## 🛠 Architecture & Tech Stack

- **Language:** Kotlin
- **Database:** Room (SQLite)
- **Background Processing:** Foreground Services, WorkManager, AlarmManager
- **UI:** XML Layouts & Jetpack Compose
- **Security:** SHA-256 PIN hashing

## ⚙️ Installation & Setup

1. **Download the APK:** Go to the [Actions tab](https://github.com/MarutiDubey/Stay-Focused/actions) and download the latest `dubey-debug` artifact, or build it locally.
2. **Install the APK** on your Android device.
3. **First-Time Setup:** Set your secure 4-digit PIN upon the first launch.
4. **Grant Required Permissions:**
   - **Usage Access:** `Settings → Special app access → Usage access → Dubey`
   - **Display over other apps:** `Settings → Special app access → Display over apps → Dubey`
5. **Ensure Persistence (Critical for ColorOS/Realme):**
   - **Battery optimization OFF:** `Settings → Battery → App launch → Dubey → Manual → all OFF`
   - **Auto-start ON:** `Settings → App Management → Dubey → Auto-start → ON`
   - **Lock in recent apps:** Open the recent apps screen and lock the Dubey app.

## 🏗️ Building Locally

You can build the APK on your local machine using Gradle:

```bash
./gradlew assembleDebug
```
*The output APK will be located at: `app/build/outputs/apk/debug/app-debug.apk`*

## 💡 How it Works

The app utilizes a Foreground Service with `START_STICKY` to track app usage via `UsageStats`. To guarantee survival against Android's aggressive task killers, it implements several layers:
- A `WorkManager` PeriodicWorker every 15 minutes.
- An `AlarmManager` heartbeat every 10 minutes.
- A `BroadcastReceiver` for `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED`.
- A non-dismissible ongoing notification to keep the service alive in the foreground.

## 📜 Default Configuration

- **Daily Limit (New Apps):** 120 minutes
- **Secret Extra Time:** 30 minutes
- **Recovery PIN:** `000000` (can be changed in settings)
- **Polling Interval:** Every 3 seconds

## 🤝 Contributing

Contributions, issues, and feature requests are welcome! Feel free to check the [issues page](https://github.com/MarutiDubey/Stay-Focused/issues).
