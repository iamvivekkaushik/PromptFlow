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

    /** Parsed markup for the loaded script: sections + pause points (spec: Phase 3). */
    var markup: ScriptMarkup.Parsed = ScriptMarkup.parse(DEMO_SCRIPT)
        private set

    /**
     * Top-of-line pixel for each spoken word, reported by the rendering surface from its
     * real TextLayout. Null until first layout — mapping falls back to the linear estimate.
     */
    @Volatile var wordTopsPx: FloatArray? = null
        set(value) {
            field = value
            // Positions only become real here; re-aim the pause pointer against them.
            if (value != null) resyncPauses(_state.value.offsetPx)
        }

    // Index into markup.pauses of the next pause ahead of the read position
    @Volatile private var pausePtr = 0

    fun pixelForWord(index: Int): Float {
        val tops = wordTopsPx
        if (tops != null && tops.isNotEmpty()) return tops[index.coerceIn(0, tops.size - 1)]
        return index.toFloat() / wordCount * contentHeightPx
    }

    private fun wordAtPixel(y: Float): Int {
        val tops = wordTopsPx
        if (tops == null || tops.isEmpty())
            return if (contentHeightPx > 0f) ((y / contentHeightPx) * wordCount).toInt().coerceIn(0, wordCount - 1) else 0
        var lo = 0; var hi = tops.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (tops[mid] <= y) lo = mid else hi = mid - 1
        }
        return lo
    }

    /** Re-aim the pause pointer at the first pause past the given scroll offset. */
    private fun resyncPauses(offsetPx: Float) {
        val pauses = markup.pauses
        // Before the first layout every word maps to pixel 0, which would skip every
        // pause. Keep the pointer at the top until real positions arrive.
        if (contentHeightPx <= 0f || wordTopsPx == null) { pausePtr = 0; return }
        var i = 0
        while (i < pauses.size && pixelForWord(pauses[i].wordIndex) <= offsetPx) i++
        pausePtr = i
    }

    // Voice-sync steering: while active, the tick loop continuously chases the last
    // matched word; when the speaker goes quiet the scroll eases to a crawl.
    @Volatile private var voiceActive = false
    @Volatile private var voiceTargetWord = -1
    @Volatile private var lastVoiceMatchNs = 0L
    private var voiceMultiplier = 1f
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
                    updateVoiceSteering(now, dt, pxPerWord)
                    val target = s.wpm / 60f * pxPerWord * SPEED_FACTOR * voiceMultiplier
                    // exponential ease, ~400ms time constant
                    currentSpeedPxSec += (target - currentSpeedPxSec) * min(1f, dt / 0.4f)
                    val next = s.offsetPx + currentSpeedPxSec * dt
                    // Pause markers: stop the moment the guide band reaches one
                    val pause = markup.pauses.getOrNull(pausePtr)
                    val pausePx = pause?.let { pixelForWord(it.wordIndex) }
                    if (pause != null && pausePx != null && pausePx in s.offsetPx..next) {
                        pausePtr++
                        if (pause.holdSec > 0) {
                            countdownRemaining = pause.holdSec.toFloat()
                            _state.update { it.copy(offsetPx = pausePx, playing = false, countdown = pause.holdSec) }
                        } else {
                            _state.update { it.copy(offsetPx = pausePx, playing = false) }
                        }
                    } else if (next >= contentHeightPx) {
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
            markup = ScriptMarkup.parse(script.body)
            tokens = markup.words
            contentHeightPx = 0f
            wordTopsPx = null
        }
        // A load always restarts from the top of the script unless the caller
        // explicitly passes a resume position (the library's Continue card).
        val resume = resumeFraction.takeIf { it > 0f && it < 0.98f }
        pendingSeekFraction = if (sameScript && contentHeightPx > 0f) null else resume
        val offset = if (resume != null && sameScript && contentHeightPx > 0f) resume * contentHeightPx else 0f
        countdownRemaining = 0f
        resetVoiceSteering()
        pausePtr = 0
        resyncPauses(offset)
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
    fun rewind() {
        resetVoiceSteering(); countdownRemaining = 0f; pausePtr = 0
        _state.update { it.copy(offsetPx = 0f, playing = false, countdown = 0) }
    }

    /**
     * Jump to the previous/next `##` section start. Backwards falls back to the top of the
     * script when there is no earlier section. Playback state is preserved.
     */
    fun jumpSection(direction: Int) {
        val starts = markup.sectionStarts
        val target: Float = if (direction < 0) {
            // small slack so repeated taps step back through sections, not to the same one
            val prev = starts.lastOrNull { pixelForWord(it) < _state.value.offsetPx - 8f }
            if (prev != null) pixelForWord(prev) else 0f
        } else {
            val next = starts.firstOrNull { pixelForWord(it) > _state.value.offsetPx + 8f }
                ?: return
            pixelForWord(next)
        }
        resetVoiceSteering()
        countdownRemaining = 0f
        _state.update { it.copy(offsetPx = target.coerceIn(0f, contentHeightPx), countdown = 0) }
        resyncPauses(target)
    }

    fun setWpm(v: Int) = _state.update { it.copy(wpm = v.coerceIn(WPM_MIN, WPM_MAX)) }
    fun nudgeWpm(delta: Int) = setWpm(_state.value.wpm + delta)

    fun seekFraction(fraction: Float) {
        if (contentHeightPx > 0f) {
            val off = fraction.coerceIn(0f, 1f) * contentHeightPx
            _state.update { it.copy(offsetPx = off) }
            resyncPauses(off)
        }
    }

    val progressFraction: Float
        get() = if (contentHeightPx > 0f) (_state.value.offsetPx / contentHeightPx).coerceIn(0f, 1f) else 0f

    /** Word index currently at the guide band. */
    val currentWordIndex: Int
        get() = wordAtPixel(_state.value.offsetPx)

    /** Speech sync: the scroller chases this word until the next match arrives. */
    fun targetWord(index: Int) {
        voiceTargetWord = index.coerceIn(0, wordCount)
        lastVoiceMatchNs = System.nanoTime()
    }

    fun setVoiceActive(active: Boolean) {
        voiceActive = active
        if (!active) resetVoiceSteering()
    }

    fun resetVoiceSteering() { voiceMultiplier = 1f; voiceTargetWord = -1; lastVoiceMatchNs = 0L }

    /**
     * Proportional controller, re-evaluated every frame: aim to close the gap to the
     * spoken word in ~1.2s, clamped to [VOICE_MIN, VOICE_MAX] of the set WPM. With no
     * match for VOICE_IDLE_AFTER_NS (speaker paused), ease down to a crawl instead of
     * running away from the reader.
     */
    private fun updateVoiceSteering(now: Long, dt: Float, pxPerWord: Float) {
        if (!voiceActive || lastVoiceMatchNs == 0L || voiceTargetWord < 0) {
            if (!voiceActive) voiceMultiplier = 1f
            return
        }
        if (now - lastVoiceMatchNs > VOICE_IDLE_AFTER_NS) {
            voiceMultiplier += (VOICE_IDLE - voiceMultiplier) * min(1f, dt / 0.6f)
            return
        }
        val desired = pixelForWord(voiceTargetWord)
        val diffPx = desired - _state.value.offsetPx
        val basePxSec = _state.value.wpm / 60f * pxPerWord * SPEED_FACTOR
        voiceMultiplier = (1f + diffPx / (basePxSec * 1.2f)).coerceIn(VOICE_MIN, VOICE_MAX)
    }

    companion object {
        const val WPM_MIN = 60
        const val WPM_MAX = 500

        /** Scroll-speed calibration: velocity is 1× the literal words-per-minute pace. */
        const val SPEED_FACTOR = 1f
        private const val FRAME_MS = 16L

        // Voice steering bounds: fast enough to visibly catch up, slow crawl when quiet
        private const val VOICE_MIN = 0.2f
        private const val VOICE_MAX = 1.8f
        private const val VOICE_IDLE = 0.25f
        private const val VOICE_IDLE_AFTER_NS = 2_200_000_000L

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
