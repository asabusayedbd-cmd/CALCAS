package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "calc_history")
data class CalcHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val expressionPlain: String,
    val expressionAstJson: String,
    val resultExact: String?,
    val resultDecimal: String,
    val modeName: String,
    val timestamp: Long = System.currentTimeMillis(),
)

@Entity(tableName = "memory_registers")
data class MemoryRegisterEntity(
    @PrimaryKey val name: String,
    val value: String,
)
