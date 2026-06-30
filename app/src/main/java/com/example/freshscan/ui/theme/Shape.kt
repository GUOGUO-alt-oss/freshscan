package com.example.freshscan.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Unified 3-tier shape system.
 *
 * Collapses the previous 7 scattered corner-radius values into
 * three semantic tiers. CircleShape is preserved for status dots
 * and step-number badges.
 *
 * Ref: docs/uiv2.md §4.1 Shapes Theme
 */
val AppShapes = Shapes(
    small = RoundedCornerShape(8.dp),   // Chip, Badge, small buttons, labels
    medium = RoundedCornerShape(16.dp),  // Card, BottomSheet top, input fields, list items
    large = RoundedCornerShape(24.dp)    // Hero cards, Modal Sheets
)
