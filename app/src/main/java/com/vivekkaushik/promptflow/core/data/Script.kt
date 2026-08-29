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
    val recorded: Boolean = false,      // true once a Studio take was saved with this script
    val createdAt: Long,
    val updatedAt: Long,
) {
    /** Spoken words only — headers and [[markers]] never count toward duration (spec: Phase 3). */
    val wordCount: Int get() = com.vivekkaushik.promptflow.core.prompter.ScriptMarkup.parse(body).words.size
}
