package com.vivekkaushik.promptflow.core.speech

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.vivekkaushik.promptflow.core.prompter.PrompterEngine
import java.util.Locale

/**
 * Voice-activated scroll (spec §04): SpeechRecognizer partial results are fuzzy-aligned
 * (Levenshtein ≤ 2 per token) against the script inside a look-ahead window, and the
 * matched word index steers the engine. Recognizer self-terminates — restarted with a
 * short delay so the session isn't a tight beep loop.
 *
 * The system recognizer plays a chime on every startListening; the music stream is muted
 * for the duration of a sync session so the restarts stay silent.
 */
class SpeechSync(
    private val context: Context,
    private val engine: PrompterEngine,
) : RecognitionListener {

    private val handler = Handler(Looper.getMainLooper())
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var recognizer: SpeechRecognizer? = null
    private var active = false
    private val mutedStreams = mutableListOf<Int>()

    fun start() {
        if (active || !SpeechRecognizer.isRecognitionAvailable(context)) return
        active = true
        engine.setVoiceActive(true)
        // Silence the recognizer's start/stop chimes for the whole session. Which stream
        // carries the tone varies by OEM, so mute every candidate we're allowed to.
        if (mutedStreams.isEmpty()) {
            for (stream in CHIME_STREAMS) {
                runCatching {
                    audio.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, 0)
                    mutedStreams.add(stream)
                }
            }
        }
        recognizer = createRecognizer()
        listen()
    }

    fun stop() {
        if (!active && recognizer == null) return
        active = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        for (stream in mutedStreams) {
            runCatching { audio.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, 0) }
        }
        mutedStreams.clear()
        engine.setVoiceActive(false)
    }

    /**
     * On-device recognizer (API 31+) when available: silent (no request chime), private,
     * and holds long sessions. The network recognizer is the fallback.
     */
    private fun createRecognizer(): SpeechRecognizer =
        (if (Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context))
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        else
            SpeechRecognizer.createSpeechRecognizer(context)
        ).also { it.setRecognitionListener(this) }

    private fun listen() {
        if (!active) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Hold one long session instead of cycling the mic every few seconds —
            // cycling flaps the privacy indicator and replays the chime on some OEMs
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 300_000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 60_000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 60_000L)
        }
        try {
            recognizer?.startListening(intent)
        } catch (_: Exception) {
            restart(RESTART_DELAY_MS)
        }
    }

    /** Recognizer sessions self-terminate; schedule the next one off the callback stack. */
    private fun restart(delayMs: Long, recreate: Boolean = false) {
        if (!active) return
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            if (!active) return@postDelayed
            if (recreate) {
                recognizer?.destroy()
                recognizer = createRecognizer()
            }
            listen()
        }, delayMs)
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val heard = partialResults
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull() ?: return
        align(heard)
    }

    override fun onResults(results: Bundle?) {
        results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()?.let { align(it) }
        restart(RESTART_DELAY_MS)
    }

    override fun onError(error: Int) {
        when (error) {
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> restart(RESTART_DELAY_MS)
            // Client/busy states recover after the recognizer is rebuilt
            SpeechRecognizer.ERROR_CLIENT,
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
            SpeechRecognizer.ERROR_AUDIO -> restart(RECOVER_DELAY_MS, recreate = true)
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> stop()
            // Network hiccups etc. — retry with a longer backoff
            else -> restart(RECOVER_DELAY_MS)
        }
    }

    private fun align(heardText: String) {
        val heard = heardText.split(Regex("\\s+"))
            .map { normalize(it) }.filter { it.isNotBlank() }
            .takeLast(PHRASE_LEN)
        if (heard.size < 2) return // single tokens misalign too easily
        val script = engine.tokens.map { normalize(it) }
        if (script.isEmpty()) return

        val from = (engine.currentWordIndex - 2).coerceAtLeast(0)
        val to = (engine.currentWordIndex + WINDOW).coerceAtMost(script.size - heard.size)
        if (to < from) return

        var bestIdx = -1
        var bestScore = Int.MAX_VALUE
        for (i in from..to) {
            var score = 0
            for (j in heard.indices) {
                score += levenshtein(heard[j], script[i + j], max = 3)
            }
            if (score < bestScore) { bestScore = score; bestIdx = i }
        }
        // accept only reasonable matches: avg distance ≤ 2 per token (spec: Levenshtein ≤ 2)
        if (bestIdx >= 0 && bestScore <= heard.size * 2) {
            engine.targetWord(bestIdx + heard.size)
        }
    }

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onEvent(eventType: Int, params: Bundle?) {}

    companion object {
        private const val WINDOW = 12
        private const val PHRASE_LEN = 4
        private const val RESTART_DELAY_MS = 200L
        private const val RECOVER_DELAY_MS = 900L
        private val CHIME_STREAMS = intArrayOf(
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_SYSTEM,
            AudioManager.STREAM_NOTIFICATION,
        )

        fun normalize(token: String): String =
            token.lowercase(Locale.getDefault()).filter { it.isLetterOrDigit() }

        fun levenshtein(a: String, b: String, max: Int = Int.MAX_VALUE): Int {
            if (a == b) return 0
            if (a.isEmpty()) return minOf(b.length, max)
            if (b.isEmpty()) return minOf(a.length, max)
            var prev = IntArray(b.length + 1) { it }
            var curr = IntArray(b.length + 1)
            for (i in 1..a.length) {
                curr[0] = i
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    curr[j] = minOf(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
                }
                val tmp = prev; prev = curr; curr = tmp
            }
            return minOf(prev[b.length], max)
        }
    }
}
