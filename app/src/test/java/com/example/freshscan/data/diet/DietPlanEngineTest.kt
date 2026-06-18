package com.example.freshscan.data.diet

import com.example.freshscan.data.ai.AIService
import com.example.freshscan.data.history.DietPlanDao
import com.example.freshscan.data.history.DietPlanEntity
import com.example.freshscan.domain.model.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DietPlanEngineTest {

    private val mockAiService = mockk<AIService>()
    private val mockDao = mockk<DietPlanDao>()

    private val engine = DietPlanEngine(mockAiService, mockDao)

    // ── Helper ──

    private fun createValidDietPlanJson(): String {
        return JSONObject().apply {
            put("dailyPlans", JSONArray().apply {
                put(JSONObject().apply {
                    put("dayIndex", 1)
                    put("dayLabel", "周一")
                    put("totalCalories", 1800)
                    put("notes", "清淡饮食")
                    put("meals", JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "BREAKFAST")
                            put("recipe", JSONObject().apply {
                                put("title", "燕麦粥")
                                put("ingredients", JSONArray().apply {
                                    put(JSONObject().apply {
                                        put("name", "燕麦"); put("amount", "50g")
                                    })
                                })
                                put("steps", JSONArray().apply {
                                    put("1. 煮水")
                                    put("2. 加入燕麦")
                                })
                                put("cookingTimeMin", 15)
                                put("calories", 350)
                                put("proteinG", 12.0)
                                put("carbsG", 45.0)
                                put("fatG", 8.0)
                            })
                        })
                    })
                })
            })
            put("totalCaloriesAvg", 1800)
            put("nutritionSummary", "均衡营养")
        }.toString()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TDEE / BMR Calculation Tests (via prompt capture)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `TDEE calculation for male with moderate activity and healthy goal`() = runTest {
        val profile = UserProfile(
            gender = Gender.MALE, weightKg = 70f, heightCm = 175, age = 30,
            activityLevel = ActivityLevel.MODERATE, goal = HealthGoal.EAT_HEALTHY
        )
        // BMR = 10*70 + 6.25*175 - 5*30 + 5 = 700 + 1093.75 - 150 + 5 = 1648.75
        // TDEE = 1648.75 * 1.55 = 2555.5625 → toInt = 2555
        val promptSlot = slot<String>()
        coEvery { mockAiService.chatJson(any(), capture(promptSlot), any()) } returns
            Result.success(createValidDietPlanJson())
        coEvery { mockDao.insert(any()) } returns Unit

        engine.generateWeekPlan(profile).collect {
            val prompt = promptSlot.captured
            assertTrue(
                "Prompt should contain 2555kcal for male, was: ${prompt.take(500)}",
                prompt.contains("2555kcal")
            )
        }
    }

    @Test
    fun `TDEE calculation for female with moderate activity and healthy goal`() = runTest {
        val profile = UserProfile(
            gender = Gender.FEMALE, weightKg = 60f, heightCm = 165, age = 25,
            activityLevel = ActivityLevel.MODERATE, goal = HealthGoal.EAT_HEALTHY
        )
        // BMR = 10*60 + 6.25*165 - 5*25 - 161 = 600 + 1031.25 - 125 - 161 = 1345.25
        // TDEE = 1345.25 * 1.55 = 2085.1375 → toInt = 2085
        val promptSlot = slot<String>()
        coEvery { mockAiService.chatJson(any(), capture(promptSlot), any()) } returns
            Result.success(createValidDietPlanJson())
        coEvery { mockDao.insert(any()) } returns Unit

        engine.generateWeekPlan(profile).collect {
            val prompt = promptSlot.captured
            assertTrue(
                "Prompt should contain 2085kcal for female, was: ${prompt.take(500)}",
                prompt.contains("2085kcal")
            )
        }
    }

    @Test
    fun `TDEE calculation with lose weight goal subtracts 400`() = runTest {
        val profile = UserProfile(
            gender = Gender.MALE, weightKg = 70f, heightCm = 175, age = 30,
            activityLevel = ActivityLevel.MODERATE, goal = HealthGoal.LOSE_WEIGHT
        )
        // BMR = 1648.75, TDEE = 2555.56, LOSE = 2555.56 - 400 = 2155.56 → 2155
        val promptSlot = slot<String>()
        coEvery { mockAiService.chatJson(any(), capture(promptSlot), any()) } returns
            Result.success(createValidDietPlanJson())
        coEvery { mockDao.insert(any()) } returns Unit

        engine.generateWeekPlan(profile).collect {
            val prompt = promptSlot.captured
            assertTrue(
                "Prompt should contain 2155kcal for LOSE_WEIGHT, was: ${prompt.take(500)}",
                prompt.contains("2155kcal")
            )
        }
    }

    @Test
    fun `TDEE calculation with build muscle goal adds 400`() = runTest {
        val profile = UserProfile(
            gender = Gender.MALE, weightKg = 70f, heightCm = 175, age = 30,
            activityLevel = ActivityLevel.MODERATE, goal = HealthGoal.BUILD_MUSCLE
        )
        // BMR = 1648.75, TDEE = 2555.56, BUILD = 2555.56 + 400 = 2955.56 → 2955
        val promptSlot = slot<String>()
        coEvery { mockAiService.chatJson(any(), capture(promptSlot), any()) } returns
            Result.success(createValidDietPlanJson())
        coEvery { mockDao.insert(any()) } returns Unit

        engine.generateWeekPlan(profile).collect {
            val prompt = promptSlot.captured
            assertTrue(
                "Prompt should contain 2955kcal for BUILD_MUSCLE, was: ${prompt.take(500)}",
                prompt.contains("2955kcal")
            )
        }
    }

    @Test
    fun `TDEE with sedentary activity level uses factor 1_2`() = runTest {
        val profile = UserProfile(
            gender = Gender.MALE, weightKg = 70f, heightCm = 175, age = 30,
            activityLevel = ActivityLevel.SEDENTARY, goal = HealthGoal.EAT_HEALTHY
        )
        // BMR = 1648.75, TDEE = 1648.75 * 1.2 = 1978.5 → 1978
        val promptSlot = slot<String>()
        coEvery { mockAiService.chatJson(any(), capture(promptSlot), any()) } returns
            Result.success(createValidDietPlanJson())
        coEvery { mockDao.insert(any()) } returns Unit

        engine.generateWeekPlan(profile).collect {
            val prompt = promptSlot.captured
            assertTrue(
                "Prompt should contain 1978kcal for SEDENTARY, was: ${prompt.take(500)}",
                prompt.contains("1978kcal")
            )
        }
    }

    @Test
    fun `TDEE uses explicit calorieTarget when set`() = runTest {
        val profile = UserProfile(
            gender = Gender.MALE, weightKg = 70f, heightCm = 175, age = 30,
            calorieTarget = 2200
        )
        val promptSlot = slot<String>()
        coEvery { mockAiService.chatJson(any(), capture(promptSlot), any()) } returns
            Result.success(createValidDietPlanJson())
        coEvery { mockDao.insert(any()) } returns Unit

        engine.generateWeekPlan(profile).collect {
            val prompt = promptSlot.captured
            assertTrue(
                "Prompt should contain explicit 2200kcal, was: ${prompt.take(500)}",
                prompt.contains("2200kcal")
            )
        }
    }

    @Test
    fun `prompt contains user profile data`() = runTest {
        val profile = UserProfile(
            age = 30, gender = Gender.FEMALE, heightCm = 165, weightKg = 55f,
            activityLevel = ActivityLevel.ACTIVE, goal = HealthGoal.MAINTAIN,
            spiceLevel = 2, saltLevel = 0, oilLevel = 1,
            excludedIngredients = setOf("花生", "香菜"),
            allergies = setOf("牛奶"),
            preferredCategories = setOf(RecipeCategory.DIET, RecipeCategory.SOUP),
            maxCookingTimeMin = 45, mealsPerDay = 4, calorieTarget = 2000
        )
        val promptSlot = slot<String>()
        coEvery { mockAiService.chatJson(any(), capture(promptSlot), any()) } returns
            Result.success(createValidDietPlanJson())
        coEvery { mockDao.insert(any()) } returns Unit

        engine.generateWeekPlan(profile).collect {
            val prompt = promptSlot.captured
            assertTrue(prompt.contains("30岁"))
            assertTrue(prompt.contains("女"))
            assertTrue(prompt.contains("165cm"))
            assertTrue("Should contain weight, got: $prompt", prompt.contains("55.0kg") || prompt.contains("55kg"))
            assertTrue(prompt.contains("积极运动"))
            assertTrue(prompt.contains("维持体重"))
            assertTrue(prompt.contains("每日餐数：4"))
            assertTrue(prompt.contains("辣度2"))
            assertTrue(prompt.contains("盐度0"))
            assertTrue(prompt.contains("油量1"))
            assertTrue(prompt.contains("花生"))
            assertTrue(prompt.contains("香菜"))
            assertTrue(prompt.contains("牛奶"))
            assertTrue(prompt.contains("减脂轻食"))
            assertTrue(prompt.contains("汤羹"))
            assertTrue(prompt.contains("45分钟"))
            assertTrue(prompt.contains("2000kcal"))
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // JSON Parsing Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `parseDietPlan round-trip preserves data`() = runTest {
        val profile = UserProfile(age = 30)
        val planJson = createValidDietPlanJson()

        coEvery { mockAiService.chatJson(any(), any(), any()) } returns
            Result.success(planJson)
        coEvery { mockDao.insert(any()) } returns Unit

        engine.generateWeekPlan(profile).collect { plan ->
            assertEquals(1, plan.dailyPlans.size)
            assertEquals(1, plan.dailyPlans[0].dayIndex)
            assertEquals("周一", plan.dailyPlans[0].dayLabel)
            assertEquals(1800, plan.totalCaloriesAvg)
            assertEquals("均衡营养", plan.nutritionSummary)
            assertEquals("清淡饮食", plan.dailyPlans[0].notes)
            assertNotNull(plan.id)
            assertEquals(1, plan.dailyPlans[0].meals.size)
            val meal = plan.dailyPlans[0].meals[0]
            assertEquals(MealType.BREAKFAST, meal.type)
            assertEquals("燕麦粥", meal.recipe.title)
            assertEquals(1, meal.recipe.ingredients.size)
            assertEquals("燕麦", meal.recipe.ingredients[0].name)
            assertEquals("50g", meal.recipe.ingredients[0].amount)
            assertEquals(2, meal.recipe.steps.size)
            assertEquals(15, meal.recipe.cookingTimeMin)
            assertEquals(350, meal.recipe.calories)
            assertEquals(12f, meal.recipe.proteinG)
            assertEquals(45f, meal.recipe.carbsG)
            assertEquals(8f, meal.recipe.fatG)
        }
    }

    @Test
    fun `parseDietPlan handles malformed JSON gracefully`() = runTest {
        val profile = UserProfile()
        coEvery { mockAiService.chatJson(any(), any(), any()) } returns
            Result.success("{bad json")
        coEvery { mockDao.insert(any()) } returns Unit

        engine.generateWeekPlan(profile).collect { plan ->
            // Fallback: empty plan returned, not exception
            assertTrue(plan.dailyPlans.isEmpty())
            assertEquals(0, plan.totalCaloriesAvg)
            assertEquals("", plan.nutritionSummary)
            assertNotNull(plan.id)
        }
    }

    @Test
    fun `parseDietPlan handles empty dailyPlans array`() = runTest {
        val profile = UserProfile()
        val emptyJson = """{"dailyPlans":[],"totalCaloriesAvg":0,"nutritionSummary":""}"""

        coEvery { mockAiService.chatJson(any(), any(), any()) } returns
            Result.success(emptyJson)
        coEvery { mockDao.insert(any()) } returns Unit

        engine.generateWeekPlan(profile).collect { plan ->
            assertEquals(0, plan.dailyPlans.size)
            assertEquals(0, plan.totalCaloriesAvg)
        }
    }

    @Test
    fun `parseDietPlan handles missing meal fields with defaults`() = runTest {
        val profile = UserProfile()
        val minimalMealJson = JSONObject().apply {
            put("dailyPlans", JSONArray().apply {
                put(JSONObject().apply {
                    put("dayIndex", 1)
                    put("dayLabel", "周一")
                    put("meals", JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "LUNCH")
                            put("recipe", JSONObject())  // empty recipe object
                        })
                    })
                })
            })
            put("totalCaloriesAvg", 0)
            put("nutritionSummary", "")
        }.toString()

        coEvery { mockAiService.chatJson(any(), any(), any()) } returns
            Result.success(minimalMealJson)
        coEvery { mockDao.insert(any()) } returns Unit

        engine.generateWeekPlan(profile).collect { plan ->
            assertEquals(1, plan.dailyPlans.size)
            val meal = plan.dailyPlans[0].meals[0]
            assertEquals(MealType.LUNCH, meal.type)
            assertEquals("", meal.recipe.title)
            assertEquals(0, meal.recipe.calories)
            assertTrue(meal.recipe.ingredients.isEmpty())
            assertTrue(meal.recipe.steps.isEmpty())
        }
    }

    @Test
    fun `parseDietPlan handles null meals array by skipping day`() = runTest {
        val profile = UserProfile()
        val noMealsJson = JSONObject().apply {
            put("dailyPlans", JSONArray().apply {
                put(JSONObject().apply {
                    put("dayIndex", 1)
                    put("dayLabel", "周一")
                    // no "meals" key
                })
            })
            put("totalCaloriesAvg", 0)
            put("nutritionSummary", "")
        }.toString()

        coEvery { mockAiService.chatJson(any(), any(), any()) } returns
            Result.success(noMealsJson)
        coEvery { mockDao.insert(any()) } returns Unit

        engine.generateWeekPlan(profile).collect { plan ->
            // Day is skipped because meals array is null/missing
            assertEquals(0, plan.dailyPlans.size)
        }
    }

    @Test
    fun `parseDietPlan coerces invalid meal type to LUNCH`() = runTest {
        val profile = UserProfile()
        val json = JSONObject().apply {
            put("dailyPlans", JSONArray().apply {
                put(JSONObject().apply {
                    put("dayIndex", 1)
                    put("dayLabel", "周一")
                    put("meals", JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "MIDNIGHT_SNACK")  // invalid enum value
                            put("recipe", JSONObject().apply {
                                put("title", "夜宵")
                            })
                        })
                    })
                })
            })
            put("totalCaloriesAvg", 0)
            put("nutritionSummary", "")
        }.toString()

        coEvery { mockAiService.chatJson(any(), any(), any()) } returns
            Result.success(json)
        coEvery { mockDao.insert(any()) } returns Unit

        engine.generateWeekPlan(profile).collect { plan ->
            val meal = plan.dailyPlans[0].meals[0]
            assertEquals(MealType.LUNCH, meal.type)  // fallback
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Error Handling Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `generateWeekPlan propagates AI service errors`() = runTest {
        val profile = UserProfile()
        coEvery { mockAiService.chatJson(any(), any(), any()) } returns
            Result.failure(RuntimeException("API error"))

        try {
            engine.generateWeekPlan(profile).collect { }
            fail("Expected exception was not thrown")
        } catch (e: Exception) {
            assertTrue(
                "Exception message should contain 'API error': ${e.message}",
                e.message?.contains("API error") == true
            )
        }
    }

    @Test
    fun `generateWeekPlan does NOT insert on AI failure`() = runTest {
        val profile = UserProfile()
        coEvery { mockAiService.chatJson(any(), any(), any()) } returns
            Result.failure(RuntimeException("API error"))

        try {
            engine.generateWeekPlan(profile).collect { }
        } catch (_: Exception) {
            // Expected
        }

        coVerify(exactly = 0) { mockDao.insert(any()) }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CRUD Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `deletePlan delegates to DAO`() = runTest {
        coEvery { mockDao.deleteById("test-id") } returns Unit
        engine.deletePlan("test-id")
        coVerify { mockDao.deleteById("test-id") }
    }

    @Test
    fun `getLatestPlan returns null when no plans`() = runTest {
        coEvery { mockDao.getLatest() } returns null
        val result = engine.getLatestPlan()
        assertNull(result)
    }

    @Test
    fun `getLatestPlan returns parsed plan from entity`() = runTest {
        val entity = createMockDietPlanEntity("test-id")
        coEvery { mockDao.getLatest() } returns entity

        val result = engine.getLatestPlan()

        assertNotNull(result)
        assertEquals("test-id", result!!.id)
        assertEquals(1800, result.totalCaloriesAvg)
        assertEquals("均衡营养", result.nutritionSummary)
    }

    @Test
    fun `getSavedPlans maps entities to domain models`() = runTest {
        val entity = createMockDietPlanEntity("plan-1")
        coEvery { mockDao.getAll() } returns flowOf(listOf(entity))

        val plans = engine.getSavedPlans().first()

        assertEquals(1, plans.size)
        assertEquals("plan-1", plans[0].id)
        assertEquals(1800, plans[0].totalCaloriesAvg)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Entity Mapping Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `toEntity and toDomain round-trip preserves profile data`() = runTest {
        val profile = UserProfile(
            age = 30, goal = HealthGoal.LOSE_WEIGHT, gender = Gender.FEMALE,
            heightCm = 165, weightKg = 60f, spiceLevel = 2, saltLevel = 0,
            oilLevel = 1, excludedIngredients = setOf("花生", "海鲜"),
            preferredCategories = setOf(RecipeCategory.DIET, RecipeCategory.HOME),
            maxCookingTimeMin = 30, activityLevel = ActivityLevel.ACTIVE,
            mealsPerDay = 4, calorieTarget = 1800, allergies = setOf("牛奶")
        )
        val planJson = createValidDietPlanJson()

        coEvery { mockAiService.chatJson(any(), any(), any()) } returns
            Result.success(planJson)
        coEvery { mockDao.insert(any()) } returns Unit

        engine.generateWeekPlan(profile).collect { plan ->
            val snapshot = plan.userProfileSnapshot
            assertEquals(30, snapshot.age)
            assertEquals(HealthGoal.LOSE_WEIGHT, snapshot.goal)
            assertEquals(Gender.FEMALE, snapshot.gender)
            assertEquals(165, snapshot.heightCm)
            assertEquals(60f, snapshot.weightKg)
            assertEquals(2, snapshot.spiceLevel)
            assertEquals(0, snapshot.saltLevel)
            assertEquals(1, snapshot.oilLevel)
            assertEquals(setOf("花生", "海鲜"), snapshot.excludedIngredients)
            assertEquals(setOf(RecipeCategory.DIET, RecipeCategory.HOME), snapshot.preferredCategories)
            assertEquals(30, snapshot.maxCookingTimeMin)
            assertEquals(ActivityLevel.ACTIVE, snapshot.activityLevel)
            assertEquals(4, snapshot.mealsPerDay)
            assertEquals(1800, snapshot.calorieTarget)
            assertEquals(setOf("牛奶"), snapshot.allergies)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Multi-day Plan Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `parseDietPlan handles multiple days`() = runTest {
        val profile = UserProfile()
        val multiDayJson = JSONObject().apply {
            put("dailyPlans", JSONArray().apply {
                put(createDayJson(1, "周一", 1800))
                put(createDayJson(2, "周二", 1750))
                put(createDayJson(3, "周三", 1850))
            })
            put("totalCaloriesAvg", 1800)
            put("nutritionSummary", "一周均衡")
        }.toString()

        coEvery { mockAiService.chatJson(any(), any(), any()) } returns
            Result.success(multiDayJson)
        coEvery { mockDao.insert(any()) } returns Unit

        engine.generateWeekPlan(profile).collect { plan ->
            assertEquals(3, plan.dailyPlans.size)
            assertEquals("周一", plan.dailyPlans[0].dayLabel)
            assertEquals("周二", plan.dailyPlans[1].dayLabel)
            assertEquals("周三", plan.dailyPlans[2].dayLabel)
            assertEquals(1800, plan.dailyPlans[0].totalCalories)
            assertEquals(1750, plan.dailyPlans[1].totalCalories)
            assertEquals(1850, plan.dailyPlans[2].totalCalories)
        }
    }

    @Test
    fun `parseDietPlan handles notes field`() = runTest {
        val profile = UserProfile()
        val json = JSONObject().apply {
            put("dailyPlans", JSONArray().apply {
                put(JSONObject().apply {
                    put("dayIndex", 1)
                    put("dayLabel", "周一")
                    put("totalCalories", 1800)
                    put("notes", "今天多摄入蛋白质")
                    put("meals", JSONArray().apply {
                        put(createMealJson("BREAKFAST", "面包"))
                    })
                })
                put(JSONObject().apply {
                    put("dayIndex", 2)
                    put("dayLabel", "周二")
                    put("totalCalories", 1750)
                    // No notes key → null
                    put("meals", JSONArray().apply {
                        put(createMealJson("LUNCH", "沙拉"))
                    })
                })
                put(JSONObject().apply {
                    put("dayIndex", 3)
                    put("dayLabel", "周三")
                    put("totalCalories", 1850)
                    put("notes", "")  // empty → null
                    put("meals", JSONArray().apply {
                        put(createMealJson("DINNER", "意面"))
                    })
                })
            })
            put("totalCaloriesAvg", 1800)
            put("nutritionSummary", "")
        }.toString()

        coEvery { mockAiService.chatJson(any(), any(), any()) } returns
            Result.success(json)
        coEvery { mockDao.insert(any()) } returns Unit

        engine.generateWeekPlan(profile).collect { plan ->
            assertEquals(3, plan.dailyPlans.size)
            assertEquals("今天多摄入蛋白质", plan.dailyPlans[0].notes)
            assertNull(plan.dailyPlans[1].notes)
            assertNull(plan.dailyPlans[2].notes)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helpers for building test JSON
    // ═══════════════════════════════════════════════════════════════════════

    private fun createDayJson(index: Int, label: String, calories: Int): JSONObject {
        return JSONObject().apply {
            put("dayIndex", index)
            put("dayLabel", label)
            put("totalCalories", calories)
            put("meals", JSONArray().apply {
                put(createMealJson("BREAKFAST", "$label 早餐"))
                put(createMealJson("LUNCH", "$label 午餐"))
            })
        }
    }

    private fun createMealJson(type: String, title: String): JSONObject {
        return JSONObject().apply {
            put("type", type)
            put("recipe", JSONObject().apply {
                put("title", title)
                put("ingredients", JSONArray().apply {
                    put(JSONObject().apply {
                        put("name", "主料"); put("amount", "适量")
                    })
                })
                put("steps", JSONArray().apply {
                    put("1. 准备")
                    put("2. 烹饪")
                })
                put("cookingTimeMin", 20)
                put("calories", 400)
                put("proteinG", 15.0)
                put("carbsG", 50.0)
                put("fatG", 10.0)
            })
        }
    }

    private fun createMockDietPlanEntity(id: String): DietPlanEntity {
        val profileJson = JSONObject().apply {
            put("spiceLevel", 0)
            put("saltLevel", 1)
            put("oilLevel", 1)
            put("excludedIngredients", JSONArray())
            put("preferredCategories", JSONArray())
            put("maxCookingTimeMin", 60)
            put("age", 25)
            put("heightCm", 170)
            put("weightKg", 65.0)
            put("gender", "UNSPECIFIED")
            put("activityLevel", "MODERATE")
            put("goal", "EAT_HEALTHY")
            put("mealsPerDay", 3)
            put("allergies", JSONArray())
        }.toString()

        val dailyPlansJson = JSONArray().apply {
            put(JSONObject().apply {
                put("dayIndex", 1)
                put("dayLabel", "周一")
                put("totalCalories", 1800)
                put("notes", "清淡饮食")
                put("meals", JSONArray().apply {
                    put(createMealJson("BREAKFAST", "燕麦粥"))
                })
            })
        }.toString()

        return DietPlanEntity(
            id = id,
            generatedAt = System.currentTimeMillis(),
            profileSnapshotJson = profileJson,
            dailyPlansJson = dailyPlansJson,
            totalCaloriesAvg = 1800,
            nutritionSummary = "均衡营养"
        )
    }
}
