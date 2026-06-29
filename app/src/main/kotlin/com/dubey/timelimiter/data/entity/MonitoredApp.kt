package com.dubey.timelimiter.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "monitored_apps")
data class MonitoredApp(
    @PrimaryKey
    val packageName: String,
    val appName: String,
    val dailyLimitMinutes: Int,
    val secretExtraMinutes: Int,
    val todayUsageMinutes: Int = 0,
    val secretExtraUsedToday: Boolean = false
)
