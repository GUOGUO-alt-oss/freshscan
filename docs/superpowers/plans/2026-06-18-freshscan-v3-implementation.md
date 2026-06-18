# FreshScan v3.0 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add produce encyclopedia (local + AI), upgrade taste profile to personalized health profile with AI-generated 7-day diet plans, and integrate DashScope Qwen API via abstract AI service layer.

**Architecture:** New Data/AI layer (AIService interface → QwenAIService), ProduceInfoEngine (local JSON + AI extension), DietPlanEngine (AI diet plan generation → Room persistence → shopping list). PersonalizeScreen replaces TasteProfileScreen. ProduceInfoSheet extends AnalysisScreen BottomSheet. DietPlanScreen shows 7-day meal plans.

**Tech Stack:** Kotlin 2.0.21, Compose (Material3), Hilt DI, Room, OkHttp 4.x, Coroutines+Flow, DashScope API (qwen-turbo/qwen-plus)

## Global Constraints

- `compileSdk = 36`, `minSdk = 24`, `targetSdk = 36`
- JVM target: Java 11, Kotlin 2.0.21
- All user-facing strings → `strings.xml` resources
- Build verification after every task: `./gradlew assembleDebug`
- Test verification: `./gradlew :app:testDebugUnitTest`
- Test dependencies: `testOptions.unitTests.isReturnDefaultValues = true`, `org.json:json:20231013`
- No hardcoded Chinese strings in Kotlin code
- API Key via `BuildConfig.AI_API_KEY` (gradle property), not in source

---

## Phase 1: Data Infrastructure

### Task 1: Dependencies + Build Config

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `gradle.properties`

**Interfaces:**
- Produces: `libs.okhttp` version catalog entry available for all modules; `AI_API_KEY` in BuildConfig

- [ ] **Step 1: Add OkHttp to version catalog**

In `gradle/libs.versions.toml`, add under `[versions]`:
```toml
okhttp = "4.12.0"
```

Add under `[libraries]`:
```toml
okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
```

- [ ] **Step 2: Add OkHttp dependency to app build.gradle.kts**

In `app/build.gradle.kts`, add after the existing `implementation(libs.mediapipe.tasks.vision)` line:
```kotlin
    // v3: Networking for AI API
    implementation(libs.okhttp)
```

- [ ] **Step 3: Add AI_API_KEY to gradle.properties**

Append to `gradle.properties`:
```properties
# v3: AI API key for DashScope / Qwen
# Set this via ~/.gradle/gradle.properties locally, never commit real keys
AI_API_KEY=
```

- [ ] **Step 4: Add BuildConfig field for API key**

In `app/build.gradle.kts`, inside `defaultConfig` block, after `buildConfigField("boolean", "ENABLE_VERBOSE_LOGGING", "true")` add:
```kotlin
            buildConfigField("String", "AI_API_KEY", "\"${findProperty("AI_API_KEY") ?: ""}\"")
```

Also add the same line in the `release` block (without verbose logging):
```kotlin
            buildConfigField("String", "AI_API_KEY", "\"${findProperty("AI_API_KEY") ?: ""}\"")
```

- [ ] **Step 5: Build verification**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL (no actual API key yet, but BuildConfig field exists)

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts gradle.properties
git commit -m "feat(v3): add OkHttp dependency and AI_API_KEY build config

- Add OkHttp 4.12.0 to version catalog
- Add AI_API_KEY BuildConfig field from gradle property
- API key sourced from ~/.gradle/gradle.properties, never in source

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2: Domain Models

**Files:**
- Create: `app/src/main/java/com/example/freshscan/domain/model/ProduceInfo.kt`
- Create: `app/src/main/java/com/example/freshscan/domain/model/UserProfile.kt`
- Create: `app/src/main/java/com/example/freshscan/domain/model/DietPlan.kt`

**Interfaces:**
- Produces: `ProduceInfo`, `NutritionFacts`, `UserProfile`, `ActivityLevel`, `HealthGoal`, `Gender`, `DietPlan`, `DailyMealPlan`, `Meal`, `MealType`, `DietRecipe` — all domain types used by data layer and UI

- [ ] **Step 1: Create ProduceInfo.kt**

```kotlin
package com.example.freshscan.domain.model

/**
 * Detailed produce information combining local preset data and AI-extended content.
 *
 * Core fields (intro, nutrition, healthBenefits, storageTips, seasonality)
 * are loaded from assets/produce_info.json and available offline immediately.
 *
 * AI-extended fields (selectionTips, pairingSuggestions, funFact) are loaded
 * on-demand via AIService when network is available.
 */
data class ProduceInfo(
    val label: String,               // Normalized type name, e.g. "苹果"
    val displayName: String,         // "红粉苹果"
    val category: String,            // "水果" / "蔬菜"

    // ── Local preset (offline, instant) ──
    val intro: String,               // Introduction (80-120 chars)
    val nutrition: NutritionFacts,   // Per 100g
    val healthBenefits: List<String>, // Health benefits (3-5 items)
    val storageTips: String,         // Storage advice
    val seasonality: String,         // Season, e.g. "9-11月"

    // ── AI extended (online, lazy loaded) ──
    val selectionTips: String? = null,
    val pairingSuggestions: List<String>? = null,
    val funFact: String? = null
)

/**
 * Nutritional facts per 100g edible portion.
 * Optional fields use null when data is unavailable for a specific produce item.
 */
data class NutritionFacts(
    val caloriesKcal: Int,
    val proteinG: Float,
    val carbsG: Float,
    val fatG: Float,
    val fiberG: Float,
    val vitaminCMg: Float? = null,
    val vitaminAUg: Float? = null,
    val potassiumMg: Float? = null,
    val glycemicIndex: Int? = null
)
```

- [ ] **Step 2: Create UserProfile.kt**

