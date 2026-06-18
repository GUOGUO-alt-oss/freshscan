package com.example.freshscan.data.history

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for persisting recognition history.
 *
 * v2.0 changes:
 * - Added [sessionId] for grouping multi-object detection results.
 * - Added [displayName] for human-readable label display.
 * - Added [isCookable] for recipe matching.
 *
 * Maps to [com.example.freshscan.domain.model.HistoryItem] via [com.example.freshscan.data.mapper.EntityMapper].
 */
@Entity(
    tableName = "history",
    indices = [
        Index(value = ["timestamp"], name = "idx_history_timestamp"),
        Index(value = ["sessionId"], name = "idx_history_session"),
        Index(value = ["fruit_category"], name = "idx_history_category"),
        Index(value = ["freshness_level"], name = "idx_history_freshness")
    ]
)
data class HistoryEntity(
    /** UUID v4 primary key, from RecognitionResult.id. */
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    /**
     * Session ID shared by all detections in a single scan.
     * v1 records have empty string as default.
     */
    @ColumnInfo(name = "sessionId")
    val sessionId: String = "",

    /** FruitCategory enum name: APPLE, BANANA, ORANGE, UNKNOWN. */
    @ColumnInfo(name = "fruit_category")
    val fruitCategory: String,

    /** FreshnessLevel enum name: FRESH, ROTTEN, UNCERTAIN. */
    @ColumnInfo(name = "freshness_level")
    val freshnessLevel: String,

    /** Top-1 confidence score, range [0.0, 1.0]. */
    @ColumnInfo(name = "confidence")
    val confidence: Float,

    /**
     * Human-readable display name (v2 only).
     * e.g. "樱桃番茄". v1 records have empty string as default.
     */
    @ColumnInfo(name = "displayName")
    val displayName: String = "",

    /**
     * Whether the detected item is cookable (v2 only).
     * true = vegetable (can be cooked), false = fruit (eat raw).
     * v1 records have 0 (false) as default.
     */
    @ColumnInfo(name = "isCookable")
    val isCookable: Boolean = false,

    /** Inference time in milliseconds. */
    @ColumnInfo(name = "inference_time_ms")
    val inferenceTimeMs: Long,

    /** Unix timestamp (ms) of the recognition event. Indexed for sorting. */
    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    /** Absolute path to thumbnail file in internal storage, or null. */
    @ColumnInfo(name = "thumbnail_path")
    val thumbnailPath: String?,

    /** Top-3 predictions serialized as JSON array string. */
    @ColumnInfo(name = "top_predictions_json")
    val topPredictionsJson: String?
)
