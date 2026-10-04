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

/**
 * KeyboardScreen
 *
 * Primary responsive IME view adhering to WCAG 2.1 AA accessibility and zero-latency audio guidelines.
 * Features:
 *  1. Dynamic QWERTY and Numeric/Symbol layouts.
 *  2. High-contrast theme (#0F172A) with distinct functional key elevation.
 *  3. Visual Security Banner (#7F1D1D) displayed when inputting into sensitive password/PIN targets.
 *  4. Instant pointer-down audio & haptic triggering.
 */
@Composable
fun KeyboardScreen(
    isSecureTarget: Boolean,
    onKeyDown: (KeyCategory) -> Unit,
    onKeyUp: (String, KeyCategory) -> Unit,
    modifier: Modifier = Modifier
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
        // Visual Security Banner & Status Header
        SecurityStatusRibbon(isSecureTarget = isSecureTarget)

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
