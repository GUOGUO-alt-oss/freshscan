package com.example.freshscan.ui.screen.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.freshscan.data.produce.ProduceInfoEngine
import com.example.freshscan.domain.model.RecognitionResult
import com.example.freshscan.domain.repository.HistoryRepository
import com.example.freshscan.ui.state.DetailUiState
import com.example.freshscan.util.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the recognition detail screen.
 *
 * Loads a historical RecognitionResult by ID (passed via navigation argument),
 * and enriches it with full produce info from [ProduceInfoEngine].
 */
@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val historyRepository: HistoryRepository,
    private val produceInfoEngine: ProduceInfoEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    private val resultId: String? = savedStateHandle.get<String>("resultId")

    init {
        resultId?.let { loadResult(it) }
    }

    /** Retry loading after a failure. */
    fun reload() {
        resultId?.let { loadResult(it) }
    }

    /**
     * Load a recognition result from history by ID.
     * Then asynchronously load the full produce info from ProduceInfoEngine.
     */
    private fun loadResult(resultId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val historyItem = historyRepository.getById(resultId)
                if (historyItem != null) {
                    val result = RecognitionResult(
                        id = historyItem.id,
                        fruitCategory = historyItem.fruitCategory,
                        freshnessLevel = historyItem.freshnessLevel,
                        confidence = historyItem.confidence,
                        topPredictions = historyItem.topPredictions,
                        inferenceTimeMs = historyItem.inferenceTimeMs,
                        timestamp = historyItem.timestamp,
                        thumbnailPath = historyItem.thumbnailPath,
                        displayName = historyItem.displayName
                    )
                    _uiState.update { it.copy(result = result, isLoading = false) }

                    // Load produce info asynchronously
                    loadProduceInfo(historyItem.displayName)
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "记录未找到") }
                    Logger.w("DetailVM", "Result not found: $resultId")
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "加载失败: ${e.message}") }
                Logger.e("DetailVM", "Failed to load result", e)
            }
        }
    }

    /**
     * Load full produce info (intro, nutrition, storage, seasonality) from the engine.
     * Uses Flow to get both core info immediately and AI extension when available.
     */
    private fun loadProduceInfo(displayName: String) {
        if (displayName.isBlank()) return
        viewModelScope.launch {
            try {
                produceInfoEngine.getInfo(displayName).collect { info ->
                    _uiState.update { it.copy(produceInfo = info) }
                }
            } catch (e: Exception) {
                Logger.w("DetailVM", "Failed to load produce info for '$displayName': ${e.message}")
            }
        }
    }
}