```kotlin
package com.example.freshscan.domain.model

/**
 * Personalized user profile for diet plan generation.
 *
 * Extends the v2 TasteProfile with body metrics, activity level,
 * health goals, and dietary constraints.
 *
 * Persisted in Room (user_profile table) as UserProfileEntity,
 * with JSON-serialized sets (excludedIngredients, preferredCategories, allergies).
 */
data class UserProfile(
    // ── Taste preferences (from v2 TasteProfile) ──
    val spiceLevel: Int = 0,                   // 0=不辣, 1=微辣, 2=中辣, 3=超辣
    val saltLevel: Int = 1,                    // 0=少盐, 1=正常, 2=偏咸
    val oilLevel: Int = 1,                     // 0=少油, 1=正常, 2=偏油
    val excludedIngredients: Set<String> = emptySet(),
    val preferredCategories: Set<RecipeCategory> = emptySet(),
    val maxCookingTimeMin: Int = 60,

    // ── v3: Body metrics ──
    val age: Int = 25,
    val heightCm: Int = 170,
    val weightKg: Float = 65f,
    val gender: Gender = Gender.UNSPECIFIED,
    val activityLevel: ActivityLevel = ActivityLevel.MODERATE,
    val goal: HealthGoal = HealthGoal.EAT_HEALTHY,
    val mealsPerDay: Int = 3,
    val calorieTarget: Int? = null,            // null = auto-calculate via BMR formula
    val allergies: Set<String> = emptySet()
)

enum class ActivityLevel(val label: String, val factor: Float) {
    SEDENTARY("久坐少动", 1.2f),
    LIGHT("轻度活动", 1.375f),
    MODERATE("中度活动", 1.55f),
    ACTIVE("积极运动", 1.725f),
    VERY_ACTIVE("高强度运动", 1.9f)
}

enum class HealthGoal(val label: String) {
    LOSE_WEIGHT("减脂瘦身"),
    BUILD_MUSCLE("增肌塑形"),
    MAINTAIN("维持体重"),
    EAT_HEALTHY("健康饮食"),
    MANAGE_BLOOD_SUGAR("控糖管理")
}

enum class Gender(val label: String) {
    MALE("男"), FEMALE("女"), UNSPECIFIED("未指定")
}
```

- [ ] **Step 3: Create DietPlan.kt**

```kotlin
package com.example.freshscan.domain.model

/**
 * AI-generated 7-day personalized diet plan.
 *
 * Generated by DietPlanEngine via Qwen API based on UserProfile.
 * Persisted as JSON columns in Room (diet_plans table).
 */
data class DietPlan(
    val id: String,                          // UUID
    val generatedAt: Long,                   // Timestamp
    val userProfileSnapshot: UserProfile,    // Profile used at generation time
    val dailyPlans: List<DailyMealPlan>,     // 7 days (Mon–Sun)
    val totalCaloriesAvg: Int,               // Average daily calorie target
    val nutritionSummary: String             // AI-generated nutrition summary
)

data class DailyMealPlan(
    val dayIndex: Int,          // 1-7
    val dayLabel: String,       // "周一" … "周日"
    val totalCalories: Int,
    val meals: List<Meal>,
    val notes: String? = null
)

data class Meal(
    val type: MealType,
    val recipe: DietRecipe
)

enum class MealType(val label: String) {
    BREAKFAST("早餐"),
    LUNCH("午餐"),
    DINNER("晚餐"),
    SNACK("加餐")
}

/**
 * A recipe within a diet plan meal.
 *
 * Similar to [Recipe] but generated by AI rather than presets.
 * Ingredients use the existing [Ingredient] domain model for
 * shopping list compatibility.
 */
data class DietRecipe(
    val title: String,
    val ingredients: List<Ingredient>,
    val steps: List<String>,         // 3-5 cooking steps
    val cookingTimeMin: Int,
    val calories: Int,
    val proteinG: Float,
    val carbsG: Float,
    val fatG: Float
)
```

- [ ] **Step 4: Build verification**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL (new domain models compile, no consumers yet)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/freshscan/domain/model/ProduceInfo.kt \
        app/src/main/java/com/example/freshscan/domain/model/UserProfile.kt \
        app/src/main/java/com/example/freshscan/domain/model/DietPlan.kt
git commit -m "feat(v3): add domain models for produce info, user profile, diet plan

- ProduceInfo + NutritionFacts: local core info + AI-extended fields
- UserProfile: extended taste profile with body metrics + health goals
- DietPlan/DailyMealPlan/Meal/DietRecipe: AI-generated meal plan models

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 3: Room Entities, DAOs, and Migration

**Files:**
- Create: `app/src/main/java/com/example/freshscan/data/history/DietPlanEntity.kt`
- Create: `app/src/main/java/com/example/freshscan/data/history/DietPlanDao.kt`
- Create: `app/src/main/java/com/example/freshscan/data/history/UserProfileEntity.kt`
- Create: `app/src/main/java/com/example/freshscan/data/history/UserProfileDao.kt`
- Modify: `app/src/main/java/com/example/freshscan/data/history/HistoryDatabase.kt`

**Interfaces:**
- Consumes: `DietPlan`, `DailyMealPlan`, `Meal`, `MealType`, `DietRecipe`, `UserProfile` domain types
- Produces: `DietPlanEntity`, `DietPlanDao`, `UserProfileEntity`, `UserProfileDao` — Room-persisted entities/DAOs

- [ ] **Step 1: Create DietPlanEntity.kt**

```kotlin
package com.example.freshscan.data.history

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "diet_plans")
data class DietPlanEntity(
    @PrimaryKey val id: String,
    val generatedAt: Long,
    val profileSnapshotJson: String,  // JSON-serialized UserProfile
    val dailyPlansJson: String,       // JSON-serialized List<DailyMealPlan>
    val totalCaloriesAvg: Int,
    val nutritionSummary: String
)
```

- [ ] **Step 2: Create DietPlanDao.kt**

```kotlin
package com.example.freshscan.data.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DietPlanDao {
    @Query("SELECT * FROM diet_plans ORDER BY generatedAt DESC LIMIT 1")
    suspend fun getLatest(): DietPlanEntity?

    @Query("SELECT * FROM diet_plans ORDER BY generatedAt DESC")
    fun getAll(): Flow<List<DietPlanEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(plan: DietPlanEntity)

    @Query("DELETE FROM diet_plans WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM diet_plans")
    suspend fun deleteAll()
}
```

- [ ] **Step 3: Create UserProfileEntity.kt**

```kotlin
package com.example.freshscan.data.history

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 1,        // Single-row table
    val spiceLevel: Int = 0,
    val saltLevel: Int = 1,
    val oilLevel: Int = 1,
    val excludedIngredients: String = "[]",   // JSON array
    val preferredCategories: String = "[]",   // JSON array
    val maxCookingTimeMin: Int = 60,
    val age: Int = 25,
    val heightCm: Int = 170,
    val weightKg: Float = 65f,
    val gender: String = "UNSPECIFIED",
    val activityLevel: String = "MODERATE",
    val goal: String = "EAT_HEALTHY",
    val mealsPerDay: Int = 3,
    val calorieTarget: Int? = null,
    val allergies: String = "[]"              // JSON array
)
```

