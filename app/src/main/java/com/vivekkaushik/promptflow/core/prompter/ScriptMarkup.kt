package com.vivekkaushik.promptflow.core.prompter

/**
 * Prompter markup (spec: Phase 3): plain text plus
 *   `## Section title`      — section header line
 *   `[[pause]]` `[[pause 3s]]` — auto-pause point (optional hold seconds; plain pause = tap to resume)
 *   `[[b-roll: drone shot]]` / `[[note ...]]` — non-spoken stage direction
 *
 * Markers are NEVER part of the spoken word stream: word counts, WPM math, duration
 * estimates and voice-sync alignment all run on [words] only.
 */
object ScriptMarkup {

    sealed interface Segment {
        /** Spoken text run (one or more lines without markers). */
        data class Speech(val text: String) : Segment
        /** `## title` line. [wordIndex] = index of the next spoken word after it. */
        data class Header(val title: String, val wordIndex: Int) : Segment
        /** `[[...]]` directive. [holdSec] > 0 = timed hold; 0 = wait for tap (pause only). */
        data class Directive(val kind: Kind, val label: String, val holdSec: Int, val wordIndex: Int) : Segment
    }

    enum class Kind { PAUSE, NOTE }

    data class Parsed(
        val segments: List<Segment>,
        val words: List<String>,
        /** Word indices where sections start (for next/prev-section jumps). Always sorted. */
        val sectionStarts: List<Int>,
        /** Pause directives by the word index they sit before. */
        val pauses: List<Segment.Directive>,
    )

    private val DIRECTIVE = Regex("""\[\[\s*([a-zA-Z-]+)\s*(?::?\s*([^\]]*?))?\s*]]""")
    private val PAUSE_SECS = Regex("""(\d+)\s*s?""")

    fun parse(body: String): Parsed {
        val segments = mutableListOf<Segment>()
        val words = mutableListOf<String>()
        val sectionStarts = mutableListOf<Int>()
        val pauses = mutableListOf<Segment.Directive>()
        val speech = StringBuilder()

        fun flushSpeech() {
            val t = speech.toString().trim()
            if (t.isNotEmpty()) {
                segments += Segment.Speech(t)
                words += t.split(Regex("\\s+")).filter { it.isNotBlank() }
            }
            speech.clear()
        }

        for (rawLine in body.lines()) {
            val line = rawLine.trimEnd()
            val headerMatch = Regex("""^\s*#{1,6}\s+(.*)$""").find(line)
            if (headerMatch != null) {
                flushSpeech()
                val idx = words.size
                segments += Segment.Header(headerMatch.groupValues[1].trim(), idx)
                sectionStarts += idx
                continue
            }
            // split the line around [[...]] directives
            var cursor = 0
            for (m in DIRECTIVE.findAll(line)) {
                speech.append(line.substring(cursor, m.range.first)).append(' ')
                flushSpeech()
                val kindRaw = m.groupValues[1].lowercase()
                val arg = m.groupValues[2].trim()
                val idx = words.size
                val seg = if (kindRaw == "pause") {
                    val secs = PAUSE_SECS.matchEntire(arg)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    Segment.Directive(Kind.PAUSE, if (secs > 0) "pause ${secs}s" else "pause", secs, idx)
                } else {
                    val label = if (arg.isNotEmpty()) "$kindRaw: $arg" else kindRaw
                    Segment.Directive(Kind.NOTE, label, 0, idx)
                }
                segments += seg
                if (seg.kind == Kind.PAUSE) pauses += seg
                cursor = m.range.last + 1
            }
            speech.append(line.substring(cursor)).append('\n')
        }
        flushSpeech()
        return Parsed(segments, words, sectionStarts, pauses)
    }
}
