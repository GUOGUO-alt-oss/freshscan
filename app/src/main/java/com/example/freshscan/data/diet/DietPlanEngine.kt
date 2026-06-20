package com.example.freshscan.data.diet

import com.example.freshscan.data.ai.AIService
import com.example.freshscan.data.history.DietPlanDao
import com.example.freshscan.data.history.DietPlanEntity
import com.example.freshscan.domain.model.*
import com.example.freshscan.util.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DietPlanEngine @Inject constructor(
    private val aiService: AIService,
    private val dietPlanDao: DietPlanDao
) {
    fun generateWeekPlan(profile: UserProfile): Flow<DietPlan> = flow {
        Logger.d("DietPlanEngine", "Generating plan for goal=${profile.goal}")
        val result = aiService.chatJson(
            systemPrompt = "你是一名注册营养师和私人厨师。根据用户档案生成7天个性化饮食计划。" +
                "严格遵循JSON格式输出，不要包含markdown标记。",
            userMessage = buildPrompt(profile)
        )
        if (result.isFailure) throw result.exceptionOrNull()
            ?: IllegalStateException("AI service returned empty failure")
        val plan = parseDietPlan(result.getOrThrow(), profile)
        // Auto-cleanup plans older than 90 days (L16)
        val ninetyDaysAgo = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000
        dietPlanDao.deleteOlderThan(ninetyDaysAgo)
        dietPlanDao.insert(toEntity(plan))
        Logger.i("DietPlanEngine", "Plan generated: ${plan.id}")
        emit(plan)
    }

    fun getSavedPlans(): Flow<List<DietPlan>> = flow {
        dietPlanDao.getAll().collect { entities ->
            emit(entities.map { toDomain(it) })
        }
    }

    suspend fun getLatestPlan(): DietPlan? =
        dietPlanDao.getLatest()?.let { toDomain(it) }

    suspend fun deletePlan(id: String) { dietPlanDao.deleteById(id) }

    // ─── Prompt building ─────────────────────────────────────────────────

    private fun buildPrompt(profile: UserProfile): String = buildString {
        appendLine("【用户档案】")
        appendLine("- 年龄：${profile.age}岁")
        appendLine("- 性别：${profile.gender.label}")
        appendLine("- 身高：${profile.heightCm}cm")
        appendLine("- 体重：${profile.weightKg}kg")
        appendLine("- 活动量：${profile.activityLevel.label}")
        appendLine("- 健康目标：${profile.goal.label}")
        appendLine("- 每日餐数：${profile.mealsPerDay}")
        appendLine("- 口味偏好：辣度${profile.spiceLevel}/盐度${profile.saltLevel}/油量${profile.oilLevel}")
        if (profile.excludedIngredients.isNotEmpty())
            appendLine("- 忌口食材：${profile.excludedIngredients.joinToString("、")}")
        if (profile.allergies.isNotEmpty())
            appendLine("- 过敏原：${profile.allergies.joinToString("、")}")
        if (profile.preferredCategories.isNotEmpty())
            appendLine("- 偏好菜系：${profile.preferredCategories.joinToString("、") { it.displayName }}")
        appendLine("- 最长烹饪时间：${profile.maxCookingTimeMin}分钟")
        val cal = profile.calorieTarget ?: calculateTDEE(profile)
        appendLine("- 每日热量目标：${cal}kcal")
        appendLine()
        appendLine("【要求】")
        appendLine("1. 每日总热量接近 ${cal}kcal")
        appendLine("2. 食材选用中国超市常见品类，7天菜式不重复")
        appendLine("3. 每道菜标注完整营养成分（热量、蛋白质、碳水、脂肪）")
        appendLine("4. 避开用户忌口和过敏原")
        appendLine("5. 每道菜3-5个简明步骤，烹饪时间≤${profile.maxCookingTimeMin}分钟")
        appendLine()
        append("返回严格JSON：{\"dailyPlans\":[{\"dayIndex\":1,\"dayLabel\":\"周一\",\"totalCalories\":1800,")
        append("\"notes\":\"\",\"meals\":[{\"type\":\"BREAKFAST\",\"recipe\":{\"title\":\"\",")
        append("\"ingredients\":[{\"name\":\"\",\"amount\":\"\"}],\"steps\":[\"\"],\"cookingTimeMin\":0,")
        append("\"calories\":0,\"proteinG\":0,\"carbsG\":0,\"fatG\":0}}]}],\"totalCaloriesAvg\":0,")
        append("\"nutritionSummary\":\"\"}")
    }

    private fun calculateTDEE(profile: UserProfile): Int {
        val maleBMR = 10 * profile.weightKg + 6.25f * profile.heightCm - 5 * profile.age + 5
        val femaleBMR = 10 * profile.weightKg + 6.25f * profile.heightCm - 5 * profile.age - 161
        val bmr = when (profile.gender) {
            Gender.MALE -> maleBMR
            Gender.FEMALE -> femaleBMR
            Gender.UNSPECIFIED -> (maleBMR + femaleBMR) / 2
        }
        val tdee = bmr * profile.activityLevel.factor
        return when (profile.goal) {
            HealthGoal.LOSE_WEIGHT -> (tdee - 400).toInt()
            HealthGoal.BUILD_MUSCLE -> (tdee + 400).toInt()
            else -> tdee.toInt()
        }
    }

    private fun parseDietPlan(jsonStr: String, profile: UserProfile): DietPlan = try {
        val root = JSONObject(jsonStr)
        val dailyArray = root.optJSONArray("dailyPlans")
            ?: throw DietPlanParseException("AI response missing 'dailyPlans' array")
        val dailyPlans = parseDailyPlansArray(dailyArray)
        DietPlan(
            id = UUID.randomUUID().toString(),
            generatedAt = System.currentTimeMillis(),
            userProfileSnapshot = profile,
            dailyPlans = dailyPlans,
            totalCaloriesAvg = root.optInt("totalCaloriesAvg", 0),
            nutritionSummary = root.optString("nutritionSummary", "")
        )
    } catch (e: DietPlanParseException) {
        throw e
    } catch (e: Exception) {
        Logger.e("DietPlanEngine", "Failed to parse AI response: ${e.message}", e)
        throw DietPlanParseException("无法解析AI生成的饮食计划", e)
    }

    private fun toEntity(plan: DietPlan): DietPlanEntity {
        val profileJson = JSONObject().apply {
            put("spiceLevel", plan.userProfileSnapshot.spiceLevel)
            put("saltLevel", plan.userProfileSnapshot.saltLevel)
            put("oilLevel", plan.userProfileSnapshot.oilLevel)
            put("excludedIngredients", JSONArray(plan.userProfileSnapshot.excludedIngredients.toList()))
            put("preferredCategories", JSONArray(plan.userProfileSnapshot.preferredCategories.map { it.name }))
            put("maxCookingTimeMin", plan.userProfileSnapshot.maxCookingTimeMin)
            put("age", plan.userProfileSnapshot.age)
            put("heightCm", plan.userProfileSnapshot.heightCm)
            put("weightKg", plan.userProfileSnapshot.weightKg.toDouble())
            put("gender", plan.userProfileSnapshot.gender.name)
            put("activityLevel", plan.userProfileSnapshot.activityLevel.name)
            put("goal", plan.userProfileSnapshot.goal.name)
            put("mealsPerDay", plan.userProfileSnapshot.mealsPerDay)
            plan.userProfileSnapshot.calorieTarget?.let { put("calorieTarget", it) }
            put("allergies", JSONArray(plan.userProfileSnapshot.allergies.toList()))
        }.toString()
        val dailyArr = JSONArray()
        plan.dailyPlans.forEach { day ->
            val dayObj = JSONObject().apply {
                put("dayIndex", day.dayIndex)
                put("dayLabel", day.dayLabel)
                put("totalCalories", day.totalCalories)
                day.notes?.let { put("notes", it) }
                val mealsArr = JSONArray()
                day.meals.forEach { meal ->
                    mealsArr.put(
                        JSONObject().apply {
                            put("type", meal.type.name)
                            put(
                                "recipe", JSONObject().apply {
                                    put("title", meal.recipe.title)
                                    put("cookingTimeMin", meal.recipe.cookingTimeMin)
                                    put("calories", meal.recipe.calories)
                                    put("proteinG", meal.recipe.proteinG.toDouble())
                                    put("carbsG", meal.recipe.carbsG.toDouble())
                                    put("fatG", meal.recipe.fatG.toDouble())
                                    put(
                                        "ingredients", JSONArray().apply {
                                            meal.recipe.ingredients.forEach {
                                                put(
                                                    JSONObject().apply {
                                                        put("name", it.name); put("amount", it.amount)
                                                    }
                                                )
                                            }
                                        }
                                    )
                                    put(
                                        "steps", JSONArray().apply {
                                            meal.recipe.steps.forEach { put(it) }
                                        }
                                    )
                                }
                            )
                        }
                    )
                }
                put("meals", mealsArr)
            }
            dailyArr.put(dayObj)
        }
        return DietPlanEntity(
            id = plan.id, generatedAt = plan.generatedAt,
            profileSnapshotJson = profileJson,
            dailyPlansJson = dailyArr.toString(),
            totalCaloriesAvg = plan.totalCaloriesAvg,
            nutritionSummary = plan.nutritionSummary
        )
    }

    private fun toDomain(entity: DietPlanEntity): DietPlan {
        val profileJson = JSONObject(entity.profileSnapshotJson)
        val profile = UserProfile(
            spiceLevel = profileJson.optInt("spiceLevel", 0),
            saltLevel = profileJson.optInt("saltLevel", 1),
            oilLevel = profileJson.optInt("oilLevel", 1),
            excludedIngredients = profileJson.optJSONArray("excludedIngredients")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }.toSet()
            } ?: emptySet(),
            preferredCategories = profileJson.optJSONArray("preferredCategories")?.let { arr ->
                (0 until arr.length()).map {
                    try {
                        RecipeCategory.valueOf(arr.getString(it))
                    } catch (_: Exception) {
                        RecipeCategory.HOME
                    }
                }.toSet()
            } ?: emptySet(),
            maxCookingTimeMin = profileJson.optInt("maxCookingTimeMin", 60),
            age = profileJson.optInt("age", 25),
            heightCm = profileJson.optInt("heightCm", 170),
            weightKg = profileJson.optDouble("weightKg", 65.0).toFloat(),
            gender = try {
                Gender.valueOf(profileJson.optString("gender", "UNSPECIFIED"))
            } catch (_: Exception) {
                Gender.UNSPECIFIED
            },
            activityLevel = try {
                ActivityLevel.valueOf(profileJson.optString("activityLevel", "MODERATE"))
            } catch (_: Exception) {
                ActivityLevel.MODERATE
            },
            goal = try {
                HealthGoal.valueOf(profileJson.optString("goal", "EAT_HEALTHY"))
            } catch (_: Exception) {
                HealthGoal.EAT_HEALTHY
            },
            mealsPerDay = profileJson.optInt("mealsPerDay", 3),
            calorieTarget = if (profileJson.has("calorieTarget")) profileJson.optInt("calorieTarget") else null,
            allergies = profileJson.optJSONArray("allergies")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }.toSet()
            } ?: emptySet()
        )
        val dailyPlans = parseDailyPlansArray(JSONArray(entity.dailyPlansJson))
        return DietPlan(
            id = entity.id,
            generatedAt = entity.generatedAt,
            userProfileSnapshot = profile,
            dailyPlans = dailyPlans,
            totalCaloriesAvg = entity.totalCaloriesAvg,
            nutritionSummary = entity.nutritionSummary
        )
    }

    /**
     * Parse a JSONArray of daily meal plan objects.
     * Shared between parseDietPlan (AI response) and toDomain (DB entity).
     */
    private fun parseDailyPlansArray(dailyArray: JSONArray): List<DailyMealPlan> {
        val result = mutableListOf<DailyMealPlan>()
        for (i in 0 until dailyArray.length()) {
            val dayObj = dailyArray.optJSONObject(i) ?: continue
            val meals = mutableListOf<Meal>()
            val mealsArray = dayObj.optJSONArray("meals") ?: continue
            for (j in 0 until mealsArray.length()) {
                val mealObj = mealsArray.optJSONObject(j) ?: continue
                val type = try {
                    MealType.valueOf(mealObj.optString("type", "LUNCH"))
                } catch (_: Exception) {
                    MealType.LUNCH
                }
                val recipeObj = mealObj.optJSONObject("recipe") ?: continue
                val ingredients = mutableListOf<Ingredient>()
                val ingArray = recipeObj.optJSONArray("ingredients")
                if (ingArray != null) {
                    for (k in 0 until ingArray.length()) {
                        val ingObj = ingArray.optJSONObject(k) ?: continue
                        ingredients.add(
                            Ingredient(
                                ingObj.optString("name", ""),
                                ingObj.optString("amount", "")
                            )
                        )
                    }
                }
                val stepsArray = recipeObj.optJSONArray("steps")
                val steps = if (stepsArray != null) {
                    (0 until stepsArray.length()).map { k -> stepsArray.optString(k, "") }
                } else {
                    emptyList()
                }
                meals.add(
                    Meal(
                        type, DietRecipe(
                            title = recipeObj.optString("title", ""),
                            ingredients = ingredients,
                            steps = steps,
                            cookingTimeMin = recipeObj.optInt("cookingTimeMin", 0),
                            calories = recipeObj.optInt("calories", 0),
                            proteinG = recipeObj.optDouble("proteinG", 0.0).toFloat(),
                            carbsG = recipeObj.optDouble("carbsG", 0.0).toFloat(),
                            fatG = recipeObj.optDouble("fatG", 0.0).toFloat()
                        )
                    )
                )
            }
            result.add(
                DailyMealPlan(
                    dayIndex = dayObj.optInt("dayIndex", 0),
                    dayLabel = dayObj.optString("dayLabel", ""),
                    totalCalories = dayObj.optInt("totalCalories", 0),
                    meals = meals,
                    notes = dayObj.optString("notes", "").ifEmpty { null }
                )
            )
        }
        return result
    }
}

/**
 * Thrown when the AI-generated diet plan JSON cannot be parsed.
 * Carries a user-friendly message suitable for UI display.
 */
class DietPlanParseException(message: String, cause: Throwable? = null) :
    Exception(message, cause)