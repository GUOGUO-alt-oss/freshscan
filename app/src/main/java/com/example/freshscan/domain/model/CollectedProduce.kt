package com.example.freshscan.domain.model

/**
 * Domain model for a collected produce entry in the user's collection (v4.2).
 */
data class CollectedProduce(
    /** Raw model label (e.g. "Tomato_Cherry_Red"). */
    val label: String,

    /** Human-readable display name. */
    val displayName: String,

    /** Category for grouping (e.g. "水果", "蔬菜"). */
    val category: String,

    /** Unix timestamp (ms) of first scan. */
    val firstScanTime: Long,

    /** Total scan count. */
    val scanCount: Int,

    /** Whether this produce is rare (金色边框展示). */
    val isRare: Boolean
)
