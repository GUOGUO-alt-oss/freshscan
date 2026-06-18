package com.example.freshscan.domain.model

/**
 * Freshness level for a recognition result.
 *
 * FRESH: The fruit/vegetable is in good condition and safe to eat.
 * ROTTEN: The fruit/vegetable shows signs of spoilage; not recommended.
 * UNCERTAIN: Confidence below threshold; result may be unreliable.
 */
enum class FreshnessLevel(
    val displayName: String,
    val emoji: String
) {
    FRESH("新鲜", "✅"),
    ROTTEN("腐烂", "⚠️"),
    UNCERTAIN("不确定", "❓")
}
