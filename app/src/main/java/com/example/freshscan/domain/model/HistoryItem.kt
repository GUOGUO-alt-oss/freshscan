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
    val topPredictions: List<Prediction> = emptyList()
)
