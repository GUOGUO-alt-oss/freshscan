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
        if (result.isFailure) throw result.exceptionOrNull()!!
        val plan = parseDietPlan(result.getOrThrow(), profile)
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
        val bmr = if (profile.gender == Gender.MALE)
            10 * profile.weightKg + 6.25f * profile.heightCm - 5 * profile.age + 5
        else
            10 * profile.weightKg + 6.25f * profile.heightCm - 5 * profile.age - 161
        val tdee = bmr * profile.activityLevel.factor
        return when (profile.goal) {
            HealthGoal.LOSE_WEIGHT -> (tdee - 400).toInt()
            HealthGoal.BUILD_MUSCLE -> (tdee + 400).toInt()
            else -> tdee.toInt()
        }
    }

    private fun parseDietPlan(jsonStr: String, profile: UserProfile): DietPlan {
        val root = JSONObject(jsonStr)
        val dailyPlans = mutableListOf<DailyMealPlan>()
        val dailyArray = root.getJSONArray("dailyPlans")
        for (i in 0 until dailyArray.length()) {
            val dayObj = dailyArray.getJSONObject(i)
            val meals = mutableListOf<Meal>()
            val mealsArray = dayObj.getJSONArray("meals")
            for (j in 0 until mealsArray.length()) {
                val mealObj = mealsArray.getJSONObject(j)
                val type = try {
                    MealType.valueOf(mealObj.getString("type"))
                } catch (_: Exception) {
                    MealType.LUNCH
                }
                val recipeObj = mealObj.getJSONObject("recipe")
                val ingredients = mutableListOf<Ingredient>()
                val ingArray = recipeObj.getJSONArray("ingredients")
                for (k in 0 until ingArray.length()) {
                    val ingObj = ingArray.getJSONObject(k)
                    ingredients.add(
                        Ingredient(
                            ingObj.getString("name"),
                            ingObj.optString("amount", "")
                        )
                    )
                }
                val steps = (0 until recipeObj.getJSONArray("steps").length())
                    .map { k -> recipeObj.getJSONArray("steps").getString(k) }
                meals.add(
                    Meal(
                        type, DietRecipe(
                            title = recipeObj.getString("title"),
                            ingredients = ingredients,
                            steps = steps,
                            cookingTimeMin = recipeObj.getInt("cookingTimeMin"),
                            calories = recipeObj.getInt("calories"),
                            proteinG = recipeObj.getDouble("proteinG").toFloat(),
                            carbsG = recipeObj.getDouble("carbsG").toFloat(),
                            fatG = recipeObj.getDouble("fatG").toFloat()
                        )
                    )
                )
            }
            dailyPlans.add(
                DailyMealPlan(
                    dayIndex = dayObj.getInt("dayIndex"),
                    dayLabel = dayObj.getString("dayLabel"),
                    totalCalories = dayObj.getInt("totalCalories"),
                    meals = meals,
                    notes = dayObj.optString("notes", "").ifEmpty { null }
                )
            )
        }
        return DietPlan(
            id = UUID.randomUUID().toString(),
            generatedAt = System.currentTimeMillis(),
            userProfileSnapshot = profile,
            dailyPlans = dailyPlans,
            totalCaloriesAvg = root.getInt("totalCaloriesAvg"),
            nutritionSummary = root.getString("nutritionSummary")
        )
    }

    private fun toEntity(plan: DietPlan): DietPlanEntity {
        val profileJson = JSONObject().apply {
            put("age", plan.userProfileSnapshot.age)
            put("goal", plan.userProfileSnapshot.goal.name)
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
        val root = JSONObject().apply {
            put("dailyPlans", JSONArray(entity.dailyPlansJson))
            put("totalCaloriesAvg", entity.totalCaloriesAvg)
            put("nutritionSummary", entity.nutritionSummary)
        }
        return parseDietPlan(root.toString(), UserProfile()).copy(
            id = entity.id, generatedAt = entity.generatedAt
        )
    }
}
