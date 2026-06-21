package com.example.freshscan.ui.screen.fridge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.freshscan.domain.model.FridgeItem
import com.example.freshscan.domain.repository.FridgeRepository
import com.example.freshscan.util.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the "My Fridge" screen.
 *
 * Manages fridge items list, expiry tracking, and user actions
 * (remove item, clear all).
 */
@HiltViewModel
class FridgeViewModel @Inject constructor(
    private val fridgeRepository: FridgeRepository
) : ViewModel() {

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _isDeleting = MutableStateFlow(false)
    val isDeleting: StateFlow<Boolean> = _isDeleting.asStateFlow()

    /** Expiry warning threshold: 2 days from now. */
    private val expiryThresholdMs = 2 * 24 * 60 * 60 * 1000L

    val uiState: StateFlow<FridgeUiState> = combine(
        fridgeRepository.getItems(),
        fridgeRepository.getExpiringSoon(expiryThresholdMs),
        fridgeRepository.getCount(),
        _isDeleting
    ) { items, expiring, count, isDel ->
        val expiredCount = items.count { it.isExpired() }
        val expiringSoonCount = expiring.count { !it.isExpired() }
        FridgeUiState(
            items = items,
            expiringSoonItems = expiring.filter { !it.isExpired() },
            expiredCount = expiredCount,
            expiringSoonCount = expiringSoonCount,
            totalItems = count,
            isEmpty = items.isEmpty(),
            isDeleting = isDel
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FridgeUiState()
    )

    fun removeItem(item: FridgeItem) {
        viewModelScope.launch {
            fridgeRepository.removeItem(item.id)
            _snackbarMessage.value = "已移除 ${item.displayName}"
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            _isDeleting.value = true
            fridgeRepository.clearAll()
            _isDeleting.value = false
            _snackbarMessage.value = "已清空冰箱"
        }
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }
}

/**
 * UI state for the Fridge screen.
 */
data class FridgeUiState(
    val items: List<FridgeItem> = emptyList(),
    val expiringSoonItems: List<FridgeItem> = emptyList(),
    val expiredCount: Int = 0,
    val expiringSoonCount: Int = 0,
    val totalItems: Int = 0,
    val isEmpty: Boolean = true,
    val isDeleting: Boolean = false
)
