package com.example.freshscan.data.produce

import android.content.Context
import com.example.freshscan.data.ai.AIService
import com.example.freshscan.data.recipe.LabelNormalizer
import com.example.freshscan.domain.model.NutritionFacts
import com.example.freshscan.domain.model.ProduceInfo
import com.example.freshscan.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections
import java.util.LinkedHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProduceInfoEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val aiService: AIService,
    private val labelNormalizer: LabelNormalizer
) {
    /** LRU cache for AI-extended info (max 50 entries). */
    private val aiCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, ProduceInfo>(50, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, ProduceInfo>?
            ): Boolean = size > 50
        }
    )

    private val coreInfoCache: Map<String, ProduceInfo> by lazy { loadCoreInfo() }

    /**
     * Get produce info for a raw label. Emits core info immediately,
     * then emits again with AI extension when network is available.
     */
    fun getInfo(label: String): Flow<ProduceInfo> = flow {
        // Resolve raw label → normalized category name
        val categoryName = labelNormalizer.normalize(label).firstOrNull() ?: label
        val coreInfo = getCoreInfo(categoryName)
        emit(coreInfo)

        // Check AI cache
        aiCache[categoryName]?.let { cached ->
            if (cached.selectionTips != null) { emit(cached); return@flow }
        }

        try {
            val aiInfo = fetchAIExtension(coreInfo)
            aiCache[categoryName] = aiInfo
            emit(aiInfo)
        } catch (e: Exception) {
            Logger.w("ProduceInfoEngine", "AI extension failed: ${e.message}")
        }
    }

    fun getCoreInfo(categoryName: String): ProduceInfo {
        return coreInfoCache[categoryName]
            ?: ProduceInfo(categoryName, categoryName, "", "",
                NutritionFacts(0, 0f, 0f, 0f, 0f),
                emptyList(), "", "")
    }

    /**
     * Search produce names by query string. Returns matching display names.
     * Used by the produce encyclopedia search bar in FridgeRecipesScreen (v4.1).
     */
    fun searchProduce(query: String): List<String> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return coreInfoCache.values
            .filter { it.displayName.contains(q, ignoreCase = true) || it.label.contains(q, ignoreCase = true) }
            .map { it.displayName }
            .distinct()
            .take(10)
    }

    /**
     * Return ALL produce info entries from the in-memory cache.
     * Used by CollectionScreen to render the full 260-type grid (unlocked + locked).
     */
    fun getAllCoreInfo(): List<ProduceInfo> = coreInfoCache.values.toList()

    private fun loadCoreInfo(): Map<String, ProduceInfo> = try {
        val stream = context.assets.open(CORE_INFO_ASSET_PATH)
        val jsonStr = stream.bufferedReader().use { it.readText() }
        val array = JSONArray(jsonStr)
        val result = mutableMapOf<String, ProduceInfo>()
        for (i in 0 until array.length()) {
            try {
                val obj = array.getJSONObject(i)
                if (!obj.has("nutrition")) {
                    Logger.w("ProduceInfoEngine", "Skipping entry $i: missing nutrition")
                    continue
                }
                val nutrition = obj.getJSONObject("nutrition")
                val benefits = (0 until (obj.optJSONArray("healthBenefits")?.length() ?: 0))
                    .map { j -> obj.getJSONArray("healthBenefits").getString(j) }
                val label = obj.optString("label", "")
                if (label.isEmpty()) {
                    Logger.w("ProduceInfoEngine", "Skipping entry $i: missing label")
                    continue
                }
                result[label] = ProduceInfo(
                    label = label,
                    displayName = obj.optString("displayName", label),
                    category = obj.optString("category", ""),
                    intro = obj.optString("intro", ""),
                    nutrition = NutritionFacts(
                        caloriesKcal = nutrition.optInt("caloriesKcal", 0),
                        proteinG = nutrition.optDouble("proteinG", 0.0).toFloat(),
                        carbsG = nutrition.optDouble("carbsG", 0.0).toFloat(),
                        fatG = nutrition.optDouble("fatG", 0.0).toFloat(),
                        fiberG = nutrition.optDouble("fiberG", 0.0).toFloat(),
                        vitaminCMg = nutrition.optDouble("vitaminCMg", -1.0)
                            .takeIf { it >= 0 }?.toFloat(),
                        vitaminAUg = nutrition.optDouble("vitaminAUg", -1.0)
                            .takeIf { it >= 0 }?.toFloat(),
                        potassiumMg = nutrition.optDouble("potassiumMg", -1.0)
                            .takeIf { it >= 0 }?.toFloat(),
                        glycemicIndex = nutrition.optInt("glycemicIndex", -1)
                            .takeIf { it >= 0 }
                    ),
                    healthBenefits = benefits,
                    storageTips = obj.optString("storageTips", ""),
                    seasonality = obj.optString("seasonality", "")
                )
            } catch (e: Exception) {
                Logger.w("ProduceInfoEngine", "Skipping malformed entry $i: ${e.message}")
            }
        }
        Logger.i("ProduceInfoEngine", "Loaded ${result.size} produce info entries")
        result
    } catch (e: Exception) {
        Logger.e("ProduceInfoEngine", "Failed to load produce_info.json", e)
        emptyMap()
    }

    private suspend fun fetchAIExtension(core: ProduceInfo): ProduceInfo = withContext(Dispatchers.IO) {
        val systemPrompt = "你是一名资深营养学家和食材专家。用简洁中文回答，严格遵循 JSON 格式。"

        val isCoreIncomplete = core.intro.isBlank() && core.nutrition.caloriesKcal == 0

        val userMessage = if (isCoreIncomplete) {
            """请提供「${core.displayName}」的以下信息，输出纯 JSON：
{"intro": "50字简介", "nutrition": {"caloriesKcal": 0, "proteinG": 0.0, "carbsG": 0.0, "fatG": 0.0, "fiberG": 0.0, "potassiumMg": 0}, "storageTips": "保存方法", "seasonality": "时令月份", "selection_tips": "挑选技巧", "pairing": ["搭配1", "搭配2"], "fun_fact": "趣味知识"}
（nutrition 中数值为每100g参考值，未知字段可省略）"""
        } else {
            "请介绍：${core.displayName}（${core.category}，时令${core.seasonality}）"
        }

        val result = aiService.chatJson(systemPrompt, userMessage)
        if (result.isSuccess) parseAIExtension(result.getOrThrow(), core, isCoreIncomplete) else core
    }

    private fun parseAIExtension(jsonStr: String, core: ProduceInfo, fillCoreFields: Boolean): ProduceInfo = try {
        val obj = JSONObject(jsonStr)

        // AI extension fields (always parsed)
        val selectionTips = obj.optString("selection_tips", "").ifEmpty { null }
        val pairingSuggestions = obj.optJSONArray("pairing")?.let { arr ->
            (0 until arr.length()).map { i -> arr.getString(i) }
        }
        val funFact = obj.optString("fun_fact", "").ifEmpty { null }

        if (fillCoreFields) {
            // Merge AI-generated core info with the empty stub
            val intro = obj.optString("intro", "").ifEmpty { core.intro }
            val storageTips = obj.optString("storageTips", "").ifEmpty { core.storageTips }
            val seasonality = obj.optString("seasonality", "").ifEmpty { core.seasonality }

            val nutritionObj = obj.optJSONObject("nutrition")
            val nutrition = if (nutritionObj != null) {
                NutritionFacts(
                    caloriesKcal = nutritionObj.optInt("caloriesKcal", 0),
                    proteinG = nutritionObj.optDouble("proteinG", 0.0).toFloat(),
                    carbsG = nutritionObj.optDouble("carbsG", 0.0).toFloat(),
                    fatG = nutritionObj.optDouble("fatG", 0.0).toFloat(),
                    fiberG = nutritionObj.optDouble("fiberG", 0.0).toFloat(),
                    vitaminCMg = nutritionObj.optDouble("vitaminCMg", -1.0)
                        .takeIf { it >= 0 }?.toFloat(),
                    vitaminAUg = nutritionObj.optDouble("vitaminAUg", -1.0)
                        .takeIf { it >= 0 }?.toFloat(),
                    potassiumMg = nutritionObj.optDouble("potassiumMg", -1.0)
                        .takeIf { it >= 0 }?.toFloat(),
                    glycemicIndex = nutritionObj.optInt("glycemicIndex", -1)
                        .takeIf { it >= 0 }
                )
            } else core.nutrition

            core.copy(
                intro = intro,
                nutrition = nutrition,
                storageTips = storageTips,
                seasonality = seasonality,
                selectionTips = selectionTips,
                pairingSuggestions = pairingSuggestions,
                funFact = funFact
            )
        } else {
            core.copy(
                selectionTips = selectionTips,
                pairingSuggestions = pairingSuggestions,
                funFact = funFact
            )
        }
    } catch (e: Exception) {
        Logger.w("ProduceInfoEngine", "Failed to parse AI extension: ${e.message}")
        core
    }

    companion object {
        const val CORE_INFO_ASSET_PATH = "produce_info.json"
    }
}
