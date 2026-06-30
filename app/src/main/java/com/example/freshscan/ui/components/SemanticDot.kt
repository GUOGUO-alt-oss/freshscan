package com.example.freshscan.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.freshscan.ui.theme.LocalSemanticColors

/**
 * 2-state status dot (filled / hollow) for ingredient ownership indication.
 *
 * Replaces the old ✅/⬜ emoji approach with a clean Japanese-minimalist dot.
 *
 * - [isOwned] = true → solid dot in freshnessHigh color (green)
 * - [isOwned] = false → hollow dot in outlineVariant (gray ring, transparent fill)
 *
 * Ref: docs/uiv2.md §7.1 SemanticDot
 *
 * @param isOwned Whether the ingredient is already owned by the user.
 * @param size Diameter of the dot. Default 8dp.
 */
@Composable
fun SemanticDot(
    isOwned: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 8.dp
) {
    val semanticColors = LocalSemanticColors.current

    if (isOwned) {
        // Solid green dot
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(semanticColors.freshnessHigh)
        )
    } else {
        // Hollow gray ring (outlineVariant border, transparent fill)
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
        )
    }
}
