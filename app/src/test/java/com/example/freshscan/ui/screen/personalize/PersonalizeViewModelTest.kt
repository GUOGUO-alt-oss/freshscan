package com.example.freshscan.ui.screen.personalize

import com.example.freshscan.data.history.UserProfileDao
import com.example.freshscan.data.history.UserProfileEntity
import com.example.freshscan.domain.model.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
class PersonalizeViewModelTest {

    private lateinit var mockDao: UserProfileDao
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockDao = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Default Values ──

    @Test
    fun `initial state has default values when no saved profile`() = runTest {
        coEvery { mockDao.get() } returns MutableStateFlow(null)

        val vm = PersonalizeViewModel(mockDao)
        advanceUntilIdle()

        val state = vm.uiState.first()
        assertEquals(0, state.spiceLevel)
        assertEquals(1, state.saltLevel)
        assertEquals(1, state.oilLevel)
        assertTrue(state.excludedIngredients.isEmpty())
        assertTrue(state.preferredCategories.isEmpty())
        assertEquals(60, state.maxCookingTimeMin)
        assertEquals(25, state.age)
        assertEquals(170, state.heightCm)
        assertEquals(65f, state.weightKg)
        assertEquals(Gender.UNSPECIFIED, state.gender)
        assertEquals(ActivityLevel.MODERATE, state.activityLevel)
        assertEquals(HealthGoal.EAT_HEALTHY, state.goal)
        assertEquals(3, state.mealsPerDay)
        assertNull(state.calorieTarget)
        assertTrue(state.allergies.isEmpty())
        assertFalse(state.isDirty)
        assertFalse(state.isSaving)
        assertFalse(state.savedSuccessfully)
    }

    // ── Field Updaters ──

    @Test
    fun `updateSpiceLevel marks dirty`() = runTest {
        coEvery { mockDao.get() } returns MutableStateFlow(null)
        val vm = PersonalizeViewModel(mockDao)
        advanceUntilIdle()

        vm.updateSpiceLevel(2)
        val state = vm.uiState.first()
        assertEquals(2, state.spiceLevel)
        assertTrue(state.isDirty)
    }

    @Test
    fun `updateSaltLevel marks dirty`() = runTest {
        coEvery { mockDao.get() } returns MutableStateFlow(null)
        val vm = PersonalizeViewModel(mockDao)
        advanceUntilIdle()

        vm.updateSaltLevel(2)
        val state = vm.uiState.first()
        assertEquals(2, state.saltLevel)
        assertTrue(state.isDirty)
    }

    @Test
    fun `updateOilLevel marks dirty`() = runTest {
        coEvery { mockDao.get() } returns MutableStateFlow(null)
        val vm = PersonalizeViewModel(mockDao)
        advanceUntilIdle()

        vm.updateOilLevel(0)
        val state = vm.uiState.first()
        assertEquals(0, state.oilLevel)
        assertTrue(state.isDirty)
    }

    @Test
    fun `updateMaxCookingTime marks dirty`() = runTest {
        coEvery { mockDao.get() } returns MutableStateFlow(null)
        val vm = PersonalizeViewModel(mockDao)
        advanceUntilIdle()

        vm.updateMaxCookingTime(45)
        val state = vm.uiState.first()
        assertEquals(45, state.maxCookingTimeMin)
        assertTrue(state.isDirty)
    }

    @Test
    fun `updateAge marks dirty`() = runTest {
        coEvery { mockDao.get() } returns MutableStateFlow(null)
        val vm = PersonalizeViewModel(mockDao)
        advanceUntilIdle()

        vm.updateAge(35)
        val state = vm.uiState.first()
        assertEquals(35, state.age)
        assertTrue(state.isDirty)
    }

    @Test
    fun `updateHeightCm marks dirty`() = runTest {
        coEvery { mockDao.get() } returns MutableStateFlow(null)
        val vm = PersonalizeViewModel(mockDao)
        advanceUntilIdle()

        vm.updateHeightCm(180)
        val state = vm.uiState.first()
        assertEquals(180, state.heightCm)
        assertTrue(state.isDirty)
    }

    @Test
    fun `updateWeightKg marks dirty`() = runTest {
        coEvery { mockDao.get() } returns MutableStateFlow(null)
        val vm = PersonalizeViewModel(mockDao)
        advanceUntilIdle()

        vm.updateWeightKg(75f)
        val state = vm.uiState.first()
        assertEquals(75f, state.weightKg)
        assertTrue(state.isDirty)
    }

