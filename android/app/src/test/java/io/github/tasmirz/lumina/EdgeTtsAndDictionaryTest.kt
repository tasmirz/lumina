package io.github.tasmirz.lumina

import io.github.tasmirz.lumina.data.DictionaryService
import io.github.tasmirz.lumina.data.EdgeTtsService
import io.github.tasmirz.lumina.model.ReaderSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EdgeTtsAndDictionaryTest {

    @Test
    fun testEdgeTtsAvailableVoices() {
        val voices = EdgeTtsService.AVAILABLE_VOICES
        assertTrue(voices.isNotEmpty())
        assertTrue(voices.any { it.id == "en-US-JennyNeural" })
        assertTrue(voices.any { it.id == "en-US-GuyNeural" })
        assertTrue(voices.any { it.id == "en-US-AriaNeural" })
        assertTrue(voices.any { it.id == "en-GB-SoniaNeural" })
        assertTrue(voices.any { it.id == "en-US-ChristopherNeural" })
    }

    @Test
    fun testReaderSettingsTtsDefaults() {
        val settings = ReaderSettings()
        assertEquals("EDGE_NEURAL", settings.ttsEngine)
        assertEquals("en-US-JennyNeural", settings.ttsEdgeVoice)
        assertEquals(1.0f, settings.ttsSpeed, 0.01f)
        assertEquals(1.0f, settings.ttsPitch, 0.01f)
    }

    @Test
    fun testAutoScrollSpeedCycleLogic() {
        fun nextSpeed(current: Float): Float = when {
            current < 0.9f -> 1.0f
            current < 1.4f -> 1.5f
            current < 1.9f -> 2.0f
            else -> 0.5f
        }

        assertEquals(1.0f, nextSpeed(0.5f), 0.01f)
        assertEquals(1.5f, nextSpeed(1.0f), 0.01f)
        assertEquals(2.0f, nextSpeed(1.5f), 0.01f)
        assertEquals(0.5f, nextSpeed(2.0f), 0.01f)
    }

    @Test
    fun testDictionaryBlankPhraseLookup() = runBlocking {
        val def = DictionaryService.lookup("   ")
        assertNotNull(def)
        assertEquals("phrase", def.partOfSpeech)
    }

    @Test
    fun testDictionaryWordLookup() = runBlocking {
        val def = DictionaryService.lookup("reading")
        assertNotNull(def)
        assertTrue(def.word.isNotBlank())
        assertTrue(def.definition.isNotBlank())
    }

    @Test
    fun testAudioSessionInvalidation() {
        var currentSessionId = 1L
        var isTtsSpeaking = true
        var showTtsDock = true

        fun shouldProcessCallback(sessionId: Long): Boolean {
            return sessionId == currentSessionId && isTtsSpeaking && showTtsDock
        }

        // Active session callback is accepted
        assertTrue(shouldProcessCallback(1L))

        // When dialog/dock is removed, callbacks are rejected
        showTtsDock = false
        assertTrue(!shouldProcessCallback(1L))

        // When user stops or changes paragraph, session id increments
        showTtsDock = true
        currentSessionId = 2L
        assertTrue(!shouldProcessCallback(1L))
        assertTrue(shouldProcessCallback(2L))
    }
}
