package com.example.freshscan.data.diet

import com.example.freshscan.data.ai.AIService
import com.example.freshscan.data.history.MealHistoryDao
import com.example.freshscan.data.history.MealHistoryEntity
import com.example.freshscan.domain.model.*
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MealQueryEngineTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var engine: MealQueryEngine
    private lateinit var mockAiService: AIService
    private lateinit var mockDao: MealHistoryDao

    private val testProfile = UserProfile(
        age = 25,
        gender = Gender.MALE,
        heightCm = 175,
        weightKg = 70f,
        activityLevel = ActivityLevel.MODERATE,
        goal = HealthGoal.EAT_HEALTHY,
        spiceLevel = 1,
        saltLevel = 1,
        oilLevel = 1,
        excludedIngredients = emptySet(),
        preferredCategories = emptySet(),
        maxCookingTimeMin = 30,
        mealsPerDay = 3,
        calorieTarget = null,
        allergies = emptySet()
    )

    private val sampleAiResponse = """
        菜名：清炒西兰花鸡胸肉
        食材：鸡胸肉200g、西兰花150g、蒜3瓣、橄榄油10ml
        步骤：1.鸡胸肉切丁腌制 2.西兰花焯水 3.热锅少油翻炒鸡肉 4.加入西兰花调味
        烹饪时间：20
        热量：350
        蛋白质：35
        碳水：15
        脂肪：12
    """.trimIndent()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockAiService = mockk()
        mockDao = mockk(relaxed = true)
        engine = MealQueryEngine(mockAiService, mockDao)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── parseMealSuggestion tests ──

    @Test
    fun `parseMealSuggestion extracts all fields from well-formed response`() {
        val result = engine.parseMealSuggestion(sampleAiResponse, "减脂午餐")

        assertEquals("清炒西兰花鸡胸肉", result.title)
        assertEquals("减脂午餐", result.query)
        assertEquals(4, result.ingredients.size)
        assertEquals("鸡胸肉", result.ingredients[0].name)
        assertEquals("200g", result.ingredients[0].amount)
        assertEquals("西兰花", result.ingredients[1].name)
        assertEquals(4, result.steps.size)
        assertEquals(20, result.cookingTimeMin)
        assertEquals(350, result.calories)
        assertEquals(35f, result.proteinG)
        assertEquals(15f, result.carbsG)
        assertEquals(12f, result.fatG)
    }

    @Test
    fun `parseMealSuggestion handles minimal response with only title`() {
        val minimal = "菜名：白米饭"
        val result = engine.parseMealSuggestion(minimal, "简单主食")

        assertEquals("白米饭", result.title)
        assertEquals("简单主食", result.query)
        assertTrue(result.ingredients.isEmpty())
        assertTrue(result.steps.isEmpty())
        assertEquals(0, result.calories)
    }

    @Test(expected = MealQueryParseException::class)
    fun `parseMealSuggestion throws when title is blank`() {
        engine.parseMealSuggestion("食材：盐、油", "test")
    }

    @Test
    fun `parseMealSuggestion handles colon variants (full-width and half-width)`() {
        val text = "菜名:番茄炒蛋\n食材:番茄2个、鸡蛋3个\n烹饪时间:10\n热量:200\n蛋白质:15\n碳水:20\n脂肪:8"
        val result = engine.parseMealSuggestion(text, "家常菜")

        assertEquals("番茄炒蛋", result.title)
        assertEquals(2, result.ingredients.size)
        assertEquals(10, result.cookingTimeMin)
        assertEquals(200, result.calories)
    }

    @Test
    fun `parseMealSuggestion strips markdown code fences`() {
        val fenced = """```
菜名：蒜蓉菠菜
食材：菠菜300g、蒜4瓣
步骤：1.菠菜洗净 2.蒜切末 3.热油炒蒜 4.加菠菜翻炒
烹饪时间：10
热量：120
蛋白质：5
碳水：12
脂肪：6
```"""
        val result = engine.parseMealSuggestion(fenced, "清淡素食")

        assertEquals("蒜蓉菠菜", result.title)
        assertEquals(2, result.ingredients.size)
        assertEquals(10, result.cookingTimeMin)
        assertEquals(120, result.calories)
    }

    @Test
    fun `parseMealSuggestion strips bold markers from labels`() {
        val bolded = """**菜名**：水煮鱼片
**食材**：鱼片300g、豆芽200g、花椒10g
**步骤**：1.鱼片腌制 2.豆芽焯水 3.煮汤调味 4.下鱼片
**烹饪时间**：25
**热量**：400
**蛋白质**：38
**碳水**：10
**脂肪**：22"""
        val result = engine.parseMealSuggestion(bolded, "高蛋白晚餐")

        assertEquals("水煮鱼片", result.title)
        assertEquals(3, result.ingredients.size)
        assertEquals(25, result.cookingTimeMin)
        assertEquals(400, result.calories)
        assertEquals(38f, result.proteinG)
    }

    @Test
    fun `parseMealSuggestion strips json code fence`() {
        val fenced = """```json
菜名：宫保鸡丁
食材：鸡胸肉250g、花生50g
烹饪时间：15
热量：380
蛋白质：30
碳水：20
脂肪：18
```"""
        val result = engine.parseMealSuggestion(fenced, "家常菜")
        assertEquals("宫保鸡丁", result.title)
    }

    // ── Regression tests from real-device AI quirks ──

    @Test
    fun `parseMealSuggestion strips brackets around dish name - real device bug`() {
        // AI saw 【菜名：】 in prompt and wrapped the output in 【】
        val bracketed = """【菜名：鸡胸肉炒西兰花】
食材：鸡胸肉200g、西兰花150g、蒜3瓣、生姜5g、橄榄油10g、盐适量
步骤：1.鸡胸肉切丁 2.西兰花焯水 3.热锅加油翻炒
烹饪时间：15
热量：320
蛋白质：38
碳水：12
脂肪：10"""
        val result = engine.parseMealSuggestion(bracketed, "高蛋白晚餐")
        assertEquals("鸡胸肉炒西兰花", result.title)
        assertEquals(6, result.ingredients.size)
    }

    @Test
    fun `parseMealSuggestion handles no label prefix - real device bug`() {
        // AI omitted "菜名：" prefix entirely, just wrote dish name with colon
        val noLabel = """宫保鸡丁：
食材：鸡胸肉200g、花生米50g、青椒100g、红椒80g、干辣椒10g、葱姜蒜适量
步骤：1.鸡胸肉切丁 2.青红椒切块 3.热锅爆香 4.翻炒调味
烹饪时间：20
热量：320
蛋白质：25
碳水：15
脂肪：10"""
        val result = engine.parseMealSuggestion(noLabel, "减脂午餐")
        assertEquals("宫保鸡丁", result.title)
        assertEquals(6, result.ingredients.size)
        assertEquals(20, result.cookingTimeMin)
    }

    @Test
    fun `parseMealSuggestion handles conversational filler before format - real device bug`() {
        // AI added conversational text before the structured output
        val withFiller = """好的，根据您的要求为您推荐：
菜名：清蒸鲈鱼
食材：鲈鱼1条、姜丝5g、葱段10g、料酒10ml
步骤：1.鲈鱼清理划刀 2.腌制10分钟 3.大火蒸8分钟
烹饪时间：8
热量：210
蛋白质：25
碳水：5
脂肪：10"""
        val result = engine.parseMealSuggestion(withFiller, "清淡素食")
        assertEquals("清蒸鲈鱼", result.title)
        assertEquals(4, result.ingredients.size)
    }

    @Test
    fun `parseMealSuggestion handles variant label 推荐菜品`() {
        val variant = """推荐菜品：番茄牛肉汤
食材：番茄2个、牛肉200g、姜3片
步骤：1.牛肉切块焯水 2.番茄切块 3.炖煮30分钟
烹饪时间：30
热量：280
蛋白质：30
碳水：15
脂肪：12"""
        val result = engine.parseMealSuggestion(variant, "家常菜")
        assertEquals("番茄牛肉汤", result.title)
    }

    @Test
    fun `parseMealSuggestion handles all lines wrapped in brackets - real device bug`() {
        val allBracketed = """【菜名：蒜蓉菠菜】
【食材：菠菜300g、蒜4瓣、盐适量】
【步骤：1.菠菜洗净 2.蒜切末 3.热油炒蒜 4.加菠菜翻炒】
【烹饪时间：10】
【热量：120】
【蛋白质：5】
【碳水：12】
【脂肪：6】"""
        val result = engine.parseMealSuggestion(allBracketed, "清淡素食")
        assertEquals("蒜蓉菠菜", result.title)
        assertEquals(3, result.ingredients.size)
        assertEquals(10, result.cookingTimeMin)
        assertEquals(120, result.calories)
    }

    @Test
    fun `parseMealSuggestion handles single-line response without newlines - real device bug`() {
        // AI output everything on one line with no newlines
        val singleLine = "宫保鸡丁：鸡胸肉200g、花生米50g、青椒100g、红椒50g、葱姜蒜适量食材：鸡胸肉200g、花生米50g步骤：1.鸡胸肉切丁腌制 2.翻炒调味烹饪时间：20热量：320蛋白质：25碳水：15脂肪：10"
        val result = engine.parseMealSuggestion(singleLine, "减脂午餐")
        assertEquals("宫保鸡丁", result.title)
        assertEquals(2, result.ingredients.size)
        assertEquals(20, result.cookingTimeMin)
        assertEquals(320, result.calories)
    }

    @Test
    fun `parseMealSuggestion handles single-line with only dish name and labels - real device bug`() {
        // Real device: AI wrote dish name then ingredients on same line, no 食材 label
        val realDeviceLine = "宫保鸡丁：鸡胸肉200g、花生米50g、青椒100g、红椒50g、葱姜蒜适量、酱油10ml 步骤：1.鸡胸肉切丁腌制 2.翻炒 烹饪时间：15 热量：280 蛋白质：25 碳水：15 脂肪：10"
        val result = engine.parseMealSuggestion(realDeviceLine, "减脂午餐")
        assertEquals("宫保鸡丁", result.title)
        assertEquals(15, result.cookingTimeMin)
        assertEquals(280, result.calories)
    }

    @Test
    fun `parseMealSuggestion handles label embedded in line - contains not startsWith`() {
        // AI wrote "好的，菜名：xxx" — label not at line start
        val embedded = """好的，菜名：麻婆豆腐
食材：嫩豆腐1块、猪肉末100g、豆瓣酱15g、花椒粉5g
步骤：1.豆腐切块焯水 2.炒肉末 3.加豆瓣酱 4.放豆腐炖煮
烹饪时间：15
热量：250
蛋白质：20
碳水：12
脂肪：15"""
        val result = engine.parseMealSuggestion(embedded, "家常菜")
        assertEquals("麻婆豆腐", result.title)
        assertEquals(4, result.ingredients.size)
    }

    @Test
    fun `queryMeal passes recent dishes to prompt for exclusion`() = runTest {
        val recentEntities = listOf(
            MealHistoryEntity(
                id = "1", query = "午餐", title = "清蒸鲈鱼",
                ingredientsJson = "[]", stepsJson = "[]",
                cookingTimeMin = 8, calories = 210,
                proteinG = 25.0, carbsG = 5.0, fatG = 10.0,
                generatedAt = 1000L
            ),
            MealHistoryEntity(
                id = "2", query = "晚餐", title = "宫保鸡丁",
                ingredientsJson = "[]", stepsJson = "[]",
                cookingTimeMin = 20, calories = 320,
                proteinG = 25.0, carbsG = 15.0, fatG = 10.0,
                generatedAt = 2000L
            )
        )
        coEvery { mockDao.getRecentTitles() } returns listOf("清蒸鲈鱼", "宫保鸡丁")
        coEvery { mockDao.deleteOlderThan(any()) } just Runs
        coEvery { mockAiService.chat(any(), any()) } returns Result.success(sampleAiResponse)
        coEvery { mockDao.insert(any()) } just Runs

        engine.queryMeal("减脂午餐", testProfile).first()
        advanceUntilIdle()

        // Verify the userMessage passed to chat() contains the recent dish names
        coVerify { mockAiService.chat(any(), match { userMsg ->
            userMsg.contains("清蒸鲈鱼") && userMsg.contains("宫保鸡丁")
        }) }
    }

    // ── calculateTDEE tests ──

    @Test
    fun `calculateTDEE returns lower calories for LOSE_WEIGHT goal`() {
        val loseProfile = testProfile.copy(goal = HealthGoal.LOSE_WEIGHT)
        val maintainProfile = testProfile.copy(goal = HealthGoal.EAT_HEALTHY)

        val loseCal = engine.calculateTDEE(loseProfile)
        val maintainCal = engine.calculateTDEE(maintainProfile)

        assertTrue("Lose weight should have fewer calories", loseCal < maintainCal)
    }

    @Test
    fun `calculateTDEE returns higher calories for BUILD_MUSCLE goal`() {
        val buildProfile = testProfile.copy(goal = HealthGoal.BUILD_MUSCLE)
        val maintainProfile = testProfile.copy(goal = HealthGoal.EAT_HEALTHY)

        val buildCal = engine.calculateTDEE(buildProfile)
        val maintainCal = engine.calculateTDEE(maintainProfile)

        assertTrue("Build muscle should have more calories", buildCal > maintainCal)
    }

    @Test
    fun `calculateTDEE handles UNSPECIFIED gender as average`() {
        val maleProfile = testProfile.copy(gender = Gender.MALE)
        val femaleProfile = testProfile.copy(gender = Gender.FEMALE)
        val unspecProfile = testProfile.copy(gender = Gender.UNSPECIFIED)

        val maleCal = engine.calculateTDEE(maleProfile)
        val femaleCal = engine.calculateTDEE(femaleProfile)
        val unspecCal = engine.calculateTDEE(unspecProfile)

        // Unspecified should be between male and female
        assertTrue(unspecCal in femaleCal..maleCal)
    }

    // ── queryMeal integration tests ──

    @Test
    fun `queryMeal emits suggestion and saves to history`() = runTest {
        every { mockDao.getAll() } returns flowOf(emptyList())
        coEvery { mockAiService.chat(any(), any()) } returns Result.success(sampleAiResponse)
        coEvery { mockDao.insert(any()) } just Runs

        val result = engine.queryMeal("减脂午餐", testProfile).first()
        advanceUntilIdle()

        assertEquals("清炒西兰花鸡胸肉", result.title)
        assertEquals("减脂午餐", result.query)
        coVerify(exactly = 1) { mockDao.insert(any()) }
    }

    @Test
    fun `queryMeal works with null profile`() = runTest {
        every { mockDao.getAll() } returns flowOf(emptyList())
        coEvery { mockAiService.chat(any(), any()) } returns Result.success(sampleAiResponse)
        coEvery { mockDao.insert(any()) } just Runs

        val result = engine.queryMeal("随便吃点", null).first()
        advanceUntilIdle()

        assertEquals("清炒西兰花鸡胸肉", result.title)
    }

    @Test
    fun `queryMeal propagates AI service failure`() = runTest {
        every { mockDao.getAll() } returns flowOf(emptyList())
        coEvery { mockAiService.chat(any(), any()) } returns
            Result.failure(RuntimeException("API error"))

        try {
            engine.queryMeal("test", testProfile).first()
            fail("Should have thrown")
        } catch (e: Exception) {
            assertEquals("API error", e.message)
        }
    }

    // ── getHistory tests ──

    @Test
    fun `getHistory maps entities to domain objects`() = runTest {
        val entities = listOf(
            MealHistoryEntity(
                id = "1", query = "午餐", title = "番茄炒蛋",
                ingredientsJson = """[{"name":"番茄","amount":"2个"},{"name":"鸡蛋","amount":"3个"}]""",
                stepsJson = """["打蛋","炒蛋","加番茄"]""",
                cookingTimeMin = 10, calories = 200,
                proteinG = 15.0, carbsG = 20.0, fatG = 8.0,
                generatedAt = 1000L
            )
        )
        every { mockDao.getAll() } returns flowOf(entities)

        val history = engine.getHistory().first()

        assertEquals(1, history.size)
        assertEquals("番茄炒蛋", history[0].title)
        assertEquals(2, history[0].ingredients.size)
        assertEquals(3, history[0].steps.size)
    }
}
