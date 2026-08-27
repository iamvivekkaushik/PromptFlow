package com.vivekkaushik.promptflow.core.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.vivekkaushik.promptflow.core.prompter.PrompterEngine
import java.util.Locale

/**
 * Voice-activated scroll (spec §04): SpeechRecognizer partial results are fuzzy-aligned
 * (Levenshtein ≤ 2 per token) against the script inside a look-ahead window, and the
 * matched word index steers the engine. Recognizer self-terminates — restart on
 * ERROR_NO_MATCH / end of speech.
 */
class SpeechSync(
    private val context: Context,
    private val engine: PrompterEngine,
) : RecognitionListener {

    private var recognizer: SpeechRecognizer? = null
    private var active = false

    fun start() {
        if (active || !SpeechRecognizer.isRecognitionAvailable(context)) return
        active = true
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).also {
            it.setRecognitionListener(this)
        }
        listen()
    }

    fun stop() {
        active = false
        recognizer?.destroy()
        recognizer = null
        engine.resetVoiceSteering()
    }

    private fun listen() {
        if (!active) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            if (Build.VERSION.SDK_INT >= 33) putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        try {
            recognizer?.startListening(intent)
        } catch (_: Exception) {
            active = false
        }
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
        listen() // recognizer self-terminates after final results
    }

    override fun onError(error: Int) {
        when (error) {
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
            SpeechRecognizer.ERROR_CLIENT -> listen()
            else -> { /* keep quiet; caller may stop() */ }
        }
    }

    private fun align(heardText: String) {
        val heard = heardText.lowercase(Locale.getDefault())
            .split(Regex("\\s+")).filter { it.isNotBlank() }
            .takeLast(PHRASE_LEN)
        if (heard.isEmpty()) return
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
