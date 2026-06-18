package com.example.freshscan.data.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for the shopping_list table.
 */
@Dao
interface ShoppingListDao {

    /** Get all shopping items, newest first. Reactive. */
    @Query("SELECT * FROM shopping_list ORDER BY addedAt DESC")
    fun getAll(): Flow<List<ShoppingItemEntity>>

    /** Get unchecked items only. */
    @Query("SELECT * FROM shopping_list WHERE isChecked = 0 ORDER BY addedAt DESC")
    fun getUnchecked(): Flow<List<ShoppingItemEntity>>

    /** Insert a new item. Returns the row ID. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ShoppingItemEntity): Long

    /** Update an existing item. */
    @Update
    suspend fun update(item: ShoppingItemEntity)

    /** Delete an item by ID. */
    @Query("DELETE FROM shopping_list WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    /** Delete all checked items (clear completed). */
    @Query("DELETE FROM shopping_list WHERE isChecked = 1")
    suspend fun clearChecked(): Int

    /** Get the total count. Reactive. */
    @Query("SELECT COUNT(*) FROM shopping_list")
    fun getCountFlow(): Flow<Int>
}
