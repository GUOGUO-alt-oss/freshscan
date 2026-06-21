package com.example.freshscan.data.history

import com.example.freshscan.data.mapper.FridgeMapper
import com.example.freshscan.domain.model.DetectedItem
import com.example.freshscan.domain.model.FridgeItem
import com.example.freshscan.domain.repository.FridgeRepository
import com.example.freshscan.util.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [FridgeRepository] backed by Room [FridgeDao].
 *
 * Handles mapping between domain models and Room entities,
 * and auto-calculates expiry dates based on item category.
 */
@Singleton
class FridgeRepositoryImpl @Inject constructor(
    private val fridgeDao: FridgeDao
) : FridgeRepository {

    override fun getItems(): Flow<List<FridgeItem>> =
        fridgeDao.getAllFlow().map { entities ->
            entities.map { FridgeMapper.toDomain(it) }
        }

    override fun getExpiringSoon(withinMs: Long): Flow<List<FridgeItem>> {
        val threshold = System.currentTimeMillis() + withinMs
        return fridgeDao.getExpiringSoonFlow(threshold).map { entities ->
            entities.map { FridgeMapper.toDomain(it) }
        }
    }

    override fun getCount(): Flow<Int> = fridgeDao.getCountFlow()

    override suspend fun getById(id: String): FridgeItem? {
        val entity = fridgeDao.getById(id) ?: return null
        return FridgeMapper.toDomain(entity)
    }

    override suspend fun addFromDetectedItems(items: List<DetectedItem>): Result<Int> = try {
        val now = System.currentTimeMillis()
        val entities = items.map { item ->
            FridgeMapper.toEntity(
                item = item,
                addedAt = now,
                expiryAt = estimateExpiry(item, now)
            )
        }
        fridgeDao.insertAll(entities)
        Logger.i("FridgeRepo", "Added ${entities.size} items to fridge")
        Result.success(entities.size)
    } catch (e: Exception) {
        Logger.e("FridgeRepo", "Failed to add items to fridge", e)
        Result.failure(e)
    }

    override suspend fun removeItem(id: String): Result<Unit> = try {
        fridgeDao.deleteById(id)
        Result.success(Unit)
    } catch (e: Exception) {
        Logger.e("FridgeRepo", "Failed to remove fridge item", e)
        Result.failure(e)
    }

    override suspend fun clearAll(): Result<Unit> = try {
        fridgeDao.deleteAll()
        Result.success(Unit)
    } catch (e: Exception) {
        Logger.e("FridgeRepo", "Failed to clear fridge", e)
        Result.failure(e)
    }

    override suspend fun updateExpiry(id: String, expiryAt: Long?): Result<Unit> = try {
        fridgeDao.updateExpiry(id, expiryAt)
        Result.success(Unit)
    } catch (e: Exception) {
        Logger.e("FridgeRepo", "Failed to update expiry", e)
        Result.failure(e)
    }

    override suspend fun updateNote(id: String, note: String): Result<Unit> = try {
        fridgeDao.updateNote(id, note)
        Result.success(Unit)
    } catch (e: Exception) {
        Logger.e("FridgeRepo", "Failed to update note", e)
        Result.failure(e)
    }

    /**
     * Estimate expiry date based on item category/type.
     *
     * Defaults:
     * - Leafy greens / berries: 3 days
     * - Tomatoes / peppers / cucumbers: 5 days
     * - Most fruits: 7 days
     * - Root vegetables (potato, carrot): 14 days
     * - Other: 5 days
     */
    private fun estimateExpiry(item: DetectedItem, nowMs: Long): Long {
        val label = item.label.uppercase()
        val days = when {
            // Leafy greens & berries spoil fast
            label.contains("LETTUCE") || label.contains("SPINACH") ||
            label.contains("KALE") || label.contains("ARUGULA") ||
            label.contains("BERRY") || label.contains("BERRIES") ||
            label.contains("STRAWBERRY") || label.contains("RASPBERRY") ||
            label.contains("BLUEBERRY") || label.contains("BLACKBERRY") ||
            label.contains("MUSHROOM") -> 3

            // Tomatoes, peppers, cucumbers, squash
            label.contains("TOMATO") || label.contains("PEPPER") ||
            label.contains("CUCUMBER") || label.contains("ZUCCHINI") ||
            label.contains("SQUASH") || label.contains("EGGPLANT") ||
            label.contains("OKRA") || label.contains("BEAN") -> 5

            // Root vegetables last longest
            label.contains("POTATO") || label.contains("SWEET_POTATO") ||
            label.contains("CARROT") || label.contains("RADISH") ||
            label.contains("TURNIP") || label.contains("BEET") ||
            label.contains("ONION") || label.contains("GARLIC") ||
            label.contains("GINGER") -> 14

            // Most fruits: 7 days
            label.contains("APPLE") || label.contains("ORANGE") ||
            label.contains("BANANA") || label.contains("PEAR") ||
            label.contains("PEACH") || label.contains("PLUM") ||
            label.contains("MANGO") || label.contains("PINEAPPLE") ||
            label.contains("MELON") || label.contains("WATERMELON") ||
            label.contains("GRAPE") || label.contains("CHERRY") -> 7

            // Citrus lasts longer
            label.contains("LEMON") || label.contains("LIME") ||
            label.contains("GRAPEFRUIT") -> 14

            else -> 5
        }
        return nowMs + days * 24L * 60 * 60 * 1000
    }
}