- [ ] **Step 4: Create UserProfileDao.kt**

```kotlin
package com.example.freshscan.data.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun get(): Flow<UserProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: UserProfileEntity)
}
```

- [ ] **Step 5: Modify HistoryDatabase.kt — bump version, add entities, add migration**

Read the current file first to get exact content. In `HistoryDatabase.kt`:

Change `version = 2` to `version = 3`.

Add `UserProfileEntity::class` and `DietPlanEntity::class` to the `entities` list:
```kotlin
@Database(
    entities = [
        HistoryEntity::class,
        FavoriteRecipeEntity::class,
        ShoppingItemEntity::class,
        UserProfileEntity::class,     // v3
        DietPlanEntity::class         // v3
    ],
    version = 3,
    exportSchema = true
)
```

Add abstract methods:
```kotlin
    /** v3: User profile DAO. */
    abstract fun userProfileDao(): UserProfileDao

    /** v3: Diet plan DAO. */
    abstract fun dietPlanDao(): DietPlanDao
```

Add `MIGRATION_2_3` to companion object (after `MIGRATION_1_2`):
```kotlin
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS user_profile (
                        id INTEGER PRIMARY KEY NOT NULL DEFAULT 1,
                        spiceLevel INTEGER NOT NULL DEFAULT 0,
                        saltLevel INTEGER NOT NULL DEFAULT 1,
                        oilLevel INTEGER NOT NULL DEFAULT 1,
                        excludedIngredients TEXT NOT NULL DEFAULT '[]',
                        preferredCategories TEXT NOT NULL DEFAULT '[]',
                        maxCookingTimeMin INTEGER NOT NULL DEFAULT 60,
                        age INTEGER NOT NULL DEFAULT 25,
                        heightCm INTEGER NOT NULL DEFAULT 170,
                        weightKg REAL NOT NULL DEFAULT 65.0,
                        gender TEXT NOT NULL DEFAULT 'UNSPECIFIED',
                        activityLevel TEXT NOT NULL DEFAULT 'MODERATE',
                        goal TEXT NOT NULL DEFAULT 'EAT_HEALTHY',
                        mealsPerDay INTEGER NOT NULL DEFAULT 3,
                        calorieTarget INTEGER,
                        allergies TEXT NOT NULL DEFAULT '[]'
                    )
                """)

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS diet_plans (
                        id TEXT PRIMARY KEY NOT NULL,
                        generatedAt INTEGER NOT NULL,
                        profileSnapshotJson TEXT NOT NULL,
                        dailyPlansJson TEXT NOT NULL,
                        totalCaloriesAvg INTEGER NOT NULL,
                        nutritionSummary TEXT NOT NULL DEFAULT ''
                    )
                """)
            }
        }
```

Update the `DatabaseModule.kt` Room builder to include the new migration (find `.addMigrations(HistoryDatabase.MIGRATION_1_2)` → add `.addMigrations(HistoryDatabase.MIGRATION_1_2, HistoryDatabase.MIGRATION_2_3)`).

- [ ] **Step 6: Build verification**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/freshscan/data/history/DietPlanEntity.kt \
        app/src/main/java/com/example/freshscan/data/history/DietPlanDao.kt \
        app/src/main/java/com/example/freshscan/data/history/UserProfileEntity.kt \
        app/src/main/java/com/example/freshscan/data/history/UserProfileDao.kt \
        app/src/main/java/com/example/freshscan/data/history/HistoryDatabase.kt \
        app/src/main/java/com/example/freshscan/di/DatabaseModule.kt
git commit -m "feat(v3): add Room entities, DAOs, and migration v2→v3

- DietPlanEntity + DietPlanDao for AI-generated diet plans
- UserProfileEntity + UserProfileDao (singleton row) replacing DataStore
- MIGRATION_2_3: user_profile + diet_plans tables
- HistoryDatabase version 2→3

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 4: AI Service Interface + Error Types

**Files:**
- Create: `app/src/main/java/com/example/freshscan/data/ai/AIService.kt`
- Create: `app/src/main/java/com/example/freshscan/data/ai/AIServiceError.kt`

**Interfaces:**
- Produces: `AIService` interface (suspend funs `chat`, `chatJson`); `AIServiceError` sealed class — used by QwenAIService, ProduceInfoEngine, DietPlanEngine

- [ ] **Step 1: Create AIServiceError.kt**

```kotlin
package com.example.freshscan.data.ai

sealed class AIServiceError(message: String) : Exception(message) {
    class NetworkError(cause: Throwable? = null) :
        AIServiceError("网络连接失败，请检查网络后重试")
    class TimeoutError :
        AIServiceError("AI 响应超时，请稍后重试")
    class QuotaExceeded :
        AIServiceError("AI 服务额度已用完")
    class InvalidResponse(cause: String) :
        AIServiceError("AI 返回格式异常：$cause")
    class UnknownError(cause: Throwable? = null) :
        AIServiceError("AI 服务异常：${cause?.message ?: "未知错误"}")
}
```

- [ ] **Step 2: Create AIService.kt**

```kotlin
package com.example.freshscan.data.ai

/**
 * Abstract interface for cloud AI services (Qwen, DeepSeek, etc.).
 *
 * All methods are suspend functions safe to call from ViewModel coroutine scope.
 * Implementation runs IO on OkHttp dispatcher threads.
 */
interface AIService {
    /**
     * General chat completion.
     *
     * @param systemPrompt System role prompt (character/persona).
     * @param userMessage User's message.
     * @return AI-generated text, or [Result.failure] with [AIServiceError].
     */
    suspend fun chat(systemPrompt: String, userMessage: String): Result<String>

    /**
     * Chat completion with JSON output expectation.
     *
     * The system prompt should instruct the model to output pure JSON.
     * The implementation may add format enforcement headers.
     *
     * @param systemPrompt System prompt that includes JSON format instructions.
     * @param userMessage User's message.
     * @param jsonSchema Optional JSON schema string for validation.
     * @return A JSON string, or [Result.failure] with [AIServiceError].
     */
    suspend fun chatJson(
        systemPrompt: String,
        userMessage: String,
        jsonSchema: String = ""
    ): Result<String>
}
```

- [ ] **Step 3: Build verification**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/freshscan/data/ai/
git commit -m "feat(v3): add AI service interface and error types

- AIService: abstract chat/chatJson suspend interface
- AIServiceError: sealed class for Network/Timeout/Quota/InvalidResponse/Unknown

