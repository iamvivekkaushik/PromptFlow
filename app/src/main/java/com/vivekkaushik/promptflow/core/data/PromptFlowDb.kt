package com.vivekkaushik.promptflow.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// Device-only storage — no login, no cloud (spec §01)
@Database(entities = [Script::class], version = 1, exportSchema = false)
abstract class PromptFlowDb : RoomDatabase() {
    abstract fun scripts(): ScriptDao

    companion object {
        fun build(context: Context): PromptFlowDb =
            Room.databaseBuilder(context, PromptFlowDb::class.java, "promptflow.db").build()
    }
}
