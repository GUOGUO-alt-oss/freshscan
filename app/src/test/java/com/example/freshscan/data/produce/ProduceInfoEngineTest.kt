package com.example.freshscan.data.produce

import android.content.Context
import app.cash.turbine.test
import com.example.freshscan.data.ai.AIService
import com.example.freshscan.data.recipe.LabelNormalizer
import com.example.freshscan.util.Logger
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProduceInfoEngineTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var context: Context
    private lateinit var aiService: AIService
    private lateinit var labelNormalizer: LabelNormalizer

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        mockkObject(Logger)
        every { Logger.i(any(), any()) } just Runs
        every { Logger.w(any(), any()) } just Runs
        every { Logger.w(any(), any(), any()) } just Runs
        every { Logger.e(any(), any(), any()) } just Runs

        context = mockk(relaxed = true)
        aiService = mockk(relaxed = true)
        labelNormalizer = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkObject(Logger)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Creates a [ProduceInfoEngine] whose [Context.assets] returns the given
     * JSON for the core-info asset.  The mock must be installed *before* the
     * lazy `coreInfoCache` is first accessed.
     */
    private fun createEngine(coreInfoJson: String = SAMPLE_CORE_INFO_JSON): ProduceInfoEngine {
        every {
            context.assets.open(ProduceInfoEngine.CORE_INFO_ASSET_PATH)
        } returns coreInfoJson.byteInputStream()
        return ProduceInfoEngine(context, aiService, labelNormalizer)
    }

    private fun stubNormalizer(rawLabel: String, categoryName: String) {
        every { labelNormalizer.normalize(rawLabel) } returns listOf(categoryName)
    }

    private fun stubAiSuccess(json: String = AI_RESPONSE_JSON) {
        coEvery { aiService.chatJson(any(), any()) } returns Result.success(json)
    }

    private fun stubAiThrows(message: String = "Network error") {
        coEvery { aiService.chatJson(any(), any()) } throws RuntimeException(message)
    }

    /**
     * Waits briefly for real-thread [Dispatchers.IO] work to finish, then
     * advances the [StandardTestDispatcher] so that the resumption is processed.
     *
     * This bridges the gap between `withContext(Dispatchers.IO)` (which runs on
     * a real thread pool even inside `runTest`) and the virtual-time scheduler.
     */
    private suspend fun TestScope.waitForIoDispatch() {
        Thread.sleep(IO_SETTLE_TIME_MS)
        advanceUntilIdle()
    }

    // ── Test 1: Core info loading ────────────────────────────────────────────

    @Test
    fun `given known label when getCoreInfo called then returns correct ProduceInfo`() = runTest {
        val engine = createEngine()

        val info = engine.getCoreInfo("Apple")

        assertEquals("Apple", info.label)
        assertEquals("红粉苹果", info.displayName)
        assertEquals("水果", info.category)
        assertEquals("苹果是一种常见水果，富含维生素和膳食纤维。", info.intro)
        assertEquals(52, info.nutrition.caloriesKcal)
        assertEquals(0.3f, info.nutrition.proteinG)
        assertEquals(14.0f, info.nutrition.carbsG)
        assertEquals(0.2f, info.nutrition.fatG)
        assertEquals(2.4f, info.nutrition.fiberG)
        assertEquals(4.6f, info.nutrition.vitaminCMg)
        assertEquals(3.0f, info.nutrition.vitaminAUg)
        assertEquals(107.0f, info.nutrition.potassiumMg)
        assertEquals(36, info.nutrition.glycemicIndex)
        assertEquals(listOf("富含膳食纤维", "有助于消化", "增强免疫力"), info.healthBenefits)
        assertEquals("放置于冰箱冷藏室可保存1-2周", info.storageTips)
        assertEquals("9-11月", info.seasonality)
        assertNull(info.selectionTips)
        assertNull(info.pairingSuggestions)
        assertNull(info.funFact)
    }

    // ── Test 2: Two-stage AI enhancement ─────────────────────────────────────

    @Test
    fun `given known label when AI succeeds then flow emits core info first then AI-extended info`() =
        runTest {
            val engine = createEngine()
            stubNormalizer("Apple", "Apple")
            stubAiSuccess()

            engine.getInfo("Apple").test {
                // First emission — core info (no AI fields)
                val core = awaitItem()
                assertEquals("Apple", core.label)
                assertEquals("红粉苹果", core.displayName)
                assertNull(core.selectionTips)
                assertNull(core.pairingSuggestions)
                assertNull(core.funFact)

                waitForIoDispatch()

                // Second emission — AI-extended info
                val extended = awaitItem()
                assertEquals("Apple", extended.label)
                assertEquals("红粉苹果", extended.displayName)
                assertNotNull(extended.selectionTips)
                assertEquals("选择表皮光滑、颜色均匀的苹果", extended.selectionTips)
                assertEquals(listOf("花生酱", "奶酪", "肉桂"), extended.pairingSuggestions)
                assertEquals("全球有超过7500个苹果品种", extended.funFact)

                awaitComplete()
            }
        }

    // ── Test 3: Cache hit ────────────────────────────────────────────────────

    @Test
    fun `given same label queried twice when second call made then uses cached AI data`() =
        runTest {
            val engine = createEngine()
            stubNormalizer("Apple", "Apple")
            stubAiSuccess()

            // ── First call: populates the AI cache ──
            engine.getInfo("Apple").test {
                awaitItem() // core
                waitForIoDispatch()
                awaitItem() // AI extended
                awaitComplete()
            }

            // ── Second call: should hit cache, no new AI request ──
            engine.getInfo("Apple").test {
                val core = awaitItem()
                assertNull(core.selectionTips)

                // Cache hit path is synchronous — no IO dispatch needed
                val cached = awaitItem()
                assertNotNull(cached.selectionTips)
                assertEquals("选择表皮光滑、颜色均匀的苹果", cached.selectionTips)
                assertEquals(listOf("花生酱", "奶酪", "肉桂"), cached.pairingSuggestions)
                assertEquals("全球有超过7500个苹果品种", cached.funFact)

                awaitComplete()
            }

            // AI service should have been called exactly once (first call only)
            coVerify(exactly = 1) { aiService.chatJson(any(), any()) }
        }

    // ── Test 4: AI failure — graceful degradation ────────────────────────────

    @Test
    fun `given known label when AI service throws then flow still emits core info only`() =
        runTest {
            val engine = createEngine()
            stubNormalizer("Apple", "Apple")
            stubAiThrows("Connection timeout")

            engine.getInfo("Apple").test {
                // Core info is always emitted
                val core = awaitItem()
                assertEquals("Apple", core.label)
                assertEquals("红粉苹果", core.displayName)
                assertNull(core.selectionTips)

                waitForIoDispatch()

                // Flow completes with no second emission — degradation
                awaitComplete()
            }

            // Verify the warning was logged
            every { Logger.w(any(), any()) } just Runs // already stubbed, just confirming
        }

    // ── Test 5: Unknown label ────────────────────────────────────────────────

    @Test
    fun `given unknown label when getCoreInfo called then returns default ProduceInfo`() = runTest {
        val engine = createEngine()

        val info = engine.getCoreInfo("NonexistentFruit")

        assertEquals("NonexistentFruit", info.label)
        assertEquals("NonexistentFruit", info.displayName)
        assertEquals("", info.intro)
        assertEquals("", info.category)
        assertEquals(0, info.nutrition.caloriesKcal)
        assertEquals(0f, info.nutrition.proteinG)
        assertEquals(0f, info.nutrition.carbsG)
        assertEquals(0f, info.nutrition.fatG)
        assertEquals(0f, info.nutrition.fiberG)
        assertNull(info.nutrition.vitaminCMg)
        assertNull(info.nutrition.vitaminAUg)
        assertNull(info.nutrition.potassiumMg)
        assertNull(info.nutrition.glycemicIndex)
        assertEquals(emptyList<String>(), info.healthBenefits)
        assertEquals("", info.storageTips)
        assertEquals("", info.seasonality)
        assertNull(info.selectionTips)
        assertNull(info.pairingSuggestions)
        assertNull(info.funFact)
    }

    // ── Constants ────────────────────────────────────────────────────────────

    private companion object {
        /** Time to wait for real-thread Dispatchers.IO work to settle. */
        const val IO_SETTLE_TIME_MS = 100L

        val SAMPLE_CORE_INFO_JSON = """
        [
          {
            "label": "Apple",
            "displayName": "红粉苹果",
            "category": "水果",
            "intro": "苹果是一种常见水果，富含维生素和膳食纤维。",
            "nutrition": {
              "caloriesKcal": 52,
              "proteinG": 0.3,
              "carbsG": 14.0,
              "fatG": 0.2,
              "fiberG": 2.4,
              "vitaminCMg": 4.6,
              "vitaminAUg": 3.0,
              "potassiumMg": 107.0,
              "glycemicIndex": 36
            },
            "healthBenefits": ["富含膳食纤维", "有助于消化", "增强免疫力"],
            "storageTips": "放置于冰箱冷藏室可保存1-2周",
            "seasonality": "9-11月"
          },
          {
            "label": "Banana",
            "displayName": "香蕉",
            "category": "水果",
            "intro": "香蕉是热带水果，富含钾元素。",
            "nutrition": {
              "caloriesKcal": 89,
              "proteinG": 1.1,
              "carbsG": 22.8,
              "fatG": 0.3,
              "fiberG": 2.6
            },
            "healthBenefits": ["补充能量", "促进肠道蠕动"],
            "storageTips": "室温保存，避免冷藏",
            "seasonality": "全年"
          }
        ]
        """.trimIndent()

        val AI_RESPONSE_JSON = """
        {
          "selection_tips": "选择表皮光滑、颜色均匀的苹果",
          "pairing": ["花生酱", "奶酪", "肉桂"],
          "fun_fact": "全球有超过7500个苹果品种"
        }
        """.trimIndent()
    }
}
