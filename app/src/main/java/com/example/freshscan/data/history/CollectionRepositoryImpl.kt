package com.example.freshscan.data.history

import com.example.freshscan.domain.model.CollectedProduce
import com.example.freshscan.domain.repository.CollectionRepository
import com.example.freshscan.util.Logger
import com.example.freshscan.util.TimeUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of CollectionRepository using Room (v4.2).
 */
@Singleton
class CollectionRepositoryImpl @Inject constructor(
    private val dao: CollectedProduceDao
) : CollectionRepository {

    override fun getCollection(): Flow<List<CollectedProduce>> {
        return dao.getAllFlow().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getTotalCollected(): Flow<Int> {
        return dao.getAllFlow().map { it.size }
    }

    override suspend fun getWeeklyNew(): Int {
        return try {
            val weekStart = TimeUtils.getWeekStartMs()
            dao.getNewCount(weekStart)
        } catch (e: Exception) {
            Logger.e("CollectionRepo", "Failed to get weekly new count", e)
            0
        }
    }

    // ─── Extension ──────────────────────────────────────────────────────────

    private fun CollectedProduceEntity.toDomain() = CollectedProduce(
        label = label,
        displayName = displayName,
        category = category,
        firstScanTime = firstScanTime,
        scanCount = scanCount,
        isRare = isRare
    )
}
