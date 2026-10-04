package com.keyguard.ime.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.keyguard.ime.haptics.KeyCategory
import com.keyguard.ime.ui.components.KeyCap

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.keyguard.ime.ui.clipboard.ClipboardRibbon
import com.keyguard.ime.ui.clipboard.ClipboardRibbonItem
import com.keyguard.ime.ui.clipboard.RibbonTheme

/**
 * KeyboardScreen
 *
 * Primary responsive IME view adhering to WCAG 2.1 AA accessibility and zero-latency audio guidelines.
 * Architecture:
 *  [Zone 1]: Top Security & Utility Command Bar (Air-gap status, Switch profile, Dictation, Vault)
 *  [Zone 2]: Dynamic Context Ribbon (Smart Vault carousel or Security Lockout banner)
 *  [Zone 3]: Primary Key Matrix (QWERTY / Numeric / Symbols with WCAG 48dp touch targets)
 */
@Composable
fun KeyboardScreen(
    isSecureTarget: Boolean,
    onKeyDown: (KeyCategory) -> Unit,
    onKeyUp: (String, KeyCategory) -> Unit,
    modifier: Modifier = Modifier,
    clipboardItems: List<ClipboardRibbonItem> = emptyList(),
    ribbonTheme: RibbonTheme = RibbonTheme.INDUSTRIAL_MATTE,
    onClipboardPaste: (ClipboardRibbonItem) -> Unit = {},
    onClipboardPinToggle: (ClipboardRibbonItem) -> Unit = {},
    onClipboardDelete: (ClipboardRibbonItem) -> Unit = {},
    activeSwitchProfile: String = "Cherry MX Blue",
    onSwitchProfileClick: () -> Unit = {},
    onVaultClick: () -> Unit = {},
    onDictationClick: () -> Unit = {},
    isDictating: Boolean = false
) {
    var isShiftActive by remember { mutableStateOf(false) }
    var isSymbolsMode by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0F172A)) // Slate 900 high-contrast background
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // =========================================================================
        // [ZONE 1] TOP SECURITY & UTILITY COMMAND BAR
        // =========================================================================
        TopCommandBar(
            isSecureTarget = isSecureTarget,
            isDictating = isDictating,
            activeSwitchProfile = activeSwitchProfile,
            vaultItemCount = clipboardItems.size,
            onSwitchProfileClick = onSwitchProfileClick,
            onDictationClick = onDictationClick,
            onVaultClick = onVaultClick
        )

        // =========================================================================
        // [ZONE 2] DYNAMIC CONTEXT RIBBON (Expandable 36dp)
        // =========================================================================
        ClipboardRibbon(
            items = clipboardItems,
            isSecureTarget = isSecureTarget,
            theme = ribbonTheme,
            onPaste = onClipboardPaste,
            onPinToggle = onClipboardPinToggle,
            onDelete = onClipboardDelete
        )

        // =========================================================================
        // [ZONE 3] PRIMARY KEY MATRIX (48dp height minimum touch targets)
        // =========================================================================
        if (!isSymbolsMode) {
            // ==========================================
            // QWERTY Alphabetic Layout
            // ==========================================

            // Row 1: Q - P
            val row1 = listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                row1.forEach { key ->
                    val displayKey = if (isShiftActive) key else key.lowercase()
                    KeyCap(
                        label = displayKey,
                        category = KeyCategory.STANDARD,
                        modifier = Modifier.weight(1.0f),
                        onKeyDown = { onKeyDown(KeyCategory.STANDARD) },
                        onKeyUp = { onKeyUp(displayKey, KeyCategory.STANDARD) }
                    )
                }
            }

            // Row 2: A - L (with 0.5f spacer offsets for traditional stagger)
            val row2 = listOf("A", "S", "D", "F", "G", "H", "J", "K", "L")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Spacer(modifier = Modifier.weight(0.5f))
                row2.forEach { key ->
                    val displayKey = if (isShiftActive) key else key.lowercase()
                    KeyCap(
                        label = displayKey,
                        category = KeyCategory.STANDARD,
                        modifier = Modifier.weight(1.0f),
                        onKeyDown = { onKeyDown(KeyCategory.STANDARD) },
                        onKeyUp = { onKeyUp(displayKey, KeyCategory.STANDARD) }
                    )
                }
                Spacer(modifier = Modifier.weight(0.5f))
            }

            // Row 3: Shift / Z - M / Backspace
            val row3 = listOf("Z", "X", "C", "V", "B", "N", "M")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Shift key
                KeyCap(
                    label = if (isShiftActive) "⇪" else "⇧",
                    category = KeyCategory.STANDARD,
                    contentDescription = if (isShiftActive) "Shift locked" else "Shift",
                    modifier = Modifier.weight(1.5f),
                    backgroundColor = if (isShiftActive) Color(0xFF3B82F6) else Color(0xFF334155),
                    textColor = Color.White,
                    onKeyDown = { onKeyDown(KeyCategory.STANDARD) },
                    onKeyUp = { isShiftActive = !isShiftActive }
                )

                row3.forEach { key ->
                    val displayKey = if (isShiftActive) key else key.lowercase()
                    KeyCap(
                        label = displayKey,
                        category = KeyCategory.STANDARD,
                        modifier = Modifier.weight(1.0f),
                        onKeyDown = { onKeyDown(KeyCategory.STANDARD) },
                        onKeyUp = { onKeyUp(displayKey, KeyCategory.STANDARD) }
                    )
                }

                // Backspace key
                KeyCap(
                    label = "⌫",
                    category = KeyCategory.BACKSPACE,
                    contentDescription = "Backspace",
                    modifier = Modifier.weight(1.5f),
                    backgroundColor = Color(0xFF334155), // Slate 700
                    textColor = Color(0xFFFCA5A5), // Subtle red tint for warning
                    onKeyDown = { onKeyDown(KeyCategory.BACKSPACE) },
                    onKeyUp = { onKeyUp("BACKSPACE", KeyCategory.BACKSPACE) }
                )
            }
        } else {
            // ==========================================
            // Numbers & Symbols Layout
            // ==========================================

            // Row 1: 1 - 0
            val numRow = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                numRow.forEach { sym ->
                    KeyCap(
                        label = sym,
                        category = KeyCategory.STANDARD,
                        modifier = Modifier.weight(1.0f),
                        onKeyDown = { onKeyDown(KeyCategory.STANDARD) },
                        onKeyUp = { onKeyUp(sym, KeyCategory.STANDARD) }
                    )
                }
            }

            // Row 2: Common symbols
            val symRow1 = listOf("@", "#", "$", "%", "&", "-", "+", "(", ")")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Spacer(modifier = Modifier.weight(0.5f))
                symRow1.forEach { sym ->
                    KeyCap(
                        label = sym,
                        category = KeyCategory.STANDARD,
                        modifier = Modifier.weight(1.0f),
                        onKeyDown = { onKeyDown(KeyCategory.STANDARD) },
                        onKeyUp = { onKeyUp(sym, KeyCategory.STANDARD) }
                    )
                }
                Spacer(modifier = Modifier.weight(0.5f))
            }

            // Row 3: Punctuation / Backspace
            val symRow2 = listOf("*", "\"", "'", ":", ";", "!", "?")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Spacer(modifier = Modifier.weight(1.0f))
                symRow2.forEach { sym ->
                    KeyCap(
                        label = sym,
                        category = KeyCategory.STANDARD,
                        modifier = Modifier.weight(1.0f),
                        onKeyDown = { onKeyDown(KeyCategory.STANDARD) },
                        onKeyUp = { onKeyUp(sym, KeyCategory.STANDARD) }
                    )
                }
                KeyCap(
                    label = "⌫",
                    category = KeyCategory.BACKSPACE,
                    contentDescription = "Backspace",
                    modifier = Modifier.weight(1.5f),
                    backgroundColor = Color(0xFF334155),
                    textColor = Color(0xFFFCA5A5),
                    onKeyDown = { onKeyDown(KeyCategory.BACKSPACE) },
                    onKeyUp = { onKeyUp("BACKSPACE", KeyCategory.BACKSPACE) }
                )
            }
        }

        // ==========================================
        // Row 4: Common Bottom Bar (Space, Enter, Switch)
        // ==========================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Mode toggle: ?123 / ABC
            KeyCap(
                label = if (isSymbolsMode) "ABC" else "?123",
                category = KeyCategory.STANDARD,
                contentDescription = if (isSymbolsMode) "Switch to letters" else "Switch to numbers and symbols",
                modifier = Modifier.weight(1.4f),
                backgroundColor = Color(0xFF334155),
                textColor = Color(0xFFE2E8F0),
                onKeyDown = { onKeyDown(KeyCategory.STANDARD) },
                onKeyUp = { isSymbolsMode = !isSymbolsMode }
            )

            // Comma
            KeyCap(
                label = ",",
                category = KeyCategory.STANDARD,
                modifier = Modifier.weight(1.0f),
                onKeyDown = { onKeyDown(KeyCategory.STANDARD) },
                onKeyUp = { onKeyUp(",", KeyCategory.STANDARD) }
            )

            // Spacebar (wide touch zone, 4.4 weight)
            KeyCap(
                label = "SPACE",
                category = KeyCategory.SPACEBAR,
                contentDescription = "Spacebar",
                modifier = Modifier.weight(4.4f),
                backgroundColor = Color(0xFF1E293B),
                textColor = Color(0xFF94A3B8),
                onKeyDown = { onKeyDown(KeyCategory.SPACEBAR) },
                onKeyUp = { onKeyUp(" ", KeyCategory.SPACEBAR) }
            )

            // Period
            KeyCap(
                label = ".",
                category = KeyCategory.STANDARD,
                modifier = Modifier.weight(1.0f),
                onKeyDown = { onKeyDown(KeyCategory.STANDARD) },
                onKeyUp = { onKeyUp(".", KeyCategory.STANDARD) }
            )

            // Enter key (Action highlighted)
            KeyCap(
                label = "↵",
                category = KeyCategory.ENTER,
                contentDescription = "Enter",
                modifier = Modifier.weight(1.6f),
                backgroundColor = Color(0xFF2563EB), // Blue 600 high contrast action
                textColor = Color.White,
                onKeyDown = { onKeyDown(KeyCategory.ENTER) },
                onKeyUp = { onKeyUp("\n", KeyCategory.ENTER) }
            )
        }
    }
}

