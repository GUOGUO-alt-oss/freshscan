package com.example.freshscan.data.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/**
 * Room DAO for wastage record operations (v4.2).
 */
@Dao
interface WastageRecordDao {

    /** Get all wastage records, newest first. */
    @Query("SELECT * FROM wastage_records ORDER BY recorded_at DESC")
    suspend fun getAll(): List<WastageRecordEntity>

    /** Total estimated value of waste since [since] (ms). */
    @Query("SELECT COALESCE(SUM(estimated_value), 0.0) FROM wastage_records WHERE recorded_at >= :since")
    suspend fun getTotalValue(since: Long): Double

    /** Number of waste records since [since] (ms). */
    @Query("SELECT COUNT(*) FROM wastage_records WHERE recorded_at >= :since")
    suspend fun getCount(since: Long): Int

    /** Insert a wastage record. */
    @Insert
    suspend fun insert(entity: WastageRecordEntity)

    /** Remove all wastage records. */
    @Query("DELETE FROM wastage_records")
    suspend fun deleteAll()
}
