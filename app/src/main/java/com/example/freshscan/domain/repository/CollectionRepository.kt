package com.example.freshscan.domain.repository

import com.example.freshscan.domain.model.CollectedProduce
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for the produce collection feature (v4.2).
 */
interface CollectionRepository {

    /** Observe all collected produce, newest first. */
    fun getCollection(): Flow<List<CollectedProduce>>

    /** Total number of unique produce types collected. */
    fun getTotalCollected(): Flow<Int>

    /** Number of new produce types unlocked in the current week. */
    suspend fun getWeeklyNew(): Int
}
