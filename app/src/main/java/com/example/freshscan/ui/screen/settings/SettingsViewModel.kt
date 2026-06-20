package com.example.freshscan.ui.screen.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.freshscan.data.history.DietPlanDao
import com.example.freshscan.data.history.HistoryDao
import com.example.freshscan.util.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for SettingsScreen.
 *
 * Manages app settings persisted via DataStore Preferences:
 * - Classic mode (v1): toggle between v2 and v1 recognition mode
 *
 * Design: docs/02-架构设计-v2.md §2, docs/03-UI设计-v2.md §4.7
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val historyDao: HistoryDao,
    private val dietPlanDao: DietPlanDao
) : ViewModel() {

    private val _isClassicMode = MutableStateFlow(false)
    val isClassicMode: StateFlow<Boolean> = _isClassicMode.asStateFlow()

    private val _clearHistoryResult = MutableSharedFlow<String>()
    val clearHistoryResult: SharedFlow<String> = _clearHistoryResult.asSharedFlow()

    init {
        loadPreferences()
    }

    /** Toggle classic mode (v1) on/off. */
    fun toggleClassicMode(enabled: Boolean) {
        _isClassicMode.value = enabled
        viewModelScope.launch {
            try {
                dataStore.edit { prefs ->
                    prefs[CLASSIC_MODE_KEY] = enabled
                }
            } catch (e: Exception) {
                Logger.e("SettingsVM", "Failed to save classic mode", e)
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            try {
                historyDao.deleteAll()
                dietPlanDao.deleteAll()
                _clearHistoryResult.emit("历史记录已清除")
            } catch (e: Exception) {
                _clearHistoryResult.emit("清除失败：${e.message ?: "未知错误"}")
            }
        }
    }

    private fun loadPreferences() {
        viewModelScope.launch {
            try {
                val value = dataStore.data.map { prefs ->
                    prefs[CLASSIC_MODE_KEY] ?: false
                }.first()
                _isClassicMode.value = value
            } catch (e: Exception) {
                Logger.e("SettingsVM", "Failed to load preferences", e)
            }
        }
    }

    companion object {
        private val CLASSIC_MODE_KEY = booleanPreferencesKey("classic_mode")
    }
}
