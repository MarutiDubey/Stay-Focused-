# Dubey — Per-App Time Limiter (Personal Use)

**Target device:** Realme 9 5G (Realme UI / ColorOS based on Android 12/13)  
**Build tool:** GitHub (push to build APK via GitHub Actions)  
**Purpose:** Personal parental control app to limit usage of **any selected apps** (YouTube, Instagram, Facebook, games, etc.) with **per-app daily time limits**, PIN-protected settings, beautiful bold Hindi blocking message, and a secret extra time Easter egg.

---

## 1. Core Features (MVP)

### 1.1 Per-App Time Limit System
- **App selector screen** (PIN-protected): List all installed apps, tick which ones to monitor (YouTube, Instagram, games, browser, etc.)
- **Per-app daily time limit**: Set a different limit for each selected app (e.g., YouTube = 4 hours, Instagram = 2 hours, WhatsApp = 1 hour)
- **Per-app usage tracking**: Monitor foreground app usage and accumulate time **per app, per day**
- **Auto midnight reset**: All app usage counters reset to 0 at midnight

### 1.2 Blocking System
- When any monitored app's time limit is reached:
  - Show a **full-screen blocking overlay** on top of that app
  - Display a **beautiful, bold Hindi message** (customizable by parent)
  - User **cannot dismiss** the overlay or return to the blocked app
  - "Close" button sends user to home screen (app remains blocked until midnight)
  - The blocked app stays blocked for the rest of the day — **cannot be opened again until midnight reset**

### 1.3 Secret Extra Time (Easter Egg)
- Parent sets a **per-app secret extra time** in settings (e.g., YouTube +30 min, Instagram +20 min)
- When blocking screen appears, if user **taps the message 10 times**:
  - Secret extra time is **automatically granted** (no PIN needed)
  - Counter extends by the secret extra amount (e.g., 4h → 4h 30min)
  - Can only be used **once per day per app**
- After secret extra time is also exhausted → app blocked until midnight reset

### 1.4 PIN Protection System
- **Dubey app itself** is locked with a PIN (4-digit)
- Only someone with the PIN (parent) can:
  - Access the app's settings
  - Add/remove apps from the monitored list
  - Change per-app time limits
  - Change per-app secret extra time amounts
  - Edit the custom blocking message text
  - Change the PIN itself
  - Manually extend or reset today's quota for any app (emergency override)
- **PIN recovery**: If PIN forgotten, enter a **6-digit secret recovery PIN** (set during first-time setup)

### 1.5 Custom Blocking Message
- Parent can edit the Hindi blocking message shown when limit is hit
- Default example:
  ```
  आज का समय समाप्त हो गया है।
  कृपया मोबाइल का उपयोग बंद करें।
  ```
- Message should be **bold, large font, beautifully designed** (centered, good padding, maybe subtle background gradient)
- Optionally show which app triggered it (e.g., "YouTube समय समाप्त")

---

## 2. Technical Architecture

### 2.1 Core Android Components

| Component | Purpose |
|---|---|
| **UsageStatsManager** | Detects which app is currently in the foreground (by package name) and tracks usage time. Requires "Usage Access" permission (granted manually once in Settings). This is the primary detection mechanism. |
| **Foreground Service** | Persistent background service that polls `UsageStatsManager` every 3-5 seconds, checks current foreground package against monitored list, and accumulates today's usage per app. Must show a notification to stay alive. Uses **START_STICKY** flag to auto-restart if killed. |
| **`TYPE_APPLICATION_OVERLAY` Window** | Draws the full-screen blocking message over the blocked app. Requires "Display over other apps" permission. |
| **Room Database (SQLite)** | Stores: monitored package list, per-app daily usage, per-app time limit, per-app secret extra time, per-app secret-extra-used flag, custom message text, PIN hash, recovery PIN hash. |
| **PackageManager** | Lists all installed apps (name + icon + package) for the app selector screen. |
| **BroadcastReceiver (BOOT_COMPLETED)** | Restarts the monitoring service automatically after phone reboot. |
| **AlarmManager / WorkManager** | Triggers midnight reset (reset all usage counters and secret-extra-used flags). Also runs a **heartbeat check every 15 minutes** to restart the service if ColorOS killed it. |
| **PIN Entry Activity** | Locks access to the Dubey app. Shows numeric keypad, validates against hashed PIN stored in Room DB. |

