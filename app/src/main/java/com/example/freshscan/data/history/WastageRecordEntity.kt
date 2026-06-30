package com.example.freshscan.data.history

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for tracking food waste records (v4.2).
 *
 * Created when the user discards expired fridge items,
 * helping to raise awareness of food waste.
 */
@Entity(
    tableName = "wastage_records",
    indices = [
        Index(value = ["recorded_at"], name = "idx_wr_time")
    ]
)
data class WastageRecordEntity(
    /** Auto-generated integer primary key. */
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Display name of the discarded item. */
    @ColumnInfo(name = "item_name")
    val itemName: String,

    /** Estimated monetary value in CNY. */
    @ColumnInfo(name = "estimated_value")
    val estimatedValue: Double = 0.0,

    /** Unix timestamp (ms) when the waste was recorded. */
    @ColumnInfo(name = "recorded_at")
    val recordedAt: Long
)
