package com.vivekkaushik.promptflow.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TakeDao {
    @Query("SELECT * FROM takes WHERE scriptId = :scriptId ORDER BY createdAt DESC")
    fun observeForScript(scriptId: Long): Flow<List<Take>>

    @Insert
    suspend fun insert(take: Take): Long

    @Query("DELETE FROM takes WHERE id = :id")
    suspend fun delete(id: Long)
}
