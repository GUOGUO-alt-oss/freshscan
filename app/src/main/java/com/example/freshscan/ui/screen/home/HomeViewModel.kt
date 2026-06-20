package com.example.freshscan.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.freshscan.data.recipe.RecipeEngine
import com.example.freshscan.domain.model.HistoryItem
import com.example.freshscan.domain.model.Recipe
import com.example.freshscan.domain.model.RecipeCategory
import com.example.freshscan.domain.repository.HistoryRepository
import com.example.freshscan.util.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the v2.0 Home screen.
 *
 * Responsibilities:
 * - Load and display recent scan summary from history
 * - Load all preset recipes and filter by category
 * - Emit one-shot side effects (camera launch, navigation) via [sideEffects]
 *
 * Design: docs/02-架构设计-v2.md §5, docs/06-详细设计文档-v2.md §4.2
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val recipeEngine: RecipeEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _sideEffects = Channel<HomeSideEffect>(Channel.BUFFERED)
    val sideEffects = _sideEffects.receiveAsFlow()

    init {
        loadInitialData()
    }

    // ─── Public API ──────────────────────────────────────────────────────────

    /** Trigger the camera intent to scan ingredients. */
    fun onScanClicked() {
        viewModelScope.launch {
            _sideEffects.send(HomeSideEffect.LaunchCamera)
        }
    }

    /** Filter recipes by category. null = show all. */
    fun onCategorySelected(category: RecipeCategory?) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    /** Reload all data from source (recipes + history). */
    fun refresh() {
        loadInitialData()
    }

    // ─── Private ─────────────────────────────────────────────────────────────

    private fun loadInitialData() {
        viewModelScope.launch {
            // Parallel: load last scan + all recipes
            launch { loadLastScanResult() }
            launch { loadAllRecipes() }
        }
    }

    private suspend fun loadLastScanResult() {
        try {
            val items = historyRepository.getHistory().first()
            if (items.isNotEmpty()) {
                val latestTimestamp = items.first().timestamp
                val latestBatch = items.takeWhile { it.timestamp == latestTimestamp }
                _uiState.update {
                    it.copy(
                        lastScanItems = latestBatch,
                        lastScanTime = latestTimestamp
                    )
                }
            }
        } catch (e: Exception) {
            Logger.e("HomeVM", "Failed to load scan history", e)
        }
    }

    private suspend fun loadAllRecipes() {
        try {
            val recipes = recipeEngine.getAllPresetRecipes()
            _uiState.update { it.copy(allRecipes = recipes) }
        } catch (e: Exception) {
            Logger.e("HomeVM", "Failed to load recipes", e)
        }
    }
}

// ─── UI State ────────────────────────────────────────────────────────────────

/**
 * UI state for the Home screen.
 *
 * @param lastScanItems Most recent scan's detected items (grouped by timestamp).
 * @param lastScanTime Unix timestamp (ms) of the most recent scan.
 * @param selectedCategory Current filter category, null = show all.
 * @param allRecipes All preset recipes (loaded once, filtered on demand).
 */
data class HomeUiState(
    val lastScanItems: List<HistoryItem> = emptyList(),
    val lastScanTime: Long? = null,
    val selectedCategory: RecipeCategory? = null,
    val allRecipes: List<Recipe> = emptyList()
)

// ─── Side Effects ────────────────────────────────────────────────────────────

/**
 * One-shot side effects from the Home screen.
 */
sealed interface HomeSideEffect {
    /** Navigate to the system camera for ingredient scanning. */
    data object LaunchCamera : HomeSideEffect
}