Co-Authored-By: Claude <noreply@anthropic.com>"
```

## Phase 2: AI Service Layer

### Task 5: QwenAIService Implementation

**Files:**
- Create: `app/src/main/java/com/example/freshscan/data/ai/QwenAIService.kt`

**Interfaces:**
- Consumes: `AIService` interface, `@AIApiKey` String from DI
- Produces: `QwenAIService : AIService` — OkHttp-based DashScope client

- [ ] **Step 1: Create QwenAIService.kt**

```kotlin
package com.example.freshscan.data.ai

import com.example.freshscan.di.AIApiKey
import com.example.freshscan.di.AIBaseUrl
import com.example.freshscan.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QwenAIService @Inject constructor(
    @AIApiKey private val apiKey: String,
    @AIBaseUrl private val baseUrl: String
) : AIService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val chatEndpoint = "${baseUrl}/services/aigc/text-generation/generation"

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    override suspend fun chat(systemPrompt: String, userMessage: String): Result<String> {
        return executeRequest(systemPrompt, userMessage, maxTokens = 1024, model = "qwen-turbo")
    }

    override suspend fun chatJson(
        systemPrompt: String,
        userMessage: String,
        jsonSchema: String
    ): Result<String> {
        val jsonSystemPrompt = buildString {
            append(systemPrompt)
            append("\n\n【重要】请严格输出纯 JSON，" +
                   "不要包含 markdown 代码块标记（```json），只输出 JSON 对象本身。")
        }
        return executeRequest(jsonSystemPrompt, userMessage, maxTokens = 4096, model = "qwen-plus")
    }

    private suspend fun executeRequest(
        systemPrompt: String,
        userMessage: String,
        maxTokens: Int,
        model: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val requestBody = JSONObject().apply {
                put("model", model)
                put("input", JSONObject().apply {
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", systemPrompt)
                        })
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", userMessage)
                        })
                    })
                })
                put("parameters", JSONObject().apply {
                    put("max_tokens", maxTokens)
                    put("temperature", 0.7)
                    put("top_p", 0.9)
                    put("result_format", "message")
                })
            }.toString()

            val request = Request.Builder()
                .url(chatEndpoint)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                Logger.e("QwenAIService", "API error ${response.code}: $errorBody")
                when (response.code) {
                    401, 403 -> return@withContext Result.failure(AIServiceError.QuotaExceeded())
                    429 -> return@withContext Result.failure(AIServiceError.QuotaExceeded())
                    else -> return@withContext Result.failure(
                        AIServiceError.UnknownError(IOException("HTTP ${response.code}"))
                    )
                }
            }

            val responseBody = response.body?.string() ?: ""
            parseResponse(responseBody)
        } catch (e: java.net.SocketTimeoutException) {
            Logger.e("QwenAIService", "Request timed out", e)
            Result.failure(AIServiceError.TimeoutError())
        } catch (e: IOException) {
            Logger.e("QwenAIService", "Network error", e)
            // Retry once for transient network errors
            try {
                Logger.d("QwenAIService", "Retrying request after network error...")
                retryRequest(systemPrompt, userMessage, maxTokens, model)
            } catch (retryErr: Exception) {
                Result.failure(AIServiceError.NetworkError(e))
            }
        } catch (e: Exception) {
            Logger.e("QwenAIService", "Unexpected error", e)
            Result.failure(AIServiceError.UnknownError(e))
        }
    }

    private suspend fun retryRequest(
        systemPrompt: String,
        userMessage: String,
        maxTokens: Int,
        model: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val requestBody = JSONObject().apply {
                put("model", model)
                put("input", JSONObject().apply {
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system"); put("content", systemPrompt)
                        })
                        put(JSONObject().apply {
                            put("role", "user"); put("content", userMessage)
                        })
                    })
                })
                put("parameters", JSONObject().apply {
                    put("max_tokens", maxTokens)
                    put("temperature", 0.7)
                    put("result_format", "message")
                })
            }.toString()
            val request = Request.Builder()
                .url(chatEndpoint)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            val response = client.newCall(request).execute()
            parseResponse(response.body?.string() ?: "")
        } catch (e: Exception) {
            Result.failure(AIServiceError.NetworkError(e))
        }
    }

    private fun parseResponse(responseBody: String): Result<String> {
        return try {
            val json = JSONObject(responseBody)
            val output = json.optJSONObject("output")
                ?: return Result.failure(AIServiceError.InvalidResponse("Missing 'output' field"))
            // Try choices (result_format=message) first, then text field
            val choices = output.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val content = choices.getJSONObject(0)
                    .optJSONObject("message")?.optString("content", "")
                if (!content.isNullOrBlank())
                    return Result.success(stripCodeFences(content))
            }
            val text = output.optString("text", null)
            if (!text.isNullOrBlank())
                return Result.success(stripCodeFences(text))
            Result.failure(AIServiceError.InvalidResponse("No content in response"))
        } catch (e: Exception) {
            Result.failure(AIServiceError.InvalidResponse("JSON parse error: ${e.message}"))
        }
    }

    private fun stripCodeFences(text: String): String {
        var result = text.trim()
        if (result.startsWith("```json")) result = result.removePrefix("```json").trim()
        else if (result.startsWith("```")) result = result.removePrefix("```").trim()
        if (result.endsWith("```")) result = result.removeSuffix("```").trim()
        return result
    }
}
```

- [ ] **Step 2: Build verification**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/freshscan/data/ai/QwenAIService.kt
git commit -m "feat(v3): implement QwenAIService — DashScope API client

- OkHttp-based: 15s connect, 30s read timeout
- chat(): qwen-turbo, max_tokens=1024
- chatJson(): qwen-plus, max_tokens=4096, JSON enforcement prompt
- Auto-retry once on IOException
- markdown code fence stripping in response
- Error mapping to AIServiceError sealed class

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 6: ProduceInfoEngine

**Files:**
- Create: `app/src/main/java/com/example/freshscan/data/produce/ProduceInfoEngine.kt`

**Interfaces:**
- Consumes: `AIService`, `@ApplicationContext Context`, `LabelNormalizer`
- Produces: `ProduceInfoEngine` — `getInfo(label): Flow<ProduceInfo>`, `getCoreInfo(name): ProduceInfo`

- [ ] **Step 1: Create ProduceInfoEngine.kt**

```kotlin
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
import java.io.BufferedReader
import java.io.InputStreamReader
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
            selectionTips = obj.optString("selection_tips", null),
            pairingSuggestions = obj.optJSONArray("pairing")?.let { arr ->
                (0 until arr.length()).map { i -> arr.getString(i) }
            },
            funFact = obj.optString("fun_fact", null)
        )
    } catch (e: Exception) {
        Logger.w("ProduceInfoEngine", "Failed to parse AI extension: ${e.message}")
        core
    }

    companion object {
        const val CORE_INFO_ASSET_PATH = "produce_info.json"
    }
}
```

- [ ] **Step 2: Build verification**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/freshscan/data/produce/ProduceInfoEngine.kt
git commit -m "feat(v3): implement ProduceInfoEngine

- Loads ~82 produce entries from assets/produce_info.json
- Emits core info instantly, AI extension asynchronously via Flow
- LRU cache (50 entries) for AI results to avoid repeated API calls
- Label resolution via LabelNormalizer (260→82 types)
- Graceful fallback: AI failure → only core info shown

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 7: DietPlanEngine

**Files:**
- Create: `app/src/main/java/com/example/freshscan/data/diet/DietPlanEngine.kt`

**Interfaces:**
- Consumes: `AIService`, `DietPlanDao`
- Produces: `DietPlanEngine` — `generateWeekPlan(profile): Flow<DietPlan>`, `getSavedPlans()`, `deletePlan(id)`

- [ ] **Step 1: Create DietPlanEngine.kt**

```kotlin
package com.example.freshscan.data.diet

