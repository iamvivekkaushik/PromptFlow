package com.vivekkaushik.promptflow.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Device-only storage — no login, no cloud (spec §01)
@Database(entities = [Script::class, Take::class], version = 3, exportSchema = false)
abstract class PromptFlowDb : RoomDatabase() {
    abstract fun scripts(): ScriptDao
    abstract fun takes(): TakeDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE scripts ADD COLUMN recorded INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS takes (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "scriptId INTEGER NOT NULL, " +
                        "uri TEXT NOT NULL, " +
                        "durationMs INTEGER NOT NULL, " +
                        "quality TEXT NOT NULL, " +
                        "fps INTEGER NOT NULL, " +
                        "createdAt INTEGER NOT NULL, " +
                        "FOREIGN KEY(scriptId) REFERENCES scripts(id) ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_takes_scriptId ON takes(scriptId)")
            }
        }

        fun build(context: Context): PromptFlowDb =
            Room.databaseBuilder(context, PromptFlowDb::class.java, "promptflow.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
