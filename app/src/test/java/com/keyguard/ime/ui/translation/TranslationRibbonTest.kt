package com.keyguard.ime.ui.translation

import com.keyguard.ime.security.DlpViolationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TranslationRibbonTest
 *
 * Unit tests validating state transitions, default languages, and security rules for TranslationRibbon.
 */
class TranslationRibbonTest {

    @Test
    fun `default target languages includes essential world languages`() {
        assertNotNull(DEFAULT_TARGET_LANGUAGES)
        assertTrue(DEFAULT_TARGET_LANGUAGES.size >= 8)

        val codes = DEFAULT_TARGET_LANGUAGES.map { it.code }
        assertTrue(codes.contains("es"))
        assertTrue(codes.contains("fr"))
        assertTrue(codes.contains("de"))
        assertTrue(codes.contains("hi"))
        assertTrue(codes.contains("ja"))
        assertTrue(codes.contains("zh"))
        assertTrue(codes.contains("ar"))
        assertTrue(codes.contains("en"))
    }

    @Test
    fun `TranslationRibbonState models full lifecycle`() {
        val idle: TranslationRibbonState = TranslationRibbonState.Idle
        val loading: TranslationRibbonState = TranslationRibbonState.Loading
        val success: TranslationRibbonState = TranslationRibbonState.Success(
            originalText = "Hello",
            translatedText = "Hola",
            targetLang = "es"
        )
        val dlpError: TranslationRibbonState = TranslationRibbonState.DlpError(
            reason = "Credit card number detected (Luhn verified)",
            violationType = DlpViolationType.CREDIT_CARD
        )
        val networkError: TranslationRibbonState = TranslationRibbonState.NetworkError(
            message = "Connection timed out",
            isTimeout = true
        )

        assertEquals(TranslationRibbonState.Idle, idle)
        assertEquals(TranslationRibbonState.Loading, loading)

        assertTrue(success is TranslationRibbonState.Success)
        val s = success as TranslationRibbonState.Success
        assertEquals("Hola", s.translatedText)
        assertEquals("es", s.targetLang)

        assertTrue(dlpError is TranslationRibbonState.DlpError)
        val d = dlpError as TranslationRibbonState.DlpError
        assertEquals(DlpViolationType.CREDIT_CARD, d.violationType)

        assertTrue(networkError is TranslationRibbonState.NetworkError)
        val n = networkError as TranslationRibbonState.NetworkError
        assertTrue(n.isTimeout)
    }

    @Test
    fun `TargetLanguage properties are correctly exposed`() {
        val spanish = TargetLanguage("es", "Spanish", "🇪🇸")
        assertEquals("es", spanish.code)
        assertEquals("Spanish", spanish.displayName)
        assertEquals("🇪🇸", spanish.flagEmoji)
    }
}

