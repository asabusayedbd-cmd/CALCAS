package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CalcDao {
    @Query("SELECT * FROM calc_history ORDER BY timestamp DESC LIMIT 100")
    fun observeHistory(): Flow<List<CalcHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(item: CalcHistoryEntity)

    @Query("DELETE FROM calc_history WHERE id = :id")
    suspend fun deleteHistoryById(id: Long)

    @Query("DELETE FROM calc_history")
    suspend fun clearHistory()

    @Query("SELECT * FROM memory_registers")
    fun observeMemories(): Flow<List<MemoryRegisterEntity>>

    @Query("SELECT * FROM memory_registers")
    suspend fun getAllMemoriesOnce(): List<MemoryRegisterEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMemory(reg: MemoryRegisterEntity)

    @Query("DELETE FROM memory_registers")
    suspend fun clearMemories()
}
