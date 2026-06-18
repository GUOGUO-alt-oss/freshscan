# Design Doc Fixes Specification

> Date: 2026-06-14
> Scope: Fix 23 issues across three design documents (PRD, Architecture, Detailed Design) + create one new document
> Target: Cross-document consistency, completeness, and technical correctness before any code implementation

## Baseline Decisions (Confirmed)

| Decision | Value | Rationale |
|----------|-------|-----------|
| Fruit list | apple, banana, orange, **tomato**, strawberry, mango | Matches Kaggle dataset |
| Package name | `com.example.freshscan` | Matches existing project |
| minSdk | 24 (Android 7.0) | Already set in code |
| compileSdk | 36 | Already set in code |
| Java target | 11 | Already set in code |
| Kotlin | 2.0.21 | Already set in code, built-in Compose compiler |
| AGP | 8.13.2 | Already set in code |

---

## Round 1: Cross-Document Consistency Fixes

### 1.1 PRD: Fruit list correction (蓝莓 → 番茄)

**File:** `docs/01-需求分析-PRD.md`

- §1 产品定位、§4 P0功能表、§8 Q-01、附录：所有"蓝莓(Blueberry)"改为"番茄(Tomato)"
- Q-01 结论改为"已确认使用 Kaggle 数据集中的 6 种水果（含番茄）"

### 1.2 PRD: Add UNCERTAIN state to P0

- §4 P0功能表增加一行："**不确定状态提示** — 当置信度低于阈值时，显示'不确定'而非强行判断新鲜/腐烂，并提示用户调整角度/光线"

### 1.3 Architecture: Package name (fruitfreshness → freshscan)

**File:** `docs/02-架构设计文档.md`

- §6 文件结构：所有 `com/example/fruitfreshness` → `com/example/freshscan`
- §6 目录树中的 `FruitFreshnessApp.kt` → 保留（Application 类名不变，只是包路径改）
- 所有代码块中的 package 声明同步更新

### 1.4 Detailed Design: Package name (fruitfreshness → freshscan)

**File:** `docs/03-详细设计文档.md`

- 所有包路径引用 `com.example.fruitfreshness` → `com.example.freshscan`
- §7.1 build.gradle.kts 中 applicationId 改为 `com.example.freshscan`

### 1.5 Architecture: SDK / version alignment

- §3.1 技术栈总览表：最低 SDK → API 24 (Android 7.0)，Kotlin → 2.0+（备注内置 Compose 编译器）
- §10.1 libs.versions.toml：Kotlin → 2.0.21，AGP → 8.13.2，删除 `compose-compiler` 版本号（Kotlin 2.0 内置）
- §10.1 删除 `kotlin-compose` 之外的旧 compose-compiler 引用

### 1.6 Detailed Design: SDK / version alignment

- §7.1 build.gradle.kts：compileSdk → 36，minSdk → 24，Java → VERSION_11，jvmTarget → "11"
- §7.1 删除 `composeOptions { kotlinCompilerExtensionVersion = "1.5.12" }`（Kotlin 2.0 不需要）
- §7.1 `buildFeatures { compose = true }` 保留（Kotlin 2.0 仍需要启用）

---

## Round 2: Fill Missing Content

### 2.1 New: Model Training Guide

**New file:** `docs/04-模型训练说明.md`

Minimum contents:
- Kaggle dataset URL: `https://www.kaggle.com/datasets/sriramr/fruits-fresh-and-rotten-for-classification`
- Dataset stats: 13,599 images, 6 fruits × 2 states = 12 classes
- Training framework: TensorFlow 2.x / Keras
- Base model: MobileNetV3-Small (ImageNet pretrained)
- Data split: 80/10/10 (train/val/test), stratified
- Data augmentation: random horizontal flip, rotation ±20°, brightness ±15%, zoom ±15%
- Input size: 224×224×3, RGB, pixel range [0,1]
- Output: 12-class softmax
- Training config: Adam optimizer, lr=1e-4, batch=32, epochs=30-50, early stopping (patience=5)
- TFLite conversion: `tf.lite.TFLiteConverter.from_keras_model()` → INT8 quantization with representative dataset
- Quantization accuracy target: < 2% accuracy loss vs FP32
- Output files: `fruit_freshness_model.tflite` (INT8), `labels.txt` (12 lines)
- Validation: confusion matrix, per-class precision/recall, top-1 accuracy ≥ 85%

### 2.2 Architecture: App lifecycle strategy

**File:** `docs/02-架构设计文档.md` — new section after §5 (module division)

Section title: **"5.3 应用生命周期策略"**

- Single Activity + Compose Navigation — no multi-Activity complexity
- `onStop()`: release CameraX (unbind from lifecycle), keep TFLite model in memory
- `onStart()`: re-bind CameraX, resume inference if model loaded
- Configuration changes: Activity handles via `configChanges` in manifest (rotation handled manually if needed)
- Process death: `SavedStateHandle` in ViewModel preserves last result; model reloads automatically on restart
- `onDestroy()` (app exit): release camera, close TFLite interpreter, close Room database

### 2.3 Detailed Design: GPU delegate detection and degrade

**File:** `docs/03-详细设计文档.md` — add to §4 or §5

```
GPU delegate strategy:
1. check: TfLiteGpu.isGpuDelegateAvailable(context)
2. GPU available → try create GpuDelegate → add to Interpreter.Options
3. GPU creation fails → fall back to CPU 4 threads + XNNPACK
4. Runtime GPU error → catch IllegalStateException → retry on CPU
5. Decision cached in shared preferences to avoid repeated GPU attempts per session
```

### 2.4 Detailed Design: CameraError completeness

**File:** `docs/03-详细设计文档.md` — §2.1.2 `CameraError` sealed class

