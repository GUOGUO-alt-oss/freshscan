package com.example.freshscan.data.history

import com.example.freshscan.data.mapper.EntityMapper
import com.example.freshscan.domain.model.DetectedItem
import com.example.freshscan.domain.model.HistoryItem
import com.example.freshscan.domain.model.RecognitionResult
import com.example.freshscan.domain.repository.HistoryRepository
import com.example.freshscan.util.Constants
import com.example.freshscan.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [HistoryRepository] using Room.
 *
 * Automatically trims to [Constants.MAX_HISTORY_COUNT] after each insert
 * to prevent unbounded database growth. Uses @Transaction to ensure
 * insert + trim is atomic.
 */
@Singleton
class HistoryRepositoryImpl @Inject constructor(
    private val historyDao: HistoryDao,
    private val collectedProduceDao: CollectedProduceDao
) : HistoryRepository {

    override fun getHistory(): Flow<List<HistoryItem>> {
        return historyDao.getAllFlow().map { entities ->
            EntityMapper.toDomainList(entities)
        }
    }

    override suspend fun save(item: HistoryItem): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val entity = EntityMapper.toEntityFromItem(item)
                historyDao.insertAndTrim(entity, Constants.MAX_HISTORY_COUNT)
                Logger.i("History", "Saved record: ${item.id}")
                Result.success(Unit)
            } catch (e: Exception) {
                Logger.e("History", "Failed to save record", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Save a full RecognitionResult (with inference time and top predictions).
     */
    override suspend fun saveResult(result: RecognitionResult): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val entity = EntityMapper.toEntity(result)
                historyDao.insertAndTrim(entity, Constants.MAX_HISTORY_COUNT)
                Logger.i("History", "Saved result: ${result.id}")
                Result.success(Unit)
            } catch (e: Exception) {
                Logger.e("History", "Failed to save result", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Save a batch of v2 DetectedItems from a single scan session.
     *
     * All items share the same [sessionId] so they can be grouped in the
     * history view. After insertion, trims old records to stay within
     * [Constants.MAX_HISTORY_COUNT].
     */
    override suspend fun saveDetectedItems(
        items: List<DetectedItem>,
        sessionId: String,
        inferenceTimeMs: Long
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val entities = items.map { item ->
                    EntityMapper.toEntityFromDetectedItem(
                        item = item,
                        sessionId = sessionId,
                        inferenceTimeMs = inferenceTimeMs
                    )
                }
                historyDao.insertAllAndTrim(entities, Constants.MAX_HISTORY_COUNT)
                Logger.i("History", "Saved ${items.size} detected item(s) for session $sessionId")

                // v4.2: Auto-collect produce for the collection feature
                items.forEach { item ->
                    try { collectProduce(item.label, item.displayName) }
                    catch (_: Exception) { /* silent */ }
                }

                Result.success(Unit)
            } catch (e: Exception) {
                Logger.e("History", "Failed to save detected items", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun delete(id: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                historyDao.deleteById(id)
                Logger.i("History", "Deleted record: $id")
                Result.success(Unit)
            } catch (e: Exception) {
                Logger.e("History", "Failed to delete record", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun deleteAll(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                historyDao.deleteAll()
                Logger.i("History", "Cleared all records")
                Result.success(Unit)
            } catch (e: Exception) {
                Logger.e("History", "Failed to clear records", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun getById(id: String): HistoryItem? {
        return withContext(Dispatchers.IO) {
            val entity = historyDao.getById(id)
            entity?.let { EntityMapper.toDomain(it) }
        }
    }

    // ─── v4.2: Produce Collection ────────────────────────────────────────────

    private suspend fun collectProduce(label: String, displayName: String) {
        val existing = collectedProduceDao.getByLabel(label)
        if (existing != null) {
            collectedProduceDao.incrementScanCount(label)
        } else {
            collectedProduceDao.insert(
                CollectedProduceEntity(
                    label = label,
                    displayName = displayName,
                    firstScanTime = System.currentTimeMillis(),
                    scanCount = 1,
                    isRare = isRareProduce(label)
                )
            )
        }
    }

    private fun isRareProduce(label: String): Boolean {
        val rareKeywords = listOf(
            "okra", "custard", "dragon_fruit", "avocado", "kiwi",
            "mango", "papaya", "passion", "guava", "lychee",
            "durian", "jackfruit", "star_fruit", "fig", "persimmon"
        )
        return rareKeywords.any { label.contains(it, ignoreCase = true) }
    }
}
