package com.example.freshscan.data.history

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for persisting fridge items.
 *
 * Items are auto-added after scanning and can be manually removed
 * when consumed or discarded. Each item tracks when it was added
 * and an optional expiry date for freshness reminders.
 */
@Entity(
    tableName = "fridge_items",
    indices = [
        Index(value = ["added_at"], name = "idx_fridge_added_at"),
        Index(value = ["display_name"], name = "idx_fridge_display_name")
    ]
)
data class FridgeEntity(
    /** UUID v4 primary key. */
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    /** Human-readable display name (e.g. "樱桃番茄"). */
    @ColumnInfo(name = "display_name")
    val displayName: String,

    /** Raw label from the model (e.g. "Tomato_Cherry_Red"). */
    @ColumnInfo(name = "label")
    val label: String = "",

    /** FruitCategory enum name for icon/color mapping. */
    @ColumnInfo(name = "fruit_category")
    val fruitCategory: String = "UNKNOWN",

    /** Freshness level at time of scanning: FRESH, ROTTEN, UNCERTAIN. */
    @ColumnInfo(name = "freshness_level")
    val freshnessLevel: String = "FRESH",

    /** Whether this item is cookable (vegetable). */
    @ColumnInfo(name = "is_cookable")
    val isCookable: Boolean = false,

    /** Unix timestamp (ms) when the item was added to the fridge. */
    @ColumnInfo(name = "added_at")
    val addedAt: Long,

    /**
     * Unix timestamp (ms) of estimated expiry date, or null if unknown.
     * Typical defaults: leafy greens 3 days, fruits 5-7 days, root veg 14+ days.
     */
    @ColumnInfo(name = "expiry_at")
    val expiryAt: Long? = null,

    /** Path to thumbnail image, or null. */
    @ColumnInfo(name = "thumbnail_path")
    val thumbnailPath: String? = null,

    /** Confidence score from the scan [0.0, 1.0]. */
    @ColumnInfo(name = "confidence")
    val confidence: Float = 0f,

    /** Optional user note (e.g. "从超市买的"). */
    @ColumnInfo(name = "note")
    val note: String = "",

    /** v4.2: Optional user-defined expiry in days (overrides auto-estimate). */
    @ColumnInfo(name = "expiry_days")
    val expiryDays: Int? = null
)
