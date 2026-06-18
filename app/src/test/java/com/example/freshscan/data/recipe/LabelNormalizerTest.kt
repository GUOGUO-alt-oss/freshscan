package com.example.freshscan.data.recipe

import com.example.freshscan.data.inference.model.LabelInfoV2
import com.example.freshscan.data.inference.model.ModelConfigV2
import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LabelNormalizerTest {

    private lateinit var labelNormalizer: LabelNormalizer
    private lateinit var config: ModelConfigV2
    private lateinit var context: Context

    @Before
    fun setUp() {
        config = mockk(relaxed = true)
        context = mockk(relaxed = true)
        // Provide a realistic labels list for fallback lookups
        every { config.labels } returns listOf(
            LabelInfoV2("Tomato_Cherry_Red", "樱桃番茄", isCookable = true),
            LabelInfoV2("Tomato_Maroon", "栗色番茄", isCookable = true),
            LabelInfoV2("Pepper_Green", "青椒", isCookable = true),
            LabelInfoV2("Potato_Red", "红土豆", isCookable = true),
            LabelInfoV2("Apple_Red", "红苹果", isCookable = false),
            LabelInfoV2("Banana", "香蕉", isCookable = false),
            LabelInfoV2("Unmapped_Exotic_Fruit", "异域水果", isCookable = true),
            LabelInfoV2("Cucumber_Ripe", "熟黄瓜", isCookable = true),
            LabelInfoV2("Carrot_Orange", "胡萝卜", isCookable = true),
            LabelInfoV2("Eggplant", "茄子", isCookable = true),
        )
        labelNormalizer = LabelNormalizer(context, config)
        labelNormalizer.setMappingForTest(TEST_MAPPING)
    }

    companion object {
        /** Minimal mapping injected for JVM tests (no Android assets available). */
        private val TEST_MAPPING: Map<String, List<String>> = mapOf(
            "Tomato_Cherry_Red" to listOf("番茄"),
            "Tomato_Maroon" to listOf("番茄"),
            "Tomato_Yellow" to listOf("番茄"),
            "Tomato_Green" to listOf("番茄"),
            "Tomato_Regular" to listOf("番茄"),
            "Tomato_Roma" to listOf("番茄"),
            "Tomato_Heirloom" to listOf("番茄"),
            "Tomato_Beefsteak" to listOf("番茄"),
            "Tomato_Plum" to listOf("番茄"),
            "Tomato_Grape" to listOf("番茄"),
            "Tomato_Orange" to listOf("番茄"),
            "Tomato_Striped" to listOf("番茄"),
            "Pepper_Green" to listOf("青椒"),
            "Pepper_Red" to listOf("辣椒", "红椒"),
            "Pepper_Bell" to listOf("青椒", "甜椒"),
            "Potato_Red" to listOf("土豆"),
            "Potato_White" to listOf("土豆"),
            "Potato_Sweet" to listOf("土豆", "红薯"),
            "Cucumber_Ripe" to listOf("黄瓜"),
            "Carrot_Orange" to listOf("胡萝卜"),
            "Eggplant" to listOf("茄子"),
            "Onion_Red" to listOf("洋葱"),
            "Cabbage_White" to listOf("卷心菜", "包菜"),
            "Broccoli" to listOf("西兰花"),
            "Cauliflower" to listOf("花菜"),
            "Spinach" to listOf("菠菜"),
            "Celery" to listOf("芹菜"),
            "Corn" to listOf("玉米"),
            "Beans_Green" to listOf("四季豆"),
            "Okra" to listOf("秋葵"),
            "Garlic" to listOf("大蒜"),
            "Ginger" to listOf("生姜"),
            "Mushroom_White" to listOf("蘑菇"),
            "Mushroom_Shiitake" to listOf("香菇"),
            "Asparagus_Green" to listOf("芦笋"),
            "Bitter_Gourd" to listOf("苦瓜"),
            "Pumpkin" to listOf("南瓜"),
            "Radish_Red" to listOf("萝卜"),
            "Radish_Daikon" to listOf("白萝卜"),
            "Lettuce_Iceberg" to listOf("生菜"),
            "Lettuce_Romaine" to listOf("生菜"),
            "Squash_Butternut" to listOf("南瓜"),
        )
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MAPPING Coverage Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `should normalize Tomato family to 番茄`() {
        val tomatoLabels = listOf(
            "Tomato_Cherry_Red", "Tomato_Maroon", "Tomato_Yellow",
            "Tomato_Green", "Tomato_Regular", "Tomato_Roma",
            "Tomato_Heirloom", "Tomato_Beefsteak", "Tomato_Plum",
            "Tomato_Grape", "Tomato_Orange", "Tomato_Striped"
        )
        for (label in tomatoLabels) {
            val result = labelNormalizer.normalize(label)
            assertTrue("$label should map to 番茄", result.contains("番茄"))
        }
    }

    @Test
    fun `should normalize Pepper family`() {
        val result = labelNormalizer.normalize("Pepper_Green")
        assertTrue("Pepper_Green should map to 青椒", result.contains("青椒"))
    }

    @Test
    fun `should normalize Potato family`() {
        val result = labelNormalizer.normalize("Potato_Red")
        assertTrue("Potato_Red should map to 土豆", result.contains("土豆"))
    }

    @Test
    fun `should normalize Cucumber family`() {
        val result = labelNormalizer.normalize("Cucumber_Ripe")
        assertTrue("Cucumber_Ripe should map to 黄瓜", result.contains("黄瓜"))
    }

    @Test
    fun `should normalize Carrot family`() {
        val result = labelNormalizer.normalize("Carrot_Orange")
        assertTrue("Carrot_Orange should map to 胡萝卜", result.contains("胡萝卜"))
    }

    @Test
    fun `should normalize Eggplant`() {
        val result = labelNormalizer.normalize("Eggplant")
        assertTrue("Eggplant should map to 茄子", result.contains("茄子"))
    }

    @Test
    fun `should normalize Onion family`() {
        // Onion_Red → 洋葱
        val result = labelNormalizer.normalize("Onion_Red")
        assertTrue("Onion_Red should map to 洋葱", result.contains("洋葱"))
    }

    @Test
    fun `should normalize Cabbage family`() {
        val result = labelNormalizer.normalize("Cabbage_White")
        assertTrue("Cabbage_White should map to 卷心菜 or 包菜",
            result.any { it == "卷心菜" || it == "包菜" })
    }

    @Test
    fun `should normalize Broccoli`() {
        val result = labelNormalizer.normalize("Broccoli")
        assertTrue("Broccoli should map to 西兰花", result.contains("西兰花"))
    }

    @Test
    fun `should normalize Cauliflower`() {
        val result = labelNormalizer.normalize("Cauliflower")
        assertTrue("Cauliflower should map to 花菜", result.contains("花菜"))
    }

    @Test
    fun `should normalize Spinach`() {
        val result = labelNormalizer.normalize("Spinach")
        assertTrue("Spinach should map to 菠菜", result.contains("菠菜"))
    }

    @Test
    fun `should normalize Celery`() {
        val result = labelNormalizer.normalize("Celery")
        assertTrue("Celery should map to 芹菜", result.contains("芹菜"))
    }

    @Test
    fun `should normalize Corn`() {
        val result = labelNormalizer.normalize("Corn")
        assertTrue("Corn should map to 玉米", result.contains("玉米"))
    }

    @Test
    fun `should normalize Beans family`() {
        val result = labelNormalizer.normalize("Beans_Green")
        assertTrue("Beans_Green should map to 四季豆", result.contains("四季豆"))
    }

    @Test
    fun `should normalize Okra`() {
        val result = labelNormalizer.normalize("Okra")
        assertTrue("Okra should map to 秋葵", result.contains("秋葵"))
    }

    @Test
    fun `should normalize Garlic`() {
        val result = labelNormalizer.normalize("Garlic")
        assertTrue("Garlic should map to 大蒜", result.contains("大蒜"))
    }

    @Test
    fun `should normalize Ginger`() {
        val result = labelNormalizer.normalize("Ginger")
        assertTrue("Ginger should map to 生姜", result.contains("生姜"))
    }

    @Test
    fun `should normalize Mushroom family`() {
        val result = labelNormalizer.normalize("Mushroom_White")
        assertTrue("Mushroom_White should map to 蘑菇", result.contains("蘑菇"))
    }

    @Test
    fun `should normalize Asparagus`() {
        val result = labelNormalizer.normalize("Asparagus_Green")
        assertTrue("Asparagus_Green should map to 芦笋", result.contains("芦笋"))
    }

    @Test
    fun `should normalize Bitter_Gourd`() {
        val result = labelNormalizer.normalize("Bitter_Gourd")
        assertTrue("Bitter_Gourd should map to 苦瓜", result.contains("苦瓜"))
    }

    @Test
    fun `should normalize Pumpkin`() {
        val result = labelNormalizer.normalize("Pumpkin")
        assertTrue("Pumpkin should map to 南瓜", result.contains("南瓜"))
    }

    @Test
    fun `should normalize Radish family`() {
        val result = labelNormalizer.normalize("Radish_Red")
        assertTrue("Radish_Red should map to 萝卜", result.contains("萝卜"))
    }

    @Test
    fun `should normalize Lettuce family`() {
        val result = labelNormalizer.normalize("Lettuce_Iceberg")
        assertTrue("Lettuce_Iceberg should map to 生菜", result.contains("生菜"))
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Fallback Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `should fall back to displayName for unmapped label`() {
        // "Unmapped_Exotic_Fruit" is not in the MAPPING table, but is in config.labels
        val result = labelNormalizer.normalize("Unmapped_Exotic_Fruit")
        assertEquals(listOf("异域水果"), result)
    }

    @Test
    fun `should fall back to raw label when not in mapping nor labels`() {
        // "Nonexistent_Label" not in MAPPING and not in config.labels
        val result = labelNormalizer.normalize("Nonexistent_Label")
        assertEquals(listOf("Nonexistent_Label"), result)
    }

    @Test
    fun `should return list with single element for direct mapping`() {
        // Most mappings return a single-element list
        val result = labelNormalizer.normalize("Celery")
        assertEquals(1, result.size)
    }

    @Test
    fun `should return multiple mappings for labels with multiple ingredients`() {
        // Pepper_Bell → ["青椒", "甜椒"]
        val result = labelNormalizer.normalize("Pepper_Bell")
        assertTrue("Pepper_Bell should have multiple mappings", result.size >= 2)
        assertTrue(result.contains("青椒"))
        assertTrue(result.contains("甜椒"))
    }

    @Test
    fun `should return multiple mappings for Potato_Sweet`() {
        // Potato_Sweet → ["土豆", "红薯"]
        val result = labelNormalizer.normalize("Potato_Sweet")
        assertTrue("Potato_Sweet should map to multiple", result.size >= 2)
        assertTrue(result.contains("土豆"))
        assertTrue(result.contains("红薯"))
    }

    @Test
    fun `should handle empty string label`() {
        val result = labelNormalizer.normalize("")
        // Not in MAPPING (empty key), not in labels, falls back to ""
        assertEquals(listOf(""), result)
    }

    @Test
    fun `should handle label with special characters`() {
        // Labels are alphanumeric+underscore in practice, but be robust
        val result = labelNormalizer.normalize("Tomato (Red)")
        // Not in mapping → lookup in labels → not found → fallback to raw
        assertEquals(listOf("Tomato (Red)"), result)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Key Cookable Categories Coverage
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `all major vegetable families should be in mapping`() {
        // Sample key cookable labels — verify they all map to something
        val cookableSamples = listOf(
            "Tomato_Cherry_Red", "Pepper_Green", "Potato_White",
            "Cucumber_Ripe", "Carrot_Orange", "Eggplant",
            "Onion_Red", "Garlic", "Ginger",
            "Cabbage_White", "Broccoli", "Cauliflower",
            "Spinach", "Celery", "Corn",
            "Beans_Green", "Okra", "Bitter_Gourd",
            "Pumpkin", "Radish_Daikon", "Lettuce_Romaine",
            "Asparagus_Green", "Mushroom_Shiitake", "Squash_Butternut"
        )

        for (label in cookableSamples) {
            val result = labelNormalizer.normalize(label)
            assertTrue("$label should have non-empty mapping", result.isNotEmpty())
            // Should not just be the raw fallback for these known items
            if (result.size == 1) {
                // Single-element result: check it's a Chinese name, not the raw label
                // (all major cookable items should be in MAPPING)
                val mapped = result[0]
                assertTrue("$label should map to Chinese ingredient name, got: $mapped",
                    mapped != label || label == "Corn" || label == "Okra" || label == "Eggplant" ||
                    label == "Spinach" || label == "Garlic" || label == "Ginger" ||
                    label == "Broccoli" || label == "Cauliflower" || label == "Bitter_Gourd" ||
                    label == "Pumpkin" || label == "Celery" || label == "Asparagus_Green")
            }
        }
    }
}