/**
 * High-contrast Visual Security Banner.
 * Renders #7F1D1D when isSecureTarget == true to guarantee visible user notice of zero-caching.
 */
@Composable
fun SecurityStatusRibbon(isSecureTarget: Boolean) {
    val bannerColor by animateColorAsState(
        targetValue = if (isSecureTarget) Color(0xFF7F1D1D) else Color(0xFF1E293B), // #7F1D1D invariant
        label = "BannerColor"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(bannerColor)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isSecureTarget) {
                    "🛡️ AIR-GAP SECURE MODE • CACHING DISABLED"
                } else {
                    "KEYGUARD • AIR-GAPPED CORE"
                },
                color = if (isSecureTarget) Color(0xFFFFFFFF) else Color(0xFF94A3B8),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "C++ OBOE 1.9",
                color = if (isSecureTarget) Color(0xFFFCA5A5) else Color(0xFF64748B),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/**
 * TopCommandBar
 *
 * Zone 1 Top Command Bar matching the Stitch UI design specification:
 *  - Left: Air-Gapped Shield Status Badge (ZERO-NET) & Mechanical Switch Profile Selector
 *  - Right: Dictation indicator, Clipboard Vault counter badge, Gated Translate DLP lock
 */
@Composable
fun TopCommandBar(
    isSecureTarget: Boolean,
    isDictating: Boolean,
    activeSwitchProfile: String,
    vaultItemCount: Int,
    onSwitchProfileClick: () -> Unit,
    onDictationClick: () -> Unit,
    onVaultClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF0B0F19))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Air-gap status badge & switch selector
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // ZERO-NET Air-gap badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0x2610B981))
                    .border(1.dp, Color(0x4D10B981), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .semantics { contentDescription = "Air-gapped secure mode, zero network passthrough" }
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(Color(0xFF10B981), CircleShape)
                )
                Text(
                    text = "ZERO-NET",
                    color = Color(0xFF34D399),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Mechanical switch profile chip
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF1E293B))
                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(4.dp))
                    .clickable(onClick = onSwitchProfileClick)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .semantics { contentDescription = "Switch profile: $activeSwitchProfile. Tap to change." }
            ) {
                Text(
                    text = "⚙",
                    color = Color(0xFF38BDF8),
                    fontSize = 9.sp
                )
                Text(
                    text = activeSwitchProfile,
                    color = Color(0xFFE2E8F0),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Right: Dictation, Vault counter, and Gated Translate
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Voice Dictation (with Invariant 3: Hard-locked in secure password mode)
            val dictationBg = when {
                isSecureTarget -> Color(0x22334155)
                isDictating -> Color(0xFF0C4A6E)
                else -> Color(0xFF1E293B)
            }
            val dictationBorder = when {
                isSecureTarget -> Color(0x44EF4444)
                isDictating -> Color(0xFF38BDF8)
                else -> Color(0xFF334155)
            }
            val dictationDesc = when {
                isSecureTarget -> "Microphone hard-disabled in sensitive password field"
                isDictating -> "Voice dictation active and listening locally"
                else -> "Local neural voice dictation"
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(dictationBg)
                    .border(1.dp, dictationBorder, RoundedCornerShape(4.dp))
                    .clickable(enabled = !isSecureTarget, onClick = onDictationClick)
                    .padding(horizontal = 6.dp, vertical = 3.dp)
                    .semantics { contentDescription = dictationDesc }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = if (isSecureTarget) "🔒" else "🎤",
                        fontSize = 10.sp
                    )
                    if (!isSecureTarget) {
                        val barColor = if (isDictating) Color(0xFF38BDF8) else Color(0xFF64748B)
                        Box(modifier = Modifier.width(2.dp).height(if (isDictating) 10.dp else 8.dp).background(barColor, CircleShape))
                        Box(modifier = Modifier.width(2.dp).height(if (isDictating) 14.dp else 12.dp).background(barColor, CircleShape))
                        Box(modifier = Modifier.width(2.dp).height(if (isDictating) 8.dp else 6.dp).background(barColor, CircleShape))
                    }
                }
            }

            // Clipboard Vault badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF1E293B))
                    .clickable(onClick = onVaultClick)
                    .padding(horizontal = 6.dp, vertical = 3.dp)
                    .semantics { contentDescription = "Encrypted clipboard vault with $vaultItemCount items" }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(text = "📋", fontSize = 10.sp)
                    if (vaultItemCount > 0) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0xFF0284C7))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = vaultItemCount.toString(),
                                color = Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // Gated Translate with Padlock
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF1E293B))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
                    .semantics { contentDescription = "Hardware-isolated DLP translation sandbox" }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(text = "🌐", fontSize = 10.sp)
                    Text(text = "🔒", fontSize = 8.sp)
                }
            }
        }
    }
}

