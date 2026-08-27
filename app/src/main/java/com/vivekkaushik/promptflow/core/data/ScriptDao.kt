package com.vivekkaushik.promptflow.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScriptDao {
    @Query("SELECT * FROM scripts ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<Script>>

    @Query("SELECT * FROM scripts WHERE id = :id")
    suspend fun byId(id: Long): Script?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(script: Script): Long

    @Query("UPDATE scripts SET progress = :progress WHERE id = :id")
    suspend fun updateProgress(id: Long, progress: Float)

    @Query("DELETE FROM scripts WHERE id = :id")
    suspend fun delete(id: Long)
}
