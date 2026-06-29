package com.dubey.timelimiter.service

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import androidx.work.*
import java.util.concurrent.TimeUnit

class ServiceHeartbeatWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    override fun doWork(): Result {
        if (!isMonitoringServiceRunning()) {
            val serviceIntent = Intent(applicationContext, MonitoringService::class.java)
            applicationContext.startForegroundService(serviceIntent)
        }
        return Result.success()
    }

    @Suppress("DEPRECATION")
    private fun isMonitoringServiceRunning(): Boolean {
        val manager = applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == MonitoringService::class.java.name }
    }

    companion object {
        private const val WORK_NAME = "service_heartbeat"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ServiceHeartbeatWorker>(
                15, TimeUnit.MINUTES
            ).setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(false)
                    .build()
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
