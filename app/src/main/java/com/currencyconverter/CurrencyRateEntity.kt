package com.currencyconverter

import androidx.room.Entity

@Entity(tableName = "currency_rates", primaryKeys = ["code", "base"])
data class CurrencyRateEntity(
    val code: String,
    val rate: Double,
    val base: String,
    val updatedAt: Long
)
