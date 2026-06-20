package com.example.freshscan.ui.screen.recipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.freshscan.data.history.FavoriteRecipeDao
import com.example.freshscan.data.history.FavoriteRecipeEntity
import com.example.freshscan.data.history.ShoppingListDao
import com.example.freshscan.data.history.ShoppingItemEntity
import com.example.freshscan.data.recipe.RecipeEngine
import com.example.freshscan.domain.model.Recipe
import com.example.freshscan.util.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for RecipeDetailScreen.
 *
 * Loads a recipe by ID from [RecipeEngine], manages cooking step progression,
 * timer state machine, favorite toggling, and shopping list integration.
 *
 * Timer state machine:
 * ```
 *    ┌──────┐  startTimer()  ┌────────┐  pauseTimer()  ┌────────┐
 *    │ IDLE │ →────────────→│RUNNING │ →─────────────→│ PAUSED │
 *    └──┬───┘                └───┬────┘                └───┬────┘
 *       │     resetTimer()      │    resumeTimer()        │
 *       └───────────────────────┴────────────────────────┘
 * ```
 */
@HiltViewModel
class RecipeDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val recipeEngine: RecipeEngine,
    private val favoriteRecipeDao: FavoriteRecipeDao,
    private val shoppingListDao: ShoppingListDao
) : ViewModel() {

    /** Recipe ID from navigation argument. */
    private val recipeId: String = savedStateHandle.get<String>("recipeId") ?: ""

    private val _uiState = MutableStateFlow(RecipeDetailUiState())
    val uiState: StateFlow<RecipeDetailUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null

    init {
        loadRecipe()
        checkFavorite()
    }

    // ─── Public API ──────────────────────────────────────────────────────────

    /** Start a timer for a cooking step. */
    fun startTimer(stepOrder: Int, totalSeconds: Int) {
        timerJob?.cancel()
        _uiState.update {
            it.copy(
                activeTimerStep = stepOrder,
                timerState = TimerState.RUNNING,
                timerRemainingSec = totalSeconds
            )
        }
        timerJob = viewModelScope.launch {
            while (_uiState.value.timerRemainingSec > 0 &&
                   _uiState.value.timerState == TimerState.RUNNING) {
                delay(1000L)
                _uiState.update { prev ->
                    val newRemaining = prev.timerRemainingSec - 1
                    if (newRemaining <= 0) {
                        prev.copy(
                            timerRemainingSec = 0,
                            timerState = TimerState.DONE,
                            activeTimerStep = null,
                            completedSteps = prev.completedSteps + stepOrder
                        )
                    } else {
                        prev.copy(timerRemainingSec = newRemaining)
                    }
                }
            }
        }
    }

    /** Pause the active timer. */
    fun pauseTimer() {
        timerJob?.cancel()
        _uiState.update { it.copy(timerState = TimerState.PAUSED) }
    }

    /** Resume the paused timer. */
    fun resumeTimer() {
        if (_uiState.value.timerState == TimerState.PAUSED) {
            _uiState.update { it.copy(timerState = TimerState.RUNNING) }
            val stepOrder = _uiState.value.activeTimerStep ?: return
            val remaining = _uiState.value.timerRemainingSec
            timerJob = viewModelScope.launch {
                var sec = remaining
                while (sec > 0 && _uiState.value.timerState == TimerState.RUNNING) {
                    delay(1000L)
                    sec--
                    _uiState.update { prev ->
                        if (sec <= 0) {
                            prev.copy(
                                timerRemainingSec = 0,
                                timerState = TimerState.DONE,
                                activeTimerStep = null,
                                completedSteps = prev.completedSteps + stepOrder
                            )
                        } else {
                            prev.copy(timerRemainingSec = sec)
                        }
                    }
                }
            }
        }
    }

    /** Reset the timer to idle state. */
    fun resetTimer() {
        timerJob?.cancel()
        _uiState.update {
            it.copy(
                activeTimerStep = null,
                timerState = TimerState.IDLE,
                timerRemainingSec = 0
            )
        }
    }

    /** Toggle a step as completed (manual, for steps without timers). */
    fun toggleStepComplete(stepOrder: Int) {
        _uiState.update { prev ->
            val newCompleted = if (stepOrder in prev.completedSteps) {
                prev.completedSteps - stepOrder
            } else {
                prev.completedSteps + stepOrder
            }
            prev.copy(completedSteps = newCompleted)
        }
    }

    /** Toggle favorite status. */
    fun toggleFavorite() {
        viewModelScope.launch {
            try {
                val current = _uiState.value
                if (current.isFavorite) {
                    favoriteRecipeDao.deleteById(recipeId)
                    _uiState.update { it.copy(isFavorite = false) }
                } else {
                    val recipe = current.recipe ?: return@launch
                    val recipeJson = JSONObject().apply {
                        put("id", recipe.id)
                        put("title", recipe.title)
                        put("category", recipe.category.name)
                        put("difficulty", recipe.difficulty.name)
                        put("cookingTimeMin", recipe.cookingTimeMin)
                        put("matchIngredients", JSONArray(recipe.matchIngredients))
                        put("allIngredients", JSONArray().apply {
                            recipe.allIngredients.forEach { ing ->
                                put(JSONObject().apply {
                                    put("name", ing.name)
                                    put("amount", ing.amount)
                                })
                            }
                        })
                        put("steps", JSONArray().apply {
                            recipe.steps.forEach { step ->
                                put(JSONObject().apply {
                                    put("order", step.order)
                                    put("text", step.text)
                                    put("timerSec", step.timerSec)
                                })
                            }
                        })
                        put("nutrition", JSONObject().apply {
                            put("calories", recipe.nutrition.calories)
                            put("protein", recipe.nutrition.protein)
                            put("carbs", recipe.nutrition.carbs)
                            put("fat", recipe.nutrition.fat)
                            put("fiber", recipe.nutrition.fiber)
                        })
                        put("tags", JSONArray(recipe.tags))
                        put("tips", recipe.tips)
                        put("imageAsset", recipe.imageAsset)
                        put("thumbnailAsset", recipe.thumbnailAsset)
                    }
                    favoriteRecipeDao.insert(
                        FavoriteRecipeEntity(
                            recipeId = recipeId,
                            title = recipe.title,
                            category = recipe.category.name,
                            jsonData = recipeJson.toString(),
                            favoritedAt = System.currentTimeMillis()
                        )
                    )
                    _uiState.update { it.copy(isFavorite = true) }
                }
            } catch (e: Exception) {
                Logger.e("RecipeDetailVM", "Failed to toggle favorite", e)
            }
        }
    }

    /** Add missing ingredients to shopping list. */
    fun addToShoppingList() {
        val recipe = _uiState.value.recipe ?: return
        viewModelScope.launch {
            try {
                val existingItems = shoppingListDao.getAll().first().map { it.name }.toSet()
                var addedCount = 0
                recipe.allIngredients.forEach { ingredient ->
                    if (ingredient.name !in existingItems) {
                        shoppingListDao.insert(
                            ShoppingItemEntity(
                                name = ingredient.name,
                                amount = ingredient.amount,
                                isChecked = false,
                                addedAt = System.currentTimeMillis()
                            )
                        )
                        addedCount++
                    }
                }
                Logger.i("RecipeDetailVM", "Added $addedCount new items to shopping list (${recipe.allIngredients.size - addedCount} skipped as duplicates)")
            } catch (e: Exception) {
                Logger.e("RecipeDetailVM", "Failed to add to shopping list", e)
            }
        }
    }

    // ─── Private ─────────────────────────────────────────────────────────────

    private fun loadRecipe() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val recipe = recipeEngine.getRecipeById(recipeId)
                _uiState.update {
                    it.copy(recipe = recipe, isLoading = false)
                }
            } catch (e: Exception) {
                Logger.e("RecipeDetailVM", "Failed to load recipe: $recipeId", e)
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun checkFavorite() {
        viewModelScope.launch {
            try {
                val fav = favoriteRecipeDao.getById(recipeId)
                _uiState.update { it.copy(isFavorite = fav != null) }
            } catch (e: Exception) {
                Logger.w("RecipeDetailVM", "Failed to check favorite", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}

// ─── UI State + Timer ────────────────────────────────────────────────────────

/**
 * UI state for the recipe detail screen.
 */
data class RecipeDetailUiState(
    /** The loaded recipe, or null if still loading or not found. */
    val recipe: Recipe? = null,

    /** Whether the recipe is still loading from the engine. */
    val isLoading: Boolean = true,

    /** Whether this recipe is favorited. */
    val isFavorite: Boolean = false,

    /** Currently timer-active step order, or null if no timer active. */
    val activeTimerStep: Int? = null,

    /** Current timer state. */
    val timerState: TimerState = TimerState.IDLE,

    /** Remaining seconds on the active timer. */
    val timerRemainingSec: Int = 0,

    /** Set of step orders that have been completed. */
    val completedSteps: Set<Int> = emptySet()
)

/**
 * Timer state machine states.
 */
enum class TimerState { IDLE, RUNNING, PAUSED, DONE }
