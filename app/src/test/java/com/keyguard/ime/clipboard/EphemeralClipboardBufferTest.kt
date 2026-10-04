package com.keyguard.ime.clipboard

import com.keyguard.ime.clipboard.model.ClipboardItem
import com.keyguard.ime.clipboard.model.ContentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * EphemeralClipboardBufferTest
 *
 * Verifies Invariants for Phase 2 — Step 2.1:
 *  1. Zero Disk Residue / In-Memory Ring Buffer.
 *  2. NIST SP 800-88 Memory Sanitization (Array zeroization on eviction).
 *  3. Password / Secure Field Hard-Exclusion.
 *  4. Content Type Classification.
 */
class EphemeralClipboardBufferTest {

    @Test
    fun testArrayZeroizationOnCapacityEviction() {
        val buffer = EphemeralClipboardBuffer(maxCapacity = 50)

        // Track reference to the first item's backing CharArray
        val firstPayload = "SensitiveData-Index-00".toCharArray()
        buffer.add(firstPayload, isSecureTarget = false)

        // Fill remaining 49 slots (total 50)
        for (i in 1 until 50) {
            val payload = "Payload-Index-$i".toCharArray()
            buffer.add(payload, isSecureTarget = false)
        }

        assertEquals("Buffer should be at max capacity", 50, buffer.size)

        // Verify first payload is still intact before eviction
        assertFalse("First payload should not be wiped yet", firstPayload.all { it == '\u0000' })

        // Add 51st item to trigger ring buffer eviction of the 1st item
        val triggeringPayload = "TriggeringItem-51".toCharArray()
        buffer.add(triggeringPayload, isSecureTarget = false)

        assertEquals("Buffer must remain capped at 50", 50, buffer.size)

        // NIST SP 800-88 Invariant Verification:
        // Backing CharArray of the evicted item must be zero-wiped
        val isZeroized = firstPayload.all { it == '\u0000' }
        assertTrue("Evicted item backing CharArray must be completely overwritten with '\\0'", isZeroized)
        
        buffer.close()
    }

    @Test
    fun testArrayZeroizationOnTtlExpiration() {
        val buffer = EphemeralClipboardBuffer(maxCapacity = 50)
        val testPayload = "ExpiringDataPayload".toCharArray()
        val baseTimeMs = 1_000_000_000L

        buffer.add(testPayload, isSecureTarget = false, timestampMs = baseTimeMs)
        assertEquals(1, buffer.size)

        // 1. Advance time by 23 hours (should NOT expire)
        val withinTtlMs = baseTimeMs + (23 * 60 * 60 * 1000L)
        val purgedEarly = buffer.purgeExpired(withinTtlMs)
        assertEquals("Items under 24 hours must not be purged", 0, purgedEarly)
        assertFalse("Payload must remain intact before 24h", testPayload.all { it == '\u0000' })

        // 2. Advance time past 24 hours (24 hours + 10 seconds)
        val pastTtlMs = baseTimeMs + (24 * 60 * 60 * 1000L) + 10_000L
        val purgedAfterTtl = buffer.purgeExpired(pastTtlMs)

        assertEquals("Expired item must be purged", 1, purgedAfterTtl)
        assertEquals("Buffer should now be empty", 0, buffer.size)

        // NIST SP 800-88 Invariant Verification on TTL Purge
        val isZeroized = testPayload.all { it == '\u0000' }
        assertTrue("Expired item backing CharArray must be zeroized on TTL purge", isZeroized)

        buffer.close()
    }

    @Test
    fun testPasswordExclusionAndParameterSanitization() {
        val buffer = EphemeralClipboardBuffer()
        val passwordPayload = "SuperSecretPassword123!".toCharArray()

        // Attempting to add with isSecureTarget == true
        val accepted = buffer.add(passwordPayload, isSecureTarget = true)

        assertFalse("Clipboard must refuse to record inputs when isSecureTarget is true", accepted)
        assertEquals("Buffer must remain empty", 0, buffer.size)

        // Verification: The incoming parameter array must also be zero-wiped
        val isZeroized = passwordPayload.all { it == '\u0000' }
        assertTrue("Incoming password CharArray must be zero-wiped to prevent memory leaking", isZeroized)

        buffer.close()
    }

    @Test
    fun testContentTypeClassification() {
        // Plain text
        val plain = ClipboardItem.classify("Just a regular message".toCharArray())
        assertEquals(ContentType.PLAIN_TEXT, plain)

        // URL
        val url = ClipboardItem.classify("https://keyguard.internal/vault/keys".toCharArray())
        assertEquals(ContentType.URL, url)

        // Email
        val email = ClipboardItem.classify("security-team@keyguard.org".toCharArray())
        assertEquals(ContentType.EMAIL, email)

        // Code snippets
        val codeKotlin = ClipboardItem.classify("fun sanitizeBuffers() { Arrays.fill(chars, '\\0') }".toCharArray())
        assertEquals(ContentType.CODE, codeKotlin)

        val codeHtml = ClipboardItem.classify("<div class=\"keyguard-input\"><span /></div>".toCharArray())
        assertEquals(ContentType.CODE, codeHtml)

        val codeCpp = ClipboardItem.classify("#include <oboe/Oboe.h>\nauto stream = builder.openStream();".toCharArray())
        assertEquals(ContentType.CODE, codeCpp)
    }

    @Test
    fun testClearAllZeroizesAllEntries() {
        val buffer = EphemeralClipboardBuffer()
        val payload1 = "TokenAlpha123".toCharArray()
        val payload2 = "TokenBeta456".toCharArray()

        buffer.add(payload1, isSecureTarget = false)
        buffer.add(payload2, isSecureTarget = false)
        assertEquals(2, buffer.size)

        buffer.clearAll()

        assertEquals("Buffer should be empty after clearAll", 0, buffer.size)
        assertTrue("payload1 must be zeroized", payload1.all { it == '\u0000' })
        assertTrue("payload2 must be zeroized", payload2.all { it == '\u0000' })

        buffer.close()
    }
}
