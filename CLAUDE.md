# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**FreshScan (鲜识)** — an Android app for real-time fruit/vegetable freshness detection using on-device AI. Fully offline detection; AI-powered features (meal suggestions, produce encyclopedia) use DashScope Qwen API.

**Current state:** v4.1 feature-complete. 3-stage on-device inference (EfficientDet-Lite0 detection + 260-class classification + 18-class freshness), 111 preset recipes, AI meal query with favorites, virtual fridge with expiry tracking, produce encyclopedia with search, personalized health profiles, recipe sharing, seasonal produce tips. Room v6 schema, 7 entities, 18 test files. `versionName = "3.0.0"` (not yet bumped for v4).

**Key milestones (all ✅):**
- M1: DI refactor, data models, DB migration, inference pipeline, 8 screens, BottomNavigation
- M2: AnalysisViewModel 3-stage pipeline, BottomSheet 3-state, History auto-save, model accuracy fixes, RecipeEngine (111 recipes)
- M3: Unit tests (P0+P1+P2), Fruits-360 260-class model training (98.86% accuracy), EfficientDet integration, real device bug fixes, code review fixes
- v3: AI service layer (QwenAIService), ProduceInfoEngine, PersonalizeScreen (health profile), Room v2→v3 migration
- v4: Fridge feature (virtual refrigerator with expiry tracking), MealQuery (AI on-demand meal suggestions replacing 7-day diet plans), MealHistory, Room v3→v6 migrations, code review fixes, real device fixes

### Version Roadmap

