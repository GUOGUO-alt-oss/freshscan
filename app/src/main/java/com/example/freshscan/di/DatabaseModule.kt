package com.example.freshscan.di

import android.content.Context
import androidx.room.Room
import com.example.freshscan.data.history.FavoriteRecipeDao
import com.example.freshscan.data.history.HistoryDao
import com.example.freshscan.data.history.HistoryDatabase
import com.example.freshscan.data.history.ShoppingListDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing Room database and DAO dependencies.
 *
 * v2.0 changes:
 * - Added MIGRATION_1_2 for schema upgrade.
 * - Added FavoriteRecipeDao and ShoppingListDao bindings.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideHistoryDatabase(
        @ApplicationContext context: Context
    ): HistoryDatabase {
        return Room.databaseBuilder(
            context,
            HistoryDatabase::class.java,
            HistoryDatabase.DATABASE_NAME
        )
            .addMigrations(HistoryDatabase.MIGRATION_1_2)
            .build()
    }

    @Provides
    fun provideHistoryDao(database: HistoryDatabase): HistoryDao {
        return database.historyDao()
    }

    @Provides
    fun provideFavoriteRecipeDao(database: HistoryDatabase): FavoriteRecipeDao {
        return database.favoriteRecipeDao()
    }

    @Provides
    fun provideShoppingListDao(database: HistoryDatabase): ShoppingListDao {
        return database.shoppingListDao()
    }
}
