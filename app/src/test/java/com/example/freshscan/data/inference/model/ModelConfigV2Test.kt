package com.example.freshscan.data.inference.model

import android.content.Context
import android.content.res.AssetManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File

class ModelConfigV2Test {

    private lateinit var context: Context
    private lateinit var assetManager: AssetManager

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        assetManager = mockk(relaxed = true)
        every { context.getAssets() } returns assetManager
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Label Parsing Tests (using the real labels_v2.txt)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `should load all 260 labels from real labels_v2 txt`() {
        val realLabels = loadRealLabelsV2()
        every { assetManager.open("model/labels_v2.txt") } returns
            ByteArrayInputStream(realLabels.toByteArray(Charsets.UTF_8))

        val config = ModelConfigV2(context)
        val labels = config.labels  // triggers lazy loading

        assertEquals("Should load exactly 260 labels", 260, labels.size)
    }

    @Test
    fun `all loaded labels should have non-empty fields`() {
        val realLabels = loadRealLabelsV2()
        every { assetManager.open("model/labels_v2.txt") } returns
            ByteArrayInputStream(realLabels.toByteArray(Charsets.UTF_8))

        val config = ModelConfigV2(context)
        val labels = config.labels

        labels.forEachIndexed { index, label ->
            assertTrue("Label[$index] label must not be blank: '$label'", label.label.isNotBlank())
            assertTrue("Label[$index] displayName must not be blank", label.displayName.isNotBlank())
        }
    }

    @Test
    fun `numClasses should match label count`() {
        val realLabels = loadRealLabelsV2()
        every { assetManager.open("model/labels_v2.txt") } returns
            ByteArrayInputStream(realLabels.toByteArray(Charsets.UTF_8))

        val config = ModelConfigV2(context)
        assertEquals(260, config.numClasses)
    }

    @Test
    fun `labels should contain known entries at expected positions`() {
        val realLabels = loadRealLabelsV2()
        every { assetManager.open("model/labels_v2.txt") } returns
            ByteArrayInputStream(realLabels.toByteArray(Charsets.UTF_8))

        val config = ModelConfigV2(context)
        val labels = config.labels

        // Position 0: Apple_Crimson_Snow
        assertEquals("Apple_Crimson_Snow", labels[0].label)
        assertEquals("红粉苹果", labels[0].displayName)
        assertEquals(false, labels[0].isCookable)

        // Tomato entries should be cookable (around positions 45-65)
        val tomatoEntry = labels.find { it.label.startsWith("Tomato_") }
        assertNotNull("Should find a tomato entry", tomatoEntry)
        assertEquals(true, tomatoEntry!!.isCookable)
    }

