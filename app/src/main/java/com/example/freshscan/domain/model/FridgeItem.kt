package com.example.freshscan.domain.model

/**
 * Domain model for a fridge item.
 *
 * Represents an item currently stored in the user's virtual fridge.
 * Created automatically after scanning and can be managed manually.
 */
data class FridgeItem(
    /** Unique identifier (UUID v4). */
    val id: String,

    /** Human-readable display name (e.g. "樱桃番茄"). */
    val displayName: String,

    /** Raw label from the model. */
    val label: String = "",

    /** FruitCategory for icon/color mapping. */
    val fruitCategory: FruitCategory = FruitCategory.UNKNOWN,

    /** Freshness level at time of scanning. */
    val freshnessLevel: FreshnessLevel = FreshnessLevel.FRESH,

    /** Whether this item is cookable (vegetable). */
    val isCookable: Boolean = false,

    /** Unix timestamp (ms) when added to fridge. */
    val addedAt: Long,

    /** Estimated expiry timestamp, or null if unknown. */
    val expiryAt: Long? = null,

    /** Thumbnail image path, or null. */
    val thumbnailPath: String? = null,

    /** Confidence score from scan. */
    val confidence: Float = 0f,

    /** Optional user note. */
    val note: String = ""
) {
    /**
     * Days remaining until expiry, or null if no expiry set.
     * Negative values mean the item has already expired.
     */
    fun daysUntilExpiry(nowMs: Long = System.currentTimeMillis()): Int? {
        val expiry = expiryAt ?: return null
        val diffMs = expiry - nowMs
        return (diffMs / (24 * 60 * 60 * 1000L)).toInt()
    }

    /** Whether the item has expired. */
    fun isExpired(nowMs: Long = System.currentTimeMillis()): Boolean {
        val expiry = expiryAt ?: return false
        return expiry < nowMs
    }

    /** Whether the item is expiring within [days] days. */
    fun isExpiringSoon(days: Int = 2, nowMs: Long = System.currentTimeMillis()): Boolean {
        val remaining = daysUntilExpiry(nowMs) ?: return false
        return remaining in 0..days
    }
}
