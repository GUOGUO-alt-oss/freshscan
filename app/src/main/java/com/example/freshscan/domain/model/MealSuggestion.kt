package com.example.freshscan.domain.model

import java.util.UUID

/**
 * AI-generated single meal suggestion based on user query.
 *
 * Replaces the heavier 7-day DietPlan model for faster, on-demand queries.
 * Persisted in meal_history Room table.
 */
data class MealSuggestion(
    val id: String = UUID.randomUUID().toString(),
    val query: String,
    val title: String,
    val ingredients: List<Ingredient>,
    val steps: List<String>,
    val cookingTimeMin: Int,
    val calories: Int,
    val proteinG: Float,
    val carbsG: Float,
    val fatG: Float,
    val generatedAt: Long = System.currentTimeMillis()
)