import com.example.freshscan.data.ai.AIService
import com.example.freshscan.data.history.DietPlanDao
import com.example.freshscan.data.history.DietPlanEntity
import com.example.freshscan.domain.model.*
import com.example.freshscan.util.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DietPlanEngine @Inject constructor(
    private val aiService: AIService,
    private val dietPlanDao: DietPlanDao
) {
    fun generateWeekPlan(profile: UserProfile): Flow<DietPlan> = flow {
        Logger.d("DietPlanEngine", "Generating plan for goal=${profile.goal}")
        val result = aiService.chatJson(
            systemPrompt = "你是一名注册营养师和私人厨师。根据用户档案生成7天个性化饮食计划。" +
                "严格遵循JSON格式输出，不要包含markdown标记。",
            userMessage = buildPrompt(profile)
        )
        if (result.isFailure) throw result.exceptionOrNull()!!
        val plan = parseDietPlan(result.getOrThrow(), profile)
        dietPlanDao.insert(toEntity(plan))
        Logger.i("DietPlanEngine", "Plan generated: ${plan.id}")
        emit(plan)
    }

    fun getSavedPlans(): Flow<List<DietPlan>> = flow {
        dietPlanDao.getAll().collect { entities ->
            emit(entities.map { toDomain(it) })
        }
    }

    suspend fun getLatestPlan(): DietPlan? =
        dietPlanDao.getLatest()?.let { toDomain(it) }

    suspend fun deletePlan(id: String) { dietPlanDao.deleteById(id) }

    // ─── Prompt building ─────────────────────────────────────────────────

    private fun buildPrompt(profile: UserProfile): String = buildString {
        appendLine("【用户档案】")
        appendLine("- 年龄：${profile.age}岁")
        appendLine("- 性别：${profile.gender.label}")
        appendLine("- 身高：${profile.heightCm}cm")
        appendLine("- 体重：${profile.weightKg}kg")
        appendLine("- 活动量：${profile.activityLevel.label}")
        appendLine("- 健康目标：${profile.goal.label}")
        appendLine("- 每日餐数：${profile.mealsPerDay}")
        appendLine("- 口味偏好：辣度${profile.spiceLevel}/盐度${profile.saltLevel}/油量${profile.oilLevel}")
        if (profile.excludedIngredients.isNotEmpty())
            appendLine("- 忌口食材：${profile.excludedIngredients.joinToString("、")}")
        if (profile.allergies.isNotEmpty())
            appendLine("- 过敏原：${profile.allergies.joinToString("、")}")
        if (profile.preferredCategories.isNotEmpty())
            appendLine("- 偏好菜系：${profile.preferredCategories.joinToString("、") { it.displayName }}")
        appendLine("- 最长烹饪时间：${profile.maxCookingTimeMin}分钟")
        val cal = profile.calorieTarget ?: calculateTDEE(profile)
        appendLine("- 每日热量目标：${cal}kcal")
        appendLine()
        appendLine("【要求】")
        appendLine("1. 每日总热量接近 ${cal}kcal")
        appendLine("2. 食材选用中国超市常见品类，7天菜式不重复")
        appendLine("3. 每道菜标注完整营养成分（热量、蛋白质、碳水、脂肪）")
        appendLine("4. 避开用户忌口和过敏原")
        appendLine("5. 每道菜3-5个简明步骤，烹饪时间≤${profile.maxCookingTimeMin}分钟")
        appendLine()
        append("返回严格JSON：{\"dailyPlans\":[{\"dayIndex\":1,\"dayLabel\":\"周一\",\"totalCalories\":1800,")
        append("\"notes\":\"\",\"meals\":[{\"type\":\"BREAKFAST\",\"recipe\":{\"title\":\"\",")
        append("\"ingredients\":[{\"name\":\"\",\"amount\":\"\"}],\"steps\":[\"\"],\"cookingTimeMin\":0,")
        append("\"calories\":0,\"proteinG\":0,\"carbsG\":0,\"fatG\":0}}]}],\"totalCaloriesAvg\":0,")
        append("\"nutritionSummary\":\"\"}")
    }

    private fun calculateTDEE(profile: UserProfile): Int {
        val bmr = if (profile.gender == Gender.MALE)
            10 * profile.weightKg + 6.25f * profile.heightCm - 5 * profile.age + 5
        else
            10 * profile.weightKg + 6.25f * profile.heightCm - 5 * profile.age - 161
        val tdee = bmr * profile.activityLevel.factor
        return when (profile.goal) {
            HealthGoal.LOSE_WEIGHT -> (tdee - 400).toInt()
            HealthGoal.BUILD_MUSCLE -> (tdee + 400).toInt()
            else -> tdee.toInt()
        }
    }

    private fun parseDietPlan(jsonStr: String, profile: UserProfile): DietPlan {
        val root = JSONObject(jsonStr)
        val dailyPlans = mutableListOf<DailyMealPlan>()
        val dailyArray = root.getJSONArray("dailyPlans")
        for (i in 0 until dailyArray.length()) {
            val dayObj = dailyArray.getJSONObject(i)
            val meals = mutableListOf<Meal>()
            val mealsArray = dayObj.getJSONArray("meals")
            for (j in 0 until mealsArray.length()) {
                val mealObj = mealsArray.getJSONObject(j)
                val type = try { MealType.valueOf(mealObj.getString("type")) }
                    catch (_: Exception) { MealType.LUNCH }
                val recipeObj = mealObj.getJSONObject("recipe")
                val ingredients = mutableListOf<Ingredient>()
                val ingArray = recipeObj.getJSONArray("ingredients")
                for (k in 0 until ingArray.length()) {
                    val ingObj = ingArray.getJSONObject(k)
                    ingredients.add(Ingredient(ingObj.getString("name"),
                        ingObj.optString("amount", "")))
                }
                val steps = (0 until recipeObj.getJSONArray("steps").length())
                    .map { k -> recipeObj.getJSONArray("steps").getString(k) }
                meals.add(Meal(type, DietRecipe(
                    title = recipeObj.getString("title"),
                    ingredients = ingredients,
                    steps = steps,
                    cookingTimeMin = recipeObj.getInt("cookingTimeMin"),
                    calories = recipeObj.getInt("calories"),
                    proteinG = recipeObj.getDouble("proteinG").toFloat(),
                    carbsG = recipeObj.getDouble("carbsG").toFloat(),
                    fatG = recipeObj.getDouble("fatG").toFloat()
                )))
            }
            dailyPlans.add(DailyMealPlan(
                dayIndex = dayObj.getInt("dayIndex"),
                dayLabel = dayObj.getString("dayLabel"),
                totalCalories = dayObj.getInt("totalCalories"),
                meals = meals,
                notes = dayObj.optString("notes", null)
            ))
        }
        return DietPlan(
            id = UUID.randomUUID().toString(),
            generatedAt = System.currentTimeMillis(),
            userProfileSnapshot = profile,
            dailyPlans = dailyPlans,
            totalCaloriesAvg = root.getInt("totalCaloriesAvg"),
            nutritionSummary = root.getString("nutritionSummary")
        )
    }

    private fun toEntity(plan: DietPlan): DietPlanEntity {
        val profileJson = JSONObject().apply {
            put("age", plan.userProfileSnapshot.age)
            put("goal", plan.userProfileSnapshot.goal.name)
        }.toString()
        val dailyArr = JSONArray()
        plan.dailyPlans.forEach { day ->
            val dayObj = JSONObject().apply {
                put("dayIndex", day.dayIndex)
                put("dayLabel", day.dayLabel)
                put("totalCalories", day.totalCalories)
                day.notes?.let { put("notes", it) }
                val mealsArr = JSONArray()
                day.meals.forEach { meal ->
                    mealsArr.put(JSONObject().apply {
                        put("type", meal.type.name)
                        put("recipe", JSONObject().apply {
                            put("title", meal.recipe.title)
                            put("cookingTimeMin", meal.recipe.cookingTimeMin)
                            put("calories", meal.recipe.calories)
                            put("proteinG", meal.recipe.proteinG.toDouble())
                            put("carbsG", meal.recipe.carbsG.toDouble())
                            put("fatG", meal.recipe.fatG.toDouble())
                            put("ingredients", JSONArray().apply {
                                meal.recipe.ingredients.forEach {
                                    put(JSONObject().apply {
                                        put("name", it.name); put("amount", it.amount) }) } })
                            put("steps", JSONArray().apply {
                                meal.recipe.steps.forEach { put(it) } })
                        })
                    })
                }
                put("meals", mealsArr)
            }
            dailyArr.put(dayObj)
        }
        return DietPlanEntity(
            id = plan.id, generatedAt = plan.generatedAt,
            profileSnapshotJson = profileJson,
            dailyPlansJson = dailyArr.toString(),
            totalCaloriesAvg = plan.totalCaloriesAvg,
            nutritionSummary = plan.nutritionSummary
        )
    }

    private fun toDomain(entity: DietPlanEntity): DietPlan {
        val root = JSONObject().apply {
            put("dailyPlans", JSONArray(entity.dailyPlansJson))
            put("totalCaloriesAvg", entity.totalCaloriesAvg)
            put("nutritionSummary", entity.nutritionSummary)
        }
        return parseDietPlan(root.toString(), UserProfile()).copy(
            id = entity.id, generatedAt = entity.generatedAt)
    }
}
```

- [ ] **Step 2: Build verification**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/freshscan/data/diet/DietPlanEngine.kt
git commit -m "feat(v3): implement DietPlanEngine — AI 7-day meal plans

- Mifflin-St Jeor BMR + activity factor + goal adjustment → calorie target
- Structured prompt with full UserProfile context
- Parses AI JSON response → DietPlan domain model → Room entity
- CRUD: generate, getSavedPlans, getLatest, delete
- Uses AIService.chatJson with qwen-plus for structured output

Co-Authored-By: Claude <noreply@anthropic.com>"
```

