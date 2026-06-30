package com.example.freshscan.data.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for the produce collection feature (v4.2).
 */
@Dao
interface CollectedProduceDao {

    /** Observe all collected produce, newest first. */
    @Query("SELECT * FROM collected_produce ORDER BY first_scan_time DESC")
    fun getAllFlow(): Flow<List<CollectedProduceEntity>>

    /** Find a produce entry by raw model label. */
    @Query("SELECT * FROM collected_produce WHERE label = :label LIMIT 1")
    suspend fun getByLabel(label: String): CollectedProduceEntity?

    /** Total number of unique produce types collected. */
    @Query("SELECT COUNT(*) FROM collected_produce")
    suspend fun getCount(): Int

    /** Insert a new produce entry (replace if duplicate label). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CollectedProduceEntity)

    /** Increment the scan count for an existing produce. */
    @Query("UPDATE collected_produce SET scan_count = scan_count + 1 WHERE label = :label")
    suspend fun incrementScanCount(label: String)

    /** Remove all collected produce entries. */
    @Query("DELETE FROM collected_produce")
    suspend fun deleteAll()

    /** Count how many produce types were first scanned in the past [since] milliseconds. */
    @Query("SELECT COUNT(*) FROM collected_produce WHERE first_scan_time >= :since")
    suspend fun getNewCount(since: Long): Int
}
