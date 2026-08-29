package com.vivekkaushik.promptflow

import com.vivekkaushik.promptflow.core.prompter.ScriptMarkup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptMarkupTest {

    @Test
    fun `markers are excluded from spoken words`() {
        val body = """
            ## Intro
            Hey everyone welcome back. [[pause]]
            Second line here. [[b-roll: drone shot]]
            ## Outro
            Thanks for watching. [[pause 3s]]
        """.trimIndent()
        val p = ScriptMarkup.parse(body)
        assertEquals(
            listOf("Hey", "everyone", "welcome", "back.", "Second", "line", "here.", "Thanks", "for", "watching."),
            p.words
        )
    }

    @Test
    fun `sections start at the right word indices`() {
        val body = "## A\none two three\n## B\nfour five"
        val p = ScriptMarkup.parse(body)
        assertEquals(listOf(0, 3), p.sectionStarts)
    }

    @Test
    fun `pause directives carry hold seconds and word position`() {
        val body = "one two [[pause]] three [[pause 5s]] four"
        val p = ScriptMarkup.parse(body)
        assertEquals(2, p.pauses.size)
        assertEquals(2, p.pauses[0].wordIndex)
        assertEquals(0, p.pauses[0].holdSec)
        assertEquals(3, p.pauses[1].wordIndex)
        assertEquals(5, p.pauses[1].holdSec)
    }

    @Test
    fun `plain text parses to a single speech segment`() {
        val p = ScriptMarkup.parse("just some plain lines\nwith no markers at all")
        assertEquals(9, p.words.size)
        assertTrue(p.sectionStarts.isEmpty())
        assertTrue(p.pauses.isEmpty())
        assertEquals(1, p.segments.size)
    }
}
