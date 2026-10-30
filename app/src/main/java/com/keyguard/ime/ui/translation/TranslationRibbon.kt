package com.keyguard.ime.ui.translation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.keyguard.ime.security.DlpViolationType

/**
 * TargetLanguage
 *
 * Supported high-frequency target languages for isolated neural translation.
 */
data class TargetLanguage(
    val code: String,
    val displayName: String,
    val flagEmoji: String
)

val DEFAULT_TARGET_LANGUAGES = listOf(
    TargetLanguage("es", "Spanish", "🇪🇸"),
    TargetLanguage("fr", "French", "🇫🇷"),
    TargetLanguage("de", "German", "🇩🇪"),
    TargetLanguage("hi", "Hindi", "🇮🇳"),
    TargetLanguage("ja", "Japanese", "🇯🇵"),
    TargetLanguage("zh", "Chinese", "🇨🇳"),
    TargetLanguage("ar", "Arabic", "🇸🇦"),
    TargetLanguage("ru", "Russian", "🇷🇺"),
    TargetLanguage("pt", "Portuguese", "🇵🇹"),
    TargetLanguage("en", "English", "🇺🇸")
)

/**
 * TranslationRibbonState
 *
 * Visual state machine for the isolated translation context ribbon.
 */
sealed class TranslationRibbonState {
    data object Idle : TranslationRibbonState()
    data object Loading : TranslationRibbonState()
    data class Success(
        val originalText: String,
        val translatedText: String,
        val targetLang: String
    ) : TranslationRibbonState()
    data class DlpError(
        val reason: String,
        val violationType: DlpViolationType
    ) : TranslationRibbonState()
    data class NetworkError(
        val message: String,
        val isTimeout: Boolean
    ) : TranslationRibbonState()
}

/**
 * TranslationRibbon
 *
 * Inline Compose ribbon providing explicit, user-triggered translation (PRD §5.4).
 *
 * Double-Verification Invariants:
 *  1. Explicit User Intent Only: Network calls never trigger during typing.
 *     Outbound socket dispatch occurs ONLY when user explicitly taps the Translate button.
 *  2. DLP Error Warnings: If client-side DLP blocks sensitive data (credit cards, JWTs, keys),
 *     a prominent alert banner (#7F1D1D) is displayed and transmission is halted locally.
 *  3. WCAG 2.1 AA Compliant: High contrast, TalkBack semantics, accessible touch targets.
 *  4. Secure Target Lockdown: If [isSecureTarget] is true, translation is hard-disabled.
 */
