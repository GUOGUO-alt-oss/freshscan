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
        ShoppingItemEntity::class,
        UserProfileEntity::class,     // v3
        DietPlanEntity::class,        // v3
        MealHistoryEntity::class,     // v5
        FridgeEntity::class           // v6
    ],
    version = 6,
    exportSchema = true
)
abstract class HistoryDatabase : RoomDatabase() {

    abstract fun historyDao(): HistoryDao

    /** 🆕 v2.0: Favorite recipes DAO. */
    abstract fun favoriteRecipeDao(): FavoriteRecipeDao

    /** 🆕 v2.0: Shopping list DAO. */
    abstract fun shoppingListDao(): ShoppingListDao

    /** v3: User profile DAO. */
    abstract fun userProfileDao(): UserProfileDao

    /** v5: Meal history DAO. */
    abstract fun mealHistoryDao(): MealHistoryDao

    /** v6: Fridge items DAO. */
    abstract fun fridgeDao(): FridgeDao

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

        /**
         * Migration from v2 to v3:
         * 1. Create user_profile table (singleton row for user preferences).
         * 2. Create diet_plans table (AI-generated diet plans).
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS user_profile (
                        id INTEGER PRIMARY KEY NOT NULL DEFAULT 1,
                        spiceLevel INTEGER NOT NULL DEFAULT 0,
                        saltLevel INTEGER NOT NULL DEFAULT 1,
                        oilLevel INTEGER NOT NULL DEFAULT 1,
                        excludedIngredients TEXT NOT NULL DEFAULT '[]',
                        preferredCategories TEXT NOT NULL DEFAULT '[]',
                        maxCookingTimeMin INTEGER NOT NULL DEFAULT 60,
                        age INTEGER NOT NULL DEFAULT 25,
                        heightCm INTEGER NOT NULL DEFAULT 170,
                        weightKg REAL NOT NULL DEFAULT 65.0,
                        gender TEXT NOT NULL DEFAULT 'UNSPECIFIED',
                        activityLevel TEXT NOT NULL DEFAULT 'MODERATE',
                        goal TEXT NOT NULL DEFAULT 'EAT_HEALTHY',
                        mealsPerDay INTEGER NOT NULL DEFAULT 3,
                        calorieTarget INTEGER,
                        allergies TEXT NOT NULL DEFAULT '[]'
                    )
                """)

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS diet_plans (
                        id TEXT PRIMARY KEY NOT NULL,
                        generatedAt INTEGER NOT NULL,
                        profileSnapshotJson TEXT NOT NULL,
                        dailyPlansJson TEXT NOT NULL,
                        totalCaloriesAvg INTEGER NOT NULL,
                        nutritionSummary TEXT NOT NULL DEFAULT ''
                    )
                """)
            }
        }

        /**
         * Migration from v3 to v4:
         * Add CHECK(id = 1) constraint to user_profile to enforce singleton row.
         * SQLite doesn't support ALTER TABLE ADD CONSTRAINT, so we recreate the table.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // SQLite doesn't support ALTER TABLE ADD CONSTRAINT, so recreate with CHECK
                db.execSQL("""
                    CREATE TABLE user_profile_new (
                        id INTEGER PRIMARY KEY NOT NULL DEFAULT 1 CHECK(id = 1),
                        spiceLevel INTEGER NOT NULL DEFAULT 0,
                        saltLevel INTEGER NOT NULL DEFAULT 1,
                        oilLevel INTEGER NOT NULL DEFAULT 1,
                        excludedIngredients TEXT NOT NULL DEFAULT '[]',
                        preferredCategories TEXT NOT NULL DEFAULT '[]',
                        maxCookingTimeMin INTEGER NOT NULL DEFAULT 60,
                        age INTEGER NOT NULL DEFAULT 25,
                        heightCm INTEGER NOT NULL DEFAULT 170,
                        weightKg REAL NOT NULL DEFAULT 65.0,
                        gender TEXT NOT NULL DEFAULT 'UNSPECIFIED',
                        activityLevel TEXT NOT NULL DEFAULT 'MODERATE',
                        goal TEXT NOT NULL DEFAULT 'EAT_HEALTHY',
                        mealsPerDay INTEGER NOT NULL DEFAULT 3,
                        calorieTarget INTEGER,
                        allergies TEXT NOT NULL DEFAULT '[]'
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO user_profile_new (id, spiceLevel, saltLevel, oilLevel, excludedIngredients,
                        preferredCategories, maxCookingTimeMin, age, heightCm, weightKg, gender,
                        activityLevel, goal, mealsPerDay, calorieTarget, allergies)
                    SELECT id, spiceLevel, saltLevel, oilLevel, excludedIngredients,
                        preferredCategories, maxCookingTimeMin, age, heightCm, weightKg, gender,
                        activityLevel, goal, mealsPerDay, calorieTarget, allergies
                    FROM user_profile WHERE id = 1
                """.trimIndent())
                db.execSQL("DROP TABLE user_profile")
                db.execSQL("ALTER TABLE user_profile_new RENAME TO user_profile")
            }
        }

        /**
         * Migration from v4 to v5:
         * Create meal_history table for single meal query history.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS meal_history (
                        id TEXT PRIMARY KEY NOT NULL,
                        query TEXT NOT NULL,
                        title TEXT NOT NULL,
                        ingredientsJson TEXT NOT NULL,
                        stepsJson TEXT NOT NULL,
                        cookingTimeMin INTEGER NOT NULL,
                        calories INTEGER NOT NULL,
                        proteinG REAL NOT NULL,
                        carbsG REAL NOT NULL,
                        fatG REAL NOT NULL,
                        generatedAt INTEGER NOT NULL
                    )
                """)
            }
        }

        /**
         * Migration from v5 to v6:
         * Create fridge_items table for the "My Fridge" feature.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS fridge_items (
                        id TEXT PRIMARY KEY NOT NULL,
                        display_name TEXT NOT NULL,
                        label TEXT NOT NULL DEFAULT '',
                        fruit_category TEXT NOT NULL DEFAULT 'UNKNOWN',
                        freshness_level TEXT NOT NULL DEFAULT 'FRESH',
                        is_cookable INTEGER NOT NULL DEFAULT 0,
                        added_at INTEGER NOT NULL,
                        expiry_at INTEGER,
                        thumbnail_path TEXT,
                        confidence REAL NOT NULL DEFAULT 0.0,
                        note TEXT NOT NULL DEFAULT ''
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_fridge_added_at ON fridge_items(added_at)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_fridge_display_name ON fridge_items(display_name)")
            }
        }
    }
}
