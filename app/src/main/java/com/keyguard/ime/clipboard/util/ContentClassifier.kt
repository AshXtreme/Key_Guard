package com.keyguard.ime.clipboard.util

/**
 * ClassifiedContentType
 *
 * Semantic categories for clipboard entries derived entirely on-device
 * using deterministic regex matching.
 */
enum class ClassifiedContentType(
    val badgeLabel: String,
    val description: String
) {
    CODE_SNIPPET(badgeLabel = "CODE", description = "Code snippet"),
    EMAIL(badgeLabel = "EMAIL", description = "Email address"),
    URL(badgeLabel = "URL", description = "Web address"),
    PHONE_NUMBER(badgeLabel = "TEL", description = "Phone number"),
    PLAIN_TEXT(badgeLabel = "TEXT", description = "Plain text")
}

/**
 * ContentClassifier
 *
 * Zero-cloud, local-only NLP entity classifier.
 *
 * Invariant 1: Processing executes solely on-device using pre-compiled regexes.
 * Network calls for entity extraction or NLP are strictly prohibited.
 */
object ContentClassifier {

    // RFC 5322 simplified standard match
    private val EMAIL_REGEX = Regex(
        "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$"
    )

    // Strict URL (http/https/ftp) and domain (www) patterns
    private val URL_PROTOCOL_REGEX = Regex(
        "^(https?|ftp)://[a-zA-Z0-9+&@#/%?=~_|!:,.;]*[a-zA-Z0-9+&@#/%=~_|]$",
        RegexOption.IGNORE_CASE
    )
    private val URL_DOMAIN_REGEX = Regex(
        "^www\\.[a-zA-Z0-9-]+\\.[a-zA-Z]{2,}(/[a-zA-Z0-9+&@#/%?=~_|!:,.;]*)?$",
        RegexOption.IGNORE_CASE
    )

    // E.164 and international/national phone number formatting
    private val PHONE_NUMBER_REGEX = Regex(
        "^\\+?(?:[0-9]{1,4}[-.\\s]?)?(?:\\([0-9]{1,5}\\)[-.\\s]?|[0-9]{1,5}[-.\\s]?)[0-9]{2,5}[-.\\s]?[0-9]{2,5}(?:[-.\\s]?[0-9]{1,5})?$"
    )

    // Developer / CLI / Scripting syntax markers
    private val GIT_COMMAND_REGEX = Regex(
        "^git\\s+(commit|checkout|push|pull|branch|status|clone|rebase|merge|diff|log|add|stash|remote|init|fetch|reset|switch|restore)\\b",
        RegexOption.IGNORE_CASE
    )
    private val CODE_KEYWORDS_REGEX = Regex(
        "\\b(function|class|import|package|interface|struct|enum|const|val|var|def|return|async|await|public|private|protected|typedef|extern|impl|fn|export|fun)\\s+[A-Za-z0-9_]+"
    )
    private val CODE_SYNTAX_MARKERS = Regex(
        "(#include|#define|console\\.log|println|print\\(|std::|sizeof\\(|void\\s+[a-zA-Z0-9_]+|===|!==|=>|->|::)"
    )
    private val CODE_BLOCK_REGEX = Regex(
        "\\{[\\s\\S]*\\}|<\\/?[a-zA-Z][a-zA-Z0-9]*(\\s+[^>]*)?>"
    )

    /**
     * Classifies a character sequence into a [ClassifiedContentType].
     */
    fun classify(text: CharSequence?): ClassifiedContentType {
        if (text.isNullOrBlank()) return ClassifiedContentType.PLAIN_TEXT
        val trimmed = text.trim()

        // 1. Strict Web URLs
        if (URL_PROTOCOL_REGEX.matches(trimmed) || URL_DOMAIN_REGEX.matches(trimmed)) {
            return ClassifiedContentType.URL
        }

        // 2. Strict RFC 5322 Emails
        if (EMAIL_REGEX.matches(trimmed)) {
            return ClassifiedContentType.EMAIL
        }

        // 3. Code Snippets & Shell Commands
        if (isCodeSnippet(trimmed)) {
            return ClassifiedContentType.CODE_SNIPPET
        }

        // 4. Phone Numbers (Ensure minimum 7 digits and maximum 15 digits)
        if (PHONE_NUMBER_REGEX.matches(trimmed)) {
            val digitCount = trimmed.count { it.isDigit() }
            if (digitCount in 7..15) {
                return ClassifiedContentType.PHONE_NUMBER
            }
        }

        // 5. Default Fallback
        return ClassifiedContentType.PLAIN_TEXT
    }

    /**
     * Helper for character array buffers.
     */
    fun classify(chars: CharArray?): ClassifiedContentType {
        if (chars == null || chars.isEmpty()) return ClassifiedContentType.PLAIN_TEXT
        return classify(String(chars))
    }

    private fun isCodeSnippet(text: CharSequence): Boolean {
        if (GIT_COMMAND_REGEX.containsMatchIn(text)) return true
        if (CODE_KEYWORDS_REGEX.containsMatchIn(text)) return true
        if (CODE_SYNTAX_MARKERS.containsMatchIn(text)) return true
        if (CODE_BLOCK_REGEX.containsMatchIn(text)) return true
        return false
    }
}
