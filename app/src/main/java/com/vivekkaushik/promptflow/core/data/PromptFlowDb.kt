package com.vivekkaushik.promptflow.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Device-only storage — no login, no cloud (spec §01)
@Database(entities = [Script::class], version = 2, exportSchema = false)
abstract class PromptFlowDb : RoomDatabase() {
    abstract fun scripts(): ScriptDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE scripts ADD COLUMN recorded INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun build(context: Context): PromptFlowDb =
            Room.databaseBuilder(context, PromptFlowDb::class.java, "promptflow.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
