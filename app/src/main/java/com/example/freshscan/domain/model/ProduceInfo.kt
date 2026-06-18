package com.example.freshscan.domain.model

/**
 * Detailed produce information combining local preset data and AI-extended content.
 *
 * Core fields (intro, nutrition, healthBenefits, storageTips, seasonality)
 * are loaded from assets/produce_info.json and available offline immediately.
 *
 * AI-extended fields (selectionTips, pairingSuggestions, funFact) are loaded
 * on-demand via AIService when network is available.
 */
data class ProduceInfo(
    val label: String,                  // Normalized type name, e.g. "苹果"
    val displayName: String,            // "红粉苹果"
    val category: String,               // "水果" / "蔬菜"

    // ── Local preset (offline, instant) ──
    val intro: String,                  // Introduction (80-120 chars)
    val nutrition: NutritionFacts,      // Per 100g
    val healthBenefits: List<String>,   // Health benefits (3-5 items)
    val storageTips: String,            // Storage advice
    val seasonality: String,            // Season, e.g. "9-11月"

    // ── AI extended (online, lazy loaded) ──
    val selectionTips: String? = null,
    val pairingSuggestions: List<String>? = null,
    val funFact: String? = null
)

/**
 * Nutritional facts per 100g edible portion.
 * Optional fields use null when data is unavailable for a specific produce item.
 */
data class NutritionFacts(
    val caloriesKcal: Int,
    val proteinG: Float,
    val carbsG: Float,
    val fatG: Float,
    val fiberG: Float,
    val vitaminCMg: Float? = null,
    val vitaminAUg: Float? = null,
    val potassiumMg: Float? = null,
    val glycemicIndex: Int? = null
)
