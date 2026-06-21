package com.example.freshscan.domain.model

import java.util.UUID

/**
 * Domain model representing a single fruit freshness recognition result.
 *
 * Produced by [ClassifyFruitUseCase] after TFLite inference and
 * consumed by the UI layer via [MainUiState].
 */
data class RecognitionResult(
    /** Unique identifier (UUID v4) for this result. */
    val id: String = UUID.randomUUID().toString(),

    /** Detected fruit category (e.g. APPLE, BANANA). */
    val fruitCategory: FruitCategory,

    /** Detected freshness level (FRESH, ROTTEN, or UNCERTAIN). */
    val freshnessLevel: FreshnessLevel,

    /** Confidence score for the top prediction, in [0.0, 1.0]. */
    val confidence: Float,

    /** Top-N prediction list, sorted by confidence descending. */
    val topPredictions: List<Prediction>,

    /** Inference time in milliseconds (for performance monitoring). */
    val inferenceTimeMs: Long,

    /** Unix timestamp (ms) of the recognition event. */
    val timestamp: Long = System.currentTimeMillis(),

    /** Path to the saved thumbnail image, or null if not saved. */
    val thumbnailPath: String? = null,

    /** Human-readable display name from v2 model (e.g. "樱桃番茄"). */
    val displayName: String = ""
) {
    /** Best display name: v2 displayName if available, else FruitCategory fallback. */
    val effectiveName: String
        get() = displayName.ifBlank { fruitCategory.displayName }
}

/**
 * A single prediction entry with label, display name, and confidence.
 */
data class Prediction(
    /** Raw label from the model, e.g. "fresh_apple". */
    val label: String,

    /** Human-readable display name, e.g. "苹果-新鲜". */
    val displayName: String,

    /** Confidence score for this prediction, in [0.0, 1.0]. */
    val confidence: Float
)
