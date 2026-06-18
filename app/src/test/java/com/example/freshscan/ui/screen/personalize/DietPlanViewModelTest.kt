package com.example.freshscan.ui.screen.personalize

import com.example.freshscan.data.diet.DietPlanEngine
import com.example.freshscan.data.history.ShoppingItemEntity
import com.example.freshscan.data.history.ShoppingListDao
import com.example.freshscan.data.history.UserProfileDao
import com.example.freshscan.data.history.UserProfileEntity
import com.example.freshscan.domain.model.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DietPlanViewModelTest {

    private lateinit var mockEngine: DietPlanEngine
    private lateinit var mockProfileDao: UserProfileDao
    private lateinit var mockShoppingDao: ShoppingListDao
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockEngine = mockk()
        mockProfileDao = mockk()
        mockShoppingDao = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Initial State / Lifecycle Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `initial state is Idle before generatePlan runs`() = runTest {
        // Don't provide a profile flow — the VM's init will suspend on first()
        // We can check the state before the generatePlan coroutine runs
        coEvery { mockProfileDao.get() } returns MutableStateFlow(null)

        val vm = DietPlanViewModel(mockEngine, mockProfileDao, mockShoppingDao)

        // The init starts generatePlan in viewModelScope, but in runTest
        // we can check the immediate state (which starts as Idle)
        // After advancing, it should have gone through Generating → Error
        val initialState = vm.uiState.value
        assertTrue(
            "Initial state should be Idle or Generating, was: $initialState",
            initialState is DietPlanUiState.Idle
        )
    }

    @Test
    fun `null profile leads to error state`() = runTest {
        coEvery { mockProfileDao.get() } returns MutableStateFlow(null)

        val vm = DietPlanViewModel(mockEngine, mockProfileDao, mockShoppingDao)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("Expected Error state, got: $state", state is DietPlanUiState.Error)
        val error = state as DietPlanUiState.Error
        assertTrue(
            "Error message should mention profile, got: ${error.message}",
            error.message.contains("档案") || error.message.contains("profile")
        )
    }

    @Test
    fun `valid profile leads to success state`() = runTest {
        val plan = createMockDietPlan()
        val entity = UserProfileEntity(id = 1, age = 30, goal = "EAT_HEALTHY")

        coEvery { mockProfileDao.get() } returns MutableStateFlow(entity)
        coEvery { mockEngine.generateWeekPlan(any()) } returns flowOf(plan)

        val vm = DietPlanViewModel(mockEngine, mockProfileDao, mockShoppingDao)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("Expected Success state, got: $state", state is DietPlanUiState.Success)
        val success = state as DietPlanUiState.Success
        assertEquals("test-id", success.plan.id)
        assertEquals(0, success.selectedDayIndex)
        assertEquals(1, success.plan.dailyPlans.size)
    }

    @Test
    fun `engine exception leads to error state`() = runTest {
        val entity = UserProfileEntity(id = 1, age = 30, goal = "EAT_HEALTHY")

        coEvery { mockProfileDao.get() } returns MutableStateFlow(entity)
        coEvery { mockEngine.generateWeekPlan(any()) } throws RuntimeException("AI connection failed")

        val vm = DietPlanViewModel(mockEngine, mockProfileDao, mockShoppingDao)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("Expected Error state, got: $state", state is DietPlanUiState.Error)
        val error = state as DietPlanUiState.Error
        assertEquals("AI connection failed", error.message)
    }

    @Test
    fun `generatePlan can be called to retry after error`() = runTest {
        val entity = UserProfileEntity(id = 1, age = 30, goal = "EAT_HEALTHY")
        val plan = createMockDietPlan(calories = 2000)

        // First call fails
        coEvery { mockProfileDao.get() } returns MutableStateFlow(entity)
        coEvery { mockEngine.generateWeekPlan(any()) } throws RuntimeException("AI error")

        val vm = DietPlanViewModel(mockEngine, mockProfileDao, mockShoppingDao)
        advanceUntilIdle()

        assertTrue(vm.uiState.value is DietPlanUiState.Error)

        // Second call succeeds
        coEvery { mockEngine.generateWeekPlan(any()) } returns flowOf(plan)
        vm.generatePlan()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("Expected Success after retry, got: $state", state is DietPlanUiState.Success)
        assertEquals(2000, (state as DietPlanUiState.Success).plan.totalCaloriesAvg)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // selectDay Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `selectDay changes selected day index`() = runTest {
        val plan = createMockDietPlan()
        val entity = UserProfileEntity(id = 1, age = 30, goal = "EAT_HEALTHY")

        coEvery { mockProfileDao.get() } returns MutableStateFlow(entity)
        coEvery { mockEngine.generateWeekPlan(any()) } returns flowOf(plan)

        val vm = DietPlanViewModel(mockEngine, mockProfileDao, mockShoppingDao)
        advanceUntilIdle()

        // Initially day 0
        assertEquals(0, (vm.uiState.value as DietPlanUiState.Success).selectedDayIndex)

        vm.selectDay(1)
        assertEquals(1, (vm.uiState.value as DietPlanUiState.Success).selectedDayIndex)

        vm.selectDay(3)
        assertEquals(3, (vm.uiState.value as DietPlanUiState.Success).selectedDayIndex)
    }

    @Test
    fun `selectDay is no-op on non-Success state`() = runTest {
        coEvery { mockProfileDao.get() } returns MutableStateFlow(null)

        val vm = DietPlanViewModel(mockEngine, mockProfileDao, mockShoppingDao)
        advanceUntilIdle()

        // State is Error
        assertTrue(vm.uiState.value is DietPlanUiState.Error)

        // selectDay should not crash
        vm.selectDay(5)
        // State should remain Error
        assertTrue(vm.uiState.value is DietPlanUiState.Error)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // addMealToShoppingList Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `addMealToShoppingList inserts each ingredient`() = runTest {
        val plan = createMockDietPlan()
        val entity = UserProfileEntity(id = 1, age = 30, goal = "EAT_HEALTHY")

        coEvery { mockProfileDao.get() } returns MutableStateFlow(entity)
        coEvery { mockEngine.generateWeekPlan(any()) } returns flowOf(plan)
        coEvery { mockShoppingDao.insert(any()) } returns 1L

        val vm = DietPlanViewModel(mockEngine, mockProfileDao, mockShoppingDao)
        advanceUntilIdle()

        val meal = plan.dailyPlans[0].meals[0]
        vm.addMealToShoppingList(meal)
        advanceUntilIdle()

        coVerify(atLeast = 1) { mockShoppingDao.insert(any()) }
    }

    @Test
    fun `addMealToShoppingList inserts correct ingredient data`() = runTest {
        val plan = createMockDietPlan()
        val entity = UserProfileEntity(id = 1, age = 30, goal = "EAT_HEALTHY")

        coEvery { mockProfileDao.get() } returns MutableStateFlow(entity)
        coEvery { mockEngine.generateWeekPlan(any()) } returns flowOf(plan)
        coEvery { mockShoppingDao.insert(any()) } returns 1L

        val vm = DietPlanViewModel(mockEngine, mockProfileDao, mockShoppingDao)
        advanceUntilIdle()

        val meal = plan.dailyPlans[0].meals[0]
        vm.addMealToShoppingList(meal)
        advanceUntilIdle()

        coVerify {
            mockShoppingDao.insert(match { item ->
                item.name == "燕麦" && item.amount == "50g"
            })
        }
    }

    @Test
    fun `addMealToShoppingList handles meal with multiple ingredients`() = runTest {
        val meal = Meal(
            type = MealType.LUNCH,
            recipe = DietRecipe(
                title = "沙拉",
                ingredients = listOf(
                    Ingredient("生菜", "200g"),
                    Ingredient("番茄", "2个"),
                    Ingredient("黄瓜", "1根"),
                    Ingredient("橄榄油", "适量")
                ),
                steps = listOf("混合"),
                cookingTimeMin = 5,
                calories = 200,
                proteinG = 5f,
                carbsG = 15f,
                fatG = 12f
            )
        )
        val plan = DietPlan(
            id = "multi-ing",
            generatedAt = System.currentTimeMillis(),
            userProfileSnapshot = UserProfile(),
            dailyPlans = listOf(
                DailyMealPlan(dayIndex = 1, dayLabel = "周一", totalCalories = 200, meals = listOf(meal))
            ),
            totalCaloriesAvg = 200,
            nutritionSummary = ""
        )
        val entity = UserProfileEntity(id = 1, age = 30, goal = "EAT_HEALTHY")

        coEvery { mockProfileDao.get() } returns MutableStateFlow(entity)
        coEvery { mockEngine.generateWeekPlan(any()) } returns flowOf(plan)
        coEvery { mockShoppingDao.insert(any()) } returnsMany listOf(1L, 2L, 3L, 4L)

        val vm = DietPlanViewModel(mockEngine, mockProfileDao, mockShoppingDao)
        advanceUntilIdle()

        vm.addMealToShoppingList(meal)
        advanceUntilIdle()

        coVerify(exactly = 4) { mockShoppingDao.insert(any()) }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // addAllToShoppingList Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `addAllToShoppingList deduplicates ingredients by name`() = runTest {
        // Create a plan where the same ingredient appears in multiple meals
        val milk = Ingredient("牛奶", "200ml")
        val egg = Ingredient("鸡蛋", "2个")
        val meal1 = createMealWithIngredients("早餐", listOf(milk, egg))
        val meal2 = createMealWithIngredients("晚餐", listOf(egg, Ingredient("牛肉", "200g")))

        val plan = DietPlan(
            id = "dup-test",
            generatedAt = System.currentTimeMillis(),
            userProfileSnapshot = UserProfile(),
            dailyPlans = listOf(
                DailyMealPlan(dayIndex = 1, dayLabel = "周一", totalCalories = 1800,
                    meals = listOf(meal1, meal2))
            ),
            totalCaloriesAvg = 1800,
            nutritionSummary = ""
        )
        val entity = UserProfileEntity(id = 1, age = 30, goal = "EAT_HEALTHY")

        coEvery { mockProfileDao.get() } returns MutableStateFlow(entity)
        coEvery { mockEngine.generateWeekPlan(any()) } returns flowOf(plan)
        coEvery { mockShoppingDao.insert(any()) } returns 1L

        val vm = DietPlanViewModel(mockEngine, mockProfileDao, mockShoppingDao)
        advanceUntilIdle()

        vm.addAllToShoppingList()
        advanceUntilIdle()

        // Should insert exactly 3 unique ingredients (milk, egg, beef)
        // "鸡蛋" appears twice but should only be inserted once
        coVerify(exactly = 3) { mockShoppingDao.insert(any()) }
    }

    @Test
    fun `addAllToShoppingList is no-op on non-Success state`() = runTest {
        coEvery { mockProfileDao.get() } returns MutableStateFlow(null)

        val vm = DietPlanViewModel(mockEngine, mockProfileDao, mockShoppingDao)
        advanceUntilIdle()

        // State is Error
        assertTrue(vm.uiState.value is DietPlanUiState.Error)

        vm.addAllToShoppingList()
        advanceUntilIdle()

        // Should not have called insert
        coVerify(exactly = 0) { mockShoppingDao.insert(any()) }
    }

    @Test
    fun `addAllToShoppingList handles plan with empty days`() = runTest {
        val plan = DietPlan(
            id = "empty-test",
            generatedAt = System.currentTimeMillis(),
            userProfileSnapshot = UserProfile(),
            dailyPlans = emptyList(),
            totalCaloriesAvg = 0,
            nutritionSummary = ""
        )
        val entity = UserProfileEntity(id = 1, age = 30, goal = "EAT_HEALTHY")

        coEvery { mockProfileDao.get() } returns MutableStateFlow(entity)
        coEvery { mockEngine.generateWeekPlan(any()) } returns flowOf(plan)

        val vm = DietPlanViewModel(mockEngine, mockProfileDao, mockShoppingDao)
        advanceUntilIdle()

        vm.addAllToShoppingList()
        advanceUntilIdle()

        // No ingredients to insert
        coVerify(exactly = 0) { mockShoppingDao.insert(any()) }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Profile Parsing Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `profile from entity uses all fields`() = runTest {
        val entity = UserProfileEntity(
            id = 1,
            age = 45,
            gender = "FEMALE",
            goal = "LOSE_WEIGHT",
            heightCm = 160,
            weightKg = 55f,
            spiceLevel = 3,
            saltLevel = 0,
            oilLevel = 1,
            excludedIngredients = """["花生","香菜"]""",
            preferredCategories = """["DIET","COLD"]""",
            maxCookingTimeMin = 30,
            activityLevel = "LIGHT",
            mealsPerDay = 5,
            calorieTarget = 1500,
            allergies = """["虾","蟹"]"""
        )
        val plan = createMockDietPlan()

        coEvery { mockProfileDao.get() } returns MutableStateFlow(entity)
        coEvery { mockEngine.generateWeekPlan(any()) } returns flowOf(plan)

        val vm = DietPlanViewModel(mockEngine, mockProfileDao, mockShoppingDao)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("Expected Success state", state is DietPlanUiState.Success)
        coVerify {
            mockEngine.generateWeekPlan(match { profile ->
                profile.age == 45 &&
                    profile.gender == Gender.FEMALE &&
                    profile.goal == HealthGoal.LOSE_WEIGHT &&
                    profile.heightCm == 160 &&
                    profile.weightKg == 55f &&
                    profile.spiceLevel == 3 &&
                    profile.saltLevel == 0 &&
                    profile.oilLevel == 1 &&
                    profile.excludedIngredients == setOf("花生", "香菜") &&
                    profile.preferredCategories == setOf(RecipeCategory.DIET, RecipeCategory.COLD) &&
                    profile.maxCookingTimeMin == 30 &&
                    profile.activityLevel == ActivityLevel.LIGHT &&
                    profile.mealsPerDay == 5 &&
                    profile.calorieTarget == 1500 &&
                    profile.allergies == setOf("虾", "蟹")
            })
        }
    }

    @Test
    fun `profile from entity with invalid enums uses defaults`() = runTest {
        val entity = UserProfileEntity(
            id = 1,
            gender = "ALIEN",
            activityLevel = "SUPER_HUMAN",
            goal = "WORLD_DOMINATION"
        )
        val plan = createMockDietPlan()

        coEvery { mockProfileDao.get() } returns MutableStateFlow(entity)
        coEvery { mockEngine.generateWeekPlan(any()) } returns flowOf(plan)

        val vm = DietPlanViewModel(mockEngine, mockProfileDao, mockShoppingDao)
        advanceUntilIdle()

        coVerify {
            mockEngine.generateWeekPlan(match { profile ->
                profile.gender == Gender.UNSPECIFIED &&
                    profile.activityLevel == ActivityLevel.MODERATE &&
                    profile.goal == HealthGoal.EAT_HEALTHY
            })
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════

    private fun createMockDietPlan(calories: Int = 1800): DietPlan {
        return DietPlan(
            id = "test-id",
            generatedAt = System.currentTimeMillis(),
            userProfileSnapshot = UserProfile(age = 30),
            dailyPlans = listOf(
                DailyMealPlan(
                    dayIndex = 1,
                    dayLabel = "周一",
                    totalCalories = calories,
                    meals = listOf(
                        Meal(
                            type = MealType.BREAKFAST,
                            recipe = DietRecipe(
                                title = "燕麦粥",
                                ingredients = listOf(Ingredient("燕麦", "50g")),
                                steps = listOf("1. 煮水", "2. 加入燕麦"),
                                cookingTimeMin = 15,
                                calories = 350,
                                proteinG = 12f,
                                carbsG = 45f,
                                fatG = 8f
                            )
                        )
                    )
                )
            ),
            totalCaloriesAvg = calories,
            nutritionSummary = "均衡营养"
        )
    }

    private fun createMealWithIngredients(label: String, ingredients: List<Ingredient>): Meal {
        return Meal(
            type = MealType.LUNCH,
            recipe = DietRecipe(
                title = label,
                ingredients = ingredients,
                steps = listOf("1. 准备"),
                cookingTimeMin = 10,
                calories = 300,
                proteinG = 15f,
                carbsG = 30f,
                fatG = 10f
            )
        )
    }
}
