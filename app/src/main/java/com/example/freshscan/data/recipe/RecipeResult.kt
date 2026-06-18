package com.example.freshscan.data.recipe

import com.example.freshscan.domain.model.Recipe

/**
 * Result of a recipe recommendation query from [RecipeEngine.recommend].
 *
 * @property recipes Ranked list of matching recipes (best-first).
 * @property note Optional note for the user, e.g. "检测到的全是水果" or
 *               "缺少以下食材：鸡蛋、大蒜".
 */
data class RecipeResult(
    val recipes: List<Recipe>,
    val note: String? = null
)