## Phase 3: DI Wiring

### Task 8: AppModule + DatabaseModule Updates

**Files:**
- Modify: `app/src/main/java/com/example/freshscan/di/AppModule.kt`
- Modify: `app/src/main/java/com/example/freshscan/di/DatabaseModule.kt`

- [ ] **Step 1: Update AppModule.kt** — add imports then append before closing `}`:

```kotlin
import com.example.freshscan.data.ai.AIService
import com.example.freshscan.data.ai.QwenAIService
import com.example.freshscan.data.diet.DietPlanEngine
import com.example.freshscan.data.history.DietPlanDao
import com.example.freshscan.data.produce.ProduceInfoEngine
import com.example.freshscan.BuildConfig

    // ─── v3: AI Service ───
    @Provides @Singleton @AIApiKey
    fun provideAIApiKey(): String = BuildConfig.AI_API_KEY

    @Provides @Singleton @AIBaseUrl
    fun provideAIBaseUrl(): String = "https://dashscope.aliyuncs.com/api/v1"

    @Provides @Singleton
    fun provideAIService(@AIApiKey k: String, @AIBaseUrl u: String): AIService = QwenAIService(k, u)

    // ─── v3: Produce Info ───
    @Provides @Singleton
    fun provideProduceInfoEngine(@ApplicationContext ctx: Context, ai: AIService,
        ln: com.example.freshscan.data.recipe.LabelNormalizer): ProduceInfoEngine =
        ProduceInfoEngine(ctx, ai, ln)

    // ─── v3: Diet Plan ───
    @Provides @Singleton
    fun provideDietPlanEngine(ai: AIService, dao: DietPlanDao): DietPlanEngine =
        DietPlanEngine(ai, dao)
```

- [ ] **Step 2: Update DatabaseModule.kt** — add after `provideShoppingListDao`:

