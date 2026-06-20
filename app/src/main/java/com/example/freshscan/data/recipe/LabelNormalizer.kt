package com.example.freshscan.data.recipe

import android.content.Context
import com.example.freshscan.data.inference.model.ModelConfigV2
import com.example.freshscan.di.ModelV2
import com.example.freshscan.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maps Fruits-360 labels to recipe ingredient names for recipe matching.
 *
 * Mapping strategy (v2.1 — externalized to JSON):
 * 1. Load mapping table from [NORMALIZATION_ASSET_PATH] (~130 entries).
 * 2. Fall back to displayName from labels_v2.txt if no mapping found.
 * 3. The JSON format allows updating mappings without recompiling the APK
 *    (via app update or asset refresh mechanism).
 *
 * Originally hardcoded as a `companion object` map (160+ lines);
 * moved to an external JSON file for maintainability per code review §10.1 R1.
 */
@Singleton
class LabelNormalizer @Inject constructor(
    @ApplicationContext private val context: Context,
    @ModelV2 private val config: ModelConfigV2
) {
    /** Cached mapping, lazy-loaded from JSON on first access (thread-safe). */
    private val defaultMapping: Map<String, List<String>> by lazy { loadMapping() }

    /** Test-only override: when set, takes precedence over defaultMapping. */
    internal var mappingOverride: Map<String, List<String>>? = null

    /**
     * Normalize a Fruits-360 label to recipe ingredient names.
     *
     * @param fruitLabel Raw label from model output, e.g. "Tomato_Cherry_Red".
     * @return List of matching ingredient names, e.g. ["番茄"].
     *         Falls back to [displayName] if no mapping exists.
     */
    fun normalize(fruitLabel: String): List<String> {
        return (mappingOverride ?: defaultMapping)[fruitLabel]
            ?: listOf(
                config.labels
                    .find { it.label == fruitLabel }
                    ?.displayName
                    ?: fruitLabel
            )
    }

    // ─── Test Support ─────────────────────────────────────────────────────────

    /**
     * Inject a pre-built mapping for testing (bypasses JSON asset loading).
     *
     * In JVM unit tests, Android assets are unavailable, so the JSON loading
     * path will always fail. Call this in `@Before` to inject the mapping
     * directly without needing real Android Context or assets.
     */
    internal fun setMappingForTest(mapping: Map<String, List<String>>) {
        this.mappingOverride = mapping
    }

    // ─── Private ───────────────────────────────────────────────────────────────

    /**
     * Load the normalization mapping from an external JSON asset file.
     *
     * JSON format:
     * [
     *   {"label": "Tomato_Cherry_Red", "ingredients": ["番茄"]},
     *   {"label": "Pepper_Red", "ingredients": ["辣椒", "红椒"]},
     *   ...
     * ]
     *
     * On parse failure, falls back to an empty map and logs the error —
     * the displayName fallback in [normalize] handles the rest.
     */
    private fun loadMapping(): Map<String, List<String>> {
        return try {
            val inputStream = context.assets.open(NORMALIZATION_ASSET_PATH)
            val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
            val jsonStr = reader.use { it.readText() }
            val array = JSONArray(jsonStr)
            val result = mutableMapOf<String, List<String>>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val label = obj.getString("label")
                val ingredients = (0 until obj.getJSONArray("ingredients").length())
                    .map { j -> obj.getJSONArray("ingredients").getString(j) }
                result[label] = ingredients
            }
            Logger.i("LabelNormalizer", "Loaded ${result.size} label mappings from JSON")
            result
        } catch (e: Exception) {
            Logger.e("LabelNormalizer", "Failed to load mapping, using fallback", e)
            emptyMap()
        }
    }

    companion object {
        /** Asset path to the normalization JSON file. */
        const val NORMALIZATION_ASSET_PATH = "labels_v2_normalization.json"
    }
}
