package com.dubey.timelimiter.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.ActivityManager

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_HEARTBEAT -> {
                // Check if MonitoringService is running; restart if not
                if (!isServiceRunning(context, MonitoringService::class.java)) {
                    val serviceIntent = Intent(context, MonitoringService::class.java)
                    context.startForegroundService(serviceIntent)
                } else {
                    // Service is alive — reschedule next heartbeat alarm by restarting service intent
                    // (service's onStartCommand will do nothing since it returns START_STICKY)
                    val serviceIntent = Intent(context, MonitoringService::class.java)
                    context.startForegroundService(serviceIntent)
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == serviceClass.name }
    }

    companion object {
        const val ACTION_HEARTBEAT = "com.dubey.timelimiter.ACTION_HEARTBEAT"
    }
}
