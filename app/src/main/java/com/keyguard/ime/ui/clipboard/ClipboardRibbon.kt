package com.keyguard.ime.ui.clipboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.keyguard.ime.clipboard.util.ClassifiedContentType
import kotlin.math.roundToInt

/**
 * RibbonTheme
 *
 * Visual theme styling variants matching Stitch UI specifications:
 *  - [INDUSTRIAL_MATTE]: Deep midnight base (#0B0F19), matte slate surfaces, crisp 1px borders.
 *  - [LIQUID_GLASS]: Translucent acrylic (65% opacity), specular gradient rims, frosted glass chips.
 */
enum class RibbonTheme {
    INDUSTRIAL_MATTE,
    LIQUID_GLASS
}

/**
 * ClipboardRibbonItem
 *
 * UI representation of a clipboard entry displayed within the Zone 2 Context Ribbon.
 */
data class ClipboardRibbonItem(
    val id: String,
    val previewText: String,
    val classifiedType: ClassifiedContentType,
    val isPinned: Boolean = false,
    val timestampMs: Long = System.currentTimeMillis()
)

/**
 * ClipboardRibbon
 *
 * Zone 2 Context Ribbon (36dp height) positioned directly above the primary key matrix.
 *
 * Enforces:
 *  1. Zero Cloud / Local-Only NLP: Pure on-device presentation of categorized entities.
 *  2. Stitch Design Alignment: High-fidelity Industrial Matte and Liquid Glass themes.
 *  3. Smooth Scroll (60+ FPS): Low allocation LazyRow with stable item keys.
 *  4. Security Lockout State: Auto-collapses carousel in password fields with burgundy banner.
 *  5. WCAG 2.1 AA: Accessible labels, >= 4.5:1 contrast, explicit TalkBack descriptions.
 */
@Composable
fun ClipboardRibbon(
    items: List<ClipboardRibbonItem>,
    isSecureTarget: Boolean,
    theme: RibbonTheme = RibbonTheme.INDUSTRIAL_MATTE,
    onPaste: (ClipboardRibbonItem) -> Unit,
    onPinToggle: (ClipboardRibbonItem) -> Unit,
    onDelete: (ClipboardRibbonItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
    ) {
        if (isSecureTarget) {
            // =========================================================================
            // Dynamic State 1: Security Lockout Ribbon
            // =========================================================================
            SecurityLockoutBanner(theme = theme)
        } else {
            // =========================================================================
            // Dynamic State 2: Active Smart Vault Carousel
            // =========================================================================
            ActiveVaultCarousel(
                items = items,
                theme = theme,
                onPaste = onPaste,
                onPinToggle = onPinToggle,
                onDelete = onDelete
            )
        }
    }
}

/**
 * SecurityLockoutBanner
 *
 * Displays a high-contrast burgundy banner when isSecureTarget == true.
 * Alerts user that clipboard and telemetry are hard-isolated.
 */