```kotlin
    @Provides fun provideUserProfileDao(db: HistoryDatabase): UserProfileDao = db.userProfileDao()
    @Provides fun provideDietPlanDao(db: HistoryDatabase): DietPlanDao = db.dietPlanDao()
```

Also add import: `import com.example.freshscan.data.history.UserProfileDao` and update Room builder: `.addMigrations(HistoryDatabase.MIGRATION_1_2)` → `.addMigrations(HistoryDatabase.MIGRATION_1_2, HistoryDatabase.MIGRATION_2_3)`.

- [ ] **Step 3: Build + commit**

Run: `./gradlew assembleDebug`
```bash
git add app/src/main/java/com/example/freshscan/di/
git commit -m "feat(v3): wire AI service, engines, DAOs via Hilt DI

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Phase 4: UI Implementation

### Task 9: PersonalizeUiState + PersonalizeViewModel

**Files:**
- Create: `app/src/main/java/com/example/freshscan/ui/screen/personalize/PersonalizeUiState.kt`
- Create: `app/src/main/java/com/example/freshscan/ui/screen/personalize/PersonalizeViewModel.kt`

**PersonalizeUiState.kt:**
```kotlin
package com.example.freshscan.ui.screen.personalize
import com.example.freshscan.domain.model.*

data class PersonalizeUiState(
    val spiceLevel: Int = 0, val saltLevel: Int = 1, val oilLevel: Int = 1,
    val excludedIngredients: Set<String> = emptySet(),
    val preferredCategories: Set<RecipeCategory> = emptySet(),
    val maxCookingTimeMin: Int = 60,
    val age: Int = 25, val heightCm: Int = 170, val weightKg: Float = 65f,
    val gender: Gender = Gender.UNSPECIFIED,
    val activityLevel: ActivityLevel = ActivityLevel.MODERATE,
    val goal: HealthGoal = HealthGoal.EAT_HEALTHY,
    val mealsPerDay: Int = 3, val calorieTarget: Int? = null,
    val allergies: Set<String> = emptySet(),
    val isDirty: Boolean = false, val isSaving: Boolean = false,
    val savedSuccessfully: Boolean = false
)
```

**PersonalizeViewModel.kt** — Full implementation:
- Inject `UserProfileDao`
- `uiState: StateFlow<PersonalizeUiState>`, `navigateToDietPlan: SharedFlow<Unit>`
- `update*()` methods for all fields (spice/salt/oil/excluded/categories/age/height/weight/gender/activity/goal/meals/calorie/allergies)
- `save()` → serializes to `UserProfileEntity` JSON columns, upserts to Room
- `onStartCustomization()` → emits `navigateToDietPlan`
- `loadProfile()` → reads from `UserProfileDao.get()` Flow, parses JSON sets/enums

- [ ] **Step 1: Create both files**

- [ ] **Step 2: Build + commit**

Run: `./gradlew assembleDebug`
```bash
git add app/src/main/java/com/example/freshscan/ui/screen/personalize/
git commit -m "feat(v3): add PersonalizeUiState + PersonalizeViewModel

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 10: PersonalizeScreen Composable

**Files:**
- Create: `app/src/main/java/com/example/freshscan/ui/screen/personalize/PersonalizeScreen.kt`

**Pattern:** Reuse helper composables from `TasteProfileScreen.kt` (`SpiceSlider`, `TripleOptionRow`, `ExcludedIngredientsSection`, `PreferredCategoriesSection`) — copy them as private composables.

**Structure:**
```
Scaffold(TopAppBar("个性化定制", back, save)) →
  Column(verticalScroll):
    口味偏好: SpiceSlider + TripleOption(salt) + TripleOption(oil)
             + ExcludedIngredientsSection + PreferredCategoriesSection
    身体数据: GenderToggle + NumberField(age) + NumberField(height) + NumberFieldFloat(weight)
    目标与活动: ActivityLevelRow + GoalChips + MealCountSlider
    饮食约束: AllergyChips + CalorieField(optional)
    FilledTonalButton("🪄 开始定制") → viewModel.onStartCustomization()

LaunchedEffect(Unit): viewModel.navigateToDietPlan.collect { onNavigateToDietPlan() }
```

- [ ] **Step 1: Copy TasteProfileScreen.kt → PersonalizeScreen.kt, extend with new sections**

- [ ] **Step 2: Build + commit**

Run: `./gradlew assembleDebug`
```bash
git add app/src/main/java/com/example/freshscan/ui/screen/personalize/PersonalizeScreen.kt
git commit -m "feat(v3): add PersonalizeScreen composable

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 11: DietPlanViewModel

**Files:**
- Create: `app/src/main/java/com/example/freshscan/ui/screen/personalize/DietPlanViewModel.kt`

Inject: `DietPlanEngine`, `UserProfileDao`, `ShoppingListDao`

State: `DietPlanUiState(state: GENERATING|SUCCESS|ERROR, plan, selectedDayIndex, errorMessage, addedToCart)`

Methods:
- `generatePlan()` — `userProfileDao.get().first()` → `entityToProfile()` → `dietPlanEngine.generateWeekPlan()` → state SUCCESS/ERROR
- `selectDay(i)` — `selectedDayIndex = i`
- `addMealToShoppingList(meal)` — dedup check → `ShoppingListDao.insert()` per ingredient
- `addAllToShoppingList()` — iterates all days/meals/ingredients
- `entityToProfile()` — maps `UserProfileEntity` → `UserProfile` with `enumOf<T>()` safe parsing

- [ ] **Step 1: Create the file**

- [ ] **Step 2: Build + commit**

Run: `./gradlew assembleDebug`
```bash
git add app/src/main/java/com/example/freshscan/ui/screen/personalize/DietPlanViewModel.kt
git commit -m "feat(v3): add DietPlanViewModel

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 12: DietPlanScreen Composable

**Files:**
- Create: `app/src/main/java/com/example/freshscan/ui/screen/personalize/DietPlanScreen.kt`

**3-state rendering (spec §5.4):**
- GENERATING: Centered `CircularProgressIndicator` + text
- ERROR: Error message + `Button("重试")`
- SUCCESS: `Scaffold` + `TopAppBar`("饮食计划", back, `IconButton(ShoppingCart)` → `addAllToShoppingList()` + `onNavigateToShoppingList()`) + summary `Card` + `ScrollableTabRow`(7 tabs: Mon-Sun) + selected day's meals as `ElevatedCard`s

**MealCard:** Title + cookingTime + calories, expandable steps + nutrition row, `AssistChip("加入购物清单🛒")`.

