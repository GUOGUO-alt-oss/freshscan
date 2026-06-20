package com.example.freshscan.data.diet

import com.example.freshscan.data.ai.AIService
import com.example.freshscan.data.history.MealHistoryDao
import com.example.freshscan.data.history.MealHistoryEntity
import com.example.freshscan.domain.model.Ingredient
import com.example.freshscan.domain.model.MealSuggestion
import com.example.freshscan.domain.model.UserProfile
import com.example.freshscan.util.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MealQueryEngine @Inject constructor(
    private val aiService: AIService,
    private val mealHistoryDao: MealHistoryDao
) {
    /**
     * Query AI for a single meal suggestion based on user input and profile.
     * Uses chat() (qwen-turbo, maxTokens=1024) for fast response.
     * Result is automatically saved to meal history.
     */
    fun queryMeal(query: String, profile: UserProfile?): Flow<MealSuggestion> = flow {
        Logger.d("MealQueryEngine", "Querying meal for: $query")
        // Query recent history to avoid recommending the same dishes
        val recentDishes = mealHistoryDao.getRecentTitles()
        Logger.d("MealQueryEngine", "Recent dishes to exclude: $recentDishes")
        val result = aiService.chat(
            systemPrompt = "你是一名专业的中国营养师和厨师。根据用户的需求和用户信息，推荐一道菜。" +
                "第一行必须以菜名：开头，不要说任何多余的话，不要加开头语、结尾语或解释。" +
                "不要使用代码块标记，不要使用加粗标记，不要使用中括号，不要使用markdown格式。" +
                "严格按照指定格式逐行输出。",
            userMessage = buildQueryPrompt(query, profile, recentDishes)
        )
        if (result.isFailure) throw result.exceptionOrNull()
            ?: IllegalStateException("AI service returned empty failure")
        val suggestion = parseMealSuggestion(result.getOrThrow(), query)
        // Save to history
        val ninetyDaysAgo = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000
        mealHistoryDao.deleteOlderThan(ninetyDaysAgo)
        mealHistoryDao.insert(toEntity(suggestion))
        Logger.i("MealQueryEngine", "Meal suggestion saved: ${suggestion.title}")
        emit(suggestion)
    }

    /** Get all meal history as domain objects. */
    fun getHistory(): Flow<List<MealSuggestion>> =
        mealHistoryDao.getAll().map { entities -> entities.map { toDomain(it) } }

    suspend fun deleteHistoryItem(id: String) {
        mealHistoryDao.deleteById(id)
    }

    suspend fun clearHistory() {
        mealHistoryDao.deleteAll()
    }

    // ─── Prompt building ─────────────────────────────────────────────────

    private fun buildQueryPrompt(query: String, profile: UserProfile?, recentDishes: List<String>): String = buildString {
        appendLine("用户需求：$query")
        // Tell AI to avoid recently recommended dishes
        if (recentDishes.isNotEmpty()) {
            appendLine("以下菜品最近已经推荐过，请不要重复推荐：${recentDishes.joinToString("、")}")
            appendLine("请推荐一道与上述完全不同的菜品。")
        }
        if (profile != null) {
            appendLine()
            appendLine("【用户信息】")
            appendLine("- 年龄：${profile.age}岁，性别：${profile.gender.label}")
            appendLine("- 身高：${profile.heightCm}cm，体重：${profile.weightKg}kg")
            appendLine("- 活动量：${profile.activityLevel.label}")
            appendLine("- 健康目标：${profile.goal.label}")
            if (profile.excludedIngredients.isNotEmpty())
                appendLine("- 忌口：${profile.excludedIngredients.joinToString("、")}")
            if (profile.allergies.isNotEmpty())
                appendLine("- 过敏原：${profile.allergies.joinToString("、")}")
            val cal = profile.calorieTarget ?: calculateTDEE(profile)
            appendLine("- 每日热量参考：${cal}kcal")
        }
        appendLine()
        appendLine("【输出格式】请严格按以下格式输出，每行以冒号分隔：")
        appendLine("菜名：（一道中国家常菜名）")
        appendLine("食材：（用顿号分隔，每项含用量，如：鸡胸肉200g、西兰花150g）")
        appendLine("步骤：（用数字编号，如：1.鸡胸肉切丁 2.焯水 3.翻炒调味）")
        appendLine("烹饪时间：（分钟数，只写数字）")
        appendLine("热量：（kcal数，只写数字）")
        appendLine("蛋白质：（克数，只写数字）")
        appendLine("碳水：（克数，只写数字）")
        appendLine("脂肪：（克数，只写数字）")
    }

    internal fun calculateTDEE(profile: UserProfile): Int {
        val maleBMR = 10 * profile.weightKg + 6.25f * profile.heightCm - 5 * profile.age + 5
        val femaleBMR = 10 * profile.weightKg + 6.25f * profile.heightCm - 5 * profile.age - 161
        val bmr = when (profile.gender) {
            com.example.freshscan.domain.model.Gender.MALE -> maleBMR
            com.example.freshscan.domain.model.Gender.FEMALE -> femaleBMR
            com.example.freshscan.domain.model.Gender.UNSPECIFIED -> (maleBMR + femaleBMR) / 2
        }
        val tdee = bmr * profile.activityLevel.factor
        return when (profile.goal) {
            com.example.freshscan.domain.model.HealthGoal.LOSE_WEIGHT -> (tdee - 400).toInt()
            com.example.freshscan.domain.model.HealthGoal.BUILD_MUSCLE -> (tdee + 400).toInt()
            else -> tdee.toInt()
        }
    }

    // ─── Response parsing ────────────────────────────────────────────────

    /**
     * Parse the AI's plain-text response into a MealSuggestion.
     * Handles markdown code fences, bold markers, and various colon styles.
     */
    internal fun parseMealSuggestion(rawText: String, query: String): MealSuggestion {
        Logger.d("MealQueryEngine", "Raw AI response:\n$rawText")

        // Strip markdown code fences (```json, ```, etc.)
        val cleaned = stripCodeFences(rawText)

        // If AI output everything on one line, split at known labels
        val structured = if (cleaned.count { it == '\n' } < 2) {
            Logger.d("MealQueryEngine", "Single-line response detected, splitting at labels")
            var text = cleaned
            val labels = listOf("食材", "步骤", "烹饪时间", "热量", "蛋白质", "碳水", "脂肪")
            for (label in labels) {
                text = text.replace("$label：", "\n$label：")
                text = text.replace("$label:", "\n$label:")
            }
            text.trimStart('\n')
        } else {
            cleaned
        }

        val lines = structured.lines().map { it.trim() }.filter { it.isNotEmpty() }

        var title = ""
        var ingredients = listOf<Ingredient>()
        var steps = listOf<String>()
        var cookingTimeMin = 0
        var calories = 0
        var proteinG = 0f
        var carbsG = 0f
        var fatG = 0f

        for (line in lines) {
            // Strip bold markers and brackets: **菜名**：xxx → 菜名：xxx, 【菜名：xxx】 → 菜名：xxx
            val normalized = line.replace("**", "").replace("##", "")
                .replace("【", "").replace("】", "").trim()

            when {
                extractValue(normalized, "菜名", "推荐菜名", "推荐菜品", "菜品名称", "菜名称")?.let { title = it.replace("【", "").replace("】", ""); true } == true -> {}
                extractValue(normalized, "食材")?.let { raw ->
                    ingredients = raw.split("、", "，", ",").map { item ->
                        val trimmed = item.trim()
                        val match = Regex("^(.+?)(\\d+.*)$").find(trimmed)
                        if (match != null) {
                            Ingredient(match.groupValues[1], match.groupValues[2])
                        } else {
                            Ingredient(trimmed, "适量")
                        }
                    }
                    true
                } == true -> {}
                extractValue(normalized, "步骤")?.let { raw ->
                    steps = raw.split(Regex("\\d+[.、．]"))
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    true
                } == true -> {}
                extractValue(normalized, "烹饪时间", "时间")?.let {
                    cookingTimeMin = it.replace(Regex("[^\\d]"), "").toIntOrNull() ?: 0
                    true
                } == true -> {}
                extractValue(normalized, "热量", "卡路里")?.let {
                    calories = it.replace(Regex("[^\\d]"), "").toIntOrNull() ?: 0
                    true
                } == true -> {}
                extractValue(normalized, "蛋白质")?.let {
                    proteinG = it.replace(Regex("[^\\d.]"), "").toFloatOrNull() ?: 0f
                    true
                } == true -> {}
                extractValue(normalized, "碳水", "碳水化合物")?.let {
                    carbsG = it.replace(Regex("[^\\d.]"), "").toFloatOrNull() ?: 0f
                    true
                } == true -> {}
                extractValue(normalized, "脂肪")?.let {
                    fatG = it.replace(Regex("[^\\d.]"), "").toFloatOrNull() ?: 0f
                    true
                } == true -> {}
            }
        }

        // Fallback: if no "菜名：" label found, try first line as dish name
        if (title.isBlank() && lines.isNotEmpty()) {
            val firstLine = lines[0].replace("【", "").replace("】", "")
                .replace("**", "").replace("##", "")
                .trimEnd('：', ':', ' ', ' ')
            // If line contains a colon, take part before it as candidate title
            val candidate = when {
                firstLine.contains("：") -> firstLine.substringBefore("：").trim()
                firstLine.contains(":") -> firstLine.substringBefore(":").trim()
                else -> firstLine
            }
            val knownLabels = listOf("食材", "步骤", "烹饪时间", "时间", "热量", "卡路里",
                "蛋白质", "碳水", "碳水化合物", "脂肪", "菜名", "推荐菜名", "推荐菜品")
            val startsWithLabel = knownLabels.any { candidate.startsWith(it) }
            val fillerPrefixes = listOf("好的", "根据", "为您", "以下", "上面", "推荐", "没问题", "当然")
            val isConversational = fillerPrefixes.any { candidate.startsWith(it) }
            if (candidate.isNotBlank() && candidate.length <= 30 && !isConversational && !startsWithLabel) {
                Logger.d("MealQueryEngine", "Using first line as title fallback: $candidate")
                title = candidate
            }
        }

        if (title.isBlank()) {
            val preview = cleaned.take(100).replace("\n", " ")
            throw MealQueryParseException("无法从AI回复中解析菜名，内容：$preview")
        }

        return MealSuggestion(
            id = UUID.randomUUID().toString(),
            query = query,
            title = title,
            ingredients = ingredients,
            steps = steps,
            cookingTimeMin = cookingTimeMin,
            calories = calories,
            proteinG = proteinG,
            carbsG = carbsG,
            fatG = fatG
        )
    }

    /**
     * Extract the value after a label like "菜名：" or "菜名:".
     * Tries multiple label variants. Returns null if no match.
     */
    private fun extractValue(line: String, vararg labels: String): String? {
        for (label in labels) {
            val idxFull = line.indexOf("$label：")
            val idxHalf = line.indexOf("$label:")
            val idx = when {
                idxFull >= 0 && idxHalf >= 0 -> minOf(idxFull, idxHalf)
                idxFull >= 0 -> idxFull
                idxHalf >= 0 -> idxHalf
                else -> continue
            }
            // Extract everything after "label：" or "label:"
            val afterLabel = line.substring(idx + label.length)
            return afterLabel.removePrefix("：").removePrefix(":").trim()
        }
        return null
    }

    /** Strip markdown code fences from AI response (line-by-line). */
    private fun stripCodeFences(text: String): String {
        return text.lines()
            .filter { line ->
                val trimmed = line.trim()
                // Remove lines that are just code fence markers (```, ```json, ```text, etc.)
                !trimmed.matches(Regex("```\\w*"))
            }
            .joinToString("\n")
    }

    // ─── Entity mapping ──────────────────────────────────────────────────

    private fun toEntity(s: MealSuggestion): MealHistoryEntity {
        val ingJson = JSONArray().apply {
            s.ingredients.forEach {
                put(JSONObject().apply {
                    put("name", it.name)
                    put("amount", it.amount)
                })
            }
        }.toString()
        val stepsJson = JSONArray().apply {
            s.steps.forEach { put(it) }
        }.toString()
        return MealHistoryEntity(
            id = s.id,
            query = s.query,
            title = s.title,
            ingredientsJson = ingJson,
            stepsJson = stepsJson,
            cookingTimeMin = s.cookingTimeMin,
            calories = s.calories,
            proteinG = s.proteinG.toDouble(),
            carbsG = s.carbsG.toDouble(),
            fatG = s.fatG.toDouble(),
            generatedAt = s.generatedAt
        )
    }

    private fun toDomain(e: MealHistoryEntity): MealSuggestion {
        val ingredients = mutableListOf<Ingredient>()
        val ingArr = JSONArray(e.ingredientsJson)
        for (i in 0 until ingArr.length()) {
            val obj = ingArr.optJSONObject(i) ?: continue
            ingredients.add(Ingredient(
                obj.optString("name", ""),
                obj.optString("amount", "适量")
            ))
        }
        val steps = mutableListOf<String>()
        val stepsArr = JSONArray(e.stepsJson)
        for (i in 0 until stepsArr.length()) {
            steps.add(stepsArr.optString(i, ""))
        }
        return MealSuggestion(
            id = e.id,
            query = e.query,
            title = e.title,
            ingredients = ingredients,
            steps = steps,
            cookingTimeMin = e.cookingTimeMin,
            calories = e.calories,
            proteinG = e.proteinG.toFloat(),
            carbsG = e.carbsG.toFloat(),
            fatG = e.fatG.toFloat(),
            generatedAt = e.generatedAt
        )
    }
}

class MealQueryParseException(message: String, cause: Throwable? = null) :
    Exception(message, cause)