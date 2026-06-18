package com.example.freshscan.domain.repository

import com.example.freshscan.domain.model.DetectedItem
import com.example.freshscan.domain.model.HistoryItem
import com.example.freshscan.domain.model.RecognitionResult
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for history record operations.
 *
 * Implementation: [HistoryRepositoryImpl] in the data layer.
 */
interface HistoryRepository {
    /** Reactive flow of all history items, newest first. */
    fun getHistory(): Flow<List<HistoryItem>>

    /** Persist a history item. */
    suspend fun save(item: HistoryItem): Result<Unit>

    /**
     * Persist a full recognition result with inference metadata.
     * Preserves inferenceTimeMs and topPredictions that [save] discards.
     */
    suspend fun saveResult(result: RecognitionResult): Result<Unit>

    /**
     * Persist a batch of v2 DetectedItems from a single scan session.
     *
     * Each item is saved as a separate history record sharing the same
     * [sessionId] for grouping. Automatically trims old records to stay
     * within [com.example.freshscan.util.Constants.MAX_HISTORY_COUNT].
     *
     * @param items Detected items from the 3-stage inference pipeline.
     * @param sessionId UUID shared by all items in this scan.
     * @param inferenceTimeMs Total pipeline inference time in milliseconds.
     */
    suspend fun saveDetectedItems(
        items: List<DetectedItem>,
        sessionId: String,
        inferenceTimeMs: Long
    ): Result<Unit>

    /** Delete a single history item by ID. */
    suspend fun delete(id: String): Result<Unit>

    /** Delete all history items. */
    suspend fun deleteAll(): Result<Unit>

    /** Get a single history item by ID. */
    suspend fun getById(id: String): HistoryItem?
}
