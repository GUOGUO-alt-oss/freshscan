package com.example.freshscan.data.history

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 1,        // Single-row table
    val spiceLevel: Int = 0,
    val saltLevel: Int = 1,
    val oilLevel: Int = 1,
    val excludedIngredients: String = "[]",   // JSON array
    val preferredCategories: String = "[]",   // JSON array
    val maxCookingTimeMin: Int = 60,
    val age: Int = 25,
    val heightCm: Int = 170,
    val weightKg: Float = 65f,
    val gender: String = "UNSPECIFIED",
    val activityLevel: String = "MODERATE",
    val goal: String = "EAT_HEALTHY",
    val mealsPerDay: Int = 3,
    val calorieTarget: Int? = null,
    val allergies: String = "[]"              // JSON array
)