@Composable
private fun SecurityLockoutBanner(theme: RibbonTheme) {
    val backgroundColor = if (theme == RibbonTheme.LIQUID_GLASS) {
        Color(0xD97F1D1D) // 85% opacity burgundy for glassmorphism
    } else {
        Color(0xFF7F1D1D) // Solid high-contrast burgundy
    }

    val borderColor = if (theme == RibbonTheme.LIQUID_GLASS) {
        Color(0x80EF4444)
    } else {
        Color(0xFF991B1B)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .border(1.dp, borderColor)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Text(
                    text = "🔒 SECURE PASSWORD FIELD — CLIPBOARD & NETWORK HARD-LOCKED",
                    color = Color(0xFFFEF08A), // Amber-yellow high contrast (WCAG >= 4.5:1)
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0x40000000))
                    .border(1.dp, Color(0x66FCA5A5), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "RAM WIPE ON BLUR",
                    color = Color(0xFFFCA5A5),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

/**
 * ActiveVaultCarousel
 *
 * 60+ FPS horizontal scrollable carousel for recent clipboard vault items.
 */
@Composable
private fun ActiveVaultCarousel(
    items: List<ClipboardRibbonItem>,
    theme: RibbonTheme,
    onPaste: (ClipboardRibbonItem) -> Unit,
    onPinToggle: (ClipboardRibbonItem) -> Unit,
    onDelete: (ClipboardRibbonItem) -> Unit
) {
    val containerModifier = when (theme) {
        RibbonTheme.INDUSTRIAL_MATTE -> Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F19))
            .border(1.dp, Color(0xFF1E293B))

        RibbonTheme.LIQUID_GLASS -> Modifier
            .fillMaxSize()
            .background(Color(0xA60B0F19)) // 65% opacity
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    listOf(
                        Color(0x38FFFFFF), // 22% white highlight
                        Color(0x0AFFFFFF)  // 4% white specular
                    )
                ),
                shape = RoundedCornerShape(0.dp)
            )
    }

    Row(
        modifier = containerModifier.padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Vault Section Header Badge
        VaultSectionBadge(theme = theme)

        Spacer(modifier = Modifier.width(6.dp))

        if (items.isEmpty()) {
            Text(
                text = "Vault Empty — Copied text appears here",
                color = Color(0xFF64748B),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = 4.dp)
            )
        } else {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(
                    items = items,
                    key = { it.id } // Stable keys prevent unnecessary recompositions
                ) { item ->
                    ClipboardChip(
                        item = item,
                        theme = theme,
                        onPaste = { onPaste(item) },
                        onPinToggle = { onPinToggle(item) },
                        onDelete = { onDelete(item) }
                    )
                }
            }
        }
    }
}

/**
 * VaultSectionBadge
 *
 * Left-anchored identifier chip reading "VAULT:" with cyber shield accent.
 */
@Composable
private fun VaultSectionBadge(theme: RibbonTheme) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.padding(start = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(Color(0xFF38BDF8), CircleShape)
        )
        Text(
            text = "VAULT:",
            color = if (theme == RibbonTheme.LIQUID_GLASS) Color(0xFF38BDF8) else Color(0xFF94A3B8),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.5.sp
        )
    }
}

