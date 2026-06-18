package com.example.freshscan.data.history

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for favorited recipes.
 *
 * Stores a snapshot of the recipe JSON data so favorites survive
 * even if the preset_recipes.json is updated.
 */
@Entity(tableName = "favorite_recipes")
data class FavoriteRecipeEntity(
    /** Recipe ID matching the preset_recipes.json id field. */
    @PrimaryKey
    @ColumnInfo(name = "recipeId")
    val recipeId: String,

    /** Display title, e.g. "番茄炒蛋". */
    @ColumnInfo(name = "title")
    val title: String,

    /** Recipe category name for filtering. */
    @ColumnInfo(name = "category")
    val category: String,

    /** Full recipe JSON for offline access. */
    @ColumnInfo(name = "jsonData")
    val jsonData: String,

    /** Unix timestamp (ms) when favorited. */
    @ColumnInfo(name = "favoritedAt")
    val favoritedAt: Long
)
