package com.keyguard.ime.stt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * SherpaSpeechEngineTest
 *
 * Verifies voice-to-text pipeline logic:
 *  1. Hands-free voice command parsing (PRD §5.2).
 *  2. Robust handling of lexical phrases versus voice commands.
 */
class SherpaSpeechEngineTest {

    @Test
    fun testParseVoiceCommand_deleteLastWord() {
        assertEquals(VoiceCommand.DELETE_LAST_WORD, SherpaSpeechEngine.parseVoiceCommand("delete last word"))
        assertEquals(VoiceCommand.DELETE_LAST_WORD, SherpaSpeechEngine.parseVoiceCommand("Delete Last Word"))
        assertEquals(VoiceCommand.DELETE_LAST_WORD, SherpaSpeechEngine.parseVoiceCommand("please delete last word"))
        assertEquals(VoiceCommand.DELETE_LAST_WORD, SherpaSpeechEngine.parseVoiceCommand("backspace"))
    }

    @Test
    fun testParseVoiceCommand_newLine() {
        assertEquals(VoiceCommand.NEW_LINE, SherpaSpeechEngine.parseVoiceCommand("new line"))
        assertEquals(VoiceCommand.NEW_LINE, SherpaSpeechEngine.parseVoiceCommand("New Line"))
        assertEquals(VoiceCommand.NEW_LINE, SherpaSpeechEngine.parseVoiceCommand("enter"))
    }

    @Test
    fun testParseVoiceCommand_space() {
        assertEquals(VoiceCommand.SPACE, SherpaSpeechEngine.parseVoiceCommand("space"))
        assertEquals(VoiceCommand.SPACE, SherpaSpeechEngine.parseVoiceCommand("space bar"))
        assertEquals(VoiceCommand.SPACE, SherpaSpeechEngine.parseVoiceCommand("Space Bar"))
    }

    @Test
    fun testParseVoiceCommand_clearAll() {
        assertEquals(VoiceCommand.CLEAR_ALL, SherpaSpeechEngine.parseVoiceCommand("clear all"))
        assertEquals(VoiceCommand.CLEAR_ALL, SherpaSpeechEngine.parseVoiceCommand("clear text"))
        assertEquals(VoiceCommand.CLEAR_ALL, SherpaSpeechEngine.parseVoiceCommand("Clear All"))
    }

    @Test
    fun testParseVoiceCommand_normalSpeechReturnsNull() {
        assertNull(SherpaSpeechEngine.parseVoiceCommand("hello world"))
        assertNull(SherpaSpeechEngine.parseVoiceCommand("cybersecurity and privacy"))
        assertNull(SherpaSpeechEngine.parseVoiceCommand("air gapped keyboard"))
        assertNull(SherpaSpeechEngine.parseVoiceCommand(""))
    }
}
