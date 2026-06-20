package com.example.freshscan.domain.model

/**
 * Domain model for a single detected item in v2 multi-object detection.
 *
 * Produced by the three-stage inference pipeline (EfficientDet detection →
 * 260-class classification → 18-class freshness classification) and
 * consumed by AnalysisScreen/BottomSheet.
 *
 * @property bbox Normalized bounding box coordinates in [0, 1] range,
 *                relative to the original photo dimensions.
 */
data class DetectedItem(
    /** Unique identifier (UUID v4). */
    val id: String,

    /** Raw label from Fruits-360 model, e.g. "Tomato_Cherry_Red". */
    val label: String,

    /** Human-readable display name, e.g. "樱桃番茄". */
    val displayName: String,

    /** Freshness level determined by the v1 18-class model. */
    val freshnessLevel: FreshnessLevel,

    /** Top-1 confidence score, range [0.0, 1.0]. */
    val confidence: Float,

    /** Normalized bounding box coordinates [0, 1]. */
    val bbox: BoundingBox,

    /** Whether this item is cookable (true for vegetables, false for fruits). */
    val isCookable: Boolean
)
