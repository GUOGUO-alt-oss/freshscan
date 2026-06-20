package com.example.freshscan.data.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MealHistoryDao {
    @Query("SELECT * FROM meal_history ORDER BY generatedAt DESC")
    fun getAll(): Flow<List<MealHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MealHistoryEntity)

    @Query("DELETE FROM meal_history WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM meal_history")
    suspend fun deleteAll()

    @Query("SELECT title FROM meal_history ORDER BY generatedAt DESC LIMIT 10")
    suspend fun getRecentTitles(): List<String>

    @Query("DELETE FROM meal_history WHERE generatedAt < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)
}
