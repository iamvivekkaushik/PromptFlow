package com.vivekkaushik.promptflow.core.prompter

import com.vivekkaushik.promptflow.core.data.Script
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * The single prompter engine (spec §01): one scroll clock, one speech-sync target,
 * shared by Studio, Overlay and Settings so WPM/position survive surface switches.
 *
 * Scroll is linear and frame-locked; speed changes are eased over 400ms (spec §03 motion).
 * Voice sync steers velocity toward the spoken word, clamped ±40% of the set WPM —
 * no jumps, no stalls (spec §04).
 */
class PrompterEngine(scope: CoroutineScope) {

    data class State(
        val scriptId: Long = -1L,
        val title: String = "",
        val text: String = DEMO_SCRIPT,
        val playing: Boolean = false,
        val offsetPx: Float = 0f,
        val wpm: Int = 140,
        /** Seconds left on the start-delay countdown; 0 = no countdown running. */
        val countdown: Int = 0,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    /** Total laid-out height of the script text, reported by whichever surface renders it. */
    var contentHeightPx: Float = 0f
        set(value) {
            field = value
            val pending = pendingSeekFraction
            if (value > 0f && pending != null) {
                pendingSeekFraction = null
                _state.update { it.copy(offsetPx = pending.coerceIn(0f, 1f) * value) }
            }
        }
    @Volatile private var pendingSeekFraction: Float? = null
    private val wordCount: Int get() = tokens.size.coerceAtLeast(1)

    var tokens: List<String> = tokenize(DEMO_SCRIPT)
        private set

    // Voice-sync steering: velocity multiplier eased toward this, clamped ±40%
    @Volatile private var voiceMultiplier = 1f
    private var currentSpeedPxSec = 0f
    private var countdownRemaining = 0f

    init {
        scope.launch {
            var last = System.nanoTime()
            while (true) {
                delay(FRAME_MS)
                val now = System.nanoTime()
                val dt = ((now - last) / 1e9f).coerceAtMost(0.1f)
                last = now
                val s = _state.value
                if (countdownRemaining > 0f) {
                    countdownRemaining -= dt
                    if (countdownRemaining <= 0f) {
                        countdownRemaining = 0f
                        _state.update { it.copy(countdown = 0, playing = true) }
                    } else {
                        val secs = kotlin.math.ceil(countdownRemaining).toInt()
                        if (secs != s.countdown) _state.update { it.copy(countdown = secs) }
                    }
                } else if (s.playing && contentHeightPx > 0f) {
                    val pxPerWord = contentHeightPx / wordCount
                    val target = s.wpm / 60f * pxPerWord * SPEED_FACTOR * voiceMultiplier
                    // exponential ease, ~400ms time constant
                    currentSpeedPxSec += (target - currentSpeedPxSec) * min(1f, dt / 0.4f)
                    val next = s.offsetPx + currentSpeedPxSec * dt
                    if (next >= contentHeightPx) {
                        _state.update { it.copy(offsetPx = contentHeightPx, playing = false) }
                    } else {
                        _state.update { it.copy(offsetPx = next) }
                    }
                } else {
                    currentSpeedPxSec = 0f
                }
            }
        }
    }

    fun load(script: Script, resumeFraction: Float = 0f) {
        val sameScript = _state.value.scriptId == script.id && script.body == _state.value.text
        if (!sameScript) {
            tokens = tokenize(script.body)
            contentHeightPx = 0f
        }
        // A load always restarts from the top of the script unless the caller
        // explicitly passes a resume position (the library's Continue card).
        val resume = resumeFraction.takeIf { it > 0f && it < 0.98f }
        pendingSeekFraction = if (sameScript && contentHeightPx > 0f) null else resume
        val offset = if (resume != null && sameScript && contentHeightPx > 0f) resume * contentHeightPx else 0f
        countdownRemaining = 0f
        voiceMultiplier = 1f
        _state.update {
            State(
                scriptId = script.id,
                title = script.title,
                text = script.body,
                wpm = it.wpm,
                offsetPx = offset,
            )
        }
    }

    /** Toggle playback. A positive [delaySec] runs a visible countdown before scrolling starts. */
    fun togglePlay(delaySec: Int = 0) {
        val s = _state.value
        when {
            s.playing || s.countdown > 0 -> {
                countdownRemaining = 0f
                _state.update { it.copy(playing = false, countdown = 0) }
            }
            delaySec > 0 -> {
                countdownRemaining = delaySec.toFloat()
                _state.update { it.copy(countdown = delaySec) }
            }
            else -> _state.update { it.copy(playing = true) }
        }
    }

    fun pause() { countdownRemaining = 0f; _state.update { it.copy(playing = false, countdown = 0) } }
    fun rewind() { voiceMultiplier = 1f; countdownRemaining = 0f; _state.update { it.copy(offsetPx = 0f, playing = false, countdown = 0) } }

    fun setWpm(v: Int) = _state.update { it.copy(wpm = v.coerceIn(WPM_MIN, WPM_MAX)) }
    fun nudgeWpm(delta: Int) = setWpm(_state.value.wpm + delta)

    fun seekFraction(fraction: Float) {
        if (contentHeightPx > 0f) _state.update { it.copy(offsetPx = fraction.coerceIn(0f, 1f) * contentHeightPx) }
    }

    val progressFraction: Float
        get() = if (contentHeightPx > 0f) (_state.value.offsetPx / contentHeightPx).coerceIn(0f, 1f) else 0f

    /** Word index currently at the guide band. */
    val currentWordIndex: Int
        get() = (progressFraction * wordCount).toInt().coerceIn(0, wordCount - 1)

    /** Speech sync: steer the scroller toward this word. Clamped ±40% of set WPM. */
    fun targetWord(index: Int) {
        if (contentHeightPx <= 0f) return
        val desired = index.toFloat() / wordCount * contentHeightPx
        val diffPx = desired - _state.value.offsetPx
        // ~2 seconds to close the gap, then clamp
        val pxPerWord = contentHeightPx / wordCount
        val basePxSec = _state.value.wpm / 60f * pxPerWord
        voiceMultiplier = (1f + diffPx / (basePxSec * 2f)).coerceIn(0.6f, 1.4f)
    }

    fun resetVoiceSteering() { voiceMultiplier = 1f }

    companion object {
        const val WPM_MIN = 60
        const val WPM_MAX = 500

        /** Scroll-speed calibration: velocity is 1× the literal words-per-minute pace. */
        const val SPEED_FACTOR = 1f
        private const val FRAME_MS = 16L

        fun tokenize(text: String): List<String> =
            text.split(Regex("\\s+")).filter { it.isNotBlank() }

        val DEMO_SCRIPT = """
            Hey everyone — welcome back to the channel.
            Today we're unboxing the new Flux 2 camera and putting its autofocus through a real stress test.
            Before we start, hit subscribe so you don't miss the full review dropping Friday.
            First impressions: the body is lighter than the spec sheet suggests, and the grip finally fits a full hand.
            The sensor is the same 33 megapixel unit as last year, but the readout is twice as fast.
            That means less rolling shutter, cleaner 4K sixty, and eye tracking that actually sticks.
            Let's flip to the rear camera and look at the footage straight out of the card.
            No color grade here — this is the standard profile, handheld, in fading light.
        """.trimIndent()
    }
}
