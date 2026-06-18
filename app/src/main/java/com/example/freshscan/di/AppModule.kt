package com.example.freshscan.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.example.freshscan.data.history.HistoryDao
import com.example.freshscan.data.history.HistoryRepositoryImpl
import com.example.freshscan.data.inference.ModelLoader
import com.example.freshscan.data.inference.TFLiteClassifier
import com.example.freshscan.data.inference.model.ModelConfig
import com.example.freshscan.data.inference.model.ModelConfigV2
import com.example.freshscan.data.mapper.ModelMapper
import com.example.freshscan.data.mapper.ModelMapperV2
import com.example.freshscan.domain.repository.HistoryRepository
import com.example.freshscan.util.ImagePreprocessor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing application-scoped singleton dependencies.
 *
 * v2.0 bindings:
 * - @ModelV1 ModelConfig + @ModelV2 ModelConfigV2 dual model configs
 * - TFLiteClassifier instances: @FreshnessModel (18-class, CPU) + @ModelV2 (260-class)
 * - ModelMapper (v1 freshness) + ModelMapperV2 (v2 category)
 * - ImagePreprocessor, ModelLoader, repositories, DataStore
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ─── Model Configs ───

    @Provides
    @Singleton
    @ModelV1
    fun provideModelConfig(): ModelConfig = ModelConfig()

    @Provides
    @Singleton
    @ModelV2
    fun provideModelConfigV2(@ApplicationContext ctx: Context): ModelConfigV2 =
        ModelConfigV2(ctx)

    // ─── Preprocessors ───

    @Provides
    @Singleton
    fun provideImagePreprocessor(@ModelV1 config: ModelConfig): ImagePreprocessor =
        ImagePreprocessor(config)

    // ─── Model Loader ───

    @Provides
    @Singleton
    fun provideModelLoader(@ApplicationContext ctx: Context): ModelLoader =
        ModelLoader(ctx)

    // ─── TFLite Classifiers (parameterized) ───

    @Provides
    @Singleton
    @FreshnessModel
    fun provideFreshnessClassifier(
        @ApplicationContext ctx: Context,
        loader: ModelLoader
    ): TFLiteClassifier = TFLiteClassifier(
        ctx,
        modelFileName = "fruit_freshness_model.tflite",
        numClasses = 18,
        modelLoader = loader,
        forceCpuInitial = true  // CPU for precision stability on real photos
    )

    @Provides
    @Singleton
    @ModelV2
    fun provideClassifier260(
        @ApplicationContext ctx: Context,
        loader: ModelLoader
    ): TFLiteClassifier = TFLiteClassifier(
        ctx,
        modelFileName = "fruits360_model.tflite",
        numClasses = 260,
        modelLoader = loader
    )

    // ─── Model Mappers ───

    @Provides
    @Singleton
    fun provideModelMapper(@ModelV1 config: ModelConfig): ModelMapper =
        ModelMapper(config)

    @Provides
    @Singleton
    fun provideModelMapperV2(@ModelV2 config: ModelConfigV2): ModelMapperV2 =
        ModelMapperV2(config)

    // ─── Repositories ───

    @Provides
    @Singleton
    fun provideHistoryRepository(
        historyDao: HistoryDao
    ): HistoryRepository = HistoryRepositoryImpl(historyDao)

    // ─── DataStore ───

    @Provides
    @Singleton
    fun provideTasteProfileStore(@ApplicationContext ctx: Context): DataStore<Preferences> =
        TasteProfileDataStore.get(ctx)
}
