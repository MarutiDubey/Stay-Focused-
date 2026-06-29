# Dubey App — Build Summary

## What Was Built

A complete Android app to enforce per-app daily time limits on a Realme 9 5G (ColorOS/Realme UI).

---

## Architecture Overview

```
com.dubey.timelimiter/
├── DubeyApplication.kt          ← App class; init WorkManager heartbeat
├── data/
│   ├── AppDatabase.kt           ← Room database (version 1)
│   ├── entity/
│   │   ├── MonitoredApp.kt      ← Table: package, limits, usage, secret extra
│   │   └── Setting.kt           ← Table: key-value settings (PIN hash, message)
│   └── dao/
│       ├── MonitoredAppDao.kt   ← CRUD + incrementUsage + resetAllUsage
│       └── SettingDao.kt        ← get/set/delete settings by key
├── service/
│   ├── MonitoringService.kt     ← Foreground service; tracks usage via UsageStats
│   ├── BootReceiver.kt          ← Restarts service on BOOT_COMPLETED
│   ├── AlarmReceiver.kt         ← Heartbeat every 10 min via AlarmManager
│   └── ServiceHeartbeatWorker.kt← WorkManager worker: restart service if dead
├── ui/
│   ├── MainActivity.kt          ← Dashboard host; starts service
│   ├── pin/
│   │   └── PinEntryActivity.kt  ← PIN setup (first launch) + unlock + recovery
│   ├── overlay/
│   │   └── BlockingActivity.kt  ← Full-screen block; 10-tap secret extra time
│   ├── dashboard/
│   │   └── DashboardScreen.kt   ← App list with progress bars
│   ├── selector/
│   │   └── AppSelectorActivity.kt ← Pick apps to monitor
│   └── settings/
│       ├── AppSettingsActivity.kt   ← Per-app limit/secret-extra editor
│       └── GlobalSettingsActivity.kt← Message editor, PIN changer, permissions
└── utils/
    └── SecurityUtils.kt         ← SHA-256 PIN hashing + verification
```

---

## Key Bugs Fixed

| Bug | Fix |
|-----|-----|
| `MonitoringService` tracking logic wrong (guessing time deltas) | Now uses `UsageStats.totalTimeInForeground` and tracks already-counted time per app |
| `getAllApps()` Flow collected inside `withContext(IO)` — never returned | Changed to `flow.first()` for one-shot snapshot |
| PIN hardcoded as "1234" | Full SHA-256 hashed PIN stored in Room; first-launch setup flow |
| `BlockingActivity` — secret extra time was TODO | Fully implemented: 10 taps → check DB, extend limit, mark used |
| Missing `blocking_gradient` drawable | Created red→orange gradient XML |
| Missing `blocked_app_name` view in layout | Added to XML, loaded from PackageManager in Activity |
| No WorkManager heartbeat | `ServiceHeartbeatWorker` every 15 min |
| No midnight reset scheduling | `scheduleMidnightReset()` via `AlarmManager.setExactAndAllowWhileIdle` |
| AlarmReceiver was a stub | Properly checks if service is running, restarts if dead |
| `AppSelectorActivity` didn't handle deselecting apps | Now deletes deselected apps from DB |
| Missing activity declarations in Manifest | All activities registered |
| Missing `FOREGROUND_SERVICE_DATA_SYNC` permission | Added |

---

## ColorOS Survival Layers Implemented

| Layer | Mechanism |
|-------|-----------|
| 1 | Foreground Service with `START_STICKY` |
| 2 | WorkManager PeriodicWorker every 15 min |
| 3 | AlarmManager `setExactAndAllowWhileIdle` every 10 min |
| 4 | `BOOT_COMPLETED` + `MY_PACKAGE_REPLACED` BroadcastReceiver |
| 5 | Non-dismissible ongoing notification |

---

## First-Time Setup Flow

1. Launch app → **PIN Setup Screen** (first launch only)
2. Set 4-digit PIN → confirm PIN → navigate to Dashboard
3. Dashboard shows "कोई ऐप नहीं" → tap `+` → App Selector
4. Select apps → Save → apps appear with 2h default limit
5. Tap any app → App Settings → adjust daily limit + secret extra time
6. Go to Settings (gear) → enable permissions, customize blocking message

---

## To Build the APK

Push to `main` branch on GitHub → Actions tab → Download `dubey-debug` artifact.

Or locally:
```bash
./gradlew assembleDebug
# APK at: app/build/outputs/apk/debug/app-debug.apk
```

---

## Default Values

| Setting | Default |
|---------|---------|
| Daily limit (new apps) | 120 minutes (2 hours) |
| Secret extra time | 30 minutes |
| Blocking message | आज का समय समाप्त हो गया है।\nकृपया मोबाइल का उपयोग बंद करें। |
| Recovery PIN | 000000 (change in DB settings after setup) |
| Polling interval | Every 3 seconds |
| Heartbeat | Every 10 min (alarm) + 15 min (WorkManager) |

---

## Permissions Required (User Must Grant Manually)

1. **Usage Access** — `Settings → Special app access → Usage access → Dubey`
2. **Display over other apps** — `Settings → Special app access → Display over apps → Dubey`
3. **Battery optimization OFF** — `Settings → Battery → App launch → Dubey → Manual → all OFF`
4. **Auto-start ON** — `Settings → App Management → Dubey → Auto-start → ON`
