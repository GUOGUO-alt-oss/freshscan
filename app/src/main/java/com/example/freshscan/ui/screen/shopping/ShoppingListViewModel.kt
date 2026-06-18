package com.example.freshscan.ui.screen.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.freshscan.data.history.ShoppingItemEntity
import com.example.freshscan.data.history.ShoppingListDao
import com.example.freshscan.util.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for ShoppingListScreen.
 *
 * Manages a persistent shopping list of missing ingredients from recipes.
 * Items can be toggled (checked off when purchased) or deleted.
 * Duplicate detection: same name + same amount = skip insert.
 */
@HiltViewModel
class ShoppingListViewModel @Inject constructor(
    private val shoppingListDao: ShoppingListDao
) : ViewModel() {

    /** Reactive shopping list items, newest first. */
    val items: StateFlow<List<ShoppingItemEntity>> = shoppingListDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Toggle a shopping item's checked state. */
    fun toggleItem(item: ShoppingItemEntity) {
        viewModelScope.launch {
            try {
                shoppingListDao.update(item.copy(isChecked = !item.isChecked))
            } catch (e: Exception) {
                Logger.e("ShoppingListVM", "Failed to toggle item", e)
            }
        }
    }

    /** Add an item, skipping if a name+amount duplicate already exists. */
    fun addItem(name: String, amount: String) {
        viewModelScope.launch {
            try {
                val exists = items.value.any { it.name == name && it.amount == amount }
                if (exists) {
                    Logger.d("ShoppingListVM", "Skipping duplicate: $name ($amount)")
                    return@launch
                }
                shoppingListDao.insert(
                    ShoppingItemEntity(
                        name = name,
                        amount = amount,
                        isChecked = false,
                        addedAt = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                Logger.e("ShoppingListVM", "Failed to add item", e)
            }
        }
    }

    /** Delete an item by its ID. */
    fun deleteItem(itemId: Long) {
        viewModelScope.launch {
            try {
                shoppingListDao.deleteById(itemId)
            } catch (e: Exception) {
                Logger.e("ShoppingListVM", "Failed to delete item", e)
            }
        }
    }

    /** Remove all checked (purchased) items. */
    fun clearChecked() {
        viewModelScope.launch {
            try {
                shoppingListDao.clearChecked()
            } catch (e: Exception) {
                Logger.e("ShoppingListVM", "Failed to clear checked", e)
            }
        }
    }
}