    @Test
    fun `isCookable should be true for vegetables and false for fruits`() {
        val realLabels = loadRealLabelsV2()
        every { assetManager.open("model/labels_v2.txt") } returns
            ByteArrayInputStream(realLabels.toByteArray(Charsets.UTF_8))

        val config = ModelConfigV2(context)
        val labels = config.labels

        val cookableCount = labels.count { it.isCookable }
        val fruitCount = labels.count { !it.isCookable }

        assertTrue("Should have cookable vegetables", cookableCount > 0)
        assertTrue("Should have non-cookable fruits", fruitCount > 0)
        assertEquals(260, cookableCount + fruitCount)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Label Parsing Edge Cases
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `should skip comment lines starting with hash`() {
        val labelsContent = """
            # This is a comment
            # Another comment line
            0,Test_Item,测试项目,false
            # Mid-file comment
            1,Another_Item,另一项,true
        """.trimIndent()

        every { assetManager.open("model/labels_v2.txt") } returns
            ByteArrayInputStream(labelsContent.toByteArray(Charsets.UTF_8))

        val config = ModelConfigV2(context)
        val labels = config.labels

        assertEquals("Should only parse non-comment lines", 2, labels.size)
        assertEquals("Test_Item", labels[0].label)
        assertEquals("Another_Item", labels[1].label)
    }

    @Test
    fun `should skip blank lines`() {
        val labelsContent = """
            0,Item_A,项目A,false

            1,Item_B,项目B,true

            2,Item_C,项目C,false
        """.trimIndent()

        every { assetManager.open("model/labels_v2.txt") } returns
            ByteArrayInputStream(labelsContent.toByteArray(Charsets.UTF_8))

        val config = ModelConfigV2(context)
        val labels = config.labels

        assertEquals("Should skip blank lines", 3, labels.size)
    }

    @Test
    fun `should skip malformed lines with fewer than 4 fields`() {
        val labelsContent = """
            0,Valid_Item,有效项目,true
            1,Only,Three
            2,Valid_Two,有效二,false
            just_one_field
            3,Valid_Three,有效三,true
            a,b
        """.trimIndent()

        every { assetManager.open("model/labels_v2.txt") } returns
            ByteArrayInputStream(labelsContent.toByteArray(Charsets.UTF_8))

        val config = ModelConfigV2(context)
        val labels = config.labels

        assertEquals("Should skip malformed lines", 3, labels.size)
        assertEquals("Valid_Item", labels[0].label)
        assertEquals("Valid_Two", labels[1].label)
        assertEquals("Valid_Three", labels[2].label)
    }

    @Test
    fun `should trim whitespace from parsed fields`() {
        val labelsContent = """
            0,  Item_With_Spaces  ,  带空格的项目  ,  true
        """.trimIndent()

        every { assetManager.open("model/labels_v2.txt") } returns
            ByteArrayInputStream(labelsContent.toByteArray(Charsets.UTF_8))

        val config = ModelConfigV2(context)
        val labels = config.labels

        assertEquals(1, labels.size)
        assertEquals("Item_With_Spaces", labels[0].label)  // trimmed
        assertEquals("带空格的项目", labels[0].displayName)  // trimmed
    }

    @Test
    fun `should handle empty file`() {
        every { assetManager.open("model/labels_v2.txt") } returns
            ByteArrayInputStream("".toByteArray(Charsets.UTF_8))

        val config = ModelConfigV2(context)
        val labels = config.labels

        assertTrue("Empty file should produce empty list", labels.isEmpty())
        assertEquals(0, config.numClasses)
    }

    @Test
    fun `should handle file with only comments and blanks`() {
        val labelsContent = """
            # Comment one
            # Comment two

            # Comment three
        """.trimIndent()

        every { assetManager.open("model/labels_v2.txt") } returns
            ByteArrayInputStream(labelsContent.toByteArray(Charsets.UTF_8))

        val config = ModelConfigV2(context)
        val labels = config.labels

        assertTrue("Comment-only file should produce empty list", labels.isEmpty())
    }

    @Test
    fun `should lazy-load labels only once`() {
        val labelsContent = "0,Single,单个,false"
        every { assetManager.open("model/labels_v2.txt") } returns
            ByteArrayInputStream(labelsContent.toByteArray(Charsets.UTF_8))

        val config = ModelConfigV2(context)

        // First access triggers loading
        val labels1 = config.labels
        // Second access should return the cached list
        val labels2 = config.labels

        assertTrue("Should return same list instance (lazy caching)", labels1 === labels2)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Configuration Constants
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `input dimensions should be 224×224×3`() {
        val config = ModelConfigV2(context)
        assertEquals(224, config.inputWidth)
        assertEquals(224, config.inputHeight)
        assertEquals(3, config.inputChannels)
    }

    @Test
    fun `pixelDataType should be 0 float32`() {
        val config = ModelConfigV2(context)
        assertEquals(0, config.pixelDataType)
    }

    @Test
    fun `normalizationMean should be 0_0f`() {
        val config = ModelConfigV2(context)
        assertEquals(0.0f, config.normalizationMean)
    }

    @Test
    fun `normalizationStd should be 255_0f`() {
        val config = ModelConfigV2(context)
        assertEquals(255.0f, config.normalizationStd)
    }

    @Test
    fun `byteBufferCapacity should be H×W×C×4`() {
        val config = ModelConfigV2(context)
        val expected = 224 * 224 * 3 * 4  // H × W × C × sizeof(float32)
        assertEquals(expected, config.byteBufferCapacity)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // LabelInfoV2 Data Class
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `LabelInfoV2 should support equality by value`() {
        val a = LabelInfoV2("Test", "测试", true)
        val b = LabelInfoV2("Test", "测试", true)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `LabelInfoV2 copy should work correctly`() {
        val original = LabelInfoV2("Original", "原始", false)
        val copied = original.copy(label = "Copied")
        assertEquals("Copied", copied.label)
        assertEquals("原始", copied.displayName)
        assertEquals(false, copied.isCookable)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Load the real labels_v2.txt from the project's assets directory.
     */
    private fun loadRealLabelsV2(): String {
        val candidates = listOf(
            "app/src/main/assets/model/labels_v2.txt",
            "../app/src/main/assets/model/labels_v2.txt"
        )
        for (path in candidates) {
            val file = File(path)
            if (file.exists()) return file.readText(Charsets.UTF_8)
        }
        throw IllegalStateException(
            "Cannot find labels_v2.txt. Searched: $candidates."
        )
    }
}