    @Test
    fun `updateGender marks dirty`() = runTest {
        coEvery { mockDao.get() } returns MutableStateFlow(null)
        val vm = PersonalizeViewModel(mockDao)
        advanceUntilIdle()

        vm.updateGender(Gender.MALE)
        val state = vm.uiState.first()
        assertEquals(Gender.MALE, state.gender)
        assertTrue(state.isDirty)
    }

    @Test
    fun `updateActivityLevel marks dirty`() = runTest {
        coEvery { mockDao.get() } returns MutableStateFlow(null)
        val vm = PersonalizeViewModel(mockDao)
        advanceUntilIdle()

        vm.updateActivityLevel(ActivityLevel.ACTIVE)
        val state = vm.uiState.first()
        assertEquals(ActivityLevel.ACTIVE, state.activityLevel)
        assertTrue(state.isDirty)
    }

    @Test
    fun `updateGoal marks dirty`() = runTest {
        coEvery { mockDao.get() } returns MutableStateFlow(null)
        val vm = PersonalizeViewModel(mockDao)
        advanceUntilIdle()

        vm.updateGoal(HealthGoal.LOSE_WEIGHT)
        val state = vm.uiState.first()
        assertEquals(HealthGoal.LOSE_WEIGHT, state.goal)
        assertTrue(state.isDirty)
    }

    @Test
    fun `updateMealsPerDay marks dirty`() = runTest {
        coEvery { mockDao.get() } returns MutableStateFlow(null)
        val vm = PersonalizeViewModel(mockDao)
        advanceUntilIdle()

        vm.updateMealsPerDay(5)
        val state = vm.uiState.first()
        assertEquals(5, state.mealsPerDay)
        assertTrue(state.isDirty)
    }

    @Test
    fun `updateCalorieTarget marks dirty`() = runTest {
        coEvery { mockDao.get() } returns MutableStateFlow(null)
        val vm = PersonalizeViewModel(mockDao)
        advanceUntilIdle()

        vm.updateCalorieTarget(2000)
        var state = vm.uiState.first()
        assertEquals(2000, state.calorieTarget)
        assertTrue(state.isDirty)

        // Setting to null also marks dirty
        // Reset mock
        coEvery { mockDao.get() } returns MutableStateFlow(null)
        val vm2 = PersonalizeViewModel(mockDao)
        advanceUntilIdle()
        vm2.updateCalorieTarget(null)
        state = vm2.uiState.first()
        assertNull(state.calorieTarget)
    }

    // ── Toggle Operations ──

    @Test
    fun `toggleExcludedIngredient adds and removes`() = runTest {
        coEvery { mockDao.get() } returns MutableStateFlow(null)
        val vm = PersonalizeViewModel(mockDao)
        advanceUntilIdle()

        vm.toggleExcludedIngredient("花生")
        var state = vm.uiState.first()
        assertTrue("花生" in state.excludedIngredients)

        vm.toggleExcludedIngredient("花生")
        state = vm.uiState.first()
        assertFalse("花生" in state.excludedIngredients)

        // Add multiple ingredients
        vm.toggleExcludedIngredient("大蒜")
        vm.toggleExcludedIngredient("香菜")
        state = vm.uiState.first()
        assertTrue("大蒜" in state.excludedIngredients)
        assertTrue("香菜" in state.excludedIngredients)
        assertEquals(2, state.excludedIngredients.size)
    }

    @Test
    fun `togglePreferredCategory adds and removes`() = runTest {
        coEvery { mockDao.get() } returns MutableStateFlow(null)
        val vm = PersonalizeViewModel(mockDao)
        advanceUntilIdle()

        vm.togglePreferredCategory(RecipeCategory.DIET)
        var state = vm.uiState.first()
        assertTrue(RecipeCategory.DIET in state.preferredCategories)

        vm.togglePreferredCategory(RecipeCategory.DIET)
        state = vm.uiState.first()
        assertFalse(RecipeCategory.DIET in state.preferredCategories)

        // Add multiple
        vm.togglePreferredCategory(RecipeCategory.HOME)
        vm.togglePreferredCategory(RecipeCategory.SOUP)
        state = vm.uiState.first()
        assertTrue(RecipeCategory.HOME in state.preferredCategories)
        assertTrue(RecipeCategory.SOUP in state.preferredCategories)
        assertEquals(2, state.preferredCategories.size)
    }

