package com.example.freshscan.data.mapper

import com.example.freshscan.data.history.HistoryEntity
import com.example.freshscan.domain.model.DetectedItem
import com.example.freshscan.domain.model.FreshnessLevel
import com.example.freshscan.domain.model.FruitCategory
import com.example.freshscan.domain.model.HistoryItem
import com.example.freshscan.domain.model.Prediction
import com.example.freshscan.domain.model.RecognitionResult
import org.json.JSONArray
import org.json.JSONObject

/**
 * Bidirectional mapper between Room entities and domain models.
 *
 * - [toDomain]: HistoryEntity → HistoryItem (with full topPredictions)
 * - [toEntity]: RecognitionResult → HistoryEntity (for saving)
 */
object EntityMapper {

    /**
     * Convert a Room entity to a domain HistoryItem, including top predictions.
     */
    fun toDomain(entity: HistoryEntity): HistoryItem {
        return HistoryItem(
            id = entity.id,
            fruitCategory = try {
                FruitCategory.valueOf(entity.fruitCategory)
            } catch (e: IllegalArgumentException) {
                FruitCategory.UNKNOWN
            },
            freshnessLevel = try {
                FreshnessLevel.valueOf(entity.freshnessLevel)
            } catch (e: IllegalArgumentException) {
                FreshnessLevel.UNCERTAIN
            },
            confidence = entity.confidence,
            timestamp = entity.timestamp,
            thumbnailPath = entity.thumbnailPath,
            inferenceTimeMs = entity.inferenceTimeMs,
            topPredictions = jsonToPredictions(entity.topPredictionsJson),
            displayName = entity.displayName,
            isCookable = entity.isCookable,
            sessionId = entity.sessionId
        )
    }

    /**
     * Convert a RecognitionResult to a Room entity for persistence.
     *
     * Top-3 predictions are serialized to a JSON array string.
     * v2 fields (sessionId, displayName, isCookable) use defaults
     * for backward compatibility with v1 results.
     */
    fun toEntity(result: RecognitionResult): HistoryEntity {
        return HistoryEntity(
            id = result.id,
            sessionId = "",  // v1: no session grouping
            fruitCategory = result.fruitCategory.name,
            freshnessLevel = result.freshnessLevel.name,
            confidence = result.confidence,
            displayName = "",  // v1: no display name
            isCookable = false,  // v1: unknown cookability
            inferenceTimeMs = result.inferenceTimeMs,
            timestamp = result.timestamp,
            thumbnailPath = result.thumbnailPath,
            topPredictionsJson = predictionsToJson(result.topPredictions)
        )
    }

    /**
     * Convert a HistoryItem directly to a Room entity for persistence.
     * Avoids the intermediate RecognitionResult creation in save(HistoryItem).
     */
    fun toEntityFromItem(item: HistoryItem): HistoryEntity {
        return HistoryEntity(
            id = item.id,
            sessionId = item.sessionId,
            fruitCategory = item.fruitCategory.name,
            freshnessLevel = item.freshnessLevel.name,
            confidence = item.confidence,
            displayName = item.displayName,
            isCookable = item.isCookable,
            inferenceTimeMs = item.inferenceTimeMs,
            timestamp = item.timestamp,
            thumbnailPath = item.thumbnailPath,
            topPredictionsJson = predictionsToJson(item.topPredictions)
        )
    }

    /**
     * Convert a v2 DetectedItem to a Room entity for persistence.
     *
     * Unlike the v1 path, this preserves all v2-specific fields
     * (sessionId, displayName, isCookable) that are set to defaults
     * in the v1 [toEntity] / [toEntityFromItem] methods.
     *
     * @param item DetectedItem from the 3-stage inference pipeline.
     * @param sessionId UUID shared by all items in a single scan session.
     * @param inferenceTimeMs Total inference time for the full pipeline.
     * @param thumbnailPath Optional thumbnail file path, or null.
     */
    fun toEntityFromDetectedItem(
        item: DetectedItem,
        sessionId: String,
        inferenceTimeMs: Long,
        thumbnailPath: String? = null
    ): HistoryEntity {
        val fruitCategory = mapLabelToFruitCategory(item.label)

        return HistoryEntity(
            id = item.id,
            sessionId = sessionId,
            fruitCategory = fruitCategory.name,
            freshnessLevel = item.freshnessLevel.name,
            confidence = item.confidence,
            displayName = item.displayName,
            isCookable = item.isCookable,
            inferenceTimeMs = inferenceTimeMs,
            timestamp = System.currentTimeMillis(),
            thumbnailPath = thumbnailPath,
            // Top predictions not available from the v2 pipeline
            // (only top-1 is computed).  Set to null for now.
            topPredictionsJson = null
        )
    }

