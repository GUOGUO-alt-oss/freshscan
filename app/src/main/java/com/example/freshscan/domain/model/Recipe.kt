package com.example.freshscan.domain.model

/**
 * Domain model for a cooking recipe.
 *
 * Deserialized from assets/recipes/preset_recipes.json.
 * See docs/06-详细设计文档-v2.md §6.1 for the JSON schema.
 */
data class Recipe(
    /** Unique identifier (snake_case), e.g. "tomato_egg_stirfry". */
    val id: String,

    /** Display title, e.g. "番茄炒蛋". */
    val title: String,

    /** Recipe category for filtering. */
    val category: RecipeCategory,

    /** Difficulty level. */
    val difficulty: RecipeDifficulty,

    /** Total cooking time in minutes. */
    val cookingTimeMin: Int,

    /** Ingredient names used for matching with detected items. */
    val matchIngredients: List<String>,

    /** Complete ingredient list with amounts. */
    val allIngredients: List<Ingredient>,

    /** Ordered cooking steps. */
    val steps: List<CookingStep>,

    /** Nutritional information per serving. */
    val nutrition: Nutrition,

    /** Search/filter tags. */
    val tags: List<String>,

    /** Cooking tip text. */
    val tips: String,

    /** Asset path for the hero image, e.g. "tomato_egg_stirfry.webp". */
    val imageAsset: String,

    /** Asset path for the thumbnail, e.g. "tomato_egg_stirfry_thumb.webp". */
    val thumbnailAsset: String
)

/**
 * Recipe category for filtering and taste profile preferences.
 */
enum class RecipeCategory(val displayName: String) {
    DIET("减脂轻食"),
    HOME("家常菜"),
    QUICK("快手菜"),
    SOUP("汤羹"),
    COLD("凉拌菜")
}

/**
 * Recipe difficulty level.
 */
enum class RecipeDifficulty {
    EASY,
    MEDIUM,
    HARD
}

/**
 * A single ingredient with name and amount.
 */
data class Ingredient(
    val name: String,
    val amount: String
)

/**
 * A single cooking step with optional timer.
 */
data class CookingStep(
    /** Step order (1-based). */
    val order: Int,

    /** Instruction text. */
    val text: String,

    /** Timer duration in seconds, or 0 if no timer needed. */
    val timerSec: Int
)

/**
 * Nutritional information per serving.
 */
data class Nutrition(
    /** Calories in kcal. */
    val calories: Int,

    /** Protein in grams. */
    val protein: Int,

    /** Carbohydrates in grams. */
    val carbs: Int,

    /** Fat in grams. */
    val fat: Int,

    /** Dietary fiber in grams. */
    val fiber: Int
)