| Milestone | Scope | Status |
|-----------|-------|--------|
| **M1-M3** | Architecture, inference pipeline, real models, unit tests, polish | ✅ Complete |
| **v3: AI + Encyclopedia** | QwenAIService, ProduceInfoEngine, PersonalizeScreen, DietPlanEngine→MealQueryEngine, ProduceInfoSheet | ✅ Complete (DietPlan replaced by MealQuery in v4) |
| **v4: Fridge + Meal Query** | FridgeScreen + FridgeViewModel + FridgeEntity/Dao/Repository, MealQueryScreen + MealQueryViewModel, MealHistoryEntity/Dao, DB v3→v6, BoundingBox domain model, real device bug fixes | ✅ Complete |
| **UI v2: Japanese Minimalist** | Full M3 redesign: 29-role color scheme (seed #4A6741), Noto Serif/Sans SC typography (15 styles), 3-tier shape system, semantic colors via CompositionLocal, 8-screen redesign, WaveScanOverlay, SemanticDot, dark theme, page transitions | ✅ Complete (2026-06-24) |
| **v4.1: Content Enhancement** | Nav restructure (Home / AI Meal / Fridge·Recipes / Settings), AI meal favorites, favorites list page, produce encyclopedia search bar, recipe sharing card (Canvas→Bitmap→Intent), seasonal produce tips (30 entries, seasonal_produce.json) | ✅ Complete (2026-06-24) — spec: [docs/superpowers/specs/2026-06-24-content-enhancement-design.md](docs/superpowers/specs/2026-06-24-content-enhancement-design.md) |

### 2026-06-24 UI Fixes
- **TopAppBar spacing**: All tab pages — `windowInsets = WindowInsets(0, 0, 0, 0)` to bring titles closer to screen top
- **MealQueryScreen tags**: Quick tags changed from single-row `Row` to `FlowRow` (5 tags now wrap to 2 rows instead of overflowing)
- **MealQuery history sheet**: Removed duplicate `BottomSheetDefaults.DragHandle()` (BottomsheetScaffold already provides one)

### v1 Baseline (preserved)

- 53 Kotlin files, ~4,400 lines, Clean Architecture + MVVM + Hilt
- 18-class MobileNetV3-Small (3.6MB TFLite), CameraX real-time pipeline (3 FPS)
- 3 screens: Main (camera) / Detail / History
- v1 MainScreen still accessible via Settings → 经典模式

### v2-v3 Key Architecture

- **System camera** (`ACTION_IMAGE_CAPTURE`) via FileProvider (replaced real-time CameraX pipeline)
- **3 models**: EfficientDet-Lite0 (detection, 4.4MB) + MobileNetV3-260 (classification, 1.4MB INT8, 98.86%) + MobileNetV3-18 (freshness, CPU-only)
- **10+ screens**: Home / Analysis / Recipe Detail / Personalize (replaced TasteProfile) / Shopping List / History / Detail / Settings / Fridge / MealQuery / FridgeRecipes / Favorites
- **BottomSheet 3-state** results (COLLAPSED / HALF / FULL via ModalBottomSheet)
- **BottomNavigation** 4 tabs: Home / AI膳食 / 冰箱·菜谱 / 我的 (v4.1 redesign)
- **Room v6** with 7 entities: HistoryEntity, FavoriteRecipeEntity, ShoppingItemEntity, UserProfileEntity, DietPlanEntity (retained for data), MealHistoryEntity, FridgeEntity
- **AI features**: QwenAIService (DashScope API), ProduceInfoEngine (local JSON + AI extension), MealQueryEngine (on-demand meal suggestions using Mifflin-St Jeor BMR/TDEE)

## Build & Test Commands

```bash
# Build debug APK (no device needed for compilation)
./gradlew assembleDebug

# Build release APK (R8 + resource shrinking enabled)
./gradlew assembleRelease

# Run all JVM unit tests (no device needed)
./gradlew :app:testDebugUnitTest

# Run a single test class (Gradle 8.x test filtering)
./gradlew :app:testDebugUnitTest --tests "*RecipeEngineTest*"

# Clean + rebuild
./gradlew clean assembleDebug

# Lint checks
./gradlew lint
```

## Architecture: MVVM + Clean Architecture (3-layer)

| Layer | Location | Key classes |
|-------|----------|-------------|
| **Presentation** | `ui/` | Compose Screens + ViewModels + UiState data classes |
| **Domain** | `domain/` | Domain models (DetectedItem, Recipe, FridgeItem, MealSuggestion, BoundingBox, ProduceInfo, UserProfile) + Repository interfaces |
| **Data** | `data/` | Repository impls wrapping TFLite, Room, MediaPipe, OkHttp |

**Inference data flow:** System camera photo → `Bitmap` → `EfficientDetEngine` (detection) → per-box crop with 15% margin → `TFLiteClassifier` (260-class) + `TFLiteClassifier` (18-class freshness, CPU-only) → `List<DetectedItem>` → particle animation → BottomSheet results.

## Dependency Injection (Hilt)

**Qualifiers** in [di/Qualifiers.kt](app/src/main/java/com/example/freshscan/di/Qualifiers.kt):
- `@ModelV1` → `ModelConfig` (18-class freshness)
- `@ModelV2` → `ModelConfigV2` (260-class)
- `@FreshnessModel` → `TFLiteClassifier` (18-class, CPU-only, `forceCpuInitial=true`)
- `@DetectionModel` → EfficientDetEngine
- `@RecipeJsonPath` → recipe JSON asset path
- `@AIApiKey` / `@AIBaseUrl` → DashScope Qwen API credentials

**Key DI modules:**
- [AppModule.kt](app/src/main/java/com/example/freshscan/di/AppModule.kt) — provides classifiers, engines, AI service, repositories, DataStore
- [DatabaseModule.kt](app/src/main/java/com/example/freshscan/di/DatabaseModule.kt) — provides Room DB + all 6 DAOs + migrations

## Configuration Notes

- `compileSdk = 36`, `minSdk = 24`, `targetSdk = 36`
- JVM target: Java 11
- Kotlin 2.0.21 (built-in Compose compiler plugin — **no** `composeOptions` block)
- AGP 8.13.2, KSP for Room + Hilt annotation processing
- Version catalog: `gradle/libs.versions.toml`
- `android.nonTransitiveRClass = true`, `kotlin.code.style = official`
- `resourceConfigurations = ["zh", "en"]`, `ndk.abiFilters = ["arm64-v8a"]`
- ProGuard: `app/proguard-rules.pro`
- `testOptions.unitTests.isReturnDefaultValues = true` — prevents Android API stubs from throwing in JVM tests
- `testImplementation("org.json:json:20231013")` — real `org.json` for JVM tests (Android's stub returns null)

## Key Files Reference

### Inference Pipeline

| File | Purpose |
|------|---------|
| [data/inference/EfficientDetEngine.kt](app/src/main/java/com/example/freshscan/data/inference/EfficientDetEngine.kt) | MediaPipe ObjectDetector wrapper, `detect(bitmap)` |
| [data/inference/DetectionPostprocessor.kt](app/src/main/java/com/example/freshscan/data/inference/DetectionPostprocessor.kt) | NMS (IoU 0.5) + coordinate denormalization + 15% crop margin |
| [data/inference/TFLiteClassifier.kt](app/src/main/java/com/example/freshscan/data/inference/TFLiteClassifier.kt) | Parameterized classifier: `(context, modelFileName, numClasses, modelLoader, forceCpuInitial)` with lazy loading |
| [data/inference/ModelLoader.kt](app/src/main/java/com/example/freshscan/data/inference/ModelLoader.kt) | GPU/CPU delegate strategy |
| [data/mapper/ModelMapper.kt](app/src/main/java/com/example/freshscan/data/mapper/ModelMapper.kt) | v1 Softmax + Top-K mapping (uses `MathUtils.softmax()`) |
| [data/mapper/ModelMapperV2.kt](app/src/main/java/com/example/freshscan/data/mapper/ModelMapperV2.kt) | 260-class Softmax + `LabelResult` (Known/Unknown) |
| [data/mapper/EntityMapper.kt](app/src/main/java/com/example/freshscan/data/mapper/EntityMapper.kt) | Domain↔Entity mapping, `mapLabelToFruitCategory()` (multi-strategy) |
| [util/MathUtils.kt](app/src/main/java/com/example/freshscan/util/MathUtils.kt) | Numerically-stable Softmax (shared by both mappers) |
| [util/ImagePreprocessor.kt](app/src/main/java/com/example/freshscan/util/ImagePreprocessor.kt) | ARGB_8888 normalization, `bitmapToTensorBuffer` |
| [util/Constants.kt](app/src/main/java/com/example/freshscan/util/Constants.kt) | `MIN_LOGIT_FOR_CONFIDENCE = 2.0f`, deprecated CameraX constants |

### Persistence (Room v6)

| File | Purpose |
|------|---------|
| [data/history/HistoryDatabase.kt](app/src/main/java/com/example/freshscan/data/history/HistoryDatabase.kt) | Room DB v6 — 7 entities, migrations 1→2 through 5→6 |
| [data/history/HistoryDao.kt](app/src/main/java/com/example/freshscan/data/history/HistoryDao.kt) | Scan history CRUD + `@Transaction insertAllAndTrim()` |
| [data/history/FavoriteRecipeDao.kt](app/src/main/java/com/example/freshscan/data/history/FavoriteRecipeDao.kt) | Favorite recipes CRUD |
| [data/history/ShoppingListDao.kt](app/src/main/java/com/example/freshscan/data/history/ShoppingListDao.kt) | Shopping list CRUD with dedup |
| [data/history/UserProfileDao.kt](app/src/main/java/com/example/freshscan/data/history/UserProfileDao.kt) | User profile singleton (id=1) |
| [data/history/MealHistoryDao.kt](app/src/main/java/com/example/freshscan/data/history/MealHistoryDao.kt) | AI meal suggestion history, `getRecentTitles()` for exclusion prompts |
| [data/history/FridgeDao.kt](app/src/main/java/com/example/freshscan/data/history/FridgeDao.kt) | Virtual fridge CRUD + `getExpiringSoonFlow(thresholdMs)` + `getCountFlow()` |

### Recipe & Food Data

| File | Purpose |
|------|---------|
| [data/recipe/RecipeEngine.kt](app/src/main/java/com/example/freshscan/data/recipe/RecipeEngine.kt) | JSON loader + `recommend(items, profile)` match-count scoring + taste weighting |
| [data/recipe/LabelNormalizer.kt](app/src/main/java/com/example/freshscan/data/recipe/LabelNormalizer.kt) | Fruits-360 label → recipe ingredient name (loads `labels_v2_normalization.json`) |
| [assets/recipes/preset_recipes.json](app/src/main/assets/recipes/preset_recipes.json) | 111 preset recipes × 5 categories |
| [assets/produce_info.json](app/src/main/assets/produce_info.json) | Produce encyclopedia entries (5 sample, ~82 planned) |

### AI & Intelligence (v3-v4)

| File | Purpose |
|------|---------|
| [data/ai/AIService.kt](app/src/main/java/com/example/freshscan/data/ai/AIService.kt) | Abstract AI interface (`chat`, `chatJson`) |
| [data/ai/AIServiceError.kt](app/src/main/java/com/example/freshscan/data/ai/AIServiceError.kt) | AI error types (Network/Timeout/Quota/InvalidResponse/Unknown) |
| [data/ai/QwenAIService.kt](app/src/main/java/com/example/freshscan/data/ai/QwenAIService.kt) | DashScope Qwen API client (OkHttp, auto-retry, code fence stripping) |
| [data/produce/ProduceInfoEngine.kt](app/src/main/java/com/example/freshscan/data/produce/ProduceInfoEngine.kt) | Produce encyclopedia (local JSON + AI extension + LRU cache) |
| [data/diet/DietPlanEngine.kt](app/src/main/java/com/example/freshscan/data/diet/DietPlanEngine.kt) | **MealQueryEngine** class — AI on-demand meal suggestions (Mifflin-St Jeor BMR/TDEE + prompt building + Room persistence). Original DietPlanEngine deprecated. |

### Key Screens

| Screen | ViewModel | Notes |
|--------|-----------|-------|
| [HomeScreen](app/src/main/java/com/example/freshscan/ui/screen/home/HomeScreen.kt) | HomeViewModel | Landing page, FAB camera button |
| [AnalysisScreen](app/src/main/java/com/example/freshscan/ui/screen/analysis/AnalysisScreen.kt) | AnalysisViewModel | 3-stage pipeline + BottomSheet + ProduceInfoSheet |
| [RecipeDetailScreen](app/src/main/java/com/example/freshscan/ui/screen/recipe/RecipeDetailScreen.kt) | RecipeDetailViewModel | Timer state machine + favorites + shopping list |
| [PersonalizeScreen](app/src/main/java/com/example/freshscan/ui/screen/personalize/PersonalizeScreen.kt) | PersonalizeViewModel | Health profile form (replaces TasteProfile) |
| [MealQueryScreen](app/src/main/java/com/example/freshscan/ui/screen/personalize/MealQueryScreen.kt) | MealQueryViewModel | AI meal query + favorites (v4.1) + history (replaces DietPlanScreen) |
| [FridgeScreen](app/src/main/java/com/example/freshscan/ui/screen/fridge/FridgeScreen.kt) | FridgeViewModel | Virtual fridge with expiry tracking, swipe-to-delete |
| [FridgeRecipesScreen](app/src/main/java/com/example/freshscan/ui/screen/fridge/FridgeRecipesScreen.kt) | FridgeRecipesViewModel | **v4.1** — Merged fridge summary + recipe browsing + produce search |
| [FavoritesScreen](app/src/main/java/com/example/freshscan/ui/screen/favorites/FavoritesScreen.kt) | FavoritesViewModel | **v4.1** — Grouped favorites (preset + AI), swipe-to-delete |
| [HistoryScreen](app/src/main/java/com/example/freshscan/ui/screen/history/HistoryScreen.kt) | HistoryViewModel | Scan history list (demoted from tab to Settings sub-entry in v4.1) |
| [SettingsScreen](app/src/main/java/com/example/freshscan/ui/screen/settings/SettingsScreen.kt) | SettingsViewModel | Recognition mode, favorites, history, shopping list, personalize, about |

### Navigation

[NavGraph.kt](app/src/main/java/com/example/freshscan/navigation/NavGraph.kt) — 14 routes, 4-tab BottomNavigation (Home/MealQuery/FridgeRecipes/Settings). `Routes.TASTE_PROFILE`, `Routes.DIET_PLAN`, and `Routes.FRIDGE` deprecated with redirects. `Routes.FAVORITES` and `Routes.FRIDGE_RECIPES` added in v4.1. History demoted from tab to full-screen route.

### Test Files (18 files)

| File | Tests focus |
|------|-------------|
| [QwenAIServiceTest.kt](app/src/test/java/com/example/freshscan/data/ai/QwenAIServiceTest.kt) | DashScope API client: success/error responses, retry, malformed JSON |
| [MealQueryEngineTest.kt](app/src/test/java/com/example/freshscan/data/diet/MealQueryEngineTest.kt) | AI response parsing (10+ edge cases), TDEE calc, history, entity mapping |
| [ProduceInfoEngineTest.kt](app/src/test/java/com/example/freshscan/data/produce/ProduceInfoEngineTest.kt) | Local JSON loading, AI extension, cache hits, AI degradation |
| [DetectionPostprocessorTest.kt](app/src/test/java/com/example/freshscan/data/inference/DetectionPostprocessorTest.kt) | IoU (9 cases), NMS (10 cases), DetectedBox |
| [ModelConfigV2Test.kt](app/src/test/java/com/example/freshscan/data/inference/model/ModelConfigV2Test.kt) | labels_v2.txt parsing (260 lines), error handling |
| [TFLiteClassifierTest.kt](app/src/test/java/com/example/freshscan/data/inference/TFLiteClassifierTest.kt) | Multi-instance isolation, lazy loading, forceCpu, GPU fallback |
| [ModelMapperTest.kt](app/src/test/java/com/example/freshscan/data/mapper/ModelMapperTest.kt) | v1 Softmax + classification |
| [ModelMapperV2Test.kt](app/src/test/java/com/example/freshscan/data/mapper/ModelMapperV2Test.kt) | v2 Softmax precision, Known/Unknown mapping |
| [LabelNormalizerTest.kt](app/src/test/java/com/example/freshscan/data/recipe/LabelNormalizerTest.kt) | Mapping coverage, fallback, multi-mapping |
| [RecipeEngineTest.kt](app/src/test/java/com/example/freshscan/data/recipe/RecipeEngineTest.kt) | JSON parsing (111 recipes), matching algorithm (30 tests) |
| [ImagePreprocessorTest.kt](app/src/test/java/com/example/freshscan/util/ImagePreprocessorTest.kt) | ARGB_8888 normalization, config validation |
| [AnalysisViewModelTest.kt](app/src/test/java/com/example/freshscan/ui/screen/analysis/AnalysisViewModelTest.kt) | State machine (6 states), side effects, sheet states |
| [PersonalizeViewModelTest.kt](app/src/test/java/com/example/freshscan/ui/screen/personalize/PersonalizeViewModelTest.kt) | Form state, save/load, navigation |
| [SettingsViewModelTest.kt](app/src/test/java/com/example/freshscan/ui/screen/settings/SettingsViewModelTest.kt) | DataStore prefs, classic mode toggle, clearHistory |
| [RecipeDetailViewModelTest.kt](app/src/test/java/com/example/freshscan/ui/screen/recipe/RecipeDetailViewModelTest.kt) | Timer state machine, favorites, shopping list |
| [ShoppingListViewModelTest.kt](app/src/test/java/com/example/freshscan/ui/screen/shopping/ShoppingListViewModelTest.kt) | DAO integration, dedup |
| [TasteProfileViewModelTest.kt](app/src/test/java/com/example/freshscan/ui/screen/profile/TasteProfileViewModelTest.kt) | Legacy profile (deprecated, retained for coverage) |
| [SaveResultUseCaseTest.kt](app/src/test/java/com/example/freshscan/domain/usecase/SaveResultUseCaseTest.kt) | History save use case |

## Model Training

### Environment

**Python:** `C:\Users\33525\.workbuddy\binaries\python\envs\tf210\Scripts\python.exe`
**Conda env:** `tf210` — Python 3.10.11 + TF 2.10.0 + CUDA 11.8 + cuDNN 8.9.7

> TF 2.10.0 is the **last version with native Windows GPU support** (TF 2.11+ requires WSL2).

```bash
conda activate tf210
python -c "import tensorflow as tf; print(tf.config.list_physical_devices('GPU'))"
```

### Model Input/Output Contract

- **Input**: `[1, 224, 224, 3]` float32, RGB, normalized to `[0, 1]` (pixel/255.0)
- **v1 Output**: `[1, 18]` float32 logits (even=fresh, odd=rotten)
- **v2 Output**: `[1, 260]` float32 logits (Fruits-360 classes)
- **Post-process**: Softmax on Android side (ModelMapper), not in model

### Model Files in Assets

- `fruit_freshness_model.tflite` — v1 18-class freshness, CPU-only
- `efficientdet_lite0.tflite` — EfficientDet-Lite0 detection, 4.4MB
- `fruits360_model.tflite` — 260-class Fruits-360, 98.86% accuracy, INT8 quantized, 1.4MB

## Bug Fixes Reference

Key bugs encountered and fixed (for context when modifying affected files):

| # | Issue | Root Cause | Fix |
|---|-------|------------|-----|
| 1 | 重新拍照不启动相机 | `LaunchedEffect(Unit)` only fires once; stale File URI | Key on `screenState`; fresh File each Idle transition |
| 2 | 相机取消后卡死 | `TakePicture` callback ignored `success=false` | `else` branch calls `onNavigateBack()` |
| 3 | 橙子新鲜→腐败 (60%+) | GPU float precision variance | `@FreshnessModel` force CPU (`forceCpuInitial=true`) |
| 4 | 苹果完全识别不出 | `MIN_LOGIT_FOR_CONFIDENCE=4.0` too strict | Lowered to `2.0` |
| 5 | 真实照片颜色偏差 | Camera JPEG may use Display P3 | Force `ARGB_8888` in `loadBitmap` + `bitmapToTensorBuffer` |
| 6 | BottomSheet无法拖拽 | `nestedScroll` hijacked ModalBottomSheet gestures | Removed custom nestedScroll |
| 7 | DetectionPostprocessorTest IoU全部失败 | Android JVM stub `RectF.width()` returns 0f | `iou()` uses field arithmetic `(right-left)*(bottom-top)` |
| 17 | 历史记录显示"未知" | 260-class labels can't map to v1 enum (9 values) | `mapLabelToFruitCategory()` 4-strategy resolution |
| 18 | 真机拍照误识别 | COCO FOOD_CLASSES filter discarded valid boxes | Removed filter; added 15% crop margin |

## Design Documents

| Document | Content |
|----------|---------|
| [docs/01-需求分析-PRD-v2.md](docs/01-需求分析-PRD-v2.md) | Product requirements, user stories |
| [docs/02-架构设计文档-v2.md](docs/02-架构设计文档-v2.md) | System architecture, data flow, model specs |
| [docs/03-UI设计规格-v2.md](docs/03-UI设计规格-v2.md) | Page layouts, color system, animation specs |
| [docs/uiv2.md](docs/uiv2.md) | **UI v2 design spec (2026-06-24)** — 日系精致风格完整重设计，10 章涵盖颜色/字体/动效/8 页面/组件库/暗色主题/二期展望 |
| [docs/04-开发计划-v2.md](docs/04-开发计划-v2.md) | Dev schedule, dataset strategy |
| [docs/06-详细设计文档-v2.md](docs/06-详细设计文档-v2.md) | **Implementation blueprint** — DI bindings, class signatures, state machines, JSON schemas |
| [docs/05-文档审查报告-v2.md](docs/05-文档审查报告-v2.md) | Cross-document review findings |
| [docs/superpowers/specs/2026-06-18-freshscan-v3-design.md](docs/superpowers/specs/2026-06-18-freshscan-v3-design.md) | v3 design spec |
| [docs/superpowers/plans/2026-06-18-freshscan-v3-implementation.md](docs/superpowers/plans/2026-06-18-freshscan-v3-implementation.md) | v3 implementation plan (17 tasks, 6 phases) |
| [docs/superpowers/specs/2026-06-24-content-enhancement-design.md](docs/superpowers/specs/2026-06-24-content-enhancement-design.md) | **v4.1 Content Enhancement (2026-06-24)** — nav restructure + recipe favorites + produce search + sharing + seasonal tips |
| [docs/06-代码审查报告-v3.md](docs/06-代码审查报告-v3.md) | v3 code review report |
| [docs/09-代码审查报告-v4.md](docs/09-代码审查报告-v4.md) | **v4 code review (2026-06-24)** — 62 findings: 6 Critical + 16 High + 22 Medium + 17 Low + 1 Info |
| [docs/07-代码审查报告-v4.md](docs/07-代码审查报告-v4.md) | v4 code review report (earlier batch) |

## Known Issues (from 2026-06-24 code review)

**6 Critical items — all ✅ fixed (2026-06-24)** — see [docs/09-代码审查报告-v4.md](docs/09-代码审查报告-v4.md) for original findings:

| # | Issue | File | Fix applied |
|---|-------|------|-------------|
| C1 | OkHttp Response never closed (connection pool leak) | [QwenAIService.kt:143-168](app/src/main/java/com/example/freshscan/data/ai/QwenAIService.kt#L143-L168) | `try-finally` with `response.body?.close()` |
| C2 | `classify()` / `close()` race — `ensureLoaded()` outside lock | [TFLiteClassifier.kt:77-79](app/src/main/java/com/example/freshscan/data/inference/TFLiteClassifier.kt#L77-L79) | Moved `ensureLoaded()` into `synchronized(interpreterLock)` block |
| C3 | `startAnalysis()` missing job cancellation (concurrent pipelines) | [AnalysisViewModel.kt:115-210](app/src/main/java/com/example/freshscan/ui/screen/analysis/AnalysisViewModel.kt#L115-L210) | `analysisJob?.cancel()` + bitmap in `finally { recycle() }` |
| C4 | `removeItem()`/`clearAll()` no try-catch (crash risk) | [FridgeViewModel.kt:62-79](app/src/main/java/com/example/freshscan/ui/screen/fridge/FridgeViewModel.kt#L62-L79) | try-catch with error snackbar + `_isDeleting` in `finally` |
| C5 | `photoUri` lost on config change (`remember` → `rememberSaveable`) | [AnalysisScreen.kt:74-97](app/src/main/java/com/example/freshscan/ui/screen/analysis/AnalysisScreen.kt#L74-L97) | Custom `Saver<Uri?, String>` with `rememberSaveable` |
| C6 | `addItem` TOCTOU race (check-then-insert not atomic) | [ShoppingListDao.kt:40-57](app/src/main/java/com/example/freshscan/data/history/ShoppingListDao.kt#L40-L57) + [ShoppingListViewModel.kt:42-63](app/src/main/java/com/example/freshscan/ui/screen/shopping/ShoppingListViewModel.kt#L42-L63) | `@Transaction insertIfNotExists()` in DAO |

## UI v2 Overhaul (✅ Complete 2026-06-24)

Implemented the complete UI redesign specified in [docs/uiv2.md](docs/uiv2.md) across 10 phases. Build and 18 test suites pass at every phase.

### Theme System

| File | Purpose |
|------|---------|
| [ui/theme/Color.kt](app/src/main/java/com/example/freshscan/ui/theme/Color.kt) | Full 29-role M3 Light + Dark ColorScheme (seed #4A6741 苔藓绿). Old colors retained as `@Deprecated`. |
| [ui/theme/Type.kt](app/src/main/java/com/example/freshscan/ui/theme/Type.kt) | 15-style typography: Noto Serif SC Bold (display/headlines) + Noto Sans SC Regular/Medium (body/labels). `SerifFamily` / `SansFamily` font families. |
| [ui/theme/Shape.kt](app/src/main/java/com/example/freshscan/ui/theme/Shape.kt) | Unified 3-tier: small=8dp, medium=16dp, large=24dp |
| [ui/theme/Theme.kt](app/src/main/java/com/example/freshscan/ui/theme/Theme.kt) | `FreshScanTheme` wrapping M3 + `LocalSemanticColors` CompositionLocal. Auto dark/light via `isSystemInDarkTheme()`. |

### Semantic Colors

- `LocalSemanticColors` — `staticCompositionLocalOf` providing `freshnessHigh`/`Medium`/`Low`, `fridgeExpiring`/`fridgeExpired`
- 6 files migrated from hardcoded `FreshGreen`/`RottenRed`/`UncertainOrange` to semantic tokens

### New Components

| Component | Location | Purpose |
|-----------|----------|---------|
| `WaveScanOverlay` | [ui/components/WaveScanOverlay.kt](app/src/main/java/com/example/freshscan/ui/components/WaveScanOverlay.kt) | Ink-wave Canvas animation (3 concentric ripples, 0.45s stagger, moss green alpha 12%). Replaces ParticleScan. |
| `SemanticDot` | [ui/components/SemanticDot.kt](app/src/main/java/com/example/freshscan/ui/components/SemanticDot.kt) | 2-state solid/hollow dot (8dp) for ingredient ownership. Replaces ✅/⬜ emoji. |
| `FridgeMiniIndicator` | [ui/screen/home/HomeScreen.kt](app/src/main/java/com/example/freshscan/ui/screen/home/HomeScreen.kt) | Kitchen icon + count badge in TopAppBar |
| `TabIndicator` | [ui/screen/home/HomeScreen.kt](app/src/main/java/com/example/freshscan/ui/screen/home/HomeScreen.kt) | Underline-animated tab row replacing FilterChip scroll |

### Screen Changes Summary

| Screen | Key changes |
|--------|-------------|
| **HomeScreen** | 2-column `LazyVerticalGrid`, Tab indicator, Hero 200→120dp, FridgeMiniIndicator, outlined cards |
| **AnalysisScreen** | WaveScanOverlay replaces ParticleScan, photo background during analysis, Serif "AI 正在分析…", empty state single button |
| **FridgeScreen** | All colors→semantic tokens, SwipeToDismiss uses `freshnessLow` |
| **RecipeDetailScreen** | SemanticDot replaces emoji, Serif TopAppBar title, OutlinedButton+FilledTonalButton swap |
| **MealQueryScreen** | QueryArea wrapped in dialog-style Card, 5 quick tags |
| **SettingsScreen** | 4 sections wrapped in Card grouping |
| **PersonalizeScreen** | Inherits theme changes (semantic colors via CompositionLocal) |
| **HistoryScreen** | confidenceColor→`LocalSemanticColors`, SwipeToDismiss background updated |

### Dark Theme

Full `DarkColorScheme` (29 roles) + `DarkSemanticColors` delivered simultaneously. `FreshScanTheme` auto-selects based on system setting.

### Motion (P10)

NavHost page transitions: `slideInHorizontally`+`fadeIn` (300ms) / `slideOutHorizontally`+`fadeOut` (200ms) on ANALYSIS, DETAIL, RECIPE_DETAIL routes.

### Fonts

3 subset static TTFs in `res/font/`: Noto Sans SC Regular (397KB) + Medium (397KB) + Noto Serif SC Bold (536KB) ≈ 1.3MB total. Subset to ~1500 common Chinese characters via fonttools.

### Design Direction

日系精致风格 (Japanese minimalist), 苔藓绿灰调色板, Noto Serif/Sans SC, MUJI-like restraint. Cards use `outlineVariant` border strokes over shadow elevation. Serif for brand headlines, Sans for UI text.