**Note:** We intentionally **DO NOT use AccessibilityService** to avoid conflicts with banking apps (PhonePe, Paytm, Google Pay, etc.) that block when accessibility services are enabled. UsageStatsManager + proper battery whitelisting is sufficient for time tracking.

### 2.2 Data Model (Room Database)

**Table: `monitored_apps`**
- `package_name` (primary key)
- `app_name` (display name)
- `daily_limit_minutes` (e.g., 240 for 4 hours)
- `secret_extra_minutes` (e.g., 30)
- `today_usage_minutes` (accumulated, reset at midnight)
- `secret_extra_used_today` (boolean, reset at midnight)

**Table: `settings`**
- `key` (primary key: "pin_hash", "recovery_pin_hash", "blocking_message")
- `value` (hashed PIN, hashed recovery PIN, custom Hindi text)

### 2.3 Blocking Logic Flow

```
1. Service polls foreground app every 3-5 seconds
2. If foreground app is in monitored list:
   - Increment today_usage_minutes
   - Check: today_usage_minutes >= daily_limit_minutes?
     - YES → Show blocking overlay
     - NO → Continue monitoring
3. On blocking overlay:
   - User sees custom Hindi message + "Close" button
   - Detect taps on message area:
     - If tapped 10 times AND secret_extra_used_today == false:
       - daily_limit_minutes += secret_extra_minutes
       - secret_extra_used_today = true
       - Dismiss overlay, resume monitoring
     - Else: "Close" button → force user to home screen
4. At midnight (AlarmManager):
   - Reset all today_usage_minutes = 0
   - Reset all secret_extra_used_today = false
```

---

## 3. ColorOS Background Survival Strategy (CRITICAL)

Realme UI (ColorOS) aggressively kills background apps. You must implement **multiple layers** of protection to ensure the service never stops working.

**Important:** We **DO NOT use AccessibilityService** to avoid conflicts with banking apps (PhonePe, Paytm, Google Pay, etc.). Instead, we use multiple redundant survival mechanisms.

