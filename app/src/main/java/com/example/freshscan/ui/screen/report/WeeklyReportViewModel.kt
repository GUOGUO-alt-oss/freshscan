package com.example.freshscan.ui.screen.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.freshscan.data.history.MealHistoryDao
import com.example.freshscan.data.history.WastageRecordDao
import com.example.freshscan.domain.repository.CollectionRepository
import com.example.freshscan.domain.repository.FridgeRepository
import com.example.freshscan.domain.repository.HistoryRepository
import com.example.freshscan.util.TimeUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WeeklyReportUiState(
    val scanCount: Int = 0,
    val newProduceCount: Int = 0,
    val topProduce: List<String> = emptyList(),
    val wastedValue: Double = 0.0,
    val mealQueryCount: Int = 0,
    val isLoading: Boolean = true
)

@HiltViewModel
class WeeklyReportViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val collectionRepository: CollectionRepository,
    private val fridgeRepository: FridgeRepository,
    private val mealHistoryDao: MealHistoryDao,
    private val wastageRecordDao: WastageRecordDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(WeeklyReportUiState())
    val uiState: StateFlow<WeeklyReportUiState> = _uiState.asStateFlow()

    init {
        loadReport()
    }

    private fun loadReport() {
        viewModelScope.launch {
            try {
                val weekStart = TimeUtils.getWeekStartMs()
                val now = System.currentTimeMillis()

                // Scan count this week
                val history = historyRepository.getHistory().first()
                val scansThisWeek = history.count { it.timestamp in weekStart..now }

                // New produce unlocked this week
                val newProduce = collectionRepository.getWeeklyNew()

                // Top produce (from collection, sorted by scan count)
                val collection = collectionRepository.getCollection().first()
                val topProduceNames = collection
                    .sortedByDescending { it.scanCount }
                    .take(3)
                    .map { "${it.displayName} (${it.scanCount}次)" }

                // Wastage this week
                val wasted = wastageRecordDao.getTotalValue(weekStart)

                // Meal query count
                val mealHistory = mealHistoryDao.getAll().first()
                val mealsThisWeek = mealHistory.count { it.generatedAt in weekStart..now }

                _uiState.value = WeeklyReportUiState(
                    scanCount = scansThisWeek,
                    newProduceCount = newProduce,
                    topProduce = topProduceNames,
                    wastedValue = wasted,
                    mealQueryCount = mealsThisWeek,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = WeeklyReportUiState(isLoading = false)
            }
        }
    }
}
