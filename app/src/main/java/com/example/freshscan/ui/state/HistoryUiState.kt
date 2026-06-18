package com.example.freshscan.ui.state

import com.example.freshscan.domain.model.HistoryItem

/**
 * UI state for the history screen.
 */
data class HistoryUiState(
    /** List of history items, newest first. */
    val items: List<HistoryItem> = emptyList(),
    /** Whether the history list is empty. */
    val isEmpty: Boolean = true,
    /** Whether a delete operation is in progress. */
    val isDeleting: Boolean = false,
    /** Whether the initial data load is in progress. */
    val isLoading: Boolean = true,
    /** Snackbar message (e.g., "已删除"). */
    val snackbarMessage: String? = null
)
