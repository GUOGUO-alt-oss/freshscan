package com.example.freshscan.ui.screen.personalize

import com.example.freshscan.domain.model.ActivityLevel
import com.example.freshscan.domain.model.Gender
import com.example.freshscan.domain.model.HealthGoal
import com.example.freshscan.domain.model.RecipeCategory

data class PersonalizeUiState(
    // Taste
    val spiceLevel: Int = 0,
    val saltLevel: Int = 1,
    val oilLevel: Int = 1,
    val excludedIngredients: Set<String> = emptySet(),
    val preferredCategories: Set<RecipeCategory> = emptySet(),
    val maxCookingTimeMin: Int = 60,

    // Body
    val age: Int = 25,
    val heightCm: Int = 170,
    val weightKg: Float = 65f,
    val gender: Gender = Gender.UNSPECIFIED,
    val activityLevel: ActivityLevel = ActivityLevel.MODERATE,
    val goal: HealthGoal = HealthGoal.EAT_HEALTHY,
    val mealsPerDay: Int = 3,
    val calorieTarget: Int? = null,
    val allergies: Set<String> = emptySet(),

    // UI state
    val isDirty: Boolean = false,
    val isSaving: Boolean = false,
    val savedSuccessfully: Boolean = false,
    val errorMessage: String? = null
)
