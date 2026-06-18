package com.example.freshscan.data.history

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for the shopping list feature.
 *
 * Users can add ingredients from recipes to their shopping list.
 */
@Entity(tableName = "shopping_list")
data class ShoppingItemEntity(
    /** Auto-generated primary key. */
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    /** Ingredient name, e.g. "番茄". */
    @ColumnInfo(name = "name")
    val name: String,

    /** Amount string, e.g. "2个", "适量". */
    @ColumnInfo(name = "amount")
    val amount: String = "",

    /** Whether this item has been purchased/checked off. */
    @ColumnInfo(name = "isChecked")
    val isChecked: Boolean = false,

    /** Unix timestamp (ms) when added. */
    @ColumnInfo(name = "addedAt")
    val addedAt: Long = System.currentTimeMillis()
)
