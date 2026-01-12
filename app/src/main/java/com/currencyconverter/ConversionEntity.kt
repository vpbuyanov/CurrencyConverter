package com.currencyconverter

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversion_history")
data class ConversionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromCurrency: String,
    val toCurrency: String,
    val amount: Double,
    val result: Double,
    val createdAt: Long
)
