package com.example.freshscan.data.recipe

import android.content.Context
import com.example.freshscan.domain.model.CookingStep
import com.example.freshscan.domain.model.DetectedItem
import com.example.freshscan.domain.model.Ingredient
import com.example.freshscan.domain.model.Nutrition
import com.example.freshscan.domain.model.Recipe
import com.example.freshscan.domain.model.RecipeCategory
import com.example.freshscan.domain.model.RecipeDifficulty
import com.example.freshscan.domain.model.TasteProfile
import com.example.freshscan.R
import com.example.freshscan.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Recipe recommendation engine.
 *
 * Loads preset recipes from assets/recipes/preset_recipes.json on first use
 * and provides ingredient-matching recommendations ranked by:
 *   1. Number of matching ingredients
 *   2. Full-match bonus (all recipe ingredients available)
 *   3. Taste profile preference weighting
 *   4. Cooking time (ascending tiebreaker)
 *
 * Also serves as the single source of truth for recipe data, providing
 * [getRecipeById] for the RecipeDetail screen and [getAllPresetRecipes]
 * for the Home screen browsing feature.
 *
 * Algorithm: see docs/06-详细设计文档-v2.md §6.3
 */
@Singleton
class RecipeEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val labelNormalizer: LabelNormalizer
) {
    /** Cached preset recipes, lazy-loaded from JSON on first access (thread-safe). */
    private val presetRecipes: List<Recipe> by lazy { loadRecipes() }

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Recommend recipes based on detected items and optional taste profile.
     *
     * Algorithm:
     * 1. Filter cookable items only
     * 2. Normalize model labels → ingredient names via [LabelNormalizer]
     * 3. Score each recipe: matchCount + allMatch bonus + taste profile bonus
     * 4. Sort by score desc, cooking time asc
     *
     * @param items Detected items from the 3-stage inference pipeline.
     * @param profile Optional user taste profile for preference weighting.
     * @return Ranked recipe recommendations with optional note.
     */
    suspend fun recommend(
        items: List<DetectedItem>,
        profile: TasteProfile? = null
    ): RecipeResult = withContext(Dispatchers.Default) {
        // Step 1: Filter cookable items
        val cookable = items.filter { it.isCookable }
        if (cookable.isEmpty()) {
            return@withContext RecipeResult(
                recipes = emptyList(),
                note = context.getString(R.string.recipe_all_fruit_note)
            )
        }

        // Step 2: Normalize labels to ingredient names
        val ingredientNames = cookable
            .flatMap { labelNormalizer.normalize(it.label) }
            .distinct()
            .toSet()

        Logger.d("RecipeEngine", "Cookable: ${cookable.size}, ingredients: $ingredientNames")

        // Step 3: Score each recipe
        val recipes = presetRecipes
        val scored = recipes.map { recipe ->
            val matchCount = recipe.matchIngredients.count { it in ingredientNames }
            val hasAllMatch = recipe.matchIngredients.isNotEmpty() &&
                recipe.matchIngredients.all { it in ingredientNames }

            var score = matchCount.toFloat()

            // Full-match bonus: +3
            if (hasAllMatch) score += 3f

            // Taste profile category preference: +1.5
            if (profile != null && recipe.category in profile.preferredCategories) {
                score += 1.5f
            }

            recipe to score
        }.filter { (_, score) -> score > 0 }

        // Step 4: Sort by score desc → cooking time asc
        val sorted = scored
            .sortedWith(
                compareByDescending<Pair<Recipe, Float>> { it.second }
                    .thenBy { it.first.cookingTimeMin }
            )
            .map { it.first }

        // Generate note for missing ingredients in top recipes
        val note = buildNote(sorted.firstOrNull(), ingredientNames)

        return@withContext RecipeResult(sorted, note)
    }

    /**
     * Get a recipe by its ID.
     *
     * @param id Recipe ID (snake_case), e.g. "tomato_egg_stirfry".
     * @return The matching recipe, or null if not found.
     */
    suspend fun getRecipeById(id: String): Recipe? = withContext(Dispatchers.Default) {
        presetRecipes.find { it.id == id }
    }

    /**
     * Get all preset recipes (unsorted, for Home screen browsing).
     */
    suspend fun getAllPresetRecipes(): List<Recipe> = withContext(Dispatchers.Default) {
        presetRecipes
    }

    // ─── Private ─────────────────────────────────────────────────────────────

    /**
     * Load and parse the preset_recipes.json asset.
     *
     * Runs synchronously on the calling thread (expected to be called
     * within a coroutine on [Dispatchers.Default]).
     *
     * @throws RecipeLoadException if JSON is corrupt or unreadable, so callers
     *         can surface a user-visible error instead of silently returning
     *         "no recipes found".
     */
    private fun loadRecipes(): List<Recipe> {
        return try {
            val inputStream = context.assets.open(RECIPES_ASSET_PATH)
                ?: throw RecipeLoadException(context.getString(R.string.recipe_data_missing))
            val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
            val jsonStr = reader.use { it.readText() }
            if (jsonStr.isBlank()) throw RecipeLoadException(context.getString(R.string.recipe_data_empty))
            val root = JSONObject(jsonStr)
            val recipesArray = root.getJSONArray("recipes")
            val recipes = mutableListOf<Recipe>()

            for (i in 0 until recipesArray.length()) {
                val obj = recipesArray.getJSONObject(i)
                recipes.add(parseRecipe(obj))
            }

            // Empty recipes array is a valid edge case — let callers handle it
            // (don't throw — file is valid JSON, just has no entries)

            Logger.i("RecipeEngine", "Loaded ${recipes.size} preset recipes")
            recipes
        } catch (e: RecipeLoadException) {
            throw e // Re-throw our own exceptions directly
        } catch (e: Exception) {
            Logger.e("RecipeEngine", "Failed to load recipes", e)
            throw RecipeLoadException(
                context.getString(R.string.recipe_load_failed, e.message ?: context.getString(R.string.error_unknown)),
                e
            )
        }
    }

    /**
     * Parse a single recipe JSON object.
     */
    private fun parseRecipe(obj: JSONObject): Recipe {
        val matchIngredients = parseStringArray(obj.getJSONArray("matchIngredients"))
        val allIngredients = parseIngredients(obj.getJSONArray("allIngredients"))
        val steps = parseSteps(obj.getJSONArray("steps"))
        val nutrition = parseNutrition(obj.getJSONObject("nutrition"))
        val tags = parseStringArray(obj.getJSONArray("tags"))

        return Recipe(
            id = obj.getString("id"),
            title = obj.getString("title"),
            category = RecipeCategory.valueOf(obj.getString("category")),
            difficulty = RecipeDifficulty.valueOf(obj.getString("difficulty")),
            cookingTimeMin = obj.getInt("cookingTimeMin"),
            matchIngredients = matchIngredients,
            allIngredients = allIngredients,
            steps = steps,
            nutrition = nutrition,
            tags = tags,
            tips = obj.optString("tips", ""),
            imageAsset = obj.optString("imageAsset", ""),
            thumbnailAsset = obj.optString("thumbnailAsset", "")
        )
    }

    private fun parseStringArray(arr: JSONArray): List<String> {
        return (0 until arr.length()).map { arr.getString(it) }
    }

    private fun parseIngredients(arr: JSONArray): List<Ingredient> {
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            Ingredient(
                name = obj.getString("name"),
                amount = obj.getString("amount")
            )
        }
    }

    private fun parseSteps(arr: JSONArray): List<CookingStep> {
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            CookingStep(
                order = obj.getInt("order"),
                text = obj.getString("text"),
                timerSec = obj.optInt("timerSec", 0)
            )
        }
    }

    private fun parseNutrition(obj: JSONObject): Nutrition {
        return Nutrition(
            calories = obj.getInt("calories"),
            protein = obj.getInt("protein"),
            carbs = obj.getInt("carbs"),
            fat = obj.getInt("fat"),
            fiber = obj.getInt("fiber")
        )
    }

    /**
     * Build a note about missing ingredients for the top-ranked recipe.
     */
    private fun buildNote(topRecipe: Recipe?, ingredientNames: Set<String>): String? {
        if (topRecipe == null) return context.getString(R.string.recipe_no_match)
        val missing = topRecipe.matchIngredients.filter { it !in ingredientNames }
        if (missing.isEmpty()) return null // Perfect match, no note needed
        if (missing.size <= 2) {
            return context.getString(R.string.recipe_missing_items_2, missing.joinToString("、"))
        }
        return context.getString(R.string.recipe_missing_items_n, missing.size)
    }

    companion object {
        /** Asset path to the preset recipes JSON file. */
        const val RECIPES_ASSET_PATH = "recipes/preset_recipes.json"
    }
}

/**
 * Thrown when the preset recipes JSON cannot be loaded or parsed.
 *
 * Carries a user-friendly Chinese error message suitable for direct
 * display in the UI via [AnalysisUiState.errorMessage].
 */
class RecipeLoadException(
    message: String,
    cause: Throwable? = null
) : IllegalStateException(message, cause)
