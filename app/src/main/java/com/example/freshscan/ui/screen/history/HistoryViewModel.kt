package com.example.freshscan.ui.screen.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.freshscan.domain.repository.HistoryRepository
import com.example.freshscan.ui.state.HistoryUiState
import com.example.freshscan.util.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the history screen.
 *
 * Collects a reactive Flow from Room and maps items to UI state.
 * Supports swipe-to-delete and clear-all operations.
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyRepository: HistoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        loadHistory()
    }

    /** Start collecting history items from Room (reactive Flow). */
    fun loadHistory() {
        viewModelScope.launch {
            historyRepository.getHistory().collect { items ->
                _uiState.update { it.copy(
                    items = items,
                    isEmpty = items.isEmpty(),
                    isLoading = false
                ) }
            }
        }
    }

    /** Delete a single history item. */
    fun deleteItem(id: String) {
        viewModelScope.launch {
            val result = historyRepository.delete(id)
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(snackbarMessage = "已删除") }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(snackbarMessage = "删除失败: ${e.message}") }
                    Logger.e("HistoryVM", "Delete failed", e)
                }
            )
        }
    }

    /** Delete all history items. */
    fun deleteAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true) }
            val result = historyRepository.deleteAll()
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isDeleting = false, snackbarMessage = "已清空全部记录") }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isDeleting = false, snackbarMessage = "清空失败: ${e.message}") }
                    Logger.e("HistoryVM", "Delete all failed", e)
                }
            )
        }
    }

    /** Clear snackbar message after display. */
    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
