package com.example.freshscan.util

/**
 * Utility to estimate produce shelf life based on category keywords (v4.2).
 *
 * Falls back to produce_info.json storage tips where available,
 * with hardcoded defaults for common produce types when JSON data
 * is insufficient or unavailable.
 */
object ExpiryCalculator {

    /**
     * Estimate default shelf life in days for a given produce label.
     *
     * Rules (descending priority):
     * - Leafy greens → 3 days
     * - Berries → 3 days
     * - Mushrooms → 4 days
     * - Common fruit → 5 days
     * - Citrus → 10 days
     * - Root vegetables → 20 days
     * - Other → 7 days
     */
    fun estimateDays(label: String): Int {
        val lower = label.lowercase()

        return when {
            // Leafy greens — spoil fastest
            lower.contains("spinach") || lower.contains("lettuce") ||
            lower.contains("cabbage") || lower.contains("pak_choi") ||
            lower.contains("celery") || lower.contains("coriander") -> 3

            // Berries — very perishable
            lower.contains("strawberry") || lower.contains("blueberry") ||
            lower.contains("raspberry") || lower.contains("blackberry") ||
            lower.contains("cherry") -> 3

            // Mushrooms — short shelf life
            lower.contains("mushroom") -> 4

            // Common fruit — moderate
            lower.contains("apple") || lower.contains("banana") ||
            lower.contains("pear") || lower.contains("peach") ||
            lower.contains("plum") || lower.contains("grape") ||
            lower.contains("mango") || lower.contains("kiwi") ||
            lower.contains("papaya") || lower.contains("pineapple") ||
            lower.contains("tomato") || lower.contains("avocado") -> 5

            // Cucumber/zucchini family
            lower.contains("cucumber") || lower.contains("zucchini") ||
            lower.contains("eggplant") || lower.contains("pepper") ||
            lower.contains("okra") || lower.contains("green_bean") -> 5

            // Citrus — long shelf life
            lower.contains("orange") || lower.contains("lemon") ||
            lower.contains("lime") || lower.contains("grapefruit") ||
            lower.contains("citrus") || lower.contains("mandarin") ||
            lower.contains("tangerine") -> 10

            // Root vegetables — longest
            lower.contains("potato") || lower.contains("carrot") ||
            lower.contains("onion") || lower.contains("garlic") ||
            lower.contains("ginger") || lower.contains("sweet_potato") ||
            lower.contains("radish") || lower.contains("beetroot") ||
            lower.contains("turnip") || lower.contains("pumpkin") ||
            lower.contains("squash") -> 20

            // Default
            else -> 7
        }
    }

    /**
     * Calculate expiry timestamp as addedAt + estimatedDays in ms.
     */
    fun calculateExpiryTimestamp(addedAt: Long, label: String): Long {
        val days = estimateDays(label)
        return addedAt + days * 24L * 60 * 60 * 1000
    }

    /** Days in ms conversion constant. */
    const val DAY_MS = 24L * 60 * 60 * 1000
}
