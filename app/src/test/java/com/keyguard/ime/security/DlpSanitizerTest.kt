package com.keyguard.ime.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DlpSanitizerTest
 *
 * Comprehensive unit test suite verifying client-side Data Loss Prevention (PRD §5.4):
 *  1. Credit card numbers (Regex + Luhn checksum formula).
 *  2. Credentials (JWT, Bearer, API Keys, PEM Private Keys).
 *  3. Identifiers (US SSN, Aadhaar).
 *  4. False-positive resilience across benign conversational text.
 */
class DlpSanitizerTest {

    // =========================================================================
    // 1. Credit Cards & Luhn Mathematical Checksum
    // =========================================================================

    @Test
    fun testValidVisa_blocked() {
        // Standard Visa test cards
        val contiguous = "My payment card is 4111111111111111 for the order."
        val dashed = "Card: 4111-1111-1111-1111 expiration 12/28"
        val spaced = "Payment method: 4111 1111 1111 1111"

        val result1 = DlpSanitizer.scan(contiguous)
        val result2 = DlpSanitizer.scan(dashed)
        val result3 = DlpSanitizer.scan(spaced)

        assertTrue(result1 is DlpResult.Blocked)
        assertTrue(result2 is DlpResult.Blocked)
        assertTrue(result3 is DlpResult.Blocked)

        assertEquals(DlpViolationType.CREDIT_CARD, (result1 as DlpResult.Blocked).violationType)
        assertEquals("Credit card number detected (Luhn verified)", result1.reason)
    }

    @Test
    fun testValidMastercard_blocked() {
        val mastercard = "Use Mastercard 5500 0000 0000 0004 please"
        val result = DlpSanitizer.scan(mastercard)

        assertTrue(result is DlpResult.Blocked)
        assertEquals(DlpViolationType.CREDIT_CARD, (result as DlpResult.Blocked).violationType)
    }

    @Test
    fun testValidAmex_blocked() {
        // 15-digit American Express test card (4-6-5 grouping)
        val amex = "Charge 3782 822463 10005 for ticket booking"
        val result = DlpSanitizer.scan(amex)

        assertTrue(result is DlpResult.Blocked)
        assertEquals(DlpViolationType.CREDIT_CARD, (result as DlpResult.Blocked).violationType)
    }

    @Test
    fun testInvalidLuhnCard_clean() {
        // Same prefix/length as Visa, but checksum fails mod 10
        val invalidCard = "Invalid reference number: 4111 1111 1111 1112"
        val result = DlpSanitizer.scan(invalidCard)

        assertTrue("Failing Luhn checksum should not be flagged as a valid credit card", result is DlpResult.Clean)
    }

    @Test
    fun testLuhnAlgorithmDirectly() {
        assertTrue(DlpSanitizer.isValidLuhn("4111111111111111"))
        assertTrue(DlpSanitizer.isValidLuhn("5500000000000004"))
        assertTrue(DlpSanitizer.isValidLuhn("378282246310005"))

        assertFalse(DlpSanitizer.isValidLuhn("4111111111111112"))
        assertFalse(DlpSanitizer.isValidLuhn("1234567890123"))
        assertFalse(DlpSanitizer.isValidLuhn("12345")) // Too short
    }

    // =========================================================================
    // 2. Credentials (JWT, Bearer, API Keys, PEM Blocks)
    // =========================================================================

    @Test
    fun testJwtToken_blocked() {
        val rfcJwt = "Here is the token: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
                "eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ." +
                "SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"

        val result = DlpSanitizer.scan(rfcJwt)
        assertTrue(result is DlpResult.Blocked)
        assertEquals(DlpViolationType.JWT_CREDENTIAL, (result as DlpResult.Blocked).violationType)
    }

    @Test
    fun testBearerToken_blocked() {
        val authHeader = "Authorization: Bearer 99a8b7c6d5e4f3a2b1c0998877665544"
        val result = DlpSanitizer.scan(authHeader)

        assertTrue(result is DlpResult.Blocked)
        assertEquals(DlpViolationType.BEARER_TOKEN, (result as DlpResult.Blocked).violationType)
    }

