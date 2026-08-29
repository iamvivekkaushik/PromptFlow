package com.vivekkaushik.promptflow.core.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** One saved Studio recording, linked to the script it was read from. */
@Entity(
    tableName = "takes",
    foreignKeys = [ForeignKey(
        entity = Script::class,
        parentColumns = ["id"],
        childColumns = ["scriptId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("scriptId")],
)
data class Take(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scriptId: Long,
    val uri: String,           // MediaStore content:// uri
    val durationMs: Long,
    val quality: String,       // "4K" | "1080p" | "720p"
    val fps: Int,
    val createdAt: Long,
)
