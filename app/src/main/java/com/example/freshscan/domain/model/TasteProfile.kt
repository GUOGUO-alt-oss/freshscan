package com.example.freshscan.domain.model

/**
 * Domain model for user taste profile preferences.
 *
 * Persisted via Jetpack DataStore<Preferences>.
 * Used by RecipeEngine to weight recipe recommendations.
 */
data class TasteProfile(
    /** Spice tolerance: 0=不辣, 1=微辣, 2=中辣, 3=超辣. */
    val spiceLevel: Int = 0,

    /** Salt preference: 0=少盐, 1=正常, 2=偏咸. */
    val saltLevel: Int = 1,

    /** Oil preference: 0=少油, 1=正常, 2=偏油. */
    val oilLevel: Int = 1,

    /** Ingredients to exclude (allergy/dietary restrictions). */
    val excludedIngredients: Set<String> = emptySet(),

    /** Preferred recipe categories for ranking boost. */
    val preferredCategories: Set<RecipeCategory> = emptySet()
)
