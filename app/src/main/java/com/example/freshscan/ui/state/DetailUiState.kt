package com.example.freshscan.ui.state

import com.example.freshscan.domain.model.RecognitionResult

/**
 * UI state for the recognition detail screen.
 */
data class DetailUiState(
    /** The recognition result being displayed (includes topPredictions). */
    val result: RecognitionResult? = null,
    /** Whether data is currently loading. */
    val isLoading: Boolean = false,
    /** Error message when loading fails, or null on success. */
    val error: String? = null
)
