package com.currencyconverter

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [CurrencyRateEntity::class, ConversionEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun currencyRateDao(): CurrencyRateDao
    abstract fun conversionDao(): ConversionDao

    companion object {
        fun build(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "currency.db"
            ).fallbackToDestructiveMigration().build()
        }
    }
}
