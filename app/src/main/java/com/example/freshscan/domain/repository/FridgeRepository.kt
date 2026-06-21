package com.example.freshscan.domain.repository

import com.example.freshscan.domain.model.DetectedItem
import com.example.freshscan.domain.model.FridgeItem
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing fridge items.
 */
interface FridgeRepository {

    /** All fridge items, newest first. */
    fun getItems(): Flow<List<FridgeItem>>

    /** Items expiring within the given threshold (ms from now). */
    fun getExpiringSoon(withinMs: Long): Flow<List<FridgeItem>>

    /** Count of items in fridge. */
    fun getCount(): Flow<Int>

    /** Get a single item by ID. */
    suspend fun getById(id: String): FridgeItem?

    /** Add scanned items to the fridge. Returns count added. */
    suspend fun addFromDetectedItems(items: List<DetectedItem>): Result<Int>

    /** Remove an item from the fridge. */
    suspend fun removeItem(id: String): Result<Unit>

    /** Clear all fridge items. */
    suspend fun clearAll(): Result<Unit>

    /** Update expiry date for an item. */
    suspend fun updateExpiry(id: String, expiryAt: Long?): Result<Unit>

    /** Update note for an item. */
    suspend fun updateNote(id: String, note: String): Result<Unit>
}
