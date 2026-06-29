package com.dubey.timelimiter.service

import android.app.*
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.dubey.timelimiter.DubeyApplication
import com.dubey.timelimiter.R
import com.dubey.timelimiter.data.AppDatabase
import com.dubey.timelimiter.ui.overlay.BlockingActivity
import kotlinx.coroutines.*
import java.util.Calendar

class MonitoringService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var database: AppDatabase

    // Track how much foreground time we've already "counted" per package (in ms)
    // Key: packageName, Value: totalForegroundTime already counted (ms)
    private val countedForegroundMs = mutableMapOf<String, Long>()

    // When we last threw up the overlay for a package (ms). Used to throttle
    // re-launches so the overlay doesn't flicker every poll, while still
    // re-blocking if the user reopens an over-limit app after the cooldown.
    private val lastBlockedAt = mutableMapOf<String, Long>()

    override fun onCreate() {
        super.onCreate()
        database = (application as DubeyApplication).database
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        scheduleAlarmHeartbeat()
        scheduleMidnightReset()
        startMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_MIDNIGHT_RESET -> {
                serviceScope.launch {
                    database.monitoredAppDao().resetAllUsage()
                    countedForegroundMs.clear()
                    lastBlockedAt.clear()
                    // Re-schedule for next midnight
                    scheduleMidnightReset()
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startMonitoring() {
        serviceScope.launch {
            while (isActive) {
                try {
                    checkForegroundApp()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun checkForegroundApp() {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val currentTime = System.currentTimeMillis()

        // Query from start of today until now
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = cal.timeInMillis

        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startOfDay,
            currentTime
        )

        if (stats.isNullOrEmpty()) return

        // Find the currently-in-foreground app: it's the one used most recently
        val currentApp = stats.maxByOrNull { it.lastTimeUsed } ?: return
        val currentPkg = currentApp.packageName

        // Skip system apps and our own app
        if (currentPkg == packageName || currentPkg.startsWith("com.android")) return

        // Get all monitored apps
        val monitoredApps = database.monitoredAppDao().getAllAppsOnce()

        // Update usage for all apps that have foreground time today
        for (stat in stats) {
            val pkg = stat.packageName
            val monitoredApp = monitoredApps.find { it.packageName == pkg } ?: continue
            val totalFgMs = stat.totalTimeInForeground

            if (totalFgMs <= 0) continue

            // How much new time since we last counted?
            val alreadyCounted = countedForegroundMs[pkg] ?: 0L
            val newMs = totalFgMs - alreadyCounted

            if (newMs > 0) {
                val newMinutes = (newMs / 60_000).toInt()
                if (newMinutes > 0) {
                    database.monitoredAppDao().incrementUsage(pkg, newMinutes)
                    // Only advance the counter by the whole minutes we credited,
                    // so the leftover seconds carry over and aren't discarded.
                    countedForegroundMs[pkg] = alreadyCounted + (newMinutes * 60_000L)
                }
                // If we haven't credited a full minute yet, leave countedForegroundMs
                // unchanged so the partial seconds accumulate toward the next minute.
            }

            // Check if limit reached. Re-block whenever the over-limit app is in
            // the foreground — if the user dismisses the overlay and reopens the
            // app while still over the limit, it must block again. A short
            // cooldown prevents the overlay from re-launching on every poll.
            if (pkg == currentPkg) {
                val updatedApp = database.monitoredAppDao().getApp(pkg)
                if (updatedApp != null && updatedApp.todayUsageMinutes >= updatedApp.dailyLimitMinutes) {
                    val last = lastBlockedAt[pkg] ?: 0L
                    if (currentTime - last >= BLOCK_COOLDOWN_MS) {
                        lastBlockedAt[pkg] = currentTime
                        showBlockingOverlay(pkg)
                    }
                }
            }
        }
    }

    private fun showBlockingOverlay(packageName: String) {
        // First push the blocked app to the background by going HOME. This
        // actually stops the app from staying in the foreground — launching the
        // overlay alone is not enough, because the user could swipe back to it.
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)

        // Then show the full-screen blocking message on top of the home screen.
        val intent = Intent(this, BlockingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(BlockingActivity.EXTRA_BLOCKED_PACKAGE, packageName)
        }
        startActivity(intent)
    }

    private fun scheduleAlarmHeartbeat() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_HEARTBEAT
        }
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            this, REQUEST_CODE_HEARTBEAT, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        // Every 10 minutes
        val triggerAt = System.currentTimeMillis() + 10 * 60 * 1000L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    private fun scheduleMidnightReset() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, MonitoringService::class.java).apply {
            action = ACTION_MIDNIGHT_RESET
        }
        val pendingIntent = android.app.PendingIntent.getService(
            this, REQUEST_CODE_MIDNIGHT, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        // Trigger at next midnight
        val midnight = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, midnight.timeInMillis, pendingIntent
            )
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, midnight.timeInMillis, pendingIntent)
        }
    }

    // Call this when secret extra time is granted — clear the cooldown so the
    // overlay stops firing immediately for the now-extended package.
    fun unblockPackage(packageName: String) {
        lastBlockedAt.remove(packageName)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        private const val CHANNEL_ID = "monitoring_channel"
        private const val NOTIFICATION_ID = 1001
        private const val POLL_INTERVAL_MS = 3000L
        // Don't re-launch the overlay more than once per this window per app.
        private const val BLOCK_COOLDOWN_MS = 5000L
        private const val REQUEST_CODE_HEARTBEAT = 2001
        private const val REQUEST_CODE_MIDNIGHT = 2002
        const val ACTION_MIDNIGHT_RESET = "com.dubey.timelimiter.ACTION_MIDNIGHT_RESET"
    }
}
