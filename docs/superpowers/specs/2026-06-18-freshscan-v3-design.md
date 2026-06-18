# FreshScan v3.0 概要设计文档 — 联网 AI + 食材百科 + 个性化饮食定制

> **版本:** v1.0  
> **日期:** 2026-06-18  
> **状态:** 设计完成，待审查  
> **依赖:** v2.0 全部 M1-M3 已交付  
> **目标:** 为 v3.0 升级提供完整实现蓝图

---

## 目录

1. [概述与动机](#1-概述与动机)
2. [架构变更](#2-架构变更)
3. [数据模型](#3-数据模型)
4. [服务层设计](#4-服务层设计)
5. [UI 设计 (Material3)](#5-ui-设计)
6. [导航变更](#6-导航变更)
7. [数据流](#7-数据流)
8. [错误处理与降级策略](#8-错误处理与降级策略)
9. [数据库变更](#9-数据库变更)
10. [测试策略](#10-测试策略)
11. [实现阶段](#11-实现阶段)
12. [文件清单](#12-文件清单)
13. [风险评估](#13-风险评估)

---

## 1. 概述与动机

### 1.1 当前状态 (v2.0)

FreshScan v2.0 已完整交付：
- 3 阶段推理管线（EfficientDet 检测 → 260 类分类 → 18 类新鲜度判定）
- 8 个页面 + BottomNavigation + ModalBottomSheet 三段式结果展示
- RecipeEngine + 111 道预设菜谱 + RecipeDetailScreen（含计时器、收藏、购物清单）
- 历史记录自动保存、口味档案（DataStore）
- 完全离线运行

### 1.2 v2.0 瓶颈

| 瓶颈 | 说明 |
|------|------|
| **果蔬信息单调** | 检测出"红粉苹果"后，仅显示名称 + 新鲜度 + 置信度，用户无法了解营养、功效、挑选技巧 |
| **菜谱有限** | 111 道预制菜谱不可穷举，用户组合无法覆盖时体验断崖 |
| **个性化薄弱** | 口味档案仅有辣/盐/油/忌口/偏好类别 5 项，缺乏身体数据（年龄/身高/体重/目标），无法提供科学饮食指导 |
| **无 AI 能力** | 完全离线，无法利用大模型实现智能问答、动态菜谱生成、个性化计划 |

### 1.3 v3.0 目标

```
v3.0 = v2.0
     + 食材百科引擎（本地核心信息 + AI 扩展信息）
     + 食材详情卡片（BottomSheet 内展开，展示营养/功效/挑选/搭配）
     + 个性化定制引擎（升级口味档案 → 个性化档案，含身体数据 + 健康目标）
     + AI 饮食周计划生成（千问API → 7 天三餐计划 → 购物清单联动）
     + 联网 AI 服务抽象层（先接千问，后续可切换模型）
```

### 1.4 非目标 (v3.0)

- ❌ 社区/UGC 功能
- ❌ 食物拍照自动记录卡路里（需额外 OCR 模型）
- ❌ AI 图片生成（v3.1 预留接口）
- ❌ 语音交互
- ❌ 多语言（保持 zh/en 双语言）

---

## 2. 架构变更

### 2.1 模块总览

```
                            ┌─────────────────────────┐
                            │    联网 AI 抽象层         │
              ┌─────────────┤   AIService (interface)  ├─────────────┐
              │             │   QwenAIService (impl)   │             │
              │             └─────────────────────────┘             │
              ▼                                                    ▼
   ┌──────────────────────┐                        ┌───────────────────────┐
   │   ProduceInfoEngine   │                        │   DietPlanEngine      │
   │   ┌────────────────┐  │                        │   ┌─────────────────┐ │
   │   │ produce_info   │  │                        │   │ AI 生成         │ │
   │   │ .json (260种)  │  │                        │   │ 7天饮食计划     │ │
   │   ├────────────────┤  │                        │   ├─────────────────┤ │
   │   │ AI 扩展        │  │                        │   │ 购物清单联动    │ │
   │   │ (挑选/搭配)    │  │                        │   │ (一键加入)      │ │
   │   └────────────────┘  │                        │   └─────────────────┘ │
   └──────────┬───────────┘                        └──────────┬────────────┘
              │                                               │
              ▼                                               ▼
   ┌──────────────────────┐                        ┌───────────────────────┐
   │  ProduceInfoSheet     │                        │  PersonalizeScreen     │
   │  (BottomSheet 展开)   │                        │  + DietPlanScreen      │
   │  - 简介/营养/功效     │                        │  - 口味 + 身体数据     │
   │  - 挑选技巧/搭配/AI   │                        │  - 周计划/购物清单     │
   └──────────────────────┘                        └───────────────────────┘
```

### 2.2 层级分布

| 层 | 新增组件 | 依赖 |
|----|---------|------|
| **Presentation** | `PersonalizeScreen` + `PersonalizeViewModel` + `DietPlanScreen` + `DietPlanViewModel` + `ProduceInfoSheet` | Domain models + DI |
| **Domain** | `ProduceInfo`, `UserProfile`, `DietPlan` / `DailyMealPlan` / `Meal` / `DietRecipe` | 无外部依赖 |
| **Data/AI** | `AIService` (interface), `QwenAIService` (impl), `ProduceInfoEngine`, `DietPlanEngine` | OkHttp + JSON |
| **Data/DB** | `DietPlanEntity`, `DietPlanDao`, `UserProfileDao` | Room |
| **DI** | 新增 `@AIApiKey`, `@AIBaseUrl` 限定符, AI 相关 `@Provides` | Hilt |

### 2.3 与 v2.0 交互

- **AnalysisViewModel** → 新增 `ProduceInfoEngine` 依赖（点击食材卡片时查询信息）
- **AnalysisScreen** → `DetectedItemCard` 点击展开 `ProduceInfoSheet`
- **RecipeEngine** → 保留不变；AI 生成的菜谱直接通过 `DietPlan` 中的 `DietRecipe` 进入购物清单
- **TasteProfileScreen / TasteProfileViewModel** → **废弃/重定向** 到 `PersonalizeScreen`
- **ShoppingListViewModel** → 保持不变（已完美支持从任意来源添加食材）
- **SettingsScreen** → 「口味档案」入口改为「个性化定制」

---

## 3. 数据模型

### 3.1 果蔬详细信息 `ProduceInfo`

```kotlin
// domain/model/ProduceInfo.kt
data class ProduceInfo(
    val label: String,               // "Apple_Crimson_Snow"
    val displayName: String,         // "红粉苹果"
    val category: String,            // "水果" / "蔬菜"

    // ── 本地预置（离线立即可看）──
    val intro: String,               // 简介 (80-120字)
    val nutrition: NutritionFacts,   // 每100g营养成分
    val healthBenefits: List<String>, // 功效 (3-5条)
    val storageTips: String,         // 保存方法
    val seasonality: String,         // 时令 (如"9-11月")

    // ── AI 扩展（联网加载，null=未加载）──
    val selectionTips: String? = null,            // 挑选技巧
    val pairingSuggestions: List<String>? = null, // 搭配建议
    val funFact: String? = null                   // 趣味知识
)

data class NutritionFacts(
    val caloriesKcal: Int,       // 热量 (每100g)
    val proteinG: Float,         // 蛋白质 (g)
    val carbsG: Float,           // 碳水化合物 (g)
    val fatG: Float,             // 脂肪 (g)
    val fiberG: Float,           // 膳食纤维 (g)
    val vitaminCMg: Float? = null,    // 维生素C (mg)
    val vitaminAUg: Float? = null,    // 维生素A (μg)
    val potassiumMg: Float? = null,   // 钾 (mg)
    val glycemicIndex: Int? = null    // 升糖指数
)
```

### 3.2 个性化用户档案 `UserProfile`

```kotlin
// domain/model/UserProfile.kt
data class UserProfile(
    // ── 原 TasteProfile 字段 ──
    val spiceLevel: Int = 0,                   // 0=不辣, 1=微辣, 2=中辣, 3=超辣
    val saltLevel: Int = 1,                    // 0=少盐, 1=正常, 2=偏咸
    val oilLevel: Int = 1,                     // 0=少油, 1=正常, 2=偏油
    val excludedIngredients: Set<String> = emptySet(),
    val preferredCategories: Set<RecipeCategory> = emptySet(),

    // ── v3 新增：身体数据 ──
    val age: Int = 25,
    val heightCm: Int = 170,
    val weightKg: Float = 65f,
    val gender: Gender = Gender.UNSPECIFIED,
    val activityLevel: ActivityLevel = ActivityLevel.MODERATE,
    val goal: HealthGoal = HealthGoal.EAT_HEALTHY,
    val mealsPerDay: Int = 3,
    val calorieTarget: Int? = null,            // null=AI 自动计算
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

### 3.3 饮食计划 `DietPlan`

```kotlin
// domain/model/DietPlan.kt
data class DietPlan(
    val id: String,                          // UUID
    val generatedAt: Long,                   // 生成时间戳
    val userProfileSnapshot: UserProfile,    // 生成时档案快照
    val dailyPlans: List<DailyMealPlan>,     // 7天（周一~周日）
    val totalCaloriesAvg: Int,               // 日均热量目标
    val nutritionSummary: String             // AI 营养总结文字
)

data class DailyMealPlan(
    val dayIndex: Int,          // 1-7
    val dayLabel: String,       // "周一" ... "周日"
    val totalCalories: Int,
    val meals: List<Meal>,      // 3-5餐（早餐/午餐/晚餐 [+加餐]）
    val notes: String? = null   // 当日备注（AI 生成）
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

data class DietRecipe(
    val title: String,               // "番茄鸡胸肉沙拉"
    val ingredients: List<Ingredient>, // 可联动购物清单
    val steps: List<String>,         // 烹饪步骤 (3-5步)
    val cookingTimeMin: Int,
    val calories: Int,
    val proteinG: Float,
    val carbsG: Float,
    val fatG: Float
)
```

### 3.4 AI 服务接口

```kotlin
// data/ai/AIService.kt
interface AIService {
    /**
     * 通用 AI 问答。
     * @param systemPrompt 系统提示（角色设定）
     * @param userMessage 用户消息
     * @return AI 生成的文本
     */
    suspend fun chat(systemPrompt: String, userMessage: String): Result<String>

    /**
     * 生成 JSON 结构化输出。
     * @param systemPrompt 系统提示
     * @param userMessage 用户消息
     * @param jsonSchema 输出 JSON Schema（传给 API 的 response_format）
     * @return 解析后的 JSON 字符串
     */
    suspend fun chatJson(
        systemPrompt: String,
        userMessage: String,
        jsonSchema: String
    ): Result<String>
}

// data/ai/AIServiceError.kt
sealed class AIServiceError(message: String) : Exception(message) {
    class NetworkError(cause: Throwable? = null) : AIServiceError("网络连接失败，请检查网络后重试")
    class TimeoutError : AIServiceError("AI 响应超时，请稍后重试")
    class QuotaExceeded : AIServiceError("AI 服务额度已用完")
    class InvalidResponse(cause: String) : AIServiceError("AI 返回格式异常：$cause")
    class UnknownError(cause: Throwable? = null) : AIServiceError("AI 服务异常：${cause?.message ?: "未知错误"}")
}
```

---

## 4. 服务层设计

### 4.1 QwenAIService (千问 DashScope 实现)

```
┌──────────────────────────────────────────────────┐
│            QwenAIService : AIService              │
├──────────────────────────────────────────────────┤
│ API endpoint: https://dashscope.aliyuncs.com/     │
│              api/v1/services/aigc/                │
│              text-generation/generation           │
│                                                  │
│ 配置：                                           │
│ - model: qwen-turbo (快捷) / qwen-plus (高质)    │
│ - max_tokens: 1024 (chat) / 4096 (diet plan)     │
│ - temperature: 0.7                               │
│ - top_p: 0.9                                     │
│ - result_format: "message" / "text"              │
│                                                  │
│ 网络：                                           │
│ - OkHttp 4.x client, 连接池复用                  │
│ - 读超时: 30s (chat) / 60s (diet plan)           │
│ - 连接超时: 15s                                  │
│ - 自动重试 1 次（仅网络超时）                    │
│                                                  │
│ 线程：                                           │
│ - 所有方法 suspend, 在主线程安全调用             │
│ - 实际 IO 在 OkHttp Dispatcher 线程池            │
│                                                  │
│ API Key 管理：                                   │
│ - 通过 @Named("aiApiKey") 注入                   │
│ - 来源: BuildConfig.AI_API_KEY (gradle 属性)     │
│ - 不在代码中硬编码                               │
└──────────────────────────────────────────────────┘
```

**API 请求格式（chat）：**
```json
{
  "model": "qwen-turbo",
  "input": {
    "messages": [
      {"role": "system", "content": "<system_prompt>"},
      {"role": "user", "content": "<user_message>"}
    ]
  },
  "parameters": {
    "max_tokens": 1024,
    "temperature": 0.7,
    "result_format": "message"
  }
}
```

**API 请求格式（chatJson 结构化输出）：**
```json
{
  "model": "qwen-plus",
  "input": {
    "messages": [
      {"role": "system", "content": "<system_prompt>"},
      {"role": "user", "content": "<user_message>"}
    ]
  },
  "parameters": {
    "max_tokens": 4096,
    "temperature": 0.7,
    "result_format": "message"
  }
}
```
> 注：DashScope 当前不直接支持 `response_format: json_schema`。采用 system prompt 中强制 JSON 输出 + 客户端 `tryParse` + 重试策略。

### 4.2 ProduceInfoEngine

```
┌──────────────────────────────────────────────────┐
│          ProduceInfoEngine                       │
├──────────────────────────────────────────────────┤
│ + getInfo(label): Flow<ProduceInfo>              │
│ + getCoreInfo(label): ProduceInfo                │
│ + preload(labels): Flow<Unit>                    │
├──────────────────────────────────────────────────┤
│                                                  │
│ 加载流程：                                       │
│ 1. 从 produce_info.json 加载核心信息（同步）     │
│    → emit ProduceInfo(core fields filled)         │
│                                                  │
│ 2. 如果网络可用，后台调用 AIService.chatJson()   │
│    查询扩展信息（挑选 + 搭配 + 趣味知识）        │
│    → emit ProduceInfo(AI fields filled)           │
│                                                  │
│ 3. 缓存 AI 结果到内存 LRU (max 50)              │
│    下一次查询同一食材不重复调 API                │
│                                                  │
│ 标签解析策略：                                   │
│ - 260 个 Fruits-360 label 映射到 ~82 种唯一      │
│   果蔬类型（与训练时的 Kaggle 目录映射一致）      │
│ - 引擎通过 LabelNormalizer 将原始 label          │
│   （"Apple_Crimson_Snow"）解析为规范名称         │
│   （"苹果"），再查找 produce_info.json            │
│ - produce_info.json 按规范名称建索引（~82 条目） │
│                                                  │
│ produce_info.json 格式：                         │
│ [                                                │
│   {                                              │
│     "label": "Apple_Crimson_Snow",               │
│     "displayName": "红粉苹果",                   │
│     "category": "水果",                          │
│     "intro": "...",                              │
│     "nutrition": {...},                          │
│     "healthBenefits": [...],                     │
│     "storageTips": "...",                        │
│     "seasonality": "9-11月"                      │
│   },                                             │
│   ...260 entries...                              │
│ ]                                                │
└──────────────────────────────────────────────────┘
```

**AI 扩展信息 Prompt：**
```
你是一名资深营养学家和食材专家。请用简洁中文介绍以下食材：{displayName}

请严格按以下 JSON 格式返回（不要包含其他文字）：
{
  "selection_tips": "挑选技巧，50字以内",
  "pairing": ["最佳搭配食材1", "最佳搭配食材2"],
  "fun_fact": "有趣的知识或冷知识，40字以内"
}

要求：信息准确、实用、适合移动端阅读。
```

### 4.3 DietPlanEngine

```
┌──────────────────────────────────────────────────┐
│            DietPlanEngine                        │
├──────────────────────────────────────────────────┤
│ + generateWeekPlan(profile): Flow<DietPlan>      │
│ + getSavedPlans(): Flow<List<DietPlan>>          │
│ + getLatestPlan(): DietPlan?                     │
│ + deletePlan(id): Unit                           │
├──────────────────────────────────────────────────┤
│                                                  │
│ 生成流程 (generateWeekPlan):                     │
│ 1. 构建 Prompt（用户档案 + 口味 + 目标）         │
│ 2. 调用 AIService.chatJson()                     │
│ 3. 解析 JSON → DietPlan                         │
│ 4. 持久化到 Room (diet_plans 表)                │
│ 5. emit 结果                                     │
│                                                  │
│ 生成状态机：                                     │
│ Idle → Generating → Success / Error              │
│                                                  │
│ Loading state: UI 显示进度动画 + 预计等待提示    │
│                                                  │
│ Prompt 温度: 0.8（保证菜式多样性）               │
│                                 max_tokens: 4096 │
└──────────────────────────────────────────────────┘
```

**饮食计划 Prompt 模板：**
```
你是一名注册营养师和私人厨师。根据以下用户档案生成 7 天饮食计划。

【用户档案】
- 年龄：{age}岁
- 性别：{gender}
- 身高：{heightCm}cm
- 体重：{weightKg}kg
- 活动量：{activityLevel}
- 健康目标：{goal}
- 每日餐数：{mealsPerDay}
- 口味偏好：辣度{spiceLevel}/盐度{saltLevel}/油量{oilLevel}
- 忌口食材：{excludedIngredients}
- 过敏原：{allergies}
- 偏好菜系：{preferredCategories}
- 最长烹饪时间：{maxCookingTimeMin}分钟
{f"自定义热量目标：{calorieTarget}kcal/天" if calorieTarget else ""}

【要求】
1. 每日{f"总热量接近 {calorieTarget}kcal" if calorieTarget else "按 BMR×活动量自动计算合适热量"}
2. 食材选用中国超市常见品类
3. 7 天菜式不重复
4. 每道菜标注营养成分（热量、蛋白质、碳水、脂肪）
5. 避开用户忌口和过敏原
6. 每道菜 3-5 个简明步骤
7. 烹饪时间不超过用户设定的 {maxCookingTimeMin} 分钟

【输出格式 - 严格 JSON】
{
  "dailyPlans": [
    {
      "dayIndex": 1,
      "dayLabel": "周一",
      "totalCalories": 1800,
      "notes": "当天高蛋白低脂",
      "meals": [
        {
          "type": "BREAKFAST",
          "recipe": {
            "title": "燕麦鸡蛋白菜粥",
            "ingredients": [{"name": "燕麦", "amount": "50g"}, ...],
            "steps": ["1. ...", "2. ..."],
            "cookingTimeMin": 15,
            "calories": 350,
            "proteinG": 18.0,
            "carbsG": 45.0,
            "fatG": 8.0
          }
        },
        ...
      ]
    },
    ...共7天...
  ],
  "totalCaloriesAvg": 1800,
  "nutritionSummary": "该计划热量适中，蛋白质充足..."
}
```

---

## 5. UI 设计

### 5.1 页面总览

| 页面 | 状态 | 说明 |
|------|------|------|
| HomeScreen | 保持 | 不改动 |
| AnalysisScreen | **改造** | DetectedItemCard 添加点击展开 ProduceInfoSheet |
| HistoryScreen | 保持 | 不改动 |
| DetailScreen | 保持 | 不改动 |
| RecipeDetailScreen | **改造** | DietRecipe 的展示（复用现有布局） |
| PersonalizeScreen | **新建** | 替代 TasteProfileScreen，升级为完整个性化定制 |
| DietPlanScreen | **新建** | 展示生成的周计划，支持翻页 |
| ShoppingListScreen | 保持 | 不改动（已支持外部添加） |
| SettingsScreen | **改造** | "口味档案"入口 → "个性化定制"入口 |

### 5.2 ProduceInfoSheet — 食材详情卡片

**触发:** AnalysisScreen 中点击 `DetectedItemCard` → 展开 ProduceInfoSheet。

**布局（三段式 BottomSheet 中的扩展面板）：**

```
┌──────────────────────────────────────────┐
│  🍎  红粉苹果                   新鲜 ✅   │
│      水果 · 时令 9-11月                   │
├──────────────────────────────────────────┤
│  📖 简介                                  │
│  红粉苹果是苹果的一个栽培品种，原产于      │
│  日本青森县。果实呈深红色，果肉紧实       │
│  多汁，甜度高且带有微酸风味...            │
├──────────────────────────────────────────┤
│  📊 营养成分（每100g）                    │
│  ┌─────────┬─────────┬─────────┐         │
│  │ 热量     │ 蛋白质   │ 脂肪     │         │
│  │ 52 kcal │ 0.3g    │ 0.2g    │         │
│  ├─────────┼─────────┼─────────┤         │
│  │ 碳水     │ 纤维     │ 维C      │         │
│  │ 14g     │ 2.4g    │ 4.6mg   │         │
│  └─────────┴─────────┴─────────┘         │
├──────────────────────────────────────────┤
│  💪 健康功效                              │
│  ✅ 促进消化 — 富含膳食纤维              │
│  ✅ 抗氧化 — 含花青素和槲皮素            │
│  ✅ 心血管健康 — 钾含量有助于降压        │
│  ✅ 控制体重 — 低热量高饱腹感            │
├──────────────────────────────────────────┤
│  📦 保存方法                              │
│  室温阴凉处存放 7-10 天；冷藏可延长至    │
│  3-4 周；避免与有强烈气味的食物一起存放。 │
├──────────────────────────────────────────┤
│  🤖 AI 扩展 (带"AI"标签)                  │
│  ┌────────────────────────────────────┐   │
│  │ 🔍 挑选技巧                         │   │
│  │ 挑选果皮光滑、颜色均匀、有光泽的    │   │
│  │ 果实。轻轻按压有弹性，闻起来有      │   │
│  │ 清新果香的为佳。                    │   │
│  ├────────────────────────────────────┤   │
│  │ 🥗 搭配建议                         │   │
│  │ 肉桂、核桃、芹菜、酸奶              │   │
│  ├────────────────────────────────────┤   │
│  │ 💡 你知道吗                         │   │
│  │ 一个苹果在 25°C 室温下存放 1 天的   │   │
│  │ 老化速度相当于在 4°C 冷藏 10 天！   │   │
│  └────────────────────────────────────┘   │
└──────────────────────────────────────────┘
```

**加载状态：**
- 核心信息（intro/营养/功效/保存）：**立即展示**（从本地 JSON）
- AI 扩展信息区域：显示 `CircularProgressIndicator(16.dp)` + "AI 正在生成..." 占位 → 加载完成后替换
- 网络不可用时：隐藏 AI 区域，无提示（不打扰）
- API 出错时：显示 `TextButton("重试")` 按钮

### 5.3 PersonalizeScreen — 个性化定制

**替代 TasteProfileScreen。** 路由从 `/profile/taste` 改为 `/personalize`。

**布局（Material3 Scaffold + 分段滚动）：**

```
┌──────────────────────────────────────────────┐
│  ← 个性化定制                    保存        │
├──────────────────────────────────────────────┤
│                                              │
│  ════════════ 口味偏好 ════════════          │
│  （原 TasteProfile 全部内容在此）            │
│  辣度: [不辣] [微辣] [中辣] [超辣]           │
│  盐度: [少盐] [正常] [偏咸]                 │
│  油量: [少油] [正常] [偏油]                 │
│  饮食忌口: [花生] [乳糖] [海鲜] ...          │
│  菜谱偏好: [家常菜] [快手菜] ...            │
│  ─────────────────────────────────           │
│                                              │
│  ════════════ 身体数据 ════════════          │
│  性别: [男] [女]                             │
│  年龄: [____] 岁    (NumberPicker 风格)       │
│  身高: [____] cm                             │
│  体重: [____] kg                             │
│  ─────────────────────────────────           │
│                                              │
│  ════════════ 目标与活动 ══════════         │
│  活动量: [久坐] [轻度] [中度] [运动] [高强]  │
│  健康目标: (Card chips)                      │
│  ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐            │
│  │减脂 │ │增肌 │ │维持 │ │健康 │            │
│  │瘦身 │ │塑形 │ │体重 │ │饮食 │            │
│  └─────┘ └─────┘ └─────┘ └─────┘            │
│  ┌──────────┐                                │
│  │控糖管理  │                                │
│  └──────────┘                                │
│  每日餐数: [2] [3] [4] [5]                   │
│  ─────────────────────────────────           │
│                                              │
│  ════════════ 饮食约束 ════════════          │
│  过敏原: [花生] [海鲜] [牛奶] [鸡蛋] ...     │
│  热量目标: [____] kcal (可选，留空由AI计算)  │
│  ─────────────────────────────────           │
│                                              │
│  ┌──────────────────────────────────────┐    │
│  │     🪄  开始定制                      │    │
│  │  根据以上信息，AI 为你生成            │    │
│  │  7 天个性化饮食计划                   │    │
│  └──────────────────────────────────────┘    │
│                                              │
└──────────────────────────────────────────────┘
```

**交互细节：**
- 所有控件即时可调（本地 State），顶部 Save 按钮持久化到 Room（替代原 DataStore）
- "开始定制"按钮：
  - 点击 → 显示确认对话框："将根据您的档案生成专属 7 天饮食计划，需要联网使用 AI。继续？"
  - 确认 → 跳转到 `DietPlanScreen` 并触发生成
  - 如果网络不可用 → Snackbar "AI 定制需要网络连接"
- Material3 风格：`TopAppBar` + `Card` + `FilterChip` + `OutlinedTextField` + `FilledTonalButton`

### 5.4 DietPlanScreen — 周计划展示

```
┌──────────────────────────────────────────────┐
│  ← 饮食计划                  加入购物清单    │
├──────────────────────────────────────────────┤
│                                              │
│  📊 日均热量：1,800 kcal                     │
│  💬 "该计划均衡搭配，高蛋白低GI..."         │
│                                              │
│  ════════════ 周计划 ══════════════         │
│  [周一] [周二] [周三] [周四] [周五] [周六] [周日]
│   ████                                    │
│                                              │
│  ┌──────────────────────────────────────┐    │
│  │  周一 · 总热量 1,780 kcal            │    │
│  ├──────────────────────────────────────┤    │
│  │                                     │    │
│  │  🌅 早餐                            │    │
│  │  ┌──────────────────────────────┐   │    │
│  │  │ 燕麦鸡蛋白菜粥 · 15min · 350kcal│   │    │
│  │  │ 🛒 燕麦50g 鸡蛋2个 白菜100g   │   │    │
│  │  │ 1. 燕麦提前泡30分钟           │   │    │
│  │  │ 2. 水开后加入燕麦煮15分钟     │   │    │
│  │  │ 3. 打入鸡蛋搅拌，加白菜...    │   │    │
│  │  │             [加入购物清单 🛒] │   │    │
│  │  └──────────────────────────────┘   │    │
│  │                                     │    │
│  │  ☀️ 午餐                            │    │
│  │  ┌──────────────────────────────┐   │    │
│  │  │ 番茄鸡胸肉沙拉 · 20min · 420kcal│  │    │
│  │  │ ...                          │   │    │
│  │  │             [加入购物清单 🛒] │   │    │
│  │  └──────────────────────────────┘   │    │
│  │                                     │    │
│  │  🌙 晚餐                            │    │
│  │  ...                                │    │
│  │                                     │    │
│  └──────────────────────────────────────┘    │
│                                              │
│  💡 当日备注：今天蛋白质摄入充足，建议...    │
│                                              │
└──────────────────────────────────────────────┘
```

**交互细节：**
- **天标签页**：`ScrollableTabRow` 或 `TabRow`（Material3），7 个 Tab：「周一」到「周日」
- **餐食卡片**：`ElevatedCard`，点击展开/折叠烹饪步骤
- **购物清单按钮**：`AssistChip(onClick = { viewModel.addToShoppingList(meal) })` → 调用现有 `ShoppingListViewModel.addItem()`
- **生成状态**：
  - 生成中：全屏 `CircularProgressIndicator` + "AI 正在为你定制一周饮食计划..."
  - 生成失败：`ErrorContent` + 重试按钮
  - 无网络：`EmptyContent` + 说明文字
- **顶部操作栏**：
  - "加入购物清单"（`IconButton`）→ 一键将视图中所有食材加入购物清单
  - 复用 `ShoppingListViewModel`，不重复创建

### 5.5 SettingsScreen 入口变更

```
"口味档案" 条目
  → label: "个性化定制"
  → subtitle: "口味偏好 · 身体数据 · AI 饮食计划"
  → onClick: navController.navigate(Routes.PERSONALIZE)
```

### 5.6 AnalysisScreen DetectedItemCard 改造

```kotlin
// 现有：
DetectedItemCard(item)  // 只读卡片，无交互

// v3：
DetectedItemCard(
    item = item,
    onClick = { viewModel.onItemClicked(item) },  // 选中展示详情
    modifier = Modifier.animateItemPlacement()
)
```

点击后 → `ProduceInfoSheet` 从卡片位置展开（同一个 ModalBottomSheet 内的内容切换），展示食材详情。

---

## 6. 导航变更

### 6.1 路由表

```kotlin
object Routes {
    // ── 保持 ──
    const val HOME = "home"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val ANALYSIS = "analysis"
    const val DETAIL = "detail/{resultId}"
    const val RECIPE_DETAIL = "recipe/{recipeId}"
    const val SHOPPING_LIST = "shopping-list"

    // ── v3 新增 ──
    const val PERSONALIZE = "personalize"         // 替代 TASTE_PROFILE
    const val DIET_PLAN = "diet-plan"              // 周计划展示

    // ── v3 废弃 ──
    @Deprecated("Replaced by PERSONALIZE")
    const val TASTE_PROFILE = "profile/taste"

    // Helper
    fun detail(resultId: String) = "detail/$resultId"
    fun recipeDetail(recipeId: String) = "recipe/$recipeId"
}
```

### 6.2 导航图变更

```kotlin
// NavGraph.kt 变更：
// - composable(Routes.TASTE_PROFILE) → 替换为 composable(Routes.PERSONALIZE)
// - 新增 composable(Routes.DIET_PLAN)
// - SettingsScreen onNavigateToTasteProfile → onNavigateToPersonalize

composable(Routes.PERSONALIZE) {
    PersonalizeScreen(
        onNavigateBack = { navController.popBackStack() },
        onNavigateToDietPlan = {
            navController.navigate(Routes.DIET_PLAN)
        }
    )
}

composable(Routes.DIET_PLAN) {
    DietPlanScreen(
        onNavigateBack = { navController.popBackStack() },
        onNavigateToShoppingList = {
            navController.navigate(Routes.SHOPPING_LIST)
        }
    )
}
```

### 6.3 BackStack 清理

`TASTE_PROFILE` 路由移除后，任何 deep link 到 `/profile/taste` 的调用自动重定向到 `PERSONALIZE`（在 NavGraph 中保留一个重定向 composable，或直接在调用处修改）。

---

## 7. 数据流

### 7.1 食材详情查询流

```
User taps DetectedItemCard
  │
  ▼
AnalysisViewModel.onItemClicked(item)
  │
  ▼
ProduceInfoSheet (Compose)
  ├── Launch collect: produceInfoEngine.getInfo(item.label)
  │     │
  │     ├── Emit 1: ProduceInfo(core fields from JSON)  ← 立即可见
  │     │
  │     ├── Background: AIService.chatJson(prompt)
  │     │     ├── Success → Emit 2: ProduceInfo(AI fields filled)
  │     │     └── Error → AI fields stay null (graceful)
  │     │
  │     └── Cache in LRU for next tap
  │
  └── Render card content reactively
```

### 7.2 饮食计划生成流

```
User taps "开始定制"
  │
  ▼
PersonalizeViewModel.onStartCustomization()
  │
  ├── Validate: all required fields filled?
  │     └── No → Snackbar "请填写完整信息"
  │
  ├── Yes → navigate to DietPlanScreen
  │
  ▼
DietPlanViewModel.init
  │
  ├── Set state: Generating
  │
  ├── Launch: dietPlanEngine.generateWeekPlan(profile)
  │     │
  │     ├── Build prompt (profile → template)
  │     ├── AIService.chatJson(prompt, dietPlanSchema)
  │     │     ├── Success + valid JSON → parse DietPlan
  │     │     │     ├── Persist to Room
  │     │     │     └── Set state: Success(plan)
  │     │     └── Failure → Set state: Error(message)
  │     │
  │     └── Set state: Success(plan)
  │
  └── UI renders 7-day plan
```

### 7.3 购物清单联动流

```
User taps "加入购物清单" on a Meal
  │
  ▼
DietPlanViewModel.addToShoppingList(meal)
  │
  ├── For each Ingredient in meal.recipe.ingredients:
  │     shoppingListViewModel.addItem(name, amount)
  │
  ├── Snackbar "已加入购物清单（3项）"
  │
  └── 去重逻辑在 ShoppingListViewModel 中已实现
```

---

## 8. 错误处理与降级策略

### 8.1 AI 服务错误处理

| 错误类型 | UI 表现 | 用户操作 |
|----------|--------|---------|
| `NetworkError` | Snackbar "网络连接失败" | 重试按钮 |
| `TimeoutError` | Snackbar "AI 响应超时" | 自动重试 1 次后显示重试按钮 |
| `QuotaExceeded` | 对话框 "AI 额度已用完，请稍后再试或切换 API Key" | 确认按钮 |
| `InvalidResponse` | Snackbar "AI 返回异常" + 重试 | 重试按钮 |
| `UnknownError` | Snackbar 显示具体错误信息 | 重试按钮 |

### 8.2 降级策略

| 场景 | 降级行为 |
|------|---------|
| **AI 扩展信息不可用** | 只展示本地核心信息，AI 区域隐藏（不显示错误） |
| **饮食计划生成失败** | 显示错误 + 重试按钮 + "可以前往菜谱页面浏览 111 道预设菜谱" 建议 |
| **API 额度耗尽** | 对话框提示 + 禁用"开始定制"按钮（防止反复失败） |
| **首次使用无网络** | 引导填写本地档案，显示"需要联网以生成饮食计划"提示 |
| **JSON 解析失败** | 重试 1 次（prompt 中加强格式要求），仍失败则报 InvalidResponse |
| **produce_info.json 损坏** | 降级为仅显示 displayName，不阻塞分析流程 |

### 8.3 ProduceInfoSheet 加载态

```
加载阶段：
┌────────────────────────────────┐
│ 📖 简介                        │
│ ✓ (本地数据，立即可见)          │
│ ─────────────────               │
│ 🤖 AI 扩展                     │
│ ⏳ AI 正在生成...               │
│ (CircularProgressIndicator)    │
└────────────────────────────────┘

完成态：
┌────────────────────────────────┐
│ 🤖 AI 扩展                     │
│ 🔍 挑选技巧: ...               │
│ 🥗 搭配建议: ...               │
│ 💡 趣味知识: ...               │
└────────────────────────────────┘

错误态：
┌────────────────────────────────┐
│ 🤖 AI 扩展                     │
│ 加载失败  [重试]               │
└────────────────────────────────┘
```

---

## 9. 数据库变更

### 9.1 MIGRATION_2_3

```kotlin
// HistoryDatabase.kt — version 2 → 3

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. 用户档案表（替代 DataStore）
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS user_profile (
                id INTEGER PRIMARY KEY NOT NULL DEFAULT 1,
                spiceLevel INTEGER NOT NULL DEFAULT 0,
                saltLevel INTEGER NOT NULL DEFAULT 1,
                oilLevel INTEGER NOT NULL DEFAULT 1,
                excludedIngredients TEXT NOT NULL DEFAULT '[]',
                preferredCategories TEXT NOT NULL DEFAULT '[]',
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

        // 2. 饮食计划表
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

### 9.2 新增 Room 实体与 DAO

#### DietPlanEntity + DietPlanDao

```kotlin
// data/history/DietPlanEntity.kt
@Entity(tableName = "diet_plans")
data class DietPlanEntity(
    @PrimaryKey val id: String,
    val generatedAt: Long,
    val profileSnapshotJson: String, // JSON 序列化的 UserProfile
    val dailyPlansJson: String,      // JSON 序列化的 List<DailyMealPlan>
    val totalCaloriesAvg: Int,
    val nutritionSummary: String
)

// data/history/DietPlanDao.kt
@Dao
interface DietPlanDao {
    @Query("SELECT * FROM diet_plans ORDER BY generatedAt DESC LIMIT 1")
    fun getLatest(): DietPlanEntity?

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

#### UserProfileDao

```kotlin
// data/history/UserProfileDao.kt
@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun get(): Flow<UserProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: UserProfileEntity)
}

// data/history/UserProfileEntity.kt
@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 1,
    val spiceLevel: Int = 0,
    val saltLevel: Int = 1,
    val oilLevel: Int = 1,
    val excludedIngredients: String = "[]",   // JSON array
    val preferredCategories: String = "[]",   // JSON array
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

> **设计决策：UserProfile 从 DataStore 迁移到 Room。**
> 原因：(1) 结构化数据（年龄/身高/体重/性别/活动量等）更适合关系型存储；(2) Room 提供类型安全 + Flow 响应式查询；(3) 与 diet_plans 表共存于同一数据库，事务一致。
> 旧的 DataStore preferences 会在 PersonalizeViewModel 首次加载时进行一次迁移读取，写入 Room 后清除 DataStore 键。

> **设计决策：dailyPlansJson 存为单个 JSON 字段而非独立 meal 表。**
> 原因：(1) 饮食计划是一次性 AI 生成的整体，不需要按餐查询；(2) 避免 7天×5餐=35行的复杂关联；(3) JSON 字段足以满足"展示 + 购物清单联动"需求。如果后续需要"按食材搜索历史计划"等复杂查询，再规范化到独立表。

---

## 10. 测试策略

### 10.1 单元测试（JVM）

| 模块 | 测试文件 | 测试内容 | 预计数量 |
|------|---------|---------|---------|
| **ProduceInfoEngine** | `ProduceInfoEngineTest.kt` | JSON 解析、核心信息查询、AI 结果合并、缓存逻辑、空 label 处理 | 20+ |
| **DietPlanEngine** | `DietPlanEngineTest.kt` | Prompt 构建正确性、JSON 解析、空结果处理、持久化/删除 | 18+ |
| **QwenAIService** | `QwenAIServiceTest.kt` | `chat()` 成功/失败路径、`chatJson()` 解析、超时、重试逻辑 | 15+ |
| **UserProfile** | `UserProfileTest.kt` | Room DAO 读写、DataStore 迁移逻辑 | 10+ |
| **PersonalizeViewModel** | `PersonalizeViewModelTest.kt` | 表单状态管理、保存/校验、定制触发、错误路径 | 16+ |
| **DietPlanViewModel** | `DietPlanViewModelTest.kt` | 生成状态机、购物清单联动、天切换 | 14+ |

### 10.2 集成测试

- AI API 真实调用测试（使用测试 API Key，验证 prompt 结构和返回格式）
- `produce_info.json` 完整性校验测试（260 条目无缺失必填字段）
- Room migration 2→3 测试

### 10.3 手工测试清单

- [ ] 拍照 → 识别果蔬 → 点击卡片查看详情 → 核心信息即时显示
- [ ] 相同页面等待 AI 扩展信息加载完成
- [ ] 断网状态下点击卡片 → 仅显示核心信息，AI 区隐藏
- [ ] 个性化定制页面 → 填写完整档案 → 点击开始定制
- [ ] 查看生成的 7 天计划 → 切换天 → 展开/折叠菜谱卡片
- [ ] 点击菜谱中的「加入购物清单」→ 跳转购物清单验证内容
- [ ] 设置页「个性化定制」入口正确跳转
- [ ] 旧 TasteProfile 路由重定向到 Personalize

---

## 11. 实现阶段

### Phase 1: 数据基础设施（2天）

| 任务 | 产出 |
|------|------|
| 1.1 采集 260 种果蔬核心信息 | `produce_info.json` (~80KB) |
| 1.2 Domain models | `ProduceInfo.kt`, `UserProfile.kt`, `DietPlan.kt` |
| 1.3 Room migration 2→3 | `MIGRATION_2_3`, `DietPlanEntity`, `DietPlanDao` |
| 1.4 DI 新增绑定 | `AIService`, `ProduceInfoEngine`, `DietPlanEngine` |
| **验证** | `assembleDebug` + MIGRATION_2_3 测试 |

### Phase 2: AI 服务层（1.5天）

| 任务 | 产出 |
|------|------|
| 2.1 `AIService` 接口 + `QwenAIService` | OkHttp + JSON 解析 |
| 2.2 `ProduceInfoEngine` | JSON 加载 + AI 扩展 + LRU 缓存 |
| 2.3 `DietPlanEngine` | Prompt 构建 + JSON 解析 + 持久化 |
| **验证** | 单元测试 + mock API 测试 |

### Phase 3: UI 实现（3天）

| 任务 | 产出 |
|------|------|
| 3.1 `ProduceInfoSheet` | BottomSheet 内组件 + 加载/错误态 |
| 3.2 `PersonalizeScreen` | 完整表单 + 保存 + 校验 + 开始定制 |
| 3.3 `PersonalizeViewModel` | 表单状态 + 持久化 |
| 3.4 `DietPlanScreen` | 7天翻页 + 菜谱卡片 + 购物清单联动 |
| 3.5 `DietPlanViewModel` | 生成状态机 + 购物清单联动 |
| 3.6 导航更新 | 路由 + NavGraph + 入口修改 |
| **验证** | `assembleDebug` + UI 手工测试 |

### Phase 4: 集成测试（1.5天）

| 任务 | 产出 |
|------|------|
| 4.1 单元测试（全部） | 10 个测试文件 |
| 4.2 集成测试（API + JSON + Migration） | 3 个测试 |
| 4.3 `strings.xml` 更新 | 新增 25+ 字符串资源 |
| **验证** | 全部测试通过 + `assembleDebug` |

### Phase 5: 清理与文档（0.5天）

| 任务 | 产出 |
|------|------|
| 5.1 废弃 TasteProfile 相关代码 | @Deprecated 注解 |
| 5.2 CLAUDE.md 更新 | v3 新增文件清单 |
| 5.3 API Key 配置文档 | `docs/08-API配置说明.md` |
| **验证** | 全量 `assembleDebug` |

**总计: ~8.5 天**（含 1.5 天缓冲）

---

## 12. 文件清单

### 12.1 新增文件 (21个)

#### Data 层 (9)
| 文件 | 用途 |
|------|------|
| `data/ai/AIService.kt` | AI 抽象接口 |
| `data/ai/AIServiceError.kt` | AI 错误类型封装 |
| `data/ai/QwenAIService.kt` | 千问 DashScope 实现 |
| `data/produce/ProduceInfoEngine.kt` | 果蔬信息引擎 |
| `data/diet/DietPlanEngine.kt` | 饮食计划引擎 |
| `data/history/DietPlanEntity.kt` | Room 饮食计划实体 |
| `data/history/DietPlanDao.kt` | Room 饮食计划 DAO |
| `data/history/UserProfileEntity.kt` | Room 用户档案实体 |
| `data/history/UserProfileDao.kt` | Room 用户档案 DAO |

#### Domain 层 (3)
| 文件 | 用途 |
|------|------|
| `domain/model/ProduceInfo.kt` | 果蔬详细信息模型 + NutritionFacts |
| `domain/model/UserProfile.kt` | 用户档案模型（扩展版）+ ActivityLevel/HealthGoal/Gender 枚举 |
| `domain/model/DietPlan.kt` | 饮食计划领域模型（DietPlan/DailyMealPlan/Meal/DietRecipe/MealType） |

#### Presentation 层 (6)
| 文件 | 用途 |
|------|------|
| `ui/components/ProduceInfoSheet.kt` | 食材详情 BottomSheet 组件 |
| `ui/screen/personalize/PersonalizeScreen.kt` | 个性化定制页面 |
| `ui/screen/personalize/PersonalizeViewModel.kt` | 个性化定制 ViewModel |
| `ui/screen/personalize/DietPlanScreen.kt` | 周计划展示页面 |
| `ui/screen/personalize/DietPlanViewModel.kt` | 周计划 ViewModel |
| `ui/screen/personalize/PersonalizeUiState.kt` | UI 状态数据类 |

#### Assets (2)
| 文件 | 用途 |
|------|------|
| `assets/produce_info.json` | 260 种果蔬核心信息预置 JSON |
| `assets/personalize_schema.json` | 饮食计划输出 JSON Schema（用于 AI prompt） |

#### Config (1)
| 文件 | 用途 |
|------|------|
| `gradle.properties` (追加) | `AI_API_KEY` 配置项 |

### 12.2 修改文件 (9)

| 文件 | 变更 |
|------|------|
| `di/AppModule.kt` | 新增 `AIService`, `ProduceInfoEngine`, `DietPlanEngine` `@Provides` 绑定 |
| `di/DatabaseModule.kt` | 新增 `DietPlanDao` `@Provides` 绑定 |
| `di/Qualifiers.kt` | 新增 `@AIApiKey`, `@AIBaseUrl` 限定符 |
| `data/history/HistoryDatabase.kt` | version 2→3, 新增 `DietPlanEntity`, `MIGRATION_2_3` |
| `navigation/NavGraph.kt` | 新增 `PERSONALIZE` / `DIET_PLAN` 路由，TASTE_PROFILE 重定向 |
| `MainActivity.kt` | 底部导航隐藏规则（DIET_PLAN 隐藏 BottomNav） |
| `ui/screen/analysis/AnalysisScreen.kt` | `DetectedItemCard` 添加点击事件 + `ProduceInfoSheet` 集成 |
| `ui/screen/analysis/AnalysisViewModel.kt` | 注入 `ProduceInfoEngine`，添加 `onItemClicked()` + info Flow |
| `ui/screen/settings/SettingsScreen.kt` | "口味档案" → "个性化定制" 入口 |
| `res/values/strings.xml` | 新增 30+ v3 字符串资源 |

### 12.3 废弃文件 (2)

| 文件 | 处理 |
|------|------|
| `ui/screen/profile/TasteProfileScreen.kt` | @Deprecated，保留文件但导航不再指向 |
| `ui/screen/profile/TasteProfileViewModel.kt` | @Deprecated，DataStore 逻辑迁移到 PersonalizeViewModel + Room |

---

## 13. 风险评估

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|---------|
| **千问 API 不稳定** | 中 | 高 | 自动重试 + 降级到离线体验 + 未来支持模型切换 |
| **produce_info.json 数据质量** | 低 | 中 | 数据来源 USDA + 中国食物成分表，交叉校验核心指标 |
| **AI 生成 JSON 格式不稳定** | 中 | 中 | Prompt 中反复强调 JSON 约束 + 客户端 JSON Schema 校验 + 重试 1 次 |
| **API 费用持续消耗** | 中 | 低 | 缓存策略（食材信息 LRU，计划存 Room）+ 离线降级提示 |
| **用户对 AI 结果不满** | 中 | 低 | Prompt 迭代优化 + 展示"由 AI 生成，仅供参考"免责声明 |
| **Room schema 2→3 迁移失败** | 低 | 高 | 充分测试迁移脚本 + 导出 schema JSON |
| **String 资源缺失影响 lint 检查** | 低 | 中 | 新增字符串全部用 strings.xml 资源，不硬编码 |
| **千问 API 国内访问限制** | 低 | 中 | 用户已配置代理，API 调用走系统代理 |

---

## 附录 A: Prompt 工程记录

### A.1 食材扩展信息 Prompt

```
System: 你是一名资深营养学家和食材专家。用简洁中文回答，严格遵循 JSON 格式。

User: 请介绍：{displayName}（{category}，时令{seasonality}）

返回格式（严格 JSON，不要包含 markdown 标记）：
{
  "selection_tips": "挑选技巧（50字以内）",
  "pairing": ["搭配1", "搭配2"],
  "fun_fact": "趣味知识（40字以内）"
}
```

### A.2 饮食计划生成 Prompt

（见 §4.3 DietPlanEngine 中的完整模板）

### A.3 热量计算公式

当用户未设置自定义热量目标时，使用 Mifflin-St Jeor 公式计算 BMR：

```
BMR(男) = 10 × 体重(kg) + 6.25 × 身高(cm) - 5 × 年龄 + 5
BMR(女) = 10 × 体重(kg) + 6.25 × 身高(cm) - 5 × 年龄 - 161
TDEE = BMR × 活动量系数

目标调整:
- 减脂: TDEE - 300~500 kcal
- 增肌: TDEE + 300~500 kcal
- 维持/健康饮食/控糖: TDEE
```

公式在客户端计算后作为 `calorieTarget` 传入 prompt，不依赖 AI 做数学运算。

---

## 附录 B: 待后续版本 (v3.1+)

| 功能 | 说明 |
|------|------|
| **多模型切换 UI** | 在设置中选择 AI 模型（千问/DeepSeek/自定义） |
| **AI 图片生成** | 为 AI 菜谱生成菜品图片（预留 `DietRecipe.imagePrompt` 字段） |
| **饮食计划日历视图** | 更好的日期管理 UI |
| **营养摄入日统计** | 结合用户的真实饮食记录 |
| **扫码购物清单** | 扫码添加食材到购物清单 |
| **社区分享** | 分享自己的 AI 定制计划 |
| **扩展食材种类** | Fruits-360 之外的食材信息 |

---

> **文档状态:** ✅ 设计完成  
> **下一步:** 文档审查 → 用户审批 → 进入实现计划（writing-plans）  
> **预计实现周期:** ~8.5 天  
> **新增文件:** 21 个 | **修改文件:** 10 个 | **废弃文件:** 2 个