    /**
     * Convert a list of entities to domain models.
     */
    fun toDomainList(entities: List<HistoryEntity>): List<HistoryItem> {
        return entities.map { toDomain(it) }
    }

    /**
     * Map a v2 260-class label to a v1 [FruitCategory] for history persistence.
     *
     * The v1 [FruitCategory] enum only has 9 categories. v2 260-class labels
     * (e.g., "Apple_Crimson_Snow", "Pepper_Green", "Bitter_Gourd") need to be
     * resolved to the closest v1 category for backward-compatible storage.
     *
     * Strategy:
     * 1. Exact match: e.g., "orange" → ORANGE, "apple" → APPLE (v1 degradation path)
     * 2. Prefix match: e.g., "Apple_Crimson_Snow" → APPLE, "Cucumber_Ripe" → CUCUMBER
     * 3. De-underscored match: e.g., "Bitter_Gourd" → BITTER_GOURD
     * 4. Known mappings: "Pepper" → CAPSICUM (v1 uses CAPSICUM for peppers)
     * 5. Fallback: UNKNOWN (the [displayName] field still preserves the correct name)
     *
     * @param label Raw label from [DetectedItem.label] (260-class format or v1 enum name).
     * @return Best-matching [FruitCategory].
     */
    fun mapLabelToFruitCategory(label: String): FruitCategory {
        val upper = label.uppercase()

        // 1. Exact match (handles v1 degradation path: "apple", "orange", etc.)
        try {
            return FruitCategory.valueOf(upper)
        } catch (_: IllegalArgumentException) { /* continue */ }

        // 2. Prefix match (handles "Apple_Crimson_Snow" → "APPLE")
        for (category in FruitCategory.entries) {
            if (category == FruitCategory.UNKNOWN) continue
            if (upper.startsWith(category.name)) return category
        }

        // 3. De-underscored match (handles "Bitter_Gourd" → "BITTER_GOURD")
        val noUnderscore = upper.replace("_", "")
        for (category in FruitCategory.entries) {
            if (category == FruitCategory.UNKNOWN) continue
            if (noUnderscore.startsWith(category.name.replace("_", ""))) return category
        }

        // 4. Known mappings for labels that don't match v1 naming
        if (upper.startsWith("PEPPER")) return FruitCategory.CAPSICUM

        // 5. Fallback — displayName in the entity preserves the correct name
        return FruitCategory.UNKNOWN
    }

    // --- Private helpers ---

    /**
     * Serialize a list of Predictions to a compact JSON string.
     *
     * Format: [{"l":"fresh_apple","d":"苹果-新鲜","c":0.923}, ...]
     * Uses short keys to minimize storage size. org.json handles proper escaping.
     */
    private fun predictionsToJson(predictions: List<Prediction>): String {
        if (predictions.isEmpty()) return "[]"
        val jsonArray = JSONArray()
        predictions.forEach { p ->
            val obj = JSONObject()
            obj.put("l", p.label)
            obj.put("d", p.displayName)
            obj.put("c", p.confidence.toDouble())
            jsonArray.put(obj)
        }
        return jsonArray.toString()
    }

    /**
     * Parse a JSON string back into a list of Predictions.
     */
    private fun jsonToPredictions(json: String?): List<Prediction> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val jsonArray = JSONArray(json)
            (0 until jsonArray.length()).map { i ->
                val obj = jsonArray.getJSONObject(i)
                Prediction(
                    label = obj.optString("l", ""),
                    displayName = obj.optString("d", ""),
                    confidence = obj.optDouble("c", 0.0).toFloat()
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
