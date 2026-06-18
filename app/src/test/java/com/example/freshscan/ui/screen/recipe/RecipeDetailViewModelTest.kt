package com.example.freshscan.ui.screen.recipe

import androidx.lifecycle.SavedStateHandle
import com.example.freshscan.data.history.FavoriteRecipeDao
import com.example.freshscan.data.history.FavoriteRecipeEntity
import com.example.freshscan.data.history.ShoppingItemEntity
import com.example.freshscan.data.history.ShoppingListDao
import com.example.freshscan.data.recipe.RecipeEngine
import com.example.freshscan.domain.model.*
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecipeDetailViewModelTest {

    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var recipeEngine: RecipeEngine
    private lateinit var favoriteRecipeDao: FavoriteRecipeDao
    private lateinit var shoppingListDao: ShoppingListDao
    private lateinit var viewModel: RecipeDetailViewModel

    private val testRecipe = Recipe(
        id = "test_recipe_1",
        title = "番茄炒蛋",
        category = RecipeCategory.HOME,
        difficulty = RecipeDifficulty.EASY,
        cookingTimeMin = 10,
        matchIngredients = listOf("番茄", "鸡蛋"),
        allIngredients = listOf(
            Ingredient("番茄", "2个"),
            Ingredient("鸡蛋", "3个")
        ),
        steps = listOf(
            CookingStep(1, "切番茄", 0),
            CookingStep(2, "炒鸡蛋", 60),
            CookingStep(3, "混合翻炒", 30)
        ),
        nutrition = Nutrition(180, 12, 8, 14, 2),
        tags = listOf("家常菜"),
        tips = "选熟透的番茄",
        imageAsset = "",
        thumbnailAsset = ""
    )

    @Before
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        savedStateHandle = SavedStateHandle(mapOf("recipeId" to "test_recipe_1"))
        recipeEngine = mockk(relaxed = true)
        favoriteRecipeDao = mockk(relaxed = true)
        shoppingListDao = mockk(relaxed = true)

        // Default stubs matching actual RecipeDetailViewModel.init behavior
        coEvery { recipeEngine.getRecipeById("test_recipe_1") } returns testRecipe
        every { favoriteRecipeDao.isFavorited("test_recipe_1") } returns false
        coEvery { favoriteRecipeDao.getById("test_recipe_1") } returns null
        every { favoriteRecipeDao.getAllFlow() } returns flowOf(emptyList())
        every { shoppingListDao.getAll() } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ─── Recipe Loading ───

    @Test
    fun `given valid recipeId when initialized then loads recipe`() = runTest {
        viewModel = RecipeDetailViewModel(
            savedStateHandle, recipeEngine, favoriteRecipeDao, shoppingListDao
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull("Recipe should be loaded", state.recipe)
        assertEquals("番茄炒蛋", state.recipe?.title)
        assertFalse("Should not be loading", state.isLoading)
    }

    @Test
    fun `given missing recipeId when initialized then recipe is null`() = runTest {
        coEvery { recipeEngine.getRecipeById("missing") } returns null
        val handle = SavedStateHandle(mapOf("recipeId" to "missing"))

        viewModel = RecipeDetailViewModel(handle, recipeEngine, favoriteRecipeDao, shoppingListDao)
        advanceUntilIdle()

        assertNull("Recipe should be null", viewModel.uiState.value.recipe)
    }

    // ─── Timer State Machine ───

    @Test
    fun `given IDLE when startTimer then transitions to RUNNING`() = runTest {
        viewModel = RecipeDetailViewModel(
            savedStateHandle, recipeEngine, favoriteRecipeDao, shoppingListDao
        )
        advanceUntilIdle()

        assertEquals(TimerState.IDLE, viewModel.uiState.value.timerState)

        viewModel.startTimer(stepOrder = 2, totalSeconds = 60)

        assertEquals(TimerState.RUNNING, viewModel.uiState.value.timerState)
        assertEquals(2, viewModel.uiState.value.activeTimerStep)
        assertTrue(viewModel.uiState.value.timerRemainingSec > 0)
    }

    @Test
    fun `given RUNNING when pauseTimer then transitions to PAUSED`() = runTest {
        viewModel = RecipeDetailViewModel(
            savedStateHandle, recipeEngine, favoriteRecipeDao, shoppingListDao
        )
        advanceUntilIdle()

        viewModel.startTimer(stepOrder = 2, totalSeconds = 60)
        assertEquals(TimerState.RUNNING, viewModel.uiState.value.timerState)

        viewModel.pauseTimer()
        assertEquals(TimerState.PAUSED, viewModel.uiState.value.timerState)
    }

    @Test
    fun `given PAUSED when resumeTimer then transitions to RUNNING`() = runTest {
        viewModel = RecipeDetailViewModel(
            savedStateHandle, recipeEngine, favoriteRecipeDao, shoppingListDao
        )
        advanceUntilIdle()

        viewModel.startTimer(stepOrder = 2, totalSeconds = 60)
        viewModel.pauseTimer()
        assertEquals(TimerState.PAUSED, viewModel.uiState.value.timerState)

        viewModel.resumeTimer()
        assertEquals(TimerState.RUNNING, viewModel.uiState.value.timerState)
    }

    @Test
    fun `given active state when resetTimer then transitions to IDLE`() = runTest {
        viewModel = RecipeDetailViewModel(
            savedStateHandle, recipeEngine, favoriteRecipeDao, shoppingListDao
        )
        advanceUntilIdle()

        viewModel.startTimer(stepOrder = 1, totalSeconds = 30)
        viewModel.pauseTimer()
        viewModel.resetTimer()

        assertEquals(TimerState.IDLE, viewModel.uiState.value.timerState)
        assertNull(viewModel.uiState.value.activeTimerStep)
        assertEquals(0, viewModel.uiState.value.timerRemainingSec)
    }

    @Test
    fun `given RUNNING when timer expires then transitions to DONE`() = runTest {
        viewModel = RecipeDetailViewModel(
            savedStateHandle, recipeEngine, favoriteRecipeDao, shoppingListDao
        )
        advanceUntilIdle()

        viewModel.startTimer(stepOrder = 3, totalSeconds = 1)
        advanceTimeBy(2000)
        advanceUntilIdle()

        assertEquals(TimerState.DONE, viewModel.uiState.value.timerState)
        assertTrue(viewModel.uiState.value.completedSteps.contains(3))
    }

    // ─── Step Completion ───

    @Test
    fun `when toggleStepComplete then adds or removes from completedSteps`() = runTest {
        viewModel = RecipeDetailViewModel(
            savedStateHandle, recipeEngine, favoriteRecipeDao, shoppingListDao
        )
        advanceUntilIdle()

        viewModel.toggleStepComplete(1)
        assertTrue(viewModel.uiState.value.completedSteps.contains(1))

        viewModel.toggleStepComplete(1)
        assertFalse(viewModel.uiState.value.completedSteps.contains(1))
    }

    // ─── Favorites ───

    @Test
    fun `when toggleFavorite on non-favorite then inserts into DAO`() = runTest {
        coEvery { favoriteRecipeDao.getById("test_recipe_1") } returns null

        viewModel = RecipeDetailViewModel(
            savedStateHandle, recipeEngine, favoriteRecipeDao, shoppingListDao
        )
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isFavorite)

        viewModel.toggleFavorite()
        advanceUntilIdle()

        coVerify(exactly = 1) { favoriteRecipeDao.insert(any<FavoriteRecipeEntity>()) }
        assertTrue(viewModel.uiState.value.isFavorite)
    }

    // ─── Shopping List ───

    @Test
    fun `when addToShoppingList then inserts all ingredients`() = runTest {
        coEvery { shoppingListDao.insert(any()) } returns 1L

        viewModel = RecipeDetailViewModel(
            savedStateHandle, recipeEngine, favoriteRecipeDao, shoppingListDao
        )
        advanceUntilIdle()

        viewModel.addToShoppingList()
        advanceUntilIdle()

        coVerify(exactly = 2) { shoppingListDao.insert(any<ShoppingItemEntity>()) }
    }
}
