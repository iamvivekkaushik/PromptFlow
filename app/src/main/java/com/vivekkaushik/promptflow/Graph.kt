package com.vivekkaushik.promptflow

import android.content.Context
import com.vivekkaushik.promptflow.core.data.PromptFlowDb
import com.vivekkaushik.promptflow.core.data.SettingsStore
import com.vivekkaushik.promptflow.core.prompter.PrompterEngine
import com.vivekkaushik.promptflow.core.speech.SpeechSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** App-scoped service locator. One engine, one settings store — shared across all surfaces. */
object Graph {
    lateinit var db: PromptFlowDb; private set
    lateinit var settings: SettingsStore; private set
    lateinit var engine: PrompterEngine; private set
    lateinit var speechSync: SpeechSync; private set

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** True while a prompter surface is on screen — MainActivity routes hardware keys to the engine. */
    @Volatile var hardwareKeysActive: Boolean = false

    fun init(context: Context) {
        if (::db.isInitialized) return
        db = PromptFlowDb.build(context)
        settings = SettingsStore(context)
        engine = PrompterEngine(appScope)
        speechSync = SpeechSync(context.applicationContext, engine)
        appScope.launch {
            engine.setWpm(settings.settings.first().wpm)
        }
    }

    fun persistWpm(wpm: Int) = appScope.launch { settings.setWpm(wpm) }

    /** Flag the engine's current script as having a saved Studio take. */
    fun markCurrentScriptRecorded() {
        val id = engine.state.value.scriptId
        if (id > 0) appScope.launch { db.scripts().markRecorded(id) }
    }

    /** Persist a finished Studio take, linked to the engine's current script. */
    fun saveTake(uri: String, durationMs: Long, quality: String, fps: Int) {
        val id = engine.state.value.scriptId
        if (id > 0 && uri.isNotBlank()) appScope.launch {
            db.takes().insert(
                com.vivekkaushik.promptflow.core.data.Take(
                    scriptId = id, uri = uri, durationMs = durationMs,
                    quality = quality, fps = fps, createdAt = System.currentTimeMillis(),
                )
            )
        }
    }

    fun persistProgress() {
        val s = engine.state.value
        if (s.scriptId > 0) {
            val fraction = engine.progressFraction
            appScope.launch { db.scripts().updateProgress(s.scriptId, fraction) }
        }
    }
}