// =============================================================================
// COMPOSE PREVIEWS
// =============================================================================

@androidx.compose.ui.tooling.preview.Preview(name = "Keyboard Screen - Full Cyber Interface", showBackground = true)
@Composable
private fun KeyboardScreenFullPreview() {
    val sampleItems = listOf(
        ClipboardRibbonItem(
            id = "1",
            previewText = "git commit -m 'fix oboe latency'",
            classifiedType = com.keyguard.ime.clipboard.util.ClassifiedContentType.CODE_SNIPPET,
            isPinned = true
        ),
        ClipboardRibbonItem(
            id = "2",
            previewText = "john.doe@enterprise.io",
            classifiedType = com.keyguard.ime.clipboard.util.ClassifiedContentType.EMAIL,
            isPinned = false
        ),
        ClipboardRibbonItem(
            id = "3",
            previewText = "https://keyguard.dev/spec",
            classifiedType = com.keyguard.ime.clipboard.util.ClassifiedContentType.URL,
            isPinned = false
        )
    )

    KeyboardScreen(
        isSecureTarget = false,
        onKeyDown = {},
        onKeyUp = { _, _ -> },
        clipboardItems = sampleItems,
        ribbonTheme = RibbonTheme.INDUSTRIAL_MATTE,
        activeSwitchProfile = "Cherry MX Blue"
    )
}

@androidx.compose.ui.tooling.preview.Preview(name = "Keyboard Screen - Secure Lockout", showBackground = true)
@Composable
private fun KeyboardScreenLockoutPreview() {
    KeyboardScreen(
        isSecureTarget = true,
        onKeyDown = {},
        onKeyUp = { _, _ -> },
        clipboardItems = emptyList(),
        ribbonTheme = RibbonTheme.INDUSTRIAL_MATTE,
        activeSwitchProfile = "Cherry MX Blue"
    )
}


