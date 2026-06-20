package com.example.freshscan.data.history

import androidx.room.Entity
import androidx.room.PrimaryKey

@Deprecated("Replaced by MealHistoryEntity")
@Entity(tableName = "diet_plans")
data class DietPlanEntity(
    @PrimaryKey val id: String,
    val generatedAt: Long,
    val profileSnapshotJson: String,  // JSON-serialized UserProfile
    val dailyPlansJson: String,       // JSON-serialized List<DailyMealPlan>
    val totalCaloriesAvg: Int,
    val nutritionSummary: String
)
