package com.example.freshscan.ui.screen.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.freshscan.data.recipe.RecipeEngine
import com.example.freshscan.domain.model.CollectedProduce
import com.example.freshscan.domain.model.HistoryItem
import com.example.freshscan.domain.model.Recipe
import com.example.freshscan.domain.repository.CollectionRepository
import com.example.freshscan.domain.repository.FridgeRepository
import com.example.freshscan.domain.repository.HistoryRepository
import com.example.freshscan.util.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.Calendar
import javax.inject.Inject

/**
 * ViewModel for the v4.2 Home screen.
 *
 * v4.2 changes:
 * - Removed preset recipe listing (moved to FridgeRecipes tab)
 * - Added produce collection display
 * - Added "Tonight's Meal" recommendation based on fridge stock
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val historyRepository: HistoryRepository,
    private val recipeEngine: RecipeEngine,
    private val fridgeRepository: FridgeRepository,
    private val collectionRepository: CollectionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _sideEffects = Channel<HomeSideEffect>(Channel.BUFFERED)
    val sideEffects = _sideEffects.receiveAsFlow()

    val fridgeCount: StateFlow<Int> = fridgeRepository.getCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        loadInitialData()
        loadSeasonalTip()
    }

    // ─── Public API ──────────────────────────────────────────────────────────

    fun onScanClicked() {
        viewModelScope.launch {
            _sideEffects.send(HomeSideEffect.LaunchCamera)
        }
    }

    fun onTonightRecipeClicked(recipeId: String) {
        viewModelScope.launch {
            _sideEffects.send(HomeSideEffect.NavigateToRecipe(recipeId))
        }
    }

    fun onViewCollectionClicked() {
        viewModelScope.launch {
            _sideEffects.send(HomeSideEffect.NavigateToCollection)
        }
    }

    fun refresh() {
        loadInitialData()
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    // ─── Private ─────────────────────────────────────────────────────────────

    private fun loadInitialData() {
        viewModelScope.launch {
            launch { loadLastScanResult() }
            launch { loadCollection() }
            launch { loadTonightMeal() }
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
            _uiState.update { it.copy(errorMessage = "加载扫描历史失败") }
            Logger.e("HomeVM", "Failed to load scan history", e)
        }
    }

    private suspend fun loadCollection() {
        try {
            val items = collectionRepository.getCollection().first()
            _uiState.update {
                it.copy(
                    collectionItems = items,
                    collectionCount = items.size
                )
            }
        } catch (e: Exception) {
            Logger.e("HomeVM", "Failed to load collection", e)
        }
    }

    private suspend fun loadTonightMeal() {
        try {
            _uiState.update { it.copy(tonightLoading = true) }
            val fridgeItems = fridgeRepository.getItems().first()
            if (fridgeItems.isEmpty()) {
                _uiState.update { it.copy(tonightRecipes = emptyList(), tonightLoading = false) }
                return
            }

            val fridgeNames = fridgeItems.map { it.displayName }.toSet()
            val allRecipes = recipeEngine.getAllPresetRecipes()
            val scored: List<Pair<Recipe, Int>> = allRecipes.map { recipe ->
                val matchCount: Int = recipe.matchIngredients.count { ingredient ->
                    fridgeNames.any { fn -> ingredient.contains(fn) || fn.contains(ingredient) }
                }
                Pair(recipe, matchCount)
            }.filter { it.second > 0 }
                .sortedByDescending { it.second }
                .take(3)

            _uiState.update {
                it.copy(
                    tonightRecipes = scored.map { s -> s.first },
                    tonightLoading = false
                )
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(tonightRecipes = emptyList(), tonightLoading = false) }
            Logger.e("HomeVM", "Failed to load tonight meal", e)
        }
    }

    private fun loadSeasonalTip() {
        viewModelScope.launch {
            try {
                val tip = withContext(Dispatchers.IO) { loadSeasonalTipFromAssets() }
                _uiState.update { it.copy(seasonalTip = tip) }
            } catch (e: Exception) {
                Logger.w("HomeVM", "Failed to load seasonal tip", e)
            }
        }
    }

    private fun loadSeasonalTipFromAssets(): SeasonalTip? {
        val jsonStr = context.assets.open("seasonal_produce.json")
            .bufferedReader().use { it.readText() }
        val array = JSONArray(jsonStr)
        val currentMonth = Calendar.getInstance().get(Calendar.MONTH) + 1

        val seasonalItems = (0 until array.length()).mapNotNull { i ->
            val obj = array.getJSONObject(i)
            val months = (0 until obj.getJSONArray("months").length())
                .map { j -> obj.getJSONArray("months").getInt(j) }
            if (currentMonth in months) {
                SeasonalTip(
                    name = obj.getString("name"),
                    emoji = obj.optString("emoji", ""),
                    tip = obj.getString("tip"),
                    month = currentMonth
                )
            } else null
        }

        return if (seasonalItems.isNotEmpty()) seasonalItems.random() else null
    }
}

data class SeasonalTip(
    val name: String,
    val emoji: String,
    val tip: String,
    val month: Int
)

// ─── UI State ────────────────────────────────────────────────────────────────

data class HomeUiState(
    val lastScanItems: List<HistoryItem> = emptyList(),
    val lastScanTime: Long? = null,
    val collectionItems: List<CollectedProduce> = emptyList(),
    val collectionCount: Int = 0,
    val tonightRecipes: List<Recipe> = emptyList(),
    val tonightLoading: Boolean = false,
    val seasonalTip: SeasonalTip? = null,
    val errorMessage: String? = null
)

// ─── Side Effects ────────────────────────────────────────────────────────────

sealed interface HomeSideEffect {
    data object LaunchCamera : HomeSideEffect
    data class NavigateToRecipe(val recipeId: String) : HomeSideEffect
    data object NavigateToCollection : HomeSideEffect
}
