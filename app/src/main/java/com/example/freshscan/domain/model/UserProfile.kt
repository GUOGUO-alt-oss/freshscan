package com.example.freshscan.domain.model

/**
 * Personalized user profile for diet plan generation.
 *
 * Extends the v2 TasteProfile with body metrics, activity level,
 * health goals, and dietary constraints.
 *
 * Persisted in Room (user_profile table) as UserProfileEntity,
 * with JSON-serialized sets (excludedIngredients, preferredCategories, allergies).
 */
data class UserProfile(
    // ── Taste preferences (from v2 TasteProfile) ──
    val spiceLevel: Int = 0,                       // 0=不辣, 1=微辣, 2=中辣, 3=超辣
    val saltLevel: Int = 1,                        // 0=少盐, 1=正常, 2=偏咸
    val oilLevel: Int = 1,                         // 0=少油, 1=正常, 2=偏油
    val excludedIngredients: Set<String> = emptySet(),
    val preferredCategories: Set<RecipeCategory> = emptySet(),
    val maxCookingTimeMin: Int = 60,

    // ── v3: Body metrics ──
    val age: Int = 25,
    val heightCm: Int = 170,
    val weightKg: Float = 65f,
    val gender: Gender = Gender.UNSPECIFIED,
    val activityLevel: ActivityLevel = ActivityLevel.MODERATE,
    val goal: HealthGoal = HealthGoal.EAT_HEALTHY,
    val mealsPerDay: Int = 3,
    val calorieTarget: Int? = null,                // null = auto-calculate via BMR formula
    val allergies: Set<String> = emptySet()
)

enum class ActivityLevel(val label: String, val factor: Float) {
    SEDENTARY("久坐少动", 1.2f),
    LIGHT("轻度活动", 1.375f),
    MODERATE("中度活动", 1.55f),
    ACTIVE("积极运动", 1.725f),
    VERY_ACTIVE("高强度运动", 1.9f)
}

enum class HealthGoal(val label: String) {
    LOSE_WEIGHT("减脂瘦身"),
    BUILD_MUSCLE("增肌塑形"),
    MAINTAIN("维持体重"),
    EAT_HEALTHY("健康饮食"),
    MANAGE_BLOOD_SUGAR("控糖管理")
}

enum class Gender(val label: String) {
    MALE("男"), FEMALE("女"), UNSPECIFIED("未指定")
}