@Composable
fun TranslationRibbon(
    isSecureTarget: Boolean,
    ribbonState: TranslationRibbonState,
    selectedTargetLanguage: TargetLanguage,
    onLanguageSelected: (TargetLanguage) -> Unit,
    onTranslateTrigger: () -> Unit,
    onApplyTranslation: (String) -> Unit,
    onDismissError: () -> Unit,
    onCloseRibbon: () -> Unit,
    modifier: Modifier = Modifier,
    languages: List<TargetLanguage> = DEFAULT_TARGET_LANGUAGES
) {
    var showLanguagePicker by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF0B0F19))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // =========================================================================
        // PRIMARY CONTROL ROW: Language Selector, Explicit Translate Button, Close
        // =========================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Target Language Selector Chip
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF1E293B))
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(4.dp))
                        .clickable(
                            enabled = !isSecureTarget && ribbonState !is TranslationRibbonState.Loading,
                            onClick = { showLanguagePicker = !showLanguagePicker }
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .semantics {
                            role = Role.Button
                            contentDescription = "Target language: ${selectedTargetLanguage.displayName}. Tap to change."
                        }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = selectedTargetLanguage.flagEmoji,
                            fontSize = 12.sp
                        )
                        Text(
                            text = selectedTargetLanguage.code.uppercase(),
                            color = Color(0xFF38BDF8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = if (showLanguagePicker) "▲" else "▼",
                            color = Color(0xFF94A3B8),
                            fontSize = 8.sp
                        )
                    }
                }

                // Status Badge: TLS 1.3 / Air-Gap
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0x2610B981))
                        .border(1.dp, Color(0x4D10B981), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "TLS 1.3 • PINNED",
                        color = Color(0xFF34D399),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Right: Explicit Translate Trigger & Close Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isSecureTarget) {
                    // Password field lockout badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF7F1D1D))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "🔒 DISABLED IN SECURE FIELD",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    // EXPLICIT INTENT ONLY: User must tap to initiate outbound network request
                    val isTranslating = ribbonState is TranslationRibbonState.Loading
                    val buttonBg = if (isTranslating) Color(0xFF1E3A8A) else Color(0xFF2563EB)

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(buttonBg)
                            .clickable(
                                enabled = !isTranslating,
                                onClick = onTranslateTrigger
                            )
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                            .semantics {
                                role = Role.Button
                                contentDescription = "Translate highlighted text into ${selectedTargetLanguage.displayName}. Explicit network call."
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (isTranslating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(10.dp),
                                    color = Color.White,
                                    strokeWidth = 1.5.dp
                                )
                                Text(
                                    text = "TRANSLATING...",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            } else {
                                Text(text = "🌐", fontSize = 10.sp)
                                Text(
                                    text = "TRANSLATE",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                // Close Ribbon "✕"
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E293B))
                        .clickable(onClick = onCloseRibbon)
                        .semantics {
                            role = Role.Button
                            contentDescription = "Close translation ribbon"
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✕",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // =========================================================================
        // EXPANDABLE LANGUAGE PICKER ROW
        // =========================================================================
        AnimatedVisibility(
            visible = showLanguagePicker && !isSecureTarget,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                languages.forEach { lang ->
                    val isSelected = lang.code == selectedTargetLanguage.code
                    val chipBg = if (isSelected) Color(0xFF0284C7) else Color(0xFF1E293B)
                    val textColor = if (isSelected) Color.White else Color(0xFFCBD5E1)

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(chipBg)
                            .clickable {
                                onLanguageSelected(lang)
                                showLanguagePicker = false
                            }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                            .semantics {
                                role = Role.Button
                                contentDescription = "Select ${lang.displayName}"
                            }
                    ) {
                        Text(
                            text = "${lang.flagEmoji} ${lang.code.uppercase()}",
                            color = textColor,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // =========================================================================
        // INLINE PREVIEW ROW: Displays Translated Result with Paste Action
        // =========================================================================
        AnimatedVisibility(
            visible = ribbonState is TranslationRibbonState.Success,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            if (ribbonState is TranslationRibbonState.Success) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF064E3B)) // Deep Emerald 900
                        .border(1.dp, Color(0xFF059669), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "✓ [${ribbonState.targetLang.uppercase()}]",
                            color = Color(0xFF34D399),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = ribbonState.translatedText,
                            color = Color(0xFFF0FDF4),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // "Insert" Action Chip
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFF10B981))
                            .clickable { onApplyTranslation(ribbonState.translatedText) }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .semantics {
                                role = Role.Button
                                contentDescription = "Insert translated text: ${ribbonState.translatedText}"
                            }
                    ) {
                        Text(
                            text = "INSERT ↵",
                            color = Color.Black,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // =========================================================================
        // DLP ERROR WARNING BANNER: Burgundy Alert for Blocked Sensitive Data
        // =========================================================================
        AnimatedVisibility(
            visible = ribbonState is TranslationRibbonState.DlpError,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            if (ribbonState is TranslationRibbonState.DlpError) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF7F1D1D)) // Burgundy #7F1D1D invariant
                        .border(1.dp, Color(0xFFDC2626), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(text = "⚠️", fontSize = 11.sp)
                        Text(
                            text = "DLP BLOCKED: ${ribbonState.reason}",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Acknowledge / Dismiss Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFF991B1B))
                            .clickable(onClick = onDismissError)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .semantics {
                                role = Role.Button
                                contentDescription = "Dismiss DLP warning"
                            }
                    ) {
                        Text(
                            text = "DISMISS",
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // =========================================================================
        // NETWORK / TIMEOUT ERROR BANNER
        // =========================================================================
        AnimatedVisibility(
            visible = ribbonState is TranslationRibbonState.NetworkError,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            if (ribbonState is TranslationRibbonState.NetworkError) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF78350F)) // Amber 900
                        .border(1.dp, Color(0xFFD97706), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (ribbonState.isTimeout) "⏱️" else "⚡",
                            fontSize = 11.sp
                        )
                        Text(
                            text = ribbonState.message,
                            color = Color(0xFFFEF3C7),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFF92400E))
                            .clickable(onClick = onDismissError)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .semantics {
                                role = Role.Button
                                contentDescription = "Dismiss network error"
                            }
                    ) {
                        Text(
                            text = "DISMISS",
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

// =============================================================================
// COMPOSE PREVIEWS
// =============================================================================

@Preview(name = "Translation Ribbon - Idle", showBackground = true)
@Composable
private fun TranslationRibbonIdlePreview() {
    TranslationRibbon(
        isSecureTarget = false,
        ribbonState = TranslationRibbonState.Idle,
        selectedTargetLanguage = DEFAULT_TARGET_LANGUAGES[0],
        onLanguageSelected = {},
        onTranslateTrigger = {},
        onApplyTranslation = {},
        onDismissError = {},
        onCloseRibbon = {}
    )
}

@Preview(name = "Translation Ribbon - Success Preview", showBackground = true)
@Composable
private fun TranslationRibbonSuccessPreview() {
    TranslationRibbon(
        isSecureTarget = false,
        ribbonState = TranslationRibbonState.Success(
            originalText = "Hello security engineer",
            translatedText = "Hola ingeniero de seguridad",
            targetLang = "es"
        ),
        selectedTargetLanguage = DEFAULT_TARGET_LANGUAGES[0],
        onLanguageSelected = {},
        onTranslateTrigger = {},
        onApplyTranslation = {},
        onDismissError = {},
        onCloseRibbon = {}
    )
}

@Preview(name = "Translation Ribbon - DLP Alert Warning", showBackground = true)
@Composable
private fun TranslationRibbonDlpErrorPreview() {
    TranslationRibbon(
        isSecureTarget = false,
        ribbonState = TranslationRibbonState.DlpError(
            reason = "Credit card number detected (Luhn verified)",
            violationType = DlpViolationType.CREDIT_CARD
        ),
        selectedTargetLanguage = DEFAULT_TARGET_LANGUAGES[0],
        onLanguageSelected = {},
        onTranslateTrigger = {},
        onApplyTranslation = {},
        onDismissError = {},
        onCloseRibbon = {}
    )
}

