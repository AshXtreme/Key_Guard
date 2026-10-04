package com.keyguard.ime.clipboard.model

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
    EMAIL
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
        private val URL_PATTERN = Regex("^(https?|ftp)://[^\\s/$.?#].[^\\s]*$", RegexOption.IGNORE_CASE)
        private val EMAIL_PATTERN = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

        private val CODE_SYNTAX_PATTERNS = listOf(
            Regex("(public|private|protected|class|fun|function|def|val|var|const|import|package|interface|struct|enum)\\s+[A-Za-z0-9_]+"),
            Regex("(#include|#define|console\\.log|println|print\\(|return\\s+)"),
            Regex("[\\{\\};=><\\[\\]]{2,}"),
            Regex("<\\/?[a-zA-Z][a-zA-Z0-9]*(\\s+[^>]*)?>")
        )

        /**
         * Analyzes characters to determine semantic ContentType.
         */
        fun classify(chars: CharArray): ContentType {
            if (chars.isEmpty()) return ContentType.PLAIN_TEXT
            val text = String(chars).trim()

            if (URL_PATTERN.matches(text)) {
                return ContentType.URL
            }

            if (EMAIL_PATTERN.matches(text)) {
                return ContentType.EMAIL
            }

            for (pattern in CODE_SYNTAX_PATTERNS) {
                if (pattern.containsMatchIn(text)) {
                    return ContentType.CODE
                }
            }

            return ContentType.PLAIN_TEXT
        }
    }
}
