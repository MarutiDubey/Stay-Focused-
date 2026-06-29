package com.dubey.timelimiter

import android.app.Application
import com.dubey.timelimiter.data.AppDatabase
import com.dubey.timelimiter.service.ServiceHeartbeatWorker

class DubeyApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate() {
        super.onCreate()
        // Schedule WorkManager heartbeat (Layer 2 of ColorOS survival)
        ServiceHeartbeatWorker.schedule(this)
    }
}
