package com.example.freshscan.ui.screen.collection

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.freshscan.domain.repository.CollectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import javax.inject.Inject

/**
 * Lightweight grid entry for a single produce variety in the collection.
 */
data class ProduceEntry(
    val label: String,
    val displayName: String,
    val category: String,
    val isCollected: Boolean,
    val scanCount: Int = 0,
    val isRare: Boolean = false
)

data class CollectionUiState(
    val entries: List<ProduceEntry> = emptyList(),
    val collectedCount: Int = 0,
    val totalCount: Int = 260,
    val isLoading: Boolean = true
)

@HiltViewModel
class CollectionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val collectionRepository: CollectionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CollectionUiState())
    val uiState: StateFlow<CollectionUiState> = _uiState.asStateFlow()

    init {
        loadCollection()
    }

    private fun loadCollection() {
        viewModelScope.launch {
            try {
                val allRaw = withContext(Dispatchers.IO) { loadAllFromLabelsTxt() }
                Log.d("CollectionVM", "Loaded ${allRaw.size} varieties from labels_v2.txt")

                val collectedItems = collectionRepository.getCollection().first()
                Log.d("CollectionVM", "Collected in DB: ${collectedItems.size}")
                val collectedMap = collectedItems.associateBy { it.label }

                val rareSet = setOf(
                    "Habanero", "Heirloom", "Tigerella", "Kumato", "Romanesco",
                    "San_Marzano", "Pink_Lady", "Crimson_Snow", "Cantaloupe",
                    "Serrano", "Pimiento", "Shallot", "Galangal", "Armenian",
                    "Pattypan", "Daikon", "Kohlrabi", "Artichoke", "Rutabaga",
                    "Celeriac", "Endive", "Radicchio", "Watercress"
                )

                val entries = allRaw.map { (label, displayName, category) ->
                    val collected = collectedMap[label]
                    if (collected != null) {
                        ProduceEntry(
                            label = label,
                            displayName = displayName,
                            category = category,
                            isCollected = true,
                            scanCount = collected.scanCount,
                            isRare = collected.isRare
                        )
                    } else {
                        ProduceEntry(
                            label = label,
                            displayName = displayName,
                            category = category,
                            isCollected = false,
                            isRare = rareSet.any { label == it || label.contains(it, ignoreCase = true) }
                        )
                    }
                }

                _uiState.value = CollectionUiState(
                    entries = entries,
                    collectedCount = collectedItems.size,
                    totalCount = allRaw.size,
                    isLoading = false
                )
            } catch (e: Exception) {
                Log.e("CollectionVM", "Failed to load collection", e)
                _uiState.value = CollectionUiState(isLoading = false)
            }
        }
    }

    /**
     * Load all 260 classes from assets/model/labels_v2.txt.
     * Format: index,label,display_name,is_cookable
     *
     * Category resolution:
     * 1. Match label against labels_v2_normalization.json → get ingredient name
     * 2. Look up ingredient in produce_info.json → category (水果/蔬菜/菌菇)
     * 3. Fallback: is_cookable=false → "水果", true → "蔬菜"
     * 4. Refine: 菇/菌→菌菇, 葱/姜/蒜/调味→调味, 豆→豆类
     */
    private fun loadAllFromLabelsTxt(): List<Triple<String, String, String>> {
        // Build ingredient→category map from produce_info.json
        val categoryByIngredient = loadIngredientCategoryMap()

        // Build label→ingredient map from labels_v2_normalization.json
        val ingredientByLabel = loadLabelToIngredientMap()

        // Parse labels_v2.txt
        return context.assets.open("model/labels_v2.txt")
            .bufferedReader()
            .useLines { lines ->
                lines.filter { it.isNotBlank() && !it.startsWith("#") }
                    .mapNotNull { line ->
                        val parts = line.split(",")
                        if (parts.size < 4) return@mapNotNull null
                        val label = parts[1].trim()
                        val displayName = parts[2].trim()
                        val isCookable = parts[3].trim() == "true"

                        // Resolve category
                        val ingredient = ingredientByLabel[label]
                        val catFromInfo = ingredient?.let { categoryByIngredient[it] }
                        val category = if (catFromInfo != null) {
                            catFromInfo
                        } else if (ingredient != null) {
                            inferCategory(ingredient)
                        } else {
                            if (isCookable) "蔬菜" else "水果"
                        }

                        Triple(label, displayName, category)
                    }
                    .toList()
            }
    }

    /** produce_info.json → map of ingredientName → category */
    private fun loadIngredientCategoryMap(): Map<String, String> {
        return try {
            val text = context.assets.open("produce_info.json")
                .bufferedReader().use { it.readText() }
            val array = JSONArray(text)
            val map = mutableMapOf<String, String>()
            for (i in 0 until array.length()) {
                try {
                    val obj = array.getJSONObject(i)
                    val label = obj.optString("label", "").trim()
                    val cat = obj.optString("category", "").trim()
                    if (label.isNotEmpty() && cat.isNotEmpty()) {
                        map[label] = cat
                    }
                } catch (_: Exception) { }
            }
            map
        } catch (e: Exception) {
            Log.e("CollectionVM", "Failed to load produce_info.json", e)
            emptyMap()
        }
    }

    /** labels_v2_normalization.json → map of modelLabel → ingredientName */
    private fun loadLabelToIngredientMap(): Map<String, String> {
        return try {
            val text = context.assets.open("labels_v2_normalization.json")
                .bufferedReader().use { it.readText() }
            val array = JSONArray(text)
            val map = mutableMapOf<String, String>()
            for (i in 0 until array.length()) {
                try {
                    val obj = array.getJSONObject(i)
                    val label = obj.optString("label", "").trim()
                    val ingredients = obj.optJSONArray("ingredients")
                    val ingredient = ingredients?.optString(0)?.trim()
                    if (label.isNotEmpty() && !ingredient.isNullOrEmpty()) {
                        map[label] = ingredient
                    }
                } catch (_: Exception) { }
            }
            map
        } catch (e: Exception) {
            Log.e("CollectionVM", "Failed to load labels_v2_normalization", e)
            emptyMap()
        }
    }

    private fun inferCategory(name: String): String = when {
        name.contains("菇") || name.contains("菌") -> "菌菇"
        name.contains("豆") && !name.contains("土豆") -> "豆类"
        name.endsWith("椒") || name.contains("辣椒") -> "调味"
        name.endsWith("葱") || name.contains("蒜") || name.contains("姜") ||
        name.contains("薄荷") || name.contains("罗勒") || name.contains("迷迭香") ||
        name.contains("茴香") || name.contains("香菜") || name.contains("韭") || name.contains("芹") -> "调味"
        else -> "蔬菜"
    }
}
