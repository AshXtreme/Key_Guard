package com.keyguard.ime.security

/**
 * DlpViolationType
 *
 * Machine-readable categorization of sensitive data detected by the DLP scanner.
 */
enum class DlpViolationType {
    CREDIT_CARD,
    JWT_CREDENTIAL,
    BEARER_TOKEN,
    API_KEY,
    PRIVATE_KEY,
    GOVERNMENT_ID_SSN,
    GOVERNMENT_ID_AADHAAR,
    SENSITIVE_IDENTIFIER
}

/**
 * DlpResult
 *
 * Sealed result type representing the outcome of local deterministic DLP inspection.
 */
sealed class DlpResult {
    /**
     * Payload passed all checks without detecting sensitive credentials, cards, or IDs.
     */
    data object Clean : DlpResult()

    /**
     * Sensitive pattern detected; transmission MUST be blocked.
     *
     * @param reason Human-readable description of the violation for the UI alert.
     * @param violationType Machine-readable categorization of the finding.
     */
    data class Blocked(
        val reason: String,
        val violationType: DlpViolationType = DlpViolationType.SENSITIVE_IDENTIFIER
    ) : DlpResult()
}

/**
 * DlpSanitizer
 *
 * High-performance, deterministic Client-Side DLP inspector (PRD §5.4).
 * Enforces Zero Sensitive Leaks:
 *  1. Credit Cards: Regex match + mathematical Luhn checksum validation.
 *  2. Credentials: JWTs, Bearer tokens, API keys, and PEM blocks (BEGIN PRIVATE KEY).
 *  3. Identifiers: Social Security numbers and government IDs.
 *
 * All inspections execute 100% locally in-memory before any outbound network call.
 */
object DlpSanitizer {

    // -------------------------------------------------------------------------
    // Regex Patterns (Pre-compiled for sub-millisecond evaluation)
    // -------------------------------------------------------------------------

    // Cryptographic Keys / PEM blocks
    private val PEM_PRIVATE_KEY_REGEX = Regex(
        """-----BEGIN[ A-Z0-9_-]*PRIVATE KEY[ A-Z0-9_-]*-----""",
        RegexOption.IGNORE_CASE
    )
    private val PEM_CERT_REGEX = Regex(
        """-----BEGIN CERTIFICATE-----""",
        RegexOption.IGNORE_CASE
    )
    private val SSH_KEY_REGEX = Regex(
        """ssh-(?:rsa|ed25519|dss)\s+[A-Za-z0-9+/]{40,}"""
    )

    // JSON Web Tokens (JWT) - Header.Payload.Signature (RFC 7519)
    // Base64URL-encoded JSON headers start with 'eyJ'
    private val JWT_REGEX = Regex(
        """\beyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\b"""
    )

    // Bearer / Authorization tokens
    private val BEARER_TOKEN_REGEX = Regex(
        """(?i)\b(?:authorization:\s*)?bearer\s+[a-zA-Z0-9_\-\.=:]{20,}\b"""
    )

    // Specific & Generic Cloud API Keys
    private val GENERIC_API_KEY_REGEX = Regex(
        """(?i)\b(?:api[_-]?key|api[_-]?secret|access[_-]?token|auth[_-]?token|secret[_-]?key|client[_-]?secret)\s*[:=]\s*(?:['"][a-zA-Z0-9_\-]{16,}['"]|[a-zA-Z0-9_\-]{16,})"""
    )
    private val GITHUB_TOKEN_REGEX = Regex(
        """\b(?:ghp|gho|ghu|ghs|ghr)_[A-Za-z0-9]{36}\b|\bgithub_pat_[A-Za-z0-9_]{82}\b"""
    )
    private val AWS_ACCESS_KEY_REGEX = Regex(
        """\b(?:AKIA|ASIA)[0-9A-Z]{16}\b"""
    )
    private val SLACK_TOKEN_REGEX = Regex(
        """\bxox[baprs]-[0-9a-zA-Z]{10,48}\b"""
    )
    private val GOOGLE_API_KEY_REGEX = Regex(
        """\bAIza[0-9A-Za-z\-_]{30,45}\b"""
    )
    private val STRIPE_KEY_REGEX = Regex(
        """\b[sr]k_live_[0-9a-zA-Z]{24,}\b"""
    )
    private val OPENAI_KEY_REGEX = Regex(
        """\bsk-(?:proj-)?[a-zA-Z0-9_\-]{20,}\b"""
    )

    // Government IDs & SSNs
    // US SSN: 3 digits (not 000, 666, 900-999) - 2 digits (not 00) - 4 digits (not 0000)
    private val US_SSN_REGEX = Regex(
        """\b(?!000|666|9\d{2})\d{3}[- ](?!00)\d{2}[- ](?!0000)\d{4}\b"""
    )

    // Indian Aadhaar Number: Exactly 12 digits (grouped 4-4-4 or contiguous), first digit not 0 or 1.
    // Negative lookahead (?![ -]?\d) ensures 16-digit credit cards are not truncated into Aadhaar matches.
    private val AADHAAR_REGEX = Regex(
        """(?<!\d)[2-9]\d{3}[ -]\d{4}[ -]\d{4}(?![ -]?\d)|\b[2-9]\d{11}\b"""
    )

