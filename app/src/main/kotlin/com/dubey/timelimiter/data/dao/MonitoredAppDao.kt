package com.dubey.timelimiter.data.dao

import androidx.room.*
import com.dubey.timelimiter.data.entity.MonitoredApp
import kotlinx.coroutines.flow.Flow

@Dao
interface MonitoredAppDao {
    @Query("SELECT * FROM monitored_apps")
    fun getAllApps(): Flow<List<MonitoredApp>>

    @Query("SELECT * FROM monitored_apps")
    suspend fun getAllAppsOnce(): List<MonitoredApp>

    @Query("SELECT * FROM monitored_apps WHERE packageName = :packageName")
    suspend fun getApp(packageName: String): MonitoredApp?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApp(app: MonitoredApp)

    @Update
    suspend fun updateApp(app: MonitoredApp)

    @Delete
    suspend fun deleteApp(app: MonitoredApp)

    @Query("UPDATE monitored_apps SET todayUsageMinutes = 0, secretExtraUsedToday = 0")
    suspend fun resetAllUsage()

    @Query("UPDATE monitored_apps SET todayUsageMinutes = todayUsageMinutes + :minutes WHERE packageName = :packageName")
    suspend fun incrementUsage(packageName: String, minutes: Int)
}