- [ ] **Step 1: Create the file**

- [ ] **Step 2: Build + commit**

Run: `./gradlew assembleDebug`
```bash
git add app/src/main/java/com/example/freshscan/ui/screen/personalize/DietPlanScreen.kt
git commit -m "feat(v3): add DietPlanScreen composable

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 13: ProduceInfoSheet + AnalysisScreen Integration

**Files:**
- Create: `app/src/main/java/com/example/freshscan/ui/components/ProduceInfoSheet.kt`
- Modify: `app/src/main/java/com/example/freshscan/ui/screen/analysis/AnalysisViewModel.kt`
- Modify: `app/src/main/java/com/example/freshscan/ui/screen/analysis/AnalysisScreen.kt`

**ProduceInfoSheet layout (spec §5.2):** Intro → NutritionGrid (3×2 cards) → HealthBenefits → Storage → AI section (selection/pairing/funFact or loading indicator)

**AnalysisViewModel changes:** Inject `ProduceInfoEngine`, add `_selectedItemInfo: MutableStateFlow<ProduceInfo?>`, `onItemClicked(item)` launches `produceInfoEngine.getInfo(label).collect`, `clearSelectedItem()`.

**AnalysisScreen changes:** In BottomSheet content, toggle: if `selectedInfo != null` show `ProduceInfoSheet` with back button; else show results list. `DetectedItemCard` gets `onClick` param.

- [ ] **Step 1: Create ProduceInfoSheet.kt**

- [ ] **Step 2: Modify AnalysisViewModel.kt + AnalysisScreen.kt**

- [ ] **Step 3: Build + commit**

Run: `./gradlew assembleDebug`
```bash
git add app/src/main/java/com/example/freshscan/ui/components/ProduceInfoSheet.kt \
        app/src/main/java/com/example/freshscan/ui/screen/analysis/
git commit -m "feat(v3): add ProduceInfoSheet + AnalysisScreen integration

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Phase 5: Navigation + Assets + Cleanup

### Task 14: Navigation Routes + Settings Entry

**Files:**
- Modify: `app/src/main/java/com/example/freshscan/navigation/NavGraph.kt`
- Modify: `app/src/main/java/com/example/freshscan/ui/screen/settings/SettingsScreen.kt`

**NavGraph changes:**
- Add: `const val PERSONALIZE = "personalize"`, `const val DIET_PLAN = "diet-plan"`
- Add `@Deprecated const val TASTE_PROFILE = "profile/taste"`
- Add composable entries for `PERSONALIZE` and `DIET_PLAN`
- `TASTE_PROFILE` composable redirects to `PERSONALIZE` with `popUpTo(inclusive=true)`
- Import `PersonalizeScreen`, `DietPlanScreen`

**SettingsScreen changes:** "口味档案" entry → "个性化定制" with subtitle "口味偏好·身体数据·AI饮食计划", callback renamed from `onNavigateToTasteProfile` → `onNavigateToPersonalize`.

- [ ] **Step 1: Apply changes**

- [ ] **Step 2: Build + commit**

Run: `./gradlew assembleDebug`
```bash
git add app/src/main/java/com/example/freshscan/navigation/NavGraph.kt \
        app/src/main/java/com/example/freshscan/ui/screen/settings/SettingsScreen.kt
git commit -m "feat(v3): add PERSONALIZE+DIET_PLAN routes, update Settings entry

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 15: produce_info.json + strings.xml

**Files:**
- Create: `app/src/main/assets/produce_info.json`
- Modify: `app/src/main/res/values/strings.xml`

**produce_info.json:** Scaffold with 5 sample entries (苹果/橙子/香蕉/番茄/黄瓜) with full NutritionFacts fields.

**strings.xml:** Append 40+ v3 resources before `</resources>`:
- `personalize_*` (title, sections, fields, hints): 20 strings
- `diet_plan_*` (title, states, actions): 12 strings
- `produce_info_*` (sections, loading): 6 strings
- `settings_personalize*`: 2 strings

- [ ] **Step 1: Create assets + update strings**

- [ ] **Step 2: Build + commit**

Run: `./gradlew assembleDebug`
```bash
git add app/src/main/assets/produce_info.json app/src/main/res/values/strings.xml
git commit -m "feat(v3): add produce_info.json scaffold + v3 string resources (40+)

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 16: Unit Tests

**Files:**
- Create: `app/src/test/java/com/example/freshscan/data/diet/DietPlanEngineTest.kt`
- Create: `app/src/test/java/com/example/freshscan/ui/screen/personalize/PersonalizeViewModelTest.kt`
- Create: `app/src/test/java/com/example/freshscan/ui/screen/personalize/DietPlanViewModelTest.kt`

**Test patterns follow existing test files** (MockK + JUnit + `testOptions.unitTests.isReturnDefaultValues = true`):
- `DietPlanEngineTest`: BMR calculation (male/female/goal adjustment), prompt includes all profile fields, JSON parsing round-trip, empty profile fallback
- `PersonalizeViewModelTest`: form field updates, save triggers DAO upsert, load restores parsed state from entity
- `DietPlanViewModelTest`: state machine GENERATING→SUCCESS/ERROR, day selection, addMealToShoppingList dedup, addAllToShoppingList

- [ ] **Step 1: Create test files**

- [ ] **Step 2: Run tests + commit**

Run: `./gradlew :app:testDebugUnitTest`
```bash
git add app/src/test/
git commit -m "test(v3): add unit tests for engines and ViewModels

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 17: Cleanup — Deprecate TasteProfile + Update CLAUDE.md

**Files:**
- Modify: `app/src/main/java/com/example/freshscan/ui/screen/profile/TasteProfileScreen.kt`
- Modify: `app/src/main/java/com/example/freshscan/ui/screen/profile/TasteProfileViewModel.kt`
- Modify: `CLAUDE.md`

- [ ] **Step 1: Add `@Deprecated` annotation** to both TasteProfile files, referencing migration to PersonalizeScreen

- [ ] **Step 2: Update CLAUDE.md** — add v3 progress entry and new file references under "v3 New Files"

- [ ] **Step 3: Final verification**

Run: `./gradlew assembleDebug && ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, ~255+ tests pass

```bash
git add -A
git commit -m "chore(v3): deprecate TasteProfile, update CLAUDE.md for v3

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

> **Plan complete. 17 tasks, 6 phases. ~8.5 days estimated.**
> **Next:** Choose execution method — Subagent-Driven (recommended) or Inline.
