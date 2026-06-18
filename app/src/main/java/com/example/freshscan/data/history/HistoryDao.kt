package com.example.freshscan.data.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for the history table.
 *
 * All queries use Flow for reactive updates: when the database changes,
 * active collectors automatically receive the updated data.
 */
@Dao
interface HistoryDao {

    /** Get all history records, newest first, limited to 50. Reactive. */
    @Query("SELECT * FROM history ORDER BY timestamp DESC LIMIT 50")
    fun getAllFlow(): Flow<List<HistoryEntity>>

    /** Get a single record by ID. */
    @Query("SELECT * FROM history WHERE id = :id")
    suspend fun getById(id: String): HistoryEntity?

    /** Insert or replace a record. Returns the row ID. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: HistoryEntity): Long

    /** Batch insert. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<HistoryEntity>): List<Long>

    /** Delete a single record by ID. Returns number of rows deleted. */
    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteById(id: String): Int

    /** Delete all records. */
    @Query("DELETE FROM history")
    suspend fun deleteAll(): Int

    /** Get the total record count. Reactive. */
    @Query("SELECT COUNT(*) FROM history")
    fun getCountFlow(): Flow<Int>

    /**
     * Trim to the most recent [maxCount] records.
     * Deletes older records exceeding the limit. Returns count of deleted rows.
     */
    @Query("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY timestamp DESC LIMIT :maxCount)")
    suspend fun trimToMaxCount(maxCount: Int): Int

    /**
     * Insert a record and then trim to max count — atomic via @Transaction.
     * This ensures the database never exceeds [maxCount] records even
     * if the trim step fails after insert.
     */
    @Transaction
    suspend fun insertAndTrim(entity: HistoryEntity, maxCount: Int) {
        insert(entity)
        trimToMaxCount(maxCount)
    }

    /**
     * Batch insert entities followed by an atomic trim to [maxCount].
     * Wraps [insertAll] and [trimToMaxCount] in a single @Transaction
     * so the database can never exceed [maxCount] rows — even if a crash
     * occurs between the two operations.
     */
    @Transaction
    suspend fun insertAllAndTrim(entities: List<HistoryEntity>, maxCount: Int) {
        insertAll(entities)
        trimToMaxCount(maxCount)
    }

    /** Get records filtered by fruit category (for future filtering feature). */
    @Query("SELECT * FROM history WHERE fruit_category = :category ORDER BY timestamp DESC LIMIT 50")
    fun getByCategoryFlow(category: String): Flow<List<HistoryEntity>>
}
