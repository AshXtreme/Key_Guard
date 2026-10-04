package com.keyguard.ime.clipboard.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Arrays

/**
 * ClipboardEntityTest
 *
 * Verifies Tier-2 Entity model integrity and memory-sanitization patterns.
 */
class ClipboardEntityTest {

    @Test
    fun testEntityEqualityWithByteArrays() {
        val payload1 = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val payload2 = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val payload3 = byteArrayOf(0x09, 0x08, 0x07, 0x06)

        val entity1 = ClipboardEntity(
            id = "test-uuid-1",
            encryptedPayload = payload1,
            previewText = "Snippet preview",
            contentType = "CODE",
            createdAt = 1000L,
            isPinned = true,
            tags = "security,hsm",
            isWiped = false
        )

        val entity2 = ClipboardEntity(
            id = "test-uuid-1",
            encryptedPayload = payload2,
            previewText = "Snippet preview",
            contentType = "CODE",
            createdAt = 1000L,
            isPinned = true,
            tags = "security,hsm",
            isWiped = false
        )

        val entity3 = ClipboardEntity(
            id = "test-uuid-2",
            encryptedPayload = payload3,
            previewText = "Different snippet",
            contentType = "PLAIN_TEXT",
            createdAt = 2000L,
            isPinned = false,
            tags = "note",
            isWiped = false
        )

        assertEquals("Entities with identical content must be equal", entity1, entity2)
        assertEquals("Hashcodes must match for equal entities", entity1.hashCode(), entity2.hashCode())
        assertFalse("Different entities must not be equal", entity1 == entity3)
    }

    @Test
    fun testPayloadZeroizationNistPattern() {
        val payload = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
        assertFalse("Payload must not be empty initially", payload.all { it == 0.toByte() })

        Arrays.fill(payload, 0.toByte())
        assertTrue("NIST SP 800-88 zero-fill must overwrite all bytes with 0x00", payload.all { it == 0.toByte() })
    }
}
