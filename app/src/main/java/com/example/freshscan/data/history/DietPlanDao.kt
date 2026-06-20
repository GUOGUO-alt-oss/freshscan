package com.example.freshscan.data.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DietPlanDao {
    @Query("SELECT * FROM diet_plans ORDER BY generatedAt DESC LIMIT 1")
    suspend fun getLatest(): DietPlanEntity?

    @Query("SELECT * FROM diet_plans ORDER BY generatedAt DESC LIMIT 50")
    fun getAll(): Flow<List<DietPlanEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(plan: DietPlanEntity)

    @Query("DELETE FROM diet_plans WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM diet_plans")
    suspend fun deleteAll()

    @Query("DELETE FROM diet_plans WHERE generatedAt < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)
}
