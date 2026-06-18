package com.example.freshscan.ui.screen.profile

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.freshscan.domain.model.RecipeCategory
import com.example.freshscan.util.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for TasteProfileScreen.
 *
 * Manages user taste preferences (spice tolerance, salt/oil preference,
 * disliked ingredients, preferred categories) persisted via DataStore Preferences.
 */
@Deprecated(
    message = "Replaced by PersonalizeViewModel in v3.0",
    replaceWith = ReplaceWith("PersonalizeViewModel")
)
@HiltViewModel
class TasteProfileViewModel @Inject constructor(
    private val tasteProfileStore: DataStore<Preferences>
) : ViewModel() {

    private val _uiState = MutableStateFlow(TasteProfileUiState())
    val uiState: StateFlow<TasteProfileUiState> = _uiState.asStateFlow()

    init {
        loadPreferences()
    }

    // ─── Public API ──────────────────────────────────────────────────────────

    /** Update spice tolerance level (0=不辣, 1=微辣, 2=中辣, 3=超辣). */
    fun updateSpiceLevel(level: Int) {
        _uiState.update { it.copy(spiceLevel = level.coerceIn(0, 3), isDirty = true) }
    }

    /** Update salt preference (0=少盐, 1=正常, 2=偏咸). */
    fun updateSaltLevel(level: Int) {
        _uiState.update { it.copy(saltLevel = level.coerceIn(0, 2), isDirty = true) }
    }

    /** Update oil preference (0=少油, 1=正常, 2=偏油). */
    fun updateOilLevel(level: Int) {
        _uiState.update { it.copy(oilLevel = level.coerceIn(0, 2), isDirty = true) }
    }

    /** Toggle an ingredient in the excluded set. */
    fun toggleExcludedIngredient(ingredient: String) {
        _uiState.update { prev ->
            val newSet = prev.excludedIngredients.toMutableSet()
            if (newSet.contains(ingredient)) newSet.remove(ingredient)
            else newSet.add(ingredient)
            prev.copy(excludedIngredients = newSet, isDirty = true)
        }
    }

    /** Toggle a recipe category in preferred set. */
    fun togglePreferredCategory(category: RecipeCategory) {
        _uiState.update { prev ->
            val newSet = prev.preferredCategories.toMutableSet()
            if (newSet.contains(category)) newSet.remove(category)
            else newSet.add(category)
            prev.copy(preferredCategories = newSet, isDirty = true)
        }
    }

    /** Persist current preferences to DataStore. */
    fun save() {
        viewModelScope.launch {
            try {
                val state = _uiState.value
                tasteProfileStore.edit { prefs ->
                    prefs[intPreferencesKey("spice_level")] = state.spiceLevel
                    prefs[intPreferencesKey("salt_level")] = state.saltLevel
                    prefs[intPreferencesKey("oil_level")] = state.oilLevel
                    prefs[stringSetPreferencesKey("excluded_ingredients")] =
                        state.excludedIngredients.toSet()
                    prefs[stringSetPreferencesKey("preferred_categories")] =
                        state.preferredCategories.map { it.name }.toSet()
                }
                _uiState.update { it.copy(isDirty = false, savedSuccessfully = true) }
            } catch (e: Exception) {
                Logger.e("TasteProfileVM", "Failed to save preferences", e)
            }
        }
    }

    // ─── Private ─────────────────────────────────────────────────────────────

    private fun loadPreferences() {
        viewModelScope.launch {
            try {
                val prefs = tasteProfileStore.data.first()
                _uiState.update {
                    it.copy(
                        spiceLevel = prefs[intPreferencesKey("spice_level")] ?: 0,
                        saltLevel = prefs[intPreferencesKey("salt_level")] ?: 1,
                        oilLevel = prefs[intPreferencesKey("oil_level")] ?: 1,
                        excludedIngredients = prefs[stringSetPreferencesKey("excluded_ingredients")]
                            ?: emptySet(),
                        preferredCategories = prefs[stringSetPreferencesKey("preferred_categories")]
                            ?.mapNotNull { name ->
                                try { RecipeCategory.valueOf(name) } catch (_: Exception) { null }
                            }?.toSet() ?: emptySet()
                    )
                }
            } catch (e: Exception) {
                Logger.e("TasteProfileVM", "Failed to load preferences", e)
            }
        }
    }
}

/**
 * UI state for taste profile screen.
 */
data class TasteProfileUiState(
    /** Spice level: 0=不辣, 1=微辣, 2=中辣, 3=超辣. */
    val spiceLevel: Int = 0,

    /** Salt preference: 0=少盐, 1=正常, 2=偏咸. */
    val saltLevel: Int = 1,

    /** Oil preference: 0=少油, 1=正常, 2=偏油. */
    val oilLevel: Int = 1,

    /** Set of ingredient names to exclude from recipe recommendations. */
    val excludedIngredients: Set<String> = emptySet(),

    /** Set of preferred recipe categories for ranking boost. */
    val preferredCategories: Set<RecipeCategory> = emptySet(),

    /** Whether there are unsaved changes. */
    val isDirty: Boolean = false,

    /** Whether preferences were successfully saved in the last [save] call. */
    val savedSuccessfully: Boolean = false
)
