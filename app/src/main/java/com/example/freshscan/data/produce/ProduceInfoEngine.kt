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
    private val aiCache = object : LinkedHashMap<String, ProduceInfo>(50, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, ProduceInfo>?
        ): Boolean = size > 50
    }

    @Volatile private var coreInfoCache: Map<String, ProduceInfo>? = null

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
        return ensureCoreInfoLoaded()[categoryName]
            ?: ProduceInfo(categoryName, categoryName, "", "",
                NutritionFacts(0, 0f, 0f, 0f, 0f),
                emptyList(), "", "")
    }

    private fun ensureCoreInfoLoaded(): Map<String, ProduceInfo> {
        return coreInfoCache ?: loadCoreInfo().also { coreInfoCache = it }
    }

    private fun loadCoreInfo(): Map<String, ProduceInfo> = try {
        val stream = context.assets.open(CORE_INFO_ASSET_PATH)
        val jsonStr = stream.bufferedReader().use { it.readText() }
        val array = JSONArray(jsonStr)
        val result = mutableMapOf<String, ProduceInfo>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val nutrition = obj.getJSONObject("nutrition")
            val benefits = (0 until obj.getJSONArray("healthBenefits").length())
                .map { j -> obj.getJSONArray("healthBenefits").getString(j) }
            result[obj.getString("label")] = ProduceInfo(
                label = obj.getString("label"),
                displayName = obj.getString("displayName"),
                category = obj.getString("category"),
                intro = obj.getString("intro"),
                nutrition = NutritionFacts(
                    caloriesKcal = nutrition.getInt("caloriesKcal"),
                    proteinG = nutrition.getDouble("proteinG").toFloat(),
                    carbsG = nutrition.getDouble("carbsG").toFloat(),
                    fatG = nutrition.getDouble("fatG").toFloat(),
                    fiberG = nutrition.getDouble("fiberG").toFloat(),
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
                storageTips = obj.getString("storageTips"),
                seasonality = obj.getString("seasonality")
            )
        }
        Logger.i("ProduceInfoEngine", "Loaded ${result.size} produce info entries")
        result
    } catch (e: Exception) {
        Logger.e("ProduceInfoEngine", "Failed to load produce_info.json", e)
        emptyMap()
    }

    private suspend fun fetchAIExtension(core: ProduceInfo): ProduceInfo = withContext(Dispatchers.IO) {
        val systemPrompt = "你是一名资深营养学家和食材专家。用简洁中文回答，严格遵循 JSON 格式。"
        val userMessage = "请介绍：${core.displayName}（${core.category}，时令${core.seasonality}）"
        val result = aiService.chatJson(systemPrompt, userMessage)
        if (result.isSuccess) parseAIExtension(result.getOrThrow(), core) else core
    }

    private fun parseAIExtension(jsonStr: String, core: ProduceInfo): ProduceInfo = try {
        val obj = JSONObject(jsonStr)
        core.copy(
            selectionTips = obj.optString("selection_tips", "").ifEmpty { null },
            pairingSuggestions = obj.optJSONArray("pairing")?.let { arr ->
                (0 until arr.length()).map { i -> arr.getString(i) }
            },
            funFact = obj.optString("fun_fact", "").ifEmpty { null }
        )
    } catch (e: Exception) {
        Logger.w("ProduceInfoEngine", "Failed to parse AI extension: ${e.message}")
        core
    }

    companion object {
        const val CORE_INFO_ASSET_PATH = "produce_info.json"
    }
}