/**
 * ClipboardChip
 *
 * Interactive chip component supporting quick-tap paste, long-press pin toggle,
 * and horizontal drag NIST SP 800-88 swipe-to-delete.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClipboardChip(
    item: ClipboardRibbonItem,
    theme: RibbonTheme,
    onPaste: () -> Unit,
    onPinToggle: () -> Unit,
    onDelete: () -> Unit
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    val dismissThreshold = -120f

    val chipShape = RoundedCornerShape(8.dp)

    // Visual theme parameters
    val chipBackground = when (theme) {
        RibbonTheme.INDUSTRIAL_MATTE -> Color(0xFF1E293B)
        RibbonTheme.LIQUID_GLASS -> Color(0x8C1E293B) // 55% translucent acrylic
    }

    val chipBorder = when (theme) {
        RibbonTheme.INDUSTRIAL_MATTE -> Color(0xFF334155)
        RibbonTheme.LIQUID_GLASS -> Color(0x29FFFFFF) // 16% refractive edge highlight
    }

    // Badge colors mapped per classification
    val (badgeBg, badgeText) = when (item.classifiedType) {
        ClassifiedContentType.CODE_SNIPPET -> Color(0xFF0C4A6E) to Color(0xFF38BDF8)
        ClassifiedContentType.EMAIL -> Color(0xFF451A03) to Color(0xFFFBBF24)
        ClassifiedContentType.URL -> Color(0xFF064E3B) to Color(0xFF34D399)
        ClassifiedContentType.PHONE_NUMBER -> Color(0xFF4C1D95) to Color(0xFFA78BFA)
        ClassifiedContentType.PLAIN_TEXT -> Color(0xFF334155) to Color(0xFF94A3B8)
    }

    val accessibilityDesc = "${item.classifiedType.description}: ${item.previewText}. " +
            "Tap to paste, long press to ${if (item.isPinned) "unpin" else "pin"}, swipe left to securely wipe."

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX.roundToInt(), 0) }
            .draggable(
                state = rememberDraggableState { delta ->
                    offsetX = (offsetX + delta).coerceAtMost(0f) // Only swipe left to delete
                },
                orientation = Orientation.Horizontal,
                onDragStopped = {
                    if (offsetX < dismissThreshold) {
                        onDelete()
                    } else {
                        offsetX = 0f
                    }
                }
            )
            .clip(chipShape)
            .background(chipBackground)
            .border(1.dp, chipBorder, chipShape)
            .combinedClickable(
                onClick = onPaste,
                onLongClick = onPinToggle,
                role = Role.Button
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .semantics {
                contentDescription = accessibilityDesc
                role = Role.Button
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Category Mini Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(badgeBg)
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = item.classifiedType.badgeLabel,
                    color = badgeText,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Preview Text (Truncated to fit carousel)
            Text(
                text = item.previewText,
                color = Color(0xFFF8FAFC), // High contrast text (WCAG >= 4.5:1)
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(130.dp)
            )

            // Pin Status Indicator
            if (item.isPinned) {
                Text(
                    text = "★",
                    color = Color(0xFFF59E0B),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// =============================================================================
// COMPOSE PREVIEWS
// =============================================================================

@androidx.compose.ui.tooling.preview.Preview(name = "Industrial Matte - Active Vault", showBackground = true)
@Composable
private fun ClipboardRibbonIndustrialMattePreview() {
    val sampleItems = listOf(
        ClipboardRibbonItem(
            id = "1",
            previewText = "git commit -m 'fix oboe latency'",
            classifiedType = ClassifiedContentType.CODE_SNIPPET,
            isPinned = true
        ),
        ClipboardRibbonItem(
            id = "2",
            previewText = "john.doe@enterprise.io",
            classifiedType = ClassifiedContentType.EMAIL,
            isPinned = false
        ),
        ClipboardRibbonItem(
            id = "3",
            previewText = "https://keyguard.dev/spec",
            classifiedType = ClassifiedContentType.URL,
            isPinned = false
        ),
        ClipboardRibbonItem(
            id = "4",
            previewText = "+1-555-867-5309",
            classifiedType = ClassifiedContentType.PHONE_NUMBER,
            isPinned = false
        )
    )

    ClipboardRibbon(
        items = sampleItems,
        isSecureTarget = false,
        theme = RibbonTheme.INDUSTRIAL_MATTE,
        onPaste = {},
        onPinToggle = {},
        onDelete = {}
    )
}

@androidx.compose.ui.tooling.preview.Preview(name = "Liquid Glass - Active Vault", showBackground = true)
@Composable
private fun ClipboardRibbonLiquidGlassPreview() {
    val sampleItems = listOf(
        ClipboardRibbonItem(
            id = "1",
            previewText = "git commit -m 'fix oboe latency'",
            classifiedType = ClassifiedContentType.CODE_SNIPPET,
            isPinned = true
        ),
        ClipboardRibbonItem(
            id = "2",
            previewText = "john.doe@enterprise.io",
            classifiedType = ClassifiedContentType.EMAIL,
            isPinned = false
        ),
        ClipboardRibbonItem(
            id = "3",
            previewText = "https://keyguard.dev/spec",
            classifiedType = ClassifiedContentType.URL,
            isPinned = false
        )
    )

    ClipboardRibbon(
        items = sampleItems,
        isSecureTarget = false,
        theme = RibbonTheme.LIQUID_GLASS,
        onPaste = {},
        onPinToggle = {},
        onDelete = {}
    )
}

@androidx.compose.ui.tooling.preview.Preview(name = "Security Lockout State", showBackground = true)
@Composable
private fun ClipboardRibbonSecurityLockoutPreview() {
    ClipboardRibbon(
        items = emptyList(),
        isSecureTarget = true,
        theme = RibbonTheme.INDUSTRIAL_MATTE,
        onPaste = {},
        onPinToggle = {},
        onDelete = {}
    )
}

