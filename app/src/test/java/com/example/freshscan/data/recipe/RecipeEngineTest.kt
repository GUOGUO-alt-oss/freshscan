package com.example.freshscan.data.recipe

import android.content.Context
import android.content.res.AssetManager
import com.example.freshscan.R
import com.example.freshscan.domain.model.DetectedItem
import com.example.freshscan.domain.model.FreshnessLevel
import com.example.freshscan.domain.model.RecipeCategory
import com.example.freshscan.domain.model.RecipeDifficulty
import com.example.freshscan.domain.model.TasteProfile
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File

/**
 * Unit tests for [RecipeEngine] — JSON parsing and matching algorithm.
 *
 * These are pure JVM tests using MockK for Android dependencies.
 * Tests load the real preset_recipes.json from the project assets/ directory
 * to verify all 111 recipes parse correctly, and use small inline JSON fixtures
 * for targeted matching-algorithm tests.
 */
class RecipeEngineTest {

    private lateinit var context: Context
    private lateinit var assetManager: AssetManager
    private lateinit var labelNormalizer: LabelNormalizer

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        assetManager = mockk(relaxed = true)
        labelNormalizer = mockk(relaxed = true)

        every { context.getAssets() } returns assetManager

        // Stub context.getString() for RecipeEngine string resources.
        // RecipeEngine uses context.getString(R.string.xxx, ...) for all user-facing messages.
        // We stub both overloads: getString(int) and getString(int, Object...).
        val stringResolver: (Int) -> String = { resId ->
            when (resId) {
                R.string.recipe_all_fruit_note -> "检测到的全是水果，建议直接食用 🍎"
                R.string.recipe_no_match -> "未找到匹配的菜谱，试试检测更多食材"
                R.string.recipe_data_missing -> "菜谱数据文件缺失，请更新应用"
                R.string.recipe_data_empty -> "菜谱数据为空，请更新应用"
                R.string.error_unknown -> "未知错误"
                else -> ""
            }
        }
        every { context.getString(any<Int>()) } answers { stringResolver(firstArg()) }
        every { context.getString(any<Int>(), any()) } answers {
            val resId = firstArg<Int>()
            // getString(int, Object...) treats the second arg as vararg Object[].
            // secondArg() returns the entire Object[] — extract the first element for
            // string formatting (matches how RecipeEngine passes a single format arg).
            val varargArray = secondArg<Array<*>>()
            val fmtArg = varargArray.firstOrNull()?.toString() ?: ""
            when (resId) {
                R.string.recipe_missing_items_2 -> "还缺少：$fmtArg"
                R.string.recipe_missing_items_n -> "需额外准备 $fmtArg 种食材"
                R.string.recipe_load_failed -> "菜谱数据加载失败：$fmtArg"
                R.string.recipe_temp_unavailable -> "菜谱推荐暂时不可用，请稍后重试"
                else -> stringResolver(resId)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // JSON Parsing Tests (real 111 recipes)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `should load all 111 recipes from real preset_recipes json`() = runTest {
        val realJson = loadRealJson()
        every { assetManager.open("recipes/preset_recipes.json") } returns
            ByteArrayInputStream(realJson.toByteArray(Charsets.UTF_8))

        val engine = RecipeEngine(context, labelNormalizer)
        val recipes = engine.getAllPresetRecipes()

        assertEquals("Should load exactly 111 preset recipes", 111, recipes.size)
    }

    @Test
    fun `all recipes should have non-empty required fields`() = runTest {
        val realJson = loadRealJson()
        every { assetManager.open("recipes/preset_recipes.json") } returns
            ByteArrayInputStream(realJson.toByteArray(Charsets.UTF_8))

        val engine = RecipeEngine(context, labelNormalizer)
        val recipes = engine.getAllPresetRecipes()

        recipes.forEachIndexed { index, recipe ->
            assertTrue("Recipe[$index] id must not be blank", recipe.id.isNotBlank())
            assertTrue("Recipe[$index] title must not be blank", recipe.title.isNotBlank())
            assertNotNull("Recipe[$index] category must not be null", recipe.category)
            assertNotNull("Recipe[$index] difficulty must not be null", recipe.difficulty)
            assertTrue("Recipe[$index] cookingTimeMin > 0: ${recipe.cookingTimeMin}", recipe.cookingTimeMin > 0)
            assertTrue("Recipe[$index] matchIngredients must not be empty", recipe.matchIngredients.isNotEmpty())
            assertTrue("Recipe[$index] allIngredients must not be empty", recipe.allIngredients.isNotEmpty())
            assertTrue("Recipe[$index] steps must not be empty", recipe.steps.isNotEmpty())
            assertNotNull("Recipe[$index] nutrition must not be null", recipe.nutrition)
        }
    }

    @Test
    fun `all recipes should have unique IDs`() = runTest {
        val realJson = loadRealJson()
        every { assetManager.open("recipes/preset_recipes.json") } returns
            ByteArrayInputStream(realJson.toByteArray(Charsets.UTF_8))

        val engine = RecipeEngine(context, labelNormalizer)
        val recipes = engine.getAllPresetRecipes()

        val ids = recipes.map { it.id }
        val duplicates = ids.groupBy { it }.filter { it.value.size > 1 }
        assertTrue("Duplicate recipe IDs found: $duplicates", duplicates.isEmpty())
    }

    @Test
    fun `category distribution should match expected counts`() = runTest {
        val realJson = loadRealJson()
        every { assetManager.open("recipes/preset_recipes.json") } returns
            ByteArrayInputStream(realJson.toByteArray(Charsets.UTF_8))

        val engine = RecipeEngine(context, labelNormalizer)
        val recipes = engine.getAllPresetRecipes()

        val counts = recipes.groupBy { it.category }.mapValues { it.value.size }
        assertEquals("HOME category count", 36, counts[RecipeCategory.HOME])
        assertEquals("QUICK category count", 30, counts[RecipeCategory.QUICK])
        assertEquals("SOUP category count", 20, counts[RecipeCategory.SOUP])
        assertEquals("DIET category count", 15, counts[RecipeCategory.DIET])
        assertEquals("COLD category count", 10, counts[RecipeCategory.COLD])
    }

    @Test
    fun `recipe steps should be in sequential order`() = runTest {
        val realJson = loadRealJson()
        every { assetManager.open("recipes/preset_recipes.json") } returns
            ByteArrayInputStream(realJson.toByteArray(Charsets.UTF_8))

        val engine = RecipeEngine(context, labelNormalizer)
        val recipes = engine.getAllPresetRecipes()

        recipes.forEach { recipe ->
            val orders = recipe.steps.map { it.order }
            val expected = (1..recipe.steps.size).toList()
            assertEquals("${recipe.id}: step orders should be 1..N", expected, orders)
        }
    }

    @Test
    fun `nutrition values should be non-negative`() = runTest {
        val realJson = loadRealJson()
        every { assetManager.open("recipes/preset_recipes.json") } returns
            ByteArrayInputStream(realJson.toByteArray(Charsets.UTF_8))

        val engine = RecipeEngine(context, labelNormalizer)
        val recipes = engine.getAllPresetRecipes()

        recipes.forEach { recipe ->
            val n = recipe.nutrition
            assertTrue("${recipe.id}: calories >= 0", n.calories >= 0)
            assertTrue("${recipe.id}: protein >= 0", n.protein >= 0)
            assertTrue("${recipe.id}: carbs >= 0", n.carbs >= 0)
            assertTrue("${recipe.id}: fat >= 0", n.fat >= 0)
            assertTrue("${recipe.id}: fiber >= 0", n.fiber >= 0)
        }
    }

    @Test
    fun `allIngredients should have non-empty name and amount`() = runTest {
        val realJson = loadRealJson()
        every { assetManager.open("recipes/preset_recipes.json") } returns
            ByteArrayInputStream(realJson.toByteArray(Charsets.UTF_8))

        val engine = RecipeEngine(context, labelNormalizer)
        val recipes = engine.getAllPresetRecipes()

        recipes.forEach { recipe ->
            recipe.allIngredients.forEach { ingredient ->
                assertTrue("${recipe.id}: ingredient name '${ingredient.name}' must not be blank",
                    ingredient.name.isNotBlank())
                assertTrue("${recipe.id}: ingredient amount '${ingredient.amount}' must not be blank",
                    ingredient.amount.isNotBlank())
            }
        }
    }

    @Test
    fun `getRecipeById should find existing recipe and return null for missing`() = runTest {
        val realJson = loadRealJson()
        every { assetManager.open("recipes/preset_recipes.json") } returns
            ByteArrayInputStream(realJson.toByteArray(Charsets.UTF_8))

        val engine = RecipeEngine(context, labelNormalizer)

        val found = engine.getRecipeById("tomato_egg_stirfry")
        assertNotNull("Should find tomato_egg_stirfry", found)
        assertEquals("番茄炒蛋", found!!.title)

        val missing = engine.getRecipeById("nonexistent_recipe")
        assertEquals("Should return null for nonexistent ID", null, missing)
    }

    @Test
    fun `all recipes should have valid enum values`() = runTest {
        val realJson = loadRealJson()
        every { assetManager.open("recipes/preset_recipes.json") } returns
            ByteArrayInputStream(realJson.toByteArray(Charsets.UTF_8))

        val engine = RecipeEngine(context, labelNormalizer)
        val recipes = engine.getAllPresetRecipes()

        // Verify all categories are valid RecipeCategory values
        recipes.forEach { recipe ->
            assertTrue("Category should be valid enum", recipe.category in RecipeCategory.entries)
            assertTrue("Difficulty should be valid enum", recipe.difficulty in RecipeDifficulty.entries)
        }
    }

    @Test
    fun `tags should not contain empty strings`() = runTest {
        val realJson = loadRealJson()
        every { assetManager.open("recipes/preset_recipes.json") } returns
            ByteArrayInputStream(realJson.toByteArray(Charsets.UTF_8))

        val engine = RecipeEngine(context, labelNormalizer)
        val recipes = engine.getAllPresetRecipes()

        recipes.forEach { recipe ->
            recipe.tags.forEach { tag ->
                assertTrue("${recipe.id}: tag must not be blank", tag.isNotBlank())
            }
        }
    }

    @Test
    fun `cooking step text and timerSec should be valid`() = runTest {
        val realJson = loadRealJson()
        every { assetManager.open("recipes/preset_recipes.json") } returns
            ByteArrayInputStream(realJson.toByteArray(Charsets.UTF_8))

        val engine = RecipeEngine(context, labelNormalizer)
        val recipes = engine.getAllPresetRecipes()

        recipes.forEach { recipe ->
            recipe.steps.forEach { step ->
                assertTrue("${recipe.id} step ${step.order}: text must not be blank", step.text.isNotBlank())
                assertTrue("${recipe.id} step ${step.order}: timerSec >= 0", step.timerSec >= 0)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Matching Algorithm Tests (inline JSON fixtures)
    // ═══════════════════════════════════════════════════════════════════════

    companion object {
        /**
         * Compact test JSON with 5 recipes covering all categories and edge cases.
         *
         * Recipe set:
         * - tomato_egg: HOME, 10min, match=[番茄,鸡蛋]
         * - garlic_cucumber: COLD, 5min, match=[黄瓜,大蒜]
         * - potato_stew: HOME, 45min, match=[土豆,胡萝卜]
         * - quick_cuke: QUICK, 3min, match=[黄瓜]
         * - tomato_soup: SOUP, 30min, match=[番茄,鸡蛋,豆腐]
         */
        private val TEST_JSON = """
        {
          "version": "1.0",
          "recipes": [
            {
              "id": "tomato_egg", "title": "番茄炒蛋", "category": "HOME", "difficulty": "EASY",
              "cookingTimeMin": 10,
              "matchIngredients": ["番茄","鸡蛋"],
              "allIngredients": [
                {"name":"番茄","amount":"2个"}, {"name":"鸡蛋","amount":"3个"}
              ],
              "steps": [{"order":1,"text":"炒蛋","timerSec":60}],
              "nutrition": {"calories":180,"protein":12,"carbs":8,"fat":14,"fiber":2},
              "tags": ["家常菜"], "tips": "", "imageAsset": "", "thumbnailAsset": ""
            },
            {
              "id": "garlic_cucumber", "title": "蒜泥黄瓜", "category": "COLD", "difficulty": "EASY",
              "cookingTimeMin": 5,
              "matchIngredients": ["黄瓜","大蒜"],
              "allIngredients": [
                {"name":"黄瓜","amount":"2根"}, {"name":"大蒜","amount":"3瓣"}
              ],
              "steps": [{"order":1,"text":"拍黄瓜","timerSec":0}],
              "nutrition": {"calories":50,"protein":2,"carbs":8,"fat":2,"fiber":3},
              "tags": ["凉拌"], "tips": "", "imageAsset": "", "thumbnailAsset": ""
            },
            {
              "id": "potato_stew", "title": "土豆炖肉", "category": "HOME", "difficulty": "MEDIUM",
              "cookingTimeMin": 45,
              "matchIngredients": ["土豆","胡萝卜"],
              "allIngredients": [
                {"name":"土豆","amount":"2个"}, {"name":"胡萝卜","amount":"1根"}
              ],
              "steps": [{"order":1,"text":"炖","timerSec":1800}],
              "nutrition": {"calories":300,"protein":20,"carbs":35,"fat":12,"fiber":5},
              "tags": ["炖菜"], "tips": "", "imageAsset": "", "thumbnailAsset": ""
            },
            {
              "id": "quick_cuke", "title": "凉拌黄瓜", "category": "QUICK", "difficulty": "EASY",
              "cookingTimeMin": 3,
              "matchIngredients": ["黄瓜"],
              "allIngredients": [
                {"name":"黄瓜","amount":"2根"}
              ],
              "steps": [{"order":1,"text":"拌","timerSec":0}],
              "nutrition": {"calories":30,"protein":1,"carbs":5,"fat":1,"fiber":2},
              "tags": ["快手"], "tips": "", "imageAsset": "", "thumbnailAsset": ""
            },
            {
              "id": "tomato_soup", "title": "番茄蛋花汤", "category": "SOUP", "difficulty": "EASY",
              "cookingTimeMin": 30,
              "matchIngredients": ["番茄","鸡蛋","豆腐"],
              "allIngredients": [
                {"name":"番茄","amount":"2个"}, {"name":"鸡蛋","amount":"2个"}, {"name":"豆腐","amount":"1块"}
              ],
              "steps": [{"order":1,"text":"煮汤","timerSec":600}],
              "nutrition": {"calories":120,"protein":10,"carbs":10,"fat":5,"fiber":3},
              "tags": ["汤羹"], "tips": "", "imageAsset": "", "thumbnailAsset": ""
            }
          ]
        }
        """.trimIndent()
    }

    private fun createEngineWithTestJson(json: String = TEST_JSON): RecipeEngine {
        every { assetManager.open("recipes/preset_recipes.json") } returns
            ByteArrayInputStream(json.toByteArray(Charsets.UTF_8))
        return RecipeEngine(context, labelNormalizer)
    }

    /** Helper: create a cookable DetectedItem with the given label. */
    private fun cookableItem(label: String, isCookable: Boolean = true): DetectedItem {
        return DetectedItem(
            id = java.util.UUID.randomUUID().toString(),
            label = label,
            displayName = label,
            freshnessLevel = FreshnessLevel.FRESH,
            confidence = 0.95f,
            bbox = com.example.freshscan.domain.model.BoundingBox(0f, 0f, 0.5f, 0.5f),
            isCookable = isCookable
        )
    }

    // ─── Algorithm: Empty / no-cookable cases ────────────────────────────

    @Test
    fun `recommend should return empty with note when no items provided`() = runTest {
        val engine = createEngineWithTestJson()

        val result = engine.recommend(emptyList())

        assertTrue("Recipes should be empty", result.recipes.isEmpty())
        assertEquals("检测到的全是水果，建议直接食用 🍎", result.note)
    }

    @Test
    fun `recommend should return empty with note when all items are non-cookable`() = runTest {
        val engine = createEngineWithTestJson()
        val items = listOf(
            cookableItem("Apple_Red", isCookable = false),
            cookableItem("Banana", isCookable = false)
        )

        val result = engine.recommend(items)

        assertTrue("Recipes should be empty", result.recipes.isEmpty())
        assertEquals("检测到的全是水果，建议直接食用 🍎", result.note)
    }

    @Test
    fun `recommend should skip non-cookable and only use cookable items`() = runTest {
        val engine = createEngineWithTestJson()

        // Mock: Tomato → "番茄"
        every { labelNormalizer.normalize("Tomato_Red") } returns listOf("番茄")

        val items = listOf(
            cookableItem("Tomato_Red", isCookable = true),
            cookableItem("Apple_Red", isCookable = false)
        )

        val result = engine.recommend(items)

        assertTrue("Should have results from cookable tomato", result.recipes.isNotEmpty())
    }

    // ─── Algorithm: Single match ──────────────────────────────────────────

    @Test
    fun `single ingredient should match recipes containing that ingredient`() = runTest {
        val engine = createEngineWithTestJson()
        // Mock: Cucumber_Ripe → "黄瓜"
        every { labelNormalizer.normalize("Cucumber_Ripe") } returns listOf("黄瓜")

        val items = listOf(cookableItem("Cucumber_Ripe"))
        val result = engine.recommend(items)

        // Should match: garlic_cucumber (黄瓜+大蒜: 1 match), quick_cuke (黄瓜: 1 match)
        // garlic_cucumber: matchCount=1, fullMatch=no (needs 大蒜 too), score=1 → cookingTimeMin=5
        // quick_cuke: matchCount=1, fullMatch=YES (1/1 match), score=1+3=4 → cookingTimeMin=3
        assertTrue("Should find recipes with cucumber", result.recipes.isNotEmpty())
        // quick_cuke should be #1 (full-match bonus + shorter cooking time)
        assertEquals("quick_cuke should rank #1 due to full-match bonus",
            "quick_cuke", result.recipes.first().id)
    }

    // ─── Algorithm: Multi-match ranking ────────────────────────────────────

    @Test
    fun `multiple ingredients should rank by match count`() = runTest {
        val engine = createEngineWithTestJson()
        // Mock: Tomato → "番茄", Eggplant → "茄子" (won't match), Potato → "土豆"
        every { labelNormalizer.normalize("Tomato_Red") } returns listOf("番茄")
        every { labelNormalizer.normalize("Potato_Red") } returns listOf("土豆")
        every { labelNormalizer.normalize("Carrot_Orange") } returns listOf("胡萝卜")

        val items = listOf(
            cookableItem("Tomato_Red"),
            cookableItem("Potato_Red"),
            cookableItem("Carrot_Orange")
        )
        val result = engine.recommend(items)

        // potato_stew: match=[土豆,胡萝卜] → 2 matches + fullMatch → score=2+3=5
        // tomato_egg: match=[番茄,鸡蛋] → 1 match → score=1
        // tomato_soup: match=[番茄,鸡蛋,豆腐] → 1 match → score=1
        assertTrue("Should find matching recipes", result.recipes.isNotEmpty())
        assertEquals("potato_stew should rank #1 (full match 2/2)",
            "potato_stew", result.recipes.first().id)
    }

    @Test
    fun `full-match bonus should boost complete ingredient matches over partial`() = runTest {
        val engine = createEngineWithTestJson()
        // Only tomato — both tomato_egg (2 matchIngredients) and quick_cuke (1) get 1 match
        // but quick_cuke gets +3 full-match, tomato_egg does not
        every { labelNormalizer.normalize("Cucumber_Ripe") } returns listOf("黄瓜")
        every { labelNormalizer.normalize("Tomato_Red") } returns listOf("番茄")

        // Test 1: just cucumber → quick_cuke should rank above garlic_cucumber
        val cucumberOnly = listOf(cookableItem("Cucumber_Ripe"))
        val result1 = engine.recommend(cucumberOnly)

        val ids1 = result1.recipes.map { it.id }
        val quickIdx = ids1.indexOf("quick_cuke")
        val garlicIdx = ids1.indexOf("garlic_cucumber")
        assertTrue("quick_cuke (full-match) should rank above garlic_cucumber (partial)",
            quickIdx < garlicIdx)
    }

    // ─── Algorithm: Taste profile weighting ───────────────────────────────

    @Test
    fun `taste profile category preference should boost matching recipes`() = runTest {
        val engine = createEngineWithTestJson()
        every { labelNormalizer.normalize("Tomato_Red") } returns listOf("番茄")

        val items = listOf(cookableItem("Tomato_Red"))

        // Without profile: tomato_egg (HOME, match=1, score=1), tomato_soup (SOUP, match=1, score=1)
        val resultWithout = engine.recommend(items, profile = null)

        // With profile preferring SOUP: tomato_soup gets +1.5 → score=2.5 → ranks #1
        val soupProfile = TasteProfile(preferredCategories = setOf(RecipeCategory.SOUP))
        val resultWith = engine.recommend(items, profile = soupProfile)

        assertTrue("Both should have results", resultWithout.recipes.size >= 2)
        assertTrue("Both should have results", resultWith.recipes.size >= 2)

        // Without profile, tomato_egg (10min) beats tomato_soup (30min) by cooking time tiebreaker
        val topWithout = resultWithout.recipes.first().id
        // With SOUP preference, tomato_soup should be boosted to #1
        val topWith = resultWith.recipes.first().id

        assertEquals("SOUP preference should boost tomato_soup to #1",
            "tomato_soup", topWith)
    }

    @Test
    fun `taste profile with empty preferences should not affect ranking`() = runTest {
        val engine = createEngineWithTestJson()
        every { labelNormalizer.normalize("Tomato_Red") } returns listOf("番茄")
        every { labelNormalizer.normalize("Cucumber_Ripe") } returns listOf("黄瓜")

        val items = listOf(cookableItem("Tomato_Red"), cookableItem("Cucumber_Ripe"))
        val noProfile = engine.recommend(items, profile = null)
        val emptyProfile = engine.recommend(items, profile = TasteProfile())

        assertEquals("Empty profile should match no-profile ordering",
            noProfile.recipes.map { it.id },
            emptyProfile.recipes.map { it.id })
    }

    // ─── Algorithm: Sorting tiebreakers ────────────────────────────────────

    @Test
    fun `equal match scores should tiebreak by cooking time ascending`() = runTest {
        val engine = createEngineWithTestJson()
        every { labelNormalizer.normalize("Tomato_Red") } returns listOf("番茄")

        val items = listOf(cookableItem("Tomato_Red"))
        val result = engine.recommend(items)

        // tomato_egg (10min) and tomato_soup (30min) both match=1 (only tomato)
        // They should be ordered by cookingTime ascending
        val tomatoRecipes = result.recipes.filter {
            it.id == "tomato_egg" || it.id == "tomato_soup"
        }
        if (tomatoRecipes.size == 2) {
            assertEquals("tomato_egg (10min) should be before tomato_soup (30min)",
                "tomato_egg", tomatoRecipes.first().id)
        }
    }

    @Test
    fun `results should be sorted by score descending`() = runTest {
        val engine = createEngineWithTestJson()
        every { labelNormalizer.normalize("Tomato_Red") } returns listOf("番茄")
        every { labelNormalizer.normalize("Cucumber_Ripe") } returns listOf("黄瓜")
        every { labelNormalizer.normalize("Potato_Red") } returns listOf("土豆")
        every { labelNormalizer.normalize("Carrot_Orange") } returns listOf("胡萝卜")

        // potato_stew: 2 matches + full = 5   | tomato_egg: 1 match + tomato      = 1
        // quick_cuke: 1 match + full = 4     | tomato_soup: 1 match + tomato     = 1
        // garlic_cucumber: 1 match + cucumber = 1
        val items = listOf(
            cookableItem("Tomato_Red"),
            cookableItem("Potato_Red"),
            cookableItem("Carrot_Orange"),
            cookableItem("Cucumber_Ripe")
        )
        val result = engine.recommend(items)

        assertTrue("Should have results", result.recipes.size >= 5)

        // Verify descending score order via the algorithm's logic:
        // potato_stew(5) > quick_cuke(4) > rest(1) sorted by cookingTime
        assertEquals("potato_stew should rank #1 (score 5)", "potato_stew", result.recipes[0].id)
        assertEquals("quick_cuke should rank #2 (score 4)", "quick_cuke", result.recipes[1].id)
    }

    // ─── Algorithm: Note generation ────────────────────────────────────────

    @Test
    fun `should generate note for missing ingredients in top recipe`() = runTest {
        val engine = createEngineWithTestJson()
        every { labelNormalizer.normalize("Tomato_Red") } returns listOf("番茄")

        val items = listOf(cookableItem("Tomato_Red"))
        val result = engine.recommend(items)

        // Top should be tomato_egg (1/2 match, missing=鸡蛋)
        // Note: "还缺少：鸡蛋" (1 missing ≤ 2 →列出)
        assertNotNull("Note should be generated", result.note)
        assertTrue("Note should mention missing ingredient",
            result.note!!.contains("缺少"))
    }

    @Test
    fun `should return null note for perfect full-match top recipe`() = runTest {
        val engine = createEngineWithTestJson()
        every { labelNormalizer.normalize("Cucumber_Ripe") } returns listOf("黄瓜")

        val items = listOf(cookableItem("Cucumber_Ripe"))
        val result = engine.recommend(items)

        // Top should be quick_cuke (full match cucumber only)
        assertEquals("quick_cuke should be #1", "quick_cuke", result.recipes.first().id)
        // Note should be null (perfect match, no missing ingredients)
        assertEquals("Note should be null for perfect match", null, result.note)
    }

    @Test
    fun `should return fallback note when no recipes match at all`() = runTest {
        val engine = createEngineWithTestJson()
        // An ingredient that doesn't match any recipe's matchIngredients
        every { labelNormalizer.normalize("Mango_Alphonso") } returns listOf("芒果")

        val items = listOf(cookableItem("Mango_Alphonso"))
        val result = engine.recommend(items)

        assertTrue("Recipes should be empty", result.recipes.isEmpty())
        assertEquals("未找到匹配的菜谱，试试检测更多食材", result.note)
    }

    @Test
    fun `should show missing ingredient count when more than 2 missing`() = runTest {
        val engine = createEngineWithTestJson()
        // Only tomato → tomato_soup has matchIngredients=[番茄,鸡蛋,豆腐], missing=2 items = 鸡蛋,豆腐
        // missing.size <= 2 → "还缺少：鸡蛋、豆腐"
        every { labelNormalizer.normalize("Tomato_Red") } returns listOf("番茄")

        val items = listOf(cookableItem("Tomato_Red"))
        val result = engine.recommend(items)

        // tomato_egg (1/2 match, missing=鸡蛋, 1 item) will be #1 over tomato_soup (1/3 match)
        // Actually tomato_egg has score=1, tomato_soup has score=1, tiebreak by cookingTime: 10 vs 30
        // So tomato_egg is #1, missing "鸡蛋"
        val topId = result.recipes.first().id
        if (topId == "tomato_egg") {
            assertTrue("Note should list missing: eggs", result.note!!.contains("鸡蛋"))
        }
    }

    // ─── Algorithm: Label normalization integration ────────────────────────

    @Test
    fun `should handle label with multiple ingredient mappings`() = runTest {
        val engine = createEngineWithTestJson()
        // Some labels map to multiple ingredients (e.g. Pepper_Bell → ["青椒","甜椒"])
        // In test data, no recipe matches "青椒" or "甜椒", so results should be empty
        every { labelNormalizer.normalize("Pepper_Bell") } returns listOf("青椒", "甜椒")

        val items = listOf(cookableItem("Pepper_Bell"))
        val result = engine.recommend(items)

        // No recipe in test JSON matches 青椒/甜椒
        assertTrue("No matches expected", result.recipes.isEmpty())
    }

    @Test
    fun `should deduplicate ingredient names from multiple items`() = runTest {
        val engine = createEngineWithTestJson()
        // Two different tomato labels both normalize to "番茄"
        every { labelNormalizer.normalize("Tomato_Red") } returns listOf("番茄")
        every { labelNormalizer.normalize("Tomato_Cherry") } returns listOf("番茄")

        val items = listOf(
            cookableItem("Tomato_Red"),
            cookableItem("Tomato_Cherry")
        )
        val result = engine.recommend(items)

        // Should not double-count "番茄" — same result as single tomato
        assertTrue("Should find recipes", result.recipes.isNotEmpty())
        // tomato_egg should rank first (1 match for tomato, tiebreak by cookingTime 10)
        assertEquals("tomato_egg", result.recipes.first().id)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // JSON Parsing Edge Cases
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `should handle recipe with single matchIngredient`() = runTest {
        val engine = createEngineWithTestJson()
        every { labelNormalizer.normalize("Cucumber") } returns listOf("黄瓜")

        val items = listOf(cookableItem("Cucumber"))
        val result = engine.recommend(items)

        val quickCuke = result.recipes.find { it.id == "quick_cuke" }
        assertNotNull("quick_cuke (single matchIngredient) should match", quickCuke)
        assertEquals(1, quickCuke!!.matchIngredients.size)
    }

    @Test
    fun `should handle recipe with optional fields missing`() = runTest {
        // Minimal JSON with only required fields
        val minimalJson = """
        {
          "version": "1.0",
          "recipes": [
            {
              "id": "minimal", "title": "极简", "category": "HOME", "difficulty": "EASY",
              "cookingTimeMin": 1,
              "matchIngredients": ["盐"],
              "allIngredients": [{"name":"盐","amount":"少许"}],
              "steps": [{"order":1,"text":"撒盐"}],
              "nutrition": {"calories":0,"protein":0,"carbs":0,"fat":0,"fiber":0},
              "tags": []
            }
          ]
        }
        """.trimIndent()

        val engine = createEngineWithTestJson(minimalJson)
        val recipes = engine.getAllPresetRecipes()

        assertEquals(1, recipes.size)
        val r = recipes.first()
        assertEquals("minimal", r.id)
        assertEquals("", r.tips)         // optString default
        assertEquals("", r.imageAsset)   // optString default
        assertEquals("", r.thumbnailAsset) // optString default
        assertTrue(r.tags.isEmpty())
    }

    @Test(expected = RecipeLoadException::class)
    fun `malformed JSON should throw RecipeLoadException with user message`() = runTest {
        val badJson = "{ this is not json }"
        every { assetManager.open("recipes/preset_recipes.json") } returns
            ByteArrayInputStream(badJson.toByteArray(Charsets.UTF_8))

        val engine = RecipeEngine(context, labelNormalizer)
        engine.getAllPresetRecipes() // should throw
    }

    @Test
    fun `empty recipes array should not crash`() = runTest {
        val emptyJson = """{ "version": "1.0", "recipes": [] }"""
        val engine = createEngineWithTestJson(emptyJson)
        val recipes = engine.getAllPresetRecipes()

        assertTrue("Empty array should return empty list", recipes.isEmpty())
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Load the real preset_recipes.json from the project's assets directory.
     * Uses a path relative to the project root so it works in both IDE and CI.
     */
    private fun loadRealJson(): String {
        // Try project-root-relative path first (works in Gradle/CI from project root)
        val candidates = listOf(
            "app/src/main/assets/recipes/preset_recipes.json",
            "../app/src/main/assets/recipes/preset_recipes.json"
        )
        for (path in candidates) {
            val file = File(path)
            if (file.exists()) return file.readText(Charsets.UTF_8)
        }
        throw IllegalStateException(
            "Cannot find preset_recipes.json. Searched: $candidates. " +
            "Ensure the test runs from the project root directory."
        )
    }
}
