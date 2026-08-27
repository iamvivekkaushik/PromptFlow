package com.vivekkaushik.promptflow

import com.vivekkaushik.promptflow.core.prompter.PrompterEngine
import com.vivekkaushik.promptflow.core.speech.SpeechSync
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrompterLogicTest {

    @Test
    fun tokenize_splitsOnWhitespaceAndDropsBlanks() {
        val tokens = PrompterEngine.tokenize("Hey  everyone —\n welcome back ")
        assertEquals(listOf("Hey", "everyone", "—", "welcome", "back"), tokens)
    }

    @Test
    fun levenshtein_basicDistances() {
        assertEquals(0, SpeechSync.levenshtein("camera", "camera"))
        assertEquals(1, SpeechSync.levenshtein("camera", "cameras"))
        assertEquals(2, SpeechSync.levenshtein("kitten", "sittin"))
        assertEquals(3, SpeechSync.levenshtein("", "abc"))
    }

    @Test
    fun normalize_stripsPunctuationAndLowercases() {
        assertEquals("dont", SpeechSync.normalize("Don't"))
        assertEquals("4k", SpeechSync.normalize("4K,"))
    }

    @Test
    fun demoScript_hasWords() {
        assertTrue(PrompterEngine.tokenize(PrompterEngine.DEMO_SCRIPT).size > 50)
    }
}
