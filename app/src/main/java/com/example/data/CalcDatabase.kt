package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [CalcHistoryEntity::class, MemoryRegisterEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class CalcDatabase : RoomDatabase() {
    abstract fun calcDao(): CalcDao

    companion object {
        @Volatile
        private var INSTANCE: CalcDatabase? = null

        fun getInstance(context: Context): CalcDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CalcDatabase::class.java,
                    "natural_calc.db",
                ).build().also { INSTANCE = it }
            }
    }
}