    @Test
    fun testCloudApiKeys_blocked() {
        // Construct synthetic tokens using runtime concatenation to avoid static secret scanner false positives
        val githubToken = "Push to repo using " + "ghp_" + "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val awsKey = "Credentials AWS " + "AKIA" + "IOSFODNN7EXAMPLE for deployment"
        val googleKey = "Maps key: " + "AIza" + "SyD-123456789012345678901234567890"
        val stripeKey = "Dummy " + "sk_" + "live_" + "51ABCDEF012345678901234567890"
        val genericKey = "api_" + "key: \"super_secret_production_key_123456\""

        assertTrue(DlpSanitizer.scan(githubToken) is DlpResult.Blocked)
        assertTrue(DlpSanitizer.scan(awsKey) is DlpResult.Blocked)
        assertTrue(DlpSanitizer.scan(googleKey) is DlpResult.Blocked)
        assertTrue(DlpSanitizer.scan(stripeKey) is DlpResult.Blocked)
        assertTrue(DlpSanitizer.scan(genericKey) is DlpResult.Blocked)
    }

    @Test
    fun testPemPrivateKeyAndCert_blocked() {
        val rsaKey = "-----BEGIN RSA PRIVATE KEY-----\nMIIEowIBAAKCAQEA0m...\n-----END RSA PRIVATE KEY-----"
        val ecKey = "-----BEGIN EC PRIVATE KEY-----\nMHcCAQEEI...\n-----END EC PRIVATE KEY-----"
        val cert = "-----BEGIN CERTIFICATE-----\nMIIDXTCCAkWgAwIBAgIJAK...\n-----END CERTIFICATE-----"
        val sshKey = "ssh-rsa AAAA1234567890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890 user@host"

        assertTrue(DlpSanitizer.scan(rsaKey) is DlpResult.Blocked)
        assertTrue(DlpSanitizer.scan(ecKey) is DlpResult.Blocked)
        assertTrue(DlpSanitizer.scan(cert) is DlpResult.Blocked)
        assertTrue(DlpSanitizer.scan(sshKey) is DlpResult.Blocked)

        assertEquals(DlpViolationType.PRIVATE_KEY, (DlpSanitizer.scan(rsaKey) as DlpResult.Blocked).violationType)
    }

    // =========================================================================
    // 3. Identifiers (US SSN & Aadhaar)
    // =========================================================================

    @Test
    fun testUsSsn_blocked() {
        val ssnDashed = "Tax filer SSN is 123-45-6789"
        val ssnSpaced = "Employee SSN 123 45 6789 for onboarding"

        val result1 = DlpSanitizer.scan(ssnDashed)
        val result2 = DlpSanitizer.scan(ssnSpaced)

        assertTrue(result1 is DlpResult.Blocked)
        assertTrue(result2 is DlpResult.Blocked)
        assertEquals(DlpViolationType.GOVERNMENT_ID_SSN, (result1 as DlpResult.Blocked).violationType)
    }

    @Test
    fun testInvalidSsnPrefix_clean() {
        // Area 000 or 666 or 900+ are not valid US SSNs
        val invalidArea000 = "Reference 000-45-6789"
        val invalidArea666 = "Item 666-45-6789"
        val invalidArea900 = "Code 900-45-6789"

        assertTrue(DlpSanitizer.scan(invalidArea000) is DlpResult.Clean)
        assertTrue(DlpSanitizer.scan(invalidArea666) is DlpResult.Clean)
        assertTrue(DlpSanitizer.scan(invalidArea900) is DlpResult.Clean)
    }

    @Test
    fun testAadhaarNumber_blocked() {
        val aadhaar = "Aadhaar number: 2345 6789 0123"
        val result = DlpSanitizer.scan(aadhaar)

        assertTrue(result is DlpResult.Blocked)
        assertEquals(DlpViolationType.GOVERNMENT_ID_AADHAAR, (result as DlpResult.Blocked).violationType)
    }

    // =========================================================================
    // 4. Benign Conversational Sentences (Zero False Positives)
    // =========================================================================

    @Test
    fun testBenignPhrases_allClean() {
        val benignPhrases = listOf(
            "Hello, could you translate this paragraph into Spanish?",
            "The conference is scheduled for 10:30 AM on 2026-10-04.",
            "Please call our customer support at 555-123-4567.",
            "Our corporate headquarters is located at 1600 Amphitheatre Pkwy.",
            "We shipped package tracking number TRK-9876543210.",
            "Invoice #12345 has a subtotal of $150.75.",
            "KeyGuard uses C++ Oboe 1.9 for low-latency mechanical sound.",
            "Version 2.0.0 released with 60 FPS Compose ribbon.",
            "",
            "   "
        )

        for (phrase in benignPhrases) {
            val result = DlpSanitizer.scan(phrase)
            assertTrue("Expected clean for: '$phrase'", result is DlpResult.Clean)
            assertTrue("isClean should return true for: '$phrase'", DlpSanitizer.isClean(phrase))
        }
    }
}
