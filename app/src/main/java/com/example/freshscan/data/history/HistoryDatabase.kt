package com.example.freshscan.data.history

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.freshscan.util.Constants

/**
 * Room database for FreshScan history records.
 *
 * Schema versions:
 * - version 1 (MVP): history table only (9 fruit types × 2 states = 18 classes)
 * - version 2 (v2.0): added sessionId/displayName/isCookable columns,
 *   favorite_recipes table, shopping_list table
 *
 * Never use fallbackToDestructiveMigration() — data must be preserved.
 */
@Database(
    entities = [
        HistoryEntity::class,
        FavoriteRecipeEntity::class,
        ShoppingItemEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class HistoryDatabase : RoomDatabase() {

    abstract fun historyDao(): HistoryDao

    /** 🆕 v2.0: Favorite recipes DAO. */
    abstract fun favoriteRecipeDao(): FavoriteRecipeDao

    /** 🆕 v2.0: Shopping list DAO. */
    abstract fun shoppingListDao(): ShoppingListDao

    companion object {
        const val DATABASE_NAME = Constants.DATABASE_NAME

        /**
         * Migration from v1 to v2:
         * 1. Add sessionId, displayName, isCookable columns to history table.
         * 2. Create favorite_recipes table.
         * 3. Create shopping_list table.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add new columns to existing history table
                db.execSQL("ALTER TABLE history ADD COLUMN sessionId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE history ADD COLUMN displayName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE history ADD COLUMN isCookable INTEGER NOT NULL DEFAULT 0")

                // Create favorite_recipes table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS favorite_recipes (
                        recipeId TEXT PRIMARY KEY NOT NULL,
                        title TEXT NOT NULL,
                        category TEXT NOT NULL,
                        jsonData TEXT NOT NULL,
                        favoritedAt INTEGER NOT NULL
                    )
                """)

                // Create shopping_list table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS shopping_list (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        amount TEXT NOT NULL DEFAULT '',
                        isChecked INTEGER NOT NULL DEFAULT 0,
                        addedAt INTEGER NOT NULL
                    )
                """)
            }
        }
    }
}
