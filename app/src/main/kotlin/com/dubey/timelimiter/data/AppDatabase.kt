package com.dubey.timelimiter.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.dubey.timelimiter.data.dao.MonitoredAppDao
import com.dubey.timelimiter.data.dao.SettingDao
import com.dubey.timelimiter.data.entity.MonitoredApp
import com.dubey.timelimiter.data.entity.Setting

@Database(
    entities = [MonitoredApp::class, Setting::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun monitoredAppDao(): MonitoredAppDao
    abstract fun settingDao(): SettingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "dubey_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
