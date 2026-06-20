package com.example.freshscan.ui.screen.personalize

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.freshscan.data.diet.MealQueryEngine
import com.example.freshscan.data.history.ShoppingItemEntity
import com.example.freshscan.data.history.ShoppingListDao
import com.example.freshscan.data.history.UserProfileDao
import com.example.freshscan.data.history.UserProfileMapper
import com.example.freshscan.domain.model.MealSuggestion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class MealQueryUiState {
    data object Idle : MealQueryUiState()
    data object Querying : MealQueryUiState()
    data class Result(val suggestion: MealSuggestion) : MealQueryUiState()
    data class Error(val message: String) : MealQueryUiState()
}

@HiltViewModel
class MealQueryViewModel @Inject constructor(
    private val mealQueryEngine: MealQueryEngine,
    private val userProfileDao: UserProfileDao,
    private val shoppingListDao: ShoppingListDao
) : ViewModel() {

    private val _uiState = MutableStateFlow<MealQueryUiState>(MealQueryUiState.Idle)
    val uiState: StateFlow<MealQueryUiState> = _uiState.asStateFlow()

    val history: Flow<List<MealSuggestion>> = mealQueryEngine.getHistory()

    private var queryJob: Job? = null

    fun queryMeal(query: String) {
        queryJob?.cancel()  // Cancel previous query to prevent stale results
        queryJob = viewModelScope.launch {
            _uiState.value = MealQueryUiState.Querying
            try {
                val entity = userProfileDao.get().first()
                val profile = entity?.let { UserProfileMapper.toDomain(it) }
                mealQueryEngine.queryMeal(query, profile).collect { suggestion ->
                    _uiState.value = MealQueryUiState.Result(suggestion)
                }
            } catch (e: Exception) {
                _uiState.value = MealQueryUiState.Error(
                    e.message ?: "查询失败，请重试"
                )
            }
        }
    }

    fun deleteHistoryItem(id: String) {
        viewModelScope.launch {
            mealQueryEngine.deleteHistoryItem(id)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            mealQueryEngine.clearHistory()
        }
    }

    fun addToShoppingList(suggestion: MealSuggestion) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val seen = mutableSetOf<String>()
            suggestion.ingredients.forEach { ingredient ->
                if (ingredient.name !in seen) {
                    seen.add(ingredient.name)
                    shoppingListDao.insert(
                        ShoppingItemEntity(
                            name = ingredient.name,
                            amount = ingredient.amount,
                            addedAt = now
                        )
                    )
                }
            }
        }
    }

    fun resetState() {
        _uiState.value = MealQueryUiState.Idle
    }
}
