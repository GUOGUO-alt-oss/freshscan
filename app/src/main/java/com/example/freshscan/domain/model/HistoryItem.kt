package com.example.freshscan.domain.model

/**
 * Domain model for a history record.
 *
 * Stored in Room database and displayed in [com.example.freshscan.ui.screen.history.HistoryScreen].
 * Maps to [com.example.freshscan.data.history.HistoryEntity] via [com.example.freshscan.data.mapper.EntityMapper].
 */
data class HistoryItem(
    val id: String,
    val fruitCategory: FruitCategory,
    val freshnessLevel: FreshnessLevel,
    val confidence: Float,
    val timestamp: Long,
    val thumbnailPath: String?,
    /** Inference time in milliseconds. */
    val inferenceTimeMs: Long = 0L,
    /** Top-N predictions for detail view. */
    val topPredictions: List<Prediction> = emptyList(),
    /** Human-readable display name from v2 model (e.g. "樱桃番茄"). */
    val displayName: String = "",
    /** Whether this item is cookable (vegetable). */
    val isCookable: Boolean = false,
    /** Session ID grouping items from a single scan. */
    val sessionId: String = ""
) {
    /** Best display name: v2 displayName if available, else FruitCategory fallback. */
    val effectiveName: String
        get() = displayName.ifBlank { fruitCategory.displayName }
}
