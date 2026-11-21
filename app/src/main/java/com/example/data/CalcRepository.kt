package com.example.data

import kotlinx.coroutines.flow.Flow

class CalcRepository(private val dao: CalcDao) {
    val history: Flow<List<CalcHistoryEntity>> = dao.observeHistory()
    val memories: Flow<List<MemoryRegisterEntity>> = dao.observeMemories()

    suspend fun saveHistory(entry: CalcHistoryEntity) = dao.insertHistory(entry)

    suspend fun deleteHistory(id: Long) = dao.deleteHistoryById(id)

    suspend fun clearHistory() = dao.clearHistory()

    suspend fun loadMemoriesOnce(): List<MemoryRegisterEntity> = dao.getAllMemoriesOnce()

    suspend fun saveMemory(name: String, value: String) =
        dao.upsertMemory(MemoryRegisterEntity(name = name, value = value))

    suspend fun clearMemories() = dao.clearMemories()
}