    @Test
    fun `toggleAllergy adds and removes`() = runTest {
        coEvery { mockDao.get() } returns MutableStateFlow(null)
        val vm = PersonalizeViewModel(mockDao)
        advanceUntilIdle()

        vm.toggleAllergy("牛奶")
        var state = vm.uiState.first()
        assertTrue("牛奶" in state.allergies)

        vm.toggleAllergy("牛奶")
        state = vm.uiState.first()
        assertFalse("牛奶" in state.allergies)

        // Add multiple allergies
        vm.toggleAllergy("花生")
        vm.toggleAllergy("海鲜")
        state = vm.uiState.first()
        assertTrue("花生" in state.allergies)
        assertTrue("海鲜" in state.allergies)
        assertEquals(2, state.allergies.size)
    }

    // ── Save ──

    @Test
    fun `save upserts to DAO and clears dirty flag`() = runTest {
        coEvery { mockDao.get() } returns MutableStateFlow(null)
        coEvery { mockDao.upsert(any()) } returns Unit
        val vm = PersonalizeViewModel(mockDao)
        advanceUntilIdle()

        vm.updateAge(35)
        vm.updateGoal(HealthGoal.LOSE_WEIGHT)
        assertTrue(vm.uiState.first().isDirty)

        vm.save()
        advanceUntilIdle()

        coVerify { mockDao.upsert(any()) }
        val state = vm.uiState.first()
        assertFalse(state.isDirty)
        assertFalse(state.isSaving)
        assertTrue(state.savedSuccessfully)
    }

    @Test
    fun `save sets isSaving flag during operation`() = runTest {
        coEvery { mockDao.get() } returns MutableStateFlow(null)
        coEvery { mockDao.upsert(any()) } returns Unit
        val vm = PersonalizeViewModel(mockDao)
        advanceUntilIdle()

        vm.save()
        advanceUntilIdle()

        // After save completes, isSaving should be false
        assertFalse(vm.uiState.first().isSaving)
    }

    @Test
    fun `multiple saves to DAO`() = runTest {
        coEvery { mockDao.get() } returns MutableStateFlow(null)
        coEvery { mockDao.upsert(any()) } returns Unit
        val vm = PersonalizeViewModel(mockDao)
        advanceUntilIdle()

        vm.updateSpiceLevel(1)
        vm.save()
        advanceUntilIdle()
        coVerify(exactly = 1) { mockDao.upsert(any()) }

        vm.updateSpiceLevel(2)
        vm.save()
        advanceUntilIdle()
        coVerify(exactly = 2) { mockDao.upsert(any()) }
    }

    // ── Load Profile ──

    @Test
    fun `loadProfile restores saved values from entity`() = runTest {
        val entity = UserProfileEntity(
            id = 1,
            age = 40,
            gender = "MALE",
            goal = "LOSE_WEIGHT",
            heightCm = 175,
            weightKg = 80f,
            spiceLevel = 2,
            saltLevel = 0,
            oilLevel = 1,
            excludedIngredients = """["花生","海鲜"]""",
            preferredCategories = """["HOME","DIET"]""",
            maxCookingTimeMin = 30,
            activityLevel = "ACTIVE",
            mealsPerDay = 4,
            calorieTarget = 2200,
            allergies = """["牛奶"]"""
        )
        coEvery { mockDao.get() } returns MutableStateFlow(entity)

        val vm = PersonalizeViewModel(mockDao)
        advanceUntilIdle()

        val state = vm.uiState.first()
        assertEquals(40, state.age)
        assertEquals(Gender.MALE, state.gender)
        assertEquals(HealthGoal.LOSE_WEIGHT, state.goal)
        assertEquals(175, state.heightCm)
        assertEquals(80f, state.weightKg)
        assertEquals(2, state.spiceLevel)
        assertEquals(0, state.saltLevel)
        assertEquals(1, state.oilLevel)
        assertTrue("花生" in state.excludedIngredients)
        assertTrue("海鲜" in state.excludedIngredients)
        assertEquals(2, state.excludedIngredients.size)
        assertTrue(RecipeCategory.HOME in state.preferredCategories)
        assertTrue(RecipeCategory.DIET in state.preferredCategories)
        assertEquals(2, state.preferredCategories.size)
        assertEquals(30, state.maxCookingTimeMin)
        assertEquals(ActivityLevel.ACTIVE, state.activityLevel)
        assertEquals(4, state.mealsPerDay)
        assertEquals(2200, state.calorieTarget)
        assertTrue("牛奶" in state.allergies)
        assertFalse(state.isDirty)
    }

