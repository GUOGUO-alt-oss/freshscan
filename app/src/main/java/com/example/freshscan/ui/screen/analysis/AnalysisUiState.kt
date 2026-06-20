package com.example.freshscan.ui.screen.analysis

import android.net.Uri
import com.example.freshscan.domain.model.DetectedItem
import com.example.freshscan.domain.model.Recipe
import com.example.freshscan.domain.model.RecipeCategory

/**
 * UI state for the Analysis screen.
 *
 * Tracks the full lifecycle: photo capture → model loading → particle animation →
 * inference → results display → recipe recommendation.
 */
data class AnalysisUiState(
    /** URI of the captured photo. */
    val photoUri: Uri? = null,

    /** Current screen state in the analysis state machine. */
    val screenState: AnalysisScreenState = AnalysisScreenState.Idle,

    /** Detected items from the three-stage inference pipeline. */
    val items: List<DetectedItem> = emptyList(),

    /** Current BottomSheet expansion state. */
    val sheetState: SheetState = SheetState.COLLAPSED,

    /** Currently selected recipe category filter. */
    val selectedCategory: RecipeCategory? = null,

    /** Recommended recipes matching detected items. */
    val recipes: List<Recipe> = emptyList(),

    /** Recipe recommendation note (e.g., missing ingredients warning). */
    val recipeNote: String? = null,

    /** Error message when analysis fails. */
    val errorMessage: String? = null
)

/** BottomSheet expansion states. */
enum class SheetState { COLLAPSED, HALF, FULL }

/**
 * Analysis screen state machine.
 *
 * Idle → Loading → Animating → (Results | Empty | Error)
 *                              ↑        │
 *                              └────────┘ (retake)
 */
sealed interface AnalysisScreenState {
    /** Initial state, before photo is captured. */
    data object Idle : AnalysisScreenState

    /** Loading models on first entry. */
    data object Loading : AnalysisScreenState

    /** Particle animation playing; inference runs in background. */
    data object Animating : AnalysisScreenState

    /** Inference complete with results. */
    data class Results(val itemCount: Int) : AnalysisScreenState

    /** Inference complete with no detections. */
    data object Empty : AnalysisScreenState

    /** An error occurred during analysis. */
    data class Error(val message: String) : AnalysisScreenState
}
