package com.example.freshscan.data.history

import com.example.freshscan.domain.model.*
import org.json.JSONArray

/**
 * Shared mapper between [UserProfileEntity] (Room) and domain model [UserProfile].
 *
 * Eliminates duplicated JSON parsing and entity-to-domain mapping logic
 * previously present in both DietPlanViewModel and PersonalizeViewModel.
 */
object UserProfileMapper {

    /**
     * Parse a JSON array string to a Set of strings.
     * Returns empty set on any parse failure.
     */
    fun parseJsonSet(json: String): Set<String> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { arr.getString(it) }.toSet()
    } catch (_: Exception) {
        emptySet()
    }

    /**
     * Convert a Room [UserProfileEntity] to domain [UserProfile].
     */
    fun toDomain(entity: UserProfileEntity): UserProfile {
        return UserProfile(
            spiceLevel = entity.spiceLevel,
            saltLevel = entity.saltLevel,
            oilLevel = entity.oilLevel,
            excludedIngredients = parseJsonSet(entity.excludedIngredients),
            preferredCategories = parseJsonSet(entity.preferredCategories).mapNotNull {
                try {
                    RecipeCategory.valueOf(it)
                } catch (_: Exception) {
                    null
                }
            }.toSet(),
            maxCookingTimeMin = entity.maxCookingTimeMin,
            age = entity.age,
            heightCm = entity.heightCm,
            weightKg = entity.weightKg,
            gender = try {
                Gender.valueOf(entity.gender)
            } catch (_: Exception) {
                Gender.UNSPECIFIED
            },
            activityLevel = try {
                ActivityLevel.valueOf(entity.activityLevel)
            } catch (_: Exception) {
                ActivityLevel.MODERATE
            },
            goal = try {
                HealthGoal.valueOf(entity.goal)
            } catch (_: Exception) {
                HealthGoal.EAT_HEALTHY
            },
            mealsPerDay = entity.mealsPerDay,
            calorieTarget = entity.calorieTarget,
            allergies = parseJsonSet(entity.allergies)
        )
    }
}
