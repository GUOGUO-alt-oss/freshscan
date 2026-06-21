package com.example.freshscan.data.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for fridge item CRUD operations.
 */
@Dao
interface FridgeDao {

    /** Get all fridge items sorted by added date (newest first). */
    @Query("SELECT * FROM fridge_items ORDER BY added_at DESC")
    fun getAllFlow(): Flow<List<FridgeEntity>>

    /** Get items that are expiring soon (within [withinMs] from now). */
    @Query("""
        SELECT * FROM fridge_items 
        WHERE expiry_at IS NOT NULL AND expiry_at > 0 
        AND expiry_at < :thresholdMs
        ORDER BY expiry_at ASC
    """)
    fun getExpiringSoonFlow(thresholdMs: Long): Flow<List<FridgeEntity>>

    /** Get count of items in fridge. */
    @Query("SELECT COUNT(*) FROM fridge_items")
    fun getCountFlow(): Flow<Int>

    /** Get a single item by ID. */
    @Query("SELECT * FROM fridge_items WHERE id = :id")
    suspend fun getById(id: String): FridgeEntity?

    /** Insert a fridge item (replace if duplicate ID). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FridgeEntity)

    /** Insert multiple fridge items atomically. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<FridgeEntity>)

    /** Delete a single item by ID. */
    @Query("DELETE FROM fridge_items WHERE id = :id")
    suspend fun deleteById(id: String)

    /** Delete all fridge items. */
    @Query("DELETE FROM fridge_items")
    suspend fun deleteAll()

    /** Update the expiry date for a specific item. */
    @Query("UPDATE fridge_items SET expiry_at = :expiryAt WHERE id = :id")
    suspend fun updateExpiry(id: String, expiryAt: Long?)

    /** Update the note for a specific item. */
    @Query("UPDATE fridge_items SET note = :note WHERE id = :id")
    suspend fun updateNote(id: String, note: String)
}
