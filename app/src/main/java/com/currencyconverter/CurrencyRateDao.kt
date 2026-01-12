package com.currencyconverter

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CurrencyRateDao {
    @Query("SELECT * FROM currency_rates WHERE base = :base")
    suspend fun getAllRates(base: String): List<CurrencyRateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rates: List<CurrencyRateEntity>)

    @Query("DELETE FROM currency_rates WHERE base = :base")
    suspend fun clearBase(base: String)

    @Query("SELECT MAX(updatedAt) FROM currency_rates WHERE base = :base")
    suspend fun getLastUpdated(base: String): Long?
}
