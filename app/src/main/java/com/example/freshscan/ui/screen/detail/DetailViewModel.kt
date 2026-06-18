package com.example.freshscan.ui.screen.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
 * Loads a historical RecognitionResult by ID (passed via navigation argument).
 * HistoryItem entities are converted to RecognitionResult with full
 * topPredictions and inferenceTimeMs support.
 */
@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val historyRepository: HistoryRepository
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
     * Uses EntityMapper.toDomain through HistoryRepository to get full data.
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
                        thumbnailPath = historyItem.thumbnailPath
                    )
                    _uiState.update { it.copy(result = result, isLoading = false) }
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
}
