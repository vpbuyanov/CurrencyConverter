package com.currencyconverter

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ConversionDao {
    @Insert
    suspend fun insert(conversion: ConversionEntity)

    @Query("SELECT * FROM conversion_history ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getLatest(limit: Int): List<ConversionEntity>
}
