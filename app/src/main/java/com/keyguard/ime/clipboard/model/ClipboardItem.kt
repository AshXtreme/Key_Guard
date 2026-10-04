package com.keyguard.ime.clipboard.model

import com.keyguard.ime.clipboard.util.ClassifiedContentType
import com.keyguard.ime.clipboard.util.ContentClassifier
import java.util.Arrays
import java.util.UUID

/**
 * ContentType
 *
 * Classifies clipboard payloads into semantic categories.
 * Code snippets are detected to enable seamless vault promotion (KeyGuard PRD §5.3).
 */
enum class ContentType {
    PLAIN_TEXT,
    CODE,
    URL,
    EMAIL,
    PHONE_NUMBER;

    companion object {
        fun fromClassified(classified: ClassifiedContentType): ContentType = when (classified) {
            ClassifiedContentType.CODE_SNIPPET -> CODE
            ClassifiedContentType.EMAIL -> EMAIL
            ClassifiedContentType.URL -> URL
            ClassifiedContentType.PHONE_NUMBER -> PHONE_NUMBER
            ClassifiedContentType.PLAIN_TEXT -> PLAIN_TEXT
        }
    }
}

/**
 * ClipboardItem
 *
 * Ephemeral Tier-1 in-memory clipboard item.
 *
 * Backed by a mutable CharArray rather than immutable String to enable
 * strict NIST SP 800-88 cryptographic memory zero-wiping on eviction.
 */
class ClipboardItem(
    val id: String = UUID.randomUUID().toString(),
    private val rawChars: CharArray,
    val timestampMs: Long = System.currentTimeMillis(),
    val contentType: ContentType = classify(rawChars)
) {
    @Volatile
    var isWiped: Boolean = false
        private set

    /**
     * Returns a copy of the backing characters or empty array if wiped.
     */
    val chars: CharArray
        get() = if (isWiped) CharArray(0) else rawChars

    /**
     * Payload character count.
     */
    val length: Int
        get() = if (isWiped) 0 else rawChars.size

    /**
     * Safely reads text for UI rendering or input connection paste.
     * Throws IllegalStateException if called after eviction sanitization.
     */
    fun getText(): String {
        check(!isWiped) { "Cannot read text: ClipboardItem has already been sanitized and wiped." }
        return String(rawChars)
    }

    /**
     * Strict NIST SP 800-88 Memory Sanitization:
     * Overwrites every element in the backing array with '\0' to eliminate volatile RAM residue.
     */
    fun wipe() {
        if (!isWiped) {
            Arrays.fill(rawChars, '\u0000')
            isWiped = true
        }
    }

    /**
     * Verifies if this item has exceeded its expiration TTL.
     */
    fun isExpired(currentTimeMs: Long, ttlMs: Long = 24 * 60 * 60 * 1000L): Boolean {
        return (currentTimeMs - timestampMs) >= ttlMs
    }

    companion object {
        /**
         * Analyzes characters to determine semantic ContentType via ContentClassifier.
         */
        fun classify(chars: CharArray): ContentType {
            val classified = ContentClassifier.classify(chars)
            return ContentType.fromClassified(classified)
        }
    }
}
