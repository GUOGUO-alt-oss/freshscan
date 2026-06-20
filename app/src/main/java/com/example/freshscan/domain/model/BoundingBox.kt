package com.example.freshscan.domain.model

/**
 * Platform-independent bounding box in normalized [0, 1] coordinates.
 *
 * Replaces `android.graphics.RectF` in domain models to keep the domain
 * layer free of Android framework dependencies (Clean Architecture).
 *
 * @property left   Left edge (x-min), normalized to [0, 1].
 * @property top    Top edge (y-min), normalized to [0, 1].
 * @property right  Right edge (x-max), normalized to [0, 1].
 * @property bottom Bottom edge (y-max), normalized to [0, 1].
 */
data class BoundingBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)
