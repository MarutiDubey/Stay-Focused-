package com.dubey.timelimiter.data.dao

import androidx.room.*
import com.dubey.timelimiter.data.entity.Setting

@Dao
interface SettingDao {
    @Query("SELECT * FROM settings WHERE key = :key")
    suspend fun getSetting(key: String): Setting?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSetting(setting: Setting)

    @Query("DELETE FROM settings WHERE key = :key")
    suspend fun deleteSetting(key: String)
}