    // Candidate Credit Card pattern: 13 to 19 digits formatted as standard full groups or contiguous
    private val CREDIT_CARD_REGEX = Regex(
        """\b(?:\d{4}[ -]\d{4}[ -]\d{4}[ -]\d{1,7}|\d{4}[ -]\d{6}[ -]\d{4,5}|\d{3,4}[ -]\d{4,6}[ -]\d{4,5}|\d{13,19})\b"""
    )

    /**
     * Evaluates whether the provided text payload contains sensitive credentials or PII.
     *
     * @param text The raw text string to inspect.
     * @return [DlpResult.Clean] if safe to process, or [DlpResult.Blocked] with the failure reason.
     */
    fun scan(text: CharSequence?): DlpResult {
        if (text.isNullOrBlank()) {
            return DlpResult.Clean
        }

        val content = text.toString()

        // 1. High-priority Credentials: PEM Private Key Blocks & Certificates
        if (content.contains("PRIVATE KEY", ignoreCase = true) || content.contains("BEGIN CERTIFICATE", ignoreCase = true)) {
            if (PEM_PRIVATE_KEY_REGEX.containsMatchIn(content) || PEM_CERT_REGEX.containsMatchIn(content)) {
                return DlpResult.Blocked(
                    reason = "Cryptographic private key or certificate block detected",
                    violationType = DlpViolationType.PRIVATE_KEY
                )
            }
        }
        if (content.contains("ssh-", ignoreCase = true) && SSH_KEY_REGEX.containsMatchIn(content)) {
            return DlpResult.Blocked(
                reason = "SSH private key or credential block detected",
                violationType = DlpViolationType.PRIVATE_KEY
            )
        }

        // 2. High-priority Credentials: JSON Web Tokens (JWT)
        if (content.contains("eyJ") && JWT_REGEX.containsMatchIn(content)) {
            return DlpResult.Blocked(
                reason = "JSON Web Token (JWT) detected",
                violationType = DlpViolationType.JWT_CREDENTIAL
            )
        }

        // 3. Authorization Bearer Tokens
        if (BEARER_TOKEN_REGEX.containsMatchIn(content)) {
            return DlpResult.Blocked(
                reason = "Authorization Bearer token detected",
                violationType = DlpViolationType.BEARER_TOKEN
            )
        }

        // 4. API Keys & Secrets
        if (GITHUB_TOKEN_REGEX.containsMatchIn(content) ||
            AWS_ACCESS_KEY_REGEX.containsMatchIn(content) ||
            SLACK_TOKEN_REGEX.containsMatchIn(content) ||
            GOOGLE_API_KEY_REGEX.containsMatchIn(content) ||
            STRIPE_KEY_REGEX.containsMatchIn(content) ||
            OPENAI_KEY_REGEX.containsMatchIn(content) ||
            GENERIC_API_KEY_REGEX.containsMatchIn(content)
        ) {
            return DlpResult.Blocked(
                reason = "Sensitive API Key or Secret detected",
                violationType = DlpViolationType.API_KEY
            )
        }

        // 5. Government Identifiers: Social Security Number (SSN)
        if (US_SSN_REGEX.containsMatchIn(content)) {
            return DlpResult.Blocked(
                reason = "Social Security Number (SSN) detected",
                violationType = DlpViolationType.GOVERNMENT_ID_SSN
            )
        }

        // 6. Financial Data: Credit Card (Regex + Mathematical Luhn Checksum)
        // Evaluated before Aadhaar so full 13-19 digit cards take precedence over 12-digit patterns
        val ccMatches = CREDIT_CARD_REGEX.findAll(content)
        for (match in ccMatches) {
            val digits = match.value.filter { it.isDigit() }
            if (digits.length in 13..19 && isValidLuhn(digits)) {
                return DlpResult.Blocked(
                    reason = "Credit card number detected (Luhn verified)",
                    violationType = DlpViolationType.CREDIT_CARD
                )
            }
        }

        // 7. Government Identifiers: Aadhaar Number
        if (AADHAAR_REGEX.containsMatchIn(content)) {
            return DlpResult.Blocked(
                reason = "National Identity Number (Aadhaar) detected",
                violationType = DlpViolationType.GOVERNMENT_ID_AADHAAR
            )
        }

        return DlpResult.Clean
    }

    /**
     * Checks if the text is completely clean of any DLP violations.
     */
    fun isClean(text: CharSequence?): Boolean = scan(text) is DlpResult.Clean

    /**
     * Validates candidate credit card digits using Luhn (Mod 10) algorithm.
     *
     * @param digits String of 13 to 19 numeric digits.
     * @return true if digits satisfy the Luhn checksum formula.
     */
    fun isValidLuhn(digits: String): Boolean {
        if (digits.length !in 13..19 || !digits.all { it.isDigit() }) {
            return false
        }

        var sum = 0
        var alternate = false

        for (i in digits.length - 1 downTo 0) {
            var n = digits[i] - '0'
            if (alternate) {
                n *= 2
                if (n > 9) {
                    n -= 9
                }
            }
            sum += n
            alternate = !alternate
        }

        return (sum % 10 == 0)
    }
}
