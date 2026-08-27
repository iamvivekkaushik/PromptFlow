package com.vivekkaushik.promptflow.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scripts")
data class Script(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val source: String = "On device",   // "On device" | "Imported"
    val progress: Float = 0f,           // 0..1 read position
    val createdAt: Long,
    val updatedAt: Long,
) {
    val wordCount: Int get() = body.split(Regex("\\s+")).count { it.isNotBlank() }
}
