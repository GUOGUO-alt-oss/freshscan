package com.example.freshscan.data.mapper

import com.example.freshscan.data.history.FridgeEntity
import com.example.freshscan.domain.model.DetectedItem
import com.example.freshscan.domain.model.FreshnessLevel
import com.example.freshscan.domain.model.FridgeItem
import com.example.freshscan.domain.model.FruitCategory

/**
 * Mapper between FridgeEntity (Room) and FridgeItem (domain).
 */
object FridgeMapper {

    fun toDomain(entity: FridgeEntity): FridgeItem {
        return FridgeItem(
            id = entity.id,
            displayName = entity.displayName,
            label = entity.label,
            fruitCategory = try {
                FruitCategory.valueOf(entity.fruitCategory)
            } catch (_: IllegalArgumentException) {
                FruitCategory.UNKNOWN
            },
            freshnessLevel = try {
                FreshnessLevel.valueOf(entity.freshnessLevel)
            } catch (_: IllegalArgumentException) {
                FreshnessLevel.UNCERTAIN
            },
            isCookable = entity.isCookable,
            addedAt = entity.addedAt,
            expiryAt = entity.expiryAt,
            thumbnailPath = entity.thumbnailPath,
            confidence = entity.confidence,
            note = entity.note
        )
    }

    fun toEntity(
        item: DetectedItem,
        addedAt: Long,
        expiryAt: Long? = null
    ): FridgeEntity {
        return FridgeEntity(
            id = item.id,
            displayName = item.displayName,
            label = item.label,
            fruitCategory = EntityMapper.mapLabelToFruitCategory(item.label).name,
            freshnessLevel = item.freshnessLevel.name,
            isCookable = item.isCookable,
            addedAt = addedAt,
            expiryAt = expiryAt,
            thumbnailPath = null,
            confidence = item.confidence,
            note = ""
        )
    }
}
