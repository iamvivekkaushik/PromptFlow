package com.vivekkaushik.promptflow.core.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class PrompterSettings(
    val fontSizeSp: Int = 30,          // 18–64sp user-set (spec §03)
    val lineHeightMult: Float = 1.5f,  // 1.2–2.2×
    val fontWeight: Int = 600,         // 400 / 600 / 700
    val textColor: Long = 0xFFE4E3DB,  // high-contrast pairs only
    val fontName: String = "IBM Plex Sans",
    val customFontPath: String = "",   // user .ttf/.otf overrides fontName when set
    val mirrorH: Boolean = false,
    val mirrorV: Boolean = false,
    val voiceSync: Boolean = false,
    val tapPause: Boolean = true,
    val wpm: Int = 140,
    val overlayOpacity: Int = 62,      // 15–95%
    val startDelaySec: Int = 0,        // countdown before the prompter starts scrolling
)

private val Context.dataStore by preferencesDataStore(name = "prompter_settings")

class SettingsStore(private val context: Context) {
    private object K {
        val fontSize = intPreferencesKey("font_size")
        val lineHeight = floatPreferencesKey("line_height")
        val weight = intPreferencesKey("weight")
        val textColor = stringPreferencesKey("text_color")
        val fontName = stringPreferencesKey("font_name")
        val customFontPath = stringPreferencesKey("custom_font_path")
        val mirrorH = booleanPreferencesKey("mirror_h")
        val mirrorV = booleanPreferencesKey("mirror_v")
        val voiceSync = booleanPreferencesKey("voice_sync")
        val tapPause = booleanPreferencesKey("tap_pause")
        val wpm = intPreferencesKey("wpm")
        val opacity = intPreferencesKey("overlay_opacity")
        val startDelay = intPreferencesKey("start_delay")
    }

    val settings: Flow<PrompterSettings> = context.dataStore.data.map { p ->
        val d = PrompterSettings()
        PrompterSettings(
            fontSizeSp = p[K.fontSize] ?: d.fontSizeSp,
            lineHeightMult = p[K.lineHeight] ?: d.lineHeightMult,
            fontWeight = p[K.weight] ?: d.fontWeight,
            textColor = p[K.textColor]?.toLongOrNull(16) ?: d.textColor,
            fontName = p[K.fontName] ?: d.fontName,
            customFontPath = p[K.customFontPath] ?: d.customFontPath,
            mirrorH = p[K.mirrorH] ?: d.mirrorH,
            mirrorV = p[K.mirrorV] ?: d.mirrorV,
            voiceSync = p[K.voiceSync] ?: d.voiceSync,
            tapPause = p[K.tapPause] ?: d.tapPause,
            wpm = p[K.wpm] ?: d.wpm,
            overlayOpacity = p[K.opacity] ?: d.overlayOpacity,
            startDelaySec = p[K.startDelay] ?: d.startDelaySec,
        )
    }

    suspend fun setFontSize(v: Int) = context.dataStore.edit { it[K.fontSize] = v }
    suspend fun setLineHeight(v: Float) = context.dataStore.edit { it[K.lineHeight] = v }
    suspend fun setWeight(v: Int) = context.dataStore.edit { it[K.weight] = v }
    suspend fun setTextColor(v: Long) = context.dataStore.edit { it[K.textColor] = v.toString(16) }
    suspend fun setFontName(v: String) = context.dataStore.edit { it[K.fontName] = v; it[K.customFontPath] = "" }
    suspend fun setCustomFontPath(v: String) = context.dataStore.edit { it[K.customFontPath] = v }
    suspend fun setMirrorH(v: Boolean) = context.dataStore.edit { it[K.mirrorH] = v }
    suspend fun setMirrorV(v: Boolean) = context.dataStore.edit { it[K.mirrorV] = v }
    suspend fun setVoiceSync(v: Boolean) = context.dataStore.edit { it[K.voiceSync] = v }
    suspend fun setTapPause(v: Boolean) = context.dataStore.edit { it[K.tapPause] = v }
    suspend fun setWpm(v: Int) = context.dataStore.edit { it[K.wpm] = v }
    suspend fun setOverlayOpacity(v: Int) = context.dataStore.edit { it[K.opacity] = v }
    suspend fun setStartDelay(v: Int) = context.dataStore.edit { it[K.startDelay] = v }
}