### 3.1 In-App Setup Wizard (First Launch)
Guide user (yourself, when installing on father's phone) through these **mandatory** steps:

1. **Usage Access Permission**: Settings → Special app access → Usage access → "Dubey" → Enable
2. **Display Over Other Apps**: Settings → Special app access → Display over other apps → "Dubey" → Enable
3. **Battery Optimization = OFF**: Settings → Battery → Battery optimization → All apps → "Dubey" → "Don't optimize" (**CRITICAL**)
4. **Auto-Start = ON**: Settings → App Management → Dubey → Toggle "Auto-start" ON (**CRITICAL**)
5. **Lock in Recent Apps**: Open Dubey in recent apps switcher → Long-press → Tap "Lock" icon (prevents manual swipe-away kill)

Show a **checklist screen** with direct "Open Settings" buttons for each. **Block app usage** until all 5 are green-checked. Re-verify on every app launch.

### 3.2 Multi-Layer In-Code Survival Mechanisms

#### Layer 1: Foreground Service (Primary)
- Run as a **Foreground Service** with ongoing notification (required by Android to prevent kill)
- Notification channel = "Time Monitoring" (user can minimize, but cannot disable)
- Service polls UsageStatsManager every **3 seconds** (frequent enough to catch app switches)
- Use **START_STICKY** return flag → Android auto-restarts if killed

#### Layer 2: WorkManager Heartbeat (Auto-Restart)
- Schedule a **PeriodicWorkRequest** every 15 minutes
- Worker checks: "Is MonitoringService running?"
  - NO → Start it immediately
  - YES → Do nothing
- **Constraints**: None (run even on low battery, Doze mode)
- This ensures even if ColorOS kills the service, it restarts within 15 minutes max

#### Layer 3: AlarmManager Backup (Aggressive Restart)
- Schedule a repeating **AlarmManager** alarm every 10 minutes
- Use **`setExactAndAllowWhileIdle()`** (ignores Doze mode)
- BroadcastReceiver checks if service is alive → restart if dead
- This is more aggressive than WorkManager (survives Doze)

#### Layer 4: Boot Receiver (Restart After Reboot)
- `BroadcastReceiver` listening to **`BOOT_COMPLETED`**
- Auto-starts the monitoring service when phone boots
- Requires `RECEIVE_BOOT_COMPLETED` permission

#### Layer 5: Notification Interaction (Keep Alive)
- Foreground service notification shows:
  - Title: "Dubey सक्रिय है" (Dubey is active)
  - Text: "समय निगरानी चल रही है" (Time monitoring in progress)
- Make notification **non-dismissible** (user cannot swipe away)
- This keeps the service in Android's "important" list

#### Layer 6: Watchdog Thread (Self-Monitor)
- Inside the service, run a separate **background thread** (watchdog)
- Every 30 seconds, watchdog checks:
  - Is UsageStatsManager polling still happening?
  - Is Room database accessible?
  - If either fails → restart the service internally
- This catches internal crashes/deadlocks

### 3.3 Permission Re-Verification (On Every App Open)
When user opens Dubey app (PIN screen):
1. Check all 5 critical permissions/settings
2. If any are missing/disabled:
   - Show **blocking red screen**: "⚠️ सेटिंग बदली गई है। सर्विस बंद है।" (Settings changed. Service stopped.)
   - List which permission is missing
   - "Open Settings" button
   - Block access until fixed

### 3.4 Testing Checklist (Before Final Deployment)
Test on actual Realme 9 5G for **3 full days**:
- ✅ Service survives overnight (Doze mode)
- ✅ Service survives phone reboot
- ✅ Service survives manual force-stop (via Settings → Apps → Force Stop)
- ✅ Service survives low battery mode
- ✅ Service survives clearing recent apps (if locked)
- ✅ Blocking overlay appears correctly when limit hit
- ✅ Banking apps (PhonePe/Paytm) work without "accessibility service" error

---

## 4. UI/UX Design

### 4.1 Dubey App Screens

1. **PIN Entry Screen** (app launch):
   - Numeric keypad (0-9)
   - "Forgot PIN?" → Enter 6-digit recovery PIN
   - Clean, minimal design

2. **Main Dashboard** (after PIN):
   - List of monitored apps with:
     - App icon + name
     - Today's usage / Daily limit (e.g., "2h 30m / 4h 00m")
     - Progress bar (visual)
     - "Edit" button → change limit, secret extra time
   - "+ Add App" button → App Selector
   - "Settings" button (gear icon)

3. **App Selector Screen**:
   - Scrollable list of all installed apps
   - Checkbox next to each
   - Save → Add to monitored list with default 2h limit

4. **App Settings Screen** (edit per-app):
   - Daily Limit (hours/minutes picker)
   - Secret Extra Time (minutes picker)
   - "Remove from monitoring" button

5. **Global Settings Screen**:
   - Edit Blocking Message (text input, Hindi, preview button)
   - Change PIN
   - Change Recovery PIN
   - "Check Permissions" button (re-verify all 5 setup steps)

### 4.2 Blocking Overlay Design
- **Full-screen, non-dismissible**
- Beautiful gradient background (e.g., soft orange → red)
- **Large, bold Hindi text** (custom message, centered, white, maybe 28-32sp font)
- Below: smaller text showing which app is blocked (e.g., "YouTube समय समाप्त")
- **"बंद करें" (Close)** button at bottom (rounded corners, contrasting color)
- **Secret tap area**: Entire message text area (not visible, but detects taps)

---

## 5. Tech Stack

- **Language**: Kotlin
- **Min SDK**: 26 (Android 8.0+) — covers Realme 9 5G (Android 12/13)
- **Architecture**: MVVM (ViewModel + LiveData + Room)
- **UI**: Jetpack Compose (modern, declarative) OR XML layouts (simpler for AI code generation)
- **DI**: Hilt (optional, keeps code clean)
- **Build**: GitHub Actions workflow to build debug APK on push

---

## 6. GitHub Actions Build Setup

### 6.1 Repository Structure
```
dubey-app/
├── .github/
│   └── workflows/
│       └── build-apk.yml
├── app/
│   ├── src/
│   ├── build.gradle.kts
│   └── ...
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

### 6.2 GitHub Actions Workflow (`.github/workflows/build-apk.yml`)
```yaml
name: Build APK

on:
  push:
    branches: [ main ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
      
      - name: Grant execute permission for gradlew
        run: chmod +x gradlew
      
      - name: Build Debug APK
        run: ./gradlew assembleDebug
      
      - name: Upload APK
        uses: actions/upload-artifact@v3
        with:
          name: dubey-debug.apk
          path: app/build/outputs/apk/debug/app-debug.apk
```

After every push to `main`, GitHub builds the APK and you can download it from the Actions tab → Artifacts.

---

## 7. Key Risks & Mitigations

| Risk | Mitigation |
|---|---|
| ColorOS kills the service in background | AccessibilityService + battery whitelisting + WorkManager heartbeat + manual setup wizard |
| Father disables permissions to bypass | Re-check permissions on app open; show blocking "permission missing" screen until re-granted |
| Father uninstalls the Dubey app | Cannot prevent uninstall, but can make icon/name less obvious ("System Service" instead of "Dubey"?) or require device admin (more complex) |
| You forget both PIN and recovery PIN | Add a hidden developer backdoor (e.g., tap app icon 20 times → enter hardcoded master PIN known only to you) |
| Secret extra time exploited daily | This is by design — it's an intentional "relief valve" so the child doesn't feel completely restricted |
| OS update resets battery/autostart settings | Re-show setup wizard automatically if permissions/optimizations lost |

---

## 8. Development Milestones

### Milestone 1: Basic Monitoring + Blocking (Hardcoded)
- Foreground service polls UsageStatsManager
- Hardcode 1-2 test packages (e.g., YouTube, Instagram) with 10-minute limits
- Show simple blocking overlay (no PIN, no secret tap, no custom message yet)
- Test on Realme phone for 1 full day to prove ColorOS survival

### Milestone 2: Database + Per-App Limits
- Room database with `monitored_apps` table
- App selector screen (no PIN yet)
- Per-app limit setting
- Midnight reset logic (AlarmManager)

### Milestone 3: PIN System + Settings UI
- PIN entry on app launch (hashed storage)
- Recovery PIN system
- Settings screen (change limits, change PIN)

### Milestone 4: Custom Message + Secret Extra Time
- Editable Hindi blocking message
- Detect 10 taps on overlay → grant secret extra time (once per day per app)
- Beautiful overlay design (gradient, bold Hindi text)

### Milestone 5: Polish + GitHub Actions
- Setup wizard for permissions
- GitHub Actions APK build workflow
- Icon, app name finalization
- Test all edge cases (reboot, battery optimization, app kill, etc.)

---

## 9. First Steps (How to Start)

1. **Initialize Android project**:
   - Create new Kotlin Android project in Android Studio or via Antigravity
   - Min SDK 26, target SDK 34
   - Add dependencies: Room, WorkManager, Jetpack Compose (or XML)

2. **Request permissions** (in `AndroidManifest.xml`):
   - `QUERY_ALL_PACKAGES` (to list all apps)
   - `PACKAGE_USAGE_STATS` (UsageStatsManager)
   - `SYSTEM_ALERT_WINDOW` (overlay window)
   - `RECEIVE_BOOT_COMPLETED` (auto-start)
   - `FOREGROUND_SERVICE`
   - `BIND_ACCESSIBILITY_SERVICE` (AccessibilityService)

3. **Build Milestone 1** (hardcoded monitoring):
   - Create a foreground service
   - Poll UsageStatsManager every 5 seconds
   - If package == "com.google.android.youtube" and usage > 10 min:
     - Show overlay with "समय समाप्त" message
   - Deploy to Realme phone, test for 1 day

4. **Push to GitHub** → verify Actions builds APK

5. **Iterate** through Milestones 2–5

---

## 10. Final Notes

- **App name**: "Dubey" (or disguise as "System Service" / "Device Manager" to be less obvious?)
- **Icon**: Something subtle (not a clock or lock icon — maybe a generic gear or shield?)
- **Signing**: For personal use, debug APK is fine. For extra security, generate a release keystore and store in GitHub Secrets.
- **Testing**: Install on father's phone, use it yourself for 2-3 days first to catch bugs before deploying "in production"

---

**This plan is now fully aligned with your requirements. Ready to start coding!** 🚀
