package com.example.freshscan.data.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for the favorite_recipes table.
 */
@Dao
interface FavoriteRecipeDao {

    /** Get all favorited recipes, newest first. Reactive. */
    @Query("SELECT * FROM favorite_recipes ORDER BY favoritedAt DESC")
    fun getAllFlow(): Flow<List<FavoriteRecipeEntity>>

    /** Check if a recipe is favorited. */
    @Query("SELECT EXISTS(SELECT 1 FROM favorite_recipes WHERE recipeId = :recipeId)")
    fun isFavorited(recipeId: String): Boolean

    /** Get a single favorite by recipe ID. */
    @Query("SELECT * FROM favorite_recipes WHERE recipeId = :recipeId")
    suspend fun getById(recipeId: String): FavoriteRecipeEntity?

    /** Insert or replace a favorite. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FavoriteRecipeEntity): Long

    /** Delete a favorite by recipe ID. */
    @Query("DELETE FROM favorite_recipes WHERE recipeId = :recipeId")
    suspend fun deleteById(recipeId: String): Int

    /** Get the total count. Reactive. */
    @Query("SELECT COUNT(*) FROM favorite_recipes")
    fun getCountFlow(): Flow<Int>
}