Add two new subtypes:
```kotlin
/** 相机被系统断开（如来电） */
data object Disconnected : CameraError()
/** CameraX 内部不可恢复错误 */
data class Internal(val message: String, val throwable: Throwable? = null) : CameraError()
```

### 2.5 Architecture: Camera2 fallback strategy clarification

**File:** `docs/02-架构设计文档.md` — §12.1 风险 R-03

Change mitigation from "添加 Camera2 API 降级分支" to:
> "CameraX 已覆盖 90%+ 设备。对于已知 OEM 兼容问题，在 CameraRepository 接口下提供 Camera2FallbackCameraRepository 实现，通过设备型号白名单决定使用哪种实现。优先级 P2。"

### 2.6 PRD + Detailed Design: History save strategy

**PRD §4 P1:** 将"识别历史记录"从 P1 移到 P0，说明改为"自动保存（每条稳定结果自动写入 Room，最多 50 条）"

**Detailed Design §3:** 补充说明：
- 识别结果稳定后自动调用 `HistoryRepository.save()`
- 缩略图延迟保存：元数据先写入，用户点击"保存图片"时再写缩略图到 filesDir
- 自动 `trimToMaxCount(50)` 在每次 insert 后触发

### 2.7 Detailed Design: BoundingBox MVP simplification

**File:** `docs/03-详细设计文档.md` — §2.1.4

Replace the fake-bounding-box design:
> MVP 阶段使用**固定引导圈**（画面中央半透明椭圆环 + "将果蔬置于此处"文本），而非假目标框。
> BoundingBoxOverlay 组件保留接口但标记为 P1（待检测模型升级后启用）。
> MainScreen 的 Compose 树中：`GuideCircle` 在 P0 渲染，`BoundingBoxOverlay` 在 P1 渲染。

---

## Round 3: Detail Corrections

### 3.1 Detailed Design: Thread safety

**File:** `docs/03-详细设计文档.md` — §A.2

`recentResults` mutable list → replace with:
```kotlin
private val _recentResults = Mutex()
private val recentResults = mutableListOf<RecognitionResult>()

fun onNewResult(result: RecognitionResult) {
    viewModelScope.launch {
        _recentResults.withLock {
            recentResults.add(result)
            // ... stability check ...
            if (recentResults.size > 10) recentResults.removeFirst()
        }
    }
}
```

Or simpler alternative: use `AtomicReference` with immutable list snapshots.

### 3.2 Detailed Design: strings.xml i18n

**File:** `docs/03-详细设计文档.md` — §2

Add note:
> 所有 UI 文本在 Compose 中通过 `stringResource(R.string.xxx)` 引用。`strings.xml` 提供中文（默认）和英文两份。
> 详细设计代码示例中的硬编码中文仅为示意。

### 3.3 Detailed Design: Remove MIGRATION_1_2 placeholder

**File:** `docs/03-详细设计文档.md` — §3.3

Delete the `MIGRATION_1_2` code block. Replace with note:
> MVP 阶段版本号为 1。后续 schema 变更时，在对应版本编写 Migration 并注册到 `databaseBuilder.addMigrations()`。
> 原则：不使用 `fallbackToDestructiveMigration()`。

### 3.4 Architecture: Unify parameter types

**File:** `docs/02-架构设计文档.md` — §7.3 MainViewModel methods

`onFrameAvailable(bitmap)` → `onFrameAvailable(frameData: FrameData)`
(Match §8.1 sequence diagram and §13.2 FrameData definition.)

### 3.5 Detailed Design: Preprocessing pipeline optimization

**File:** `docs/03-详细设计文档.md` — §4.1

Add recommendation:
> 优先使用 TFLite Support Library 的 `TensorImage.load(imageProxy)` + `ImageProcessor.Builder().add(ResizeOp(...)).add(NormalizeOp(...)).build()` 进行预处理，避免中间 Bitmap 分配。仅在 TFLite Support Library 不可用时回退到手动 Bitmap 路径。

### 3.6 Detailed Design: Storage error recovery

**File:** `docs/03-详细设计文档.md` — §5.4

Add note:
> 缩略图保存失败时，HistoryItem 仍以 `thumbnailPath=null` 写入 Room。历史列表中使用默认水果图标作为占位缩略图。

### 3.7 PRD: Dataset accuracy benchmark

**File:** `docs/01-需求分析-PRD.md` — §7.1

Add footnote:
> 基准数据来源：Kaggle "Fruits Fresh and Rotten for Classification" 数据集。MobileNetV3-Small 在该数据集上经 fine-tune 后 typical top-1 accuracy ~90-95%（参考公开 notebook）。

### 3.8 Architecture: FruitCategory enum

**File:** `docs/02-架构设计文档.md` — §7.1 FruitCategory

TOMATO → TOMATO (already correct, just verify), ensure UNKNOWN is last entry.

---

## Verification Checklist

After all fixes are applied, verify:

- [ ] grep for "蓝莓"/"blueberry" across all docs → zero results
- [ ] grep for "fruitfreshness" across all docs → zero results (except this spec)
- [ ] grep for "minSdk.*26" / "API 26" across all docs → zero results (unless contextually appropriate)
- [ ] grep for "compileSdk.*34" across all docs → zero results
- [ ] grep for "JavaVersion.VERSION_17" / "jvmTarget.*17" → zero results
- [ ] grep for "1.9.23" / "8.3.2" (old Kotlin/AGP versions) → zero results
- [ ] grep for "compose-compiler" + "1.5.12" → zero results
- [ ] grep for "蓝莓" → zero results
- [ ] PRD P0 function table contains "不确定状态提示"
- [ ] Architecture doc has lifecycle section
- [ ] Detailed design CameraError has Disconnected + Internal subtypes
- [ ] docs/04-模型训练说明.md exists with minimum required sections