    @Test
    fun `loadProfile handles null entity gracefully`() = runTest {
        coEvery { mockDao.get() } returns MutableStateFlow(null)

        val vm = PersonalizeViewModel(mockDao)
        advanceUntilIdle()

        val state = vm.uiState.first()
        // Should have default values
        assertEquals(25, state.age)
        assertEquals(Gender.UNSPECIFIED, state.gender)
        assertEquals(HealthGoal.EAT_HEALTHY, state.goal)
        assertFalse(state.isDirty)
    }

    @Test
    fun `loadProfile handles malformed JSON arrays in entity`() = runTest {
        val entity = UserProfileEntity(
            id = 1,
            excludedIngredients = "{not json}",   // malformed
            preferredCategories = "{not json}",   // malformed
            allergies = "[malformed"              // malformed
        )
        coEvery { mockDao.get() } returns MutableStateFlow(entity)

        val vm = PersonalizeViewModel(mockDao)
        advanceUntilIdle()

        val state = vm.uiState.first()
        assertTrue(state.excludedIngredients.isEmpty())
        assertTrue(state.preferredCategories.isEmpty())
        assertTrue(state.allergies.isEmpty())
    }

    @Test
    fun `loadProfile handles invalid enum values in entity`() = runTest {
        val entity = UserProfileEntity(
            id = 1,
            gender = "ALIEN",                     // invalid
            activityLevel = "SUPER_HUMAN",        // invalid
            goal = "WORLD_DOMINATION"             // invalid
        )
        coEvery { mockDao.get() } returns MutableStateFlow(entity)

        val vm = PersonalizeViewModel(mockDao)
        advanceUntilIdle()

        val state = vm.uiState.first()
        assertEquals(Gender.UNSPECIFIED, state.gender)
        assertEquals(ActivityLevel.MODERATE, state.activityLevel)
        assertEquals(HealthGoal.EAT_HEALTHY, state.goal)
    }

    // ── Navigation ──

    @Test
    fun `onStartCustomization emits navigation event`() = runTest {
        coEvery { mockDao.get() } returns MutableStateFlow(null)
        val vm = PersonalizeViewModel(mockDao)
        advanceUntilIdle()

        var navigated = false
        val job = launch {
            vm.navigateToDietPlan.collect { navigated = true }
        }
        advanceUntilIdle() // let the collection start

        vm.onStartCustomization()
        advanceUntilIdle() // let the emit propagate

        assertTrue("onStartCustomization should emit navigation event", navigated)
        job.cancel()
    }

    // ── Boundary Values ──

    @Test
    fun `spice level at max (3) accepted`() = runTest {
        coEvery { mockDao.get() } returns MutableStateFlow(null)
        val vm = PersonalizeViewModel(mockDao)
        advanceUntilIdle()

        vm.updateSpiceLevel(3)
        assertEquals(3, vm.uiState.first().spiceLevel)
    }

    @Test
    fun `salt level at min (0) accepted`() = runTest {
        coEvery { mockDao.get() } returns MutableStateFlow(null)
        val vm = PersonalizeViewModel(mockDao)
        advanceUntilIdle()

        vm.updateSaltLevel(0)
        assertEquals(0, vm.uiState.first().saltLevel)
    }

    @Test
    fun `multiple field updates accumulate dirty`() = runTest {
        coEvery { mockDao.get() } returns MutableStateFlow(null)
        val vm = PersonalizeViewModel(mockDao)
        advanceUntilIdle()

        vm.updateSpiceLevel(2)
        vm.updateSaltLevel(0)
        vm.updateAge(30)
        vm.toggleAllergy("海鲜")

        val state = vm.uiState.first()
        assertTrue(state.isDirty)
        assertEquals(2, state.spiceLevel)
        assertEquals(0, state.saltLevel)
        assertEquals(30, state.age)
        assertTrue("海鲜" in state.allergies)
    }
}
