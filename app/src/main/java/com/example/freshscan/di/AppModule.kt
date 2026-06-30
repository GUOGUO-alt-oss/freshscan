package com.example.freshscan.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.example.freshscan.BuildConfig
import com.example.freshscan.data.ai.AIService
import com.example.freshscan.data.ai.QwenAIService
import com.example.freshscan.data.history.CollectedProduceDao
import com.example.freshscan.data.history.CollectionRepositoryImpl
import com.example.freshscan.data.history.FridgeDao
import com.example.freshscan.data.history.FridgeRepositoryImpl
import com.example.freshscan.data.history.HistoryDao
import com.example.freshscan.data.history.HistoryRepositoryImpl
import com.example.freshscan.data.inference.ModelLoader
import com.example.freshscan.data.inference.TFLiteClassifier
import com.example.freshscan.data.inference.model.ModelConfig
import com.example.freshscan.data.inference.model.ModelConfigV2
import com.example.freshscan.data.mapper.ModelMapper
import com.example.freshscan.data.mapper.ModelMapperV2

import com.example.freshscan.data.recipe.LabelNormalizer
import com.example.freshscan.domain.common.ResourceProvider
import com.example.freshscan.domain.common.UriInputStreamProvider
import com.example.freshscan.domain.repository.CollectionRepository
import com.example.freshscan.domain.repository.FridgeRepository
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
        historyDao: HistoryDao,
        collectedProduceDao: CollectedProduceDao
    ): HistoryRepository = HistoryRepositoryImpl(historyDao, collectedProduceDao)

    @Provides
    @Singleton
    fun provideFridgeRepository(
        fridgeDao: FridgeDao
    ): FridgeRepository = FridgeRepositoryImpl(fridgeDao)

    // ─── v4.2: Collection Repository ───

    @Provides
    @Singleton
    fun provideCollectionRepository(
        collectedProduceDao: CollectedProduceDao
    ): CollectionRepository = CollectionRepositoryImpl(collectedProduceDao)

    // ─── DataStore ───

    @Provides
    @Singleton
    fun provideTasteProfileStore(@ApplicationContext ctx: Context): DataStore<Preferences> =
        TasteProfileDataStore.get(ctx)

    // ─── v3: AI Service ───

    @Provides
    @Singleton
    @AIApiKey
    fun provideAIApiKey(): String = BuildConfig.AI_API_KEY

    @Provides
    @Singleton
    @AIBaseUrl
    fun provideAIBaseUrl(): String = "https://dashscope.aliyuncs.com/api/v1"

    @Provides
    @Singleton
    fun provideAIService(
        @AIApiKey apiKey: String,
        @AIBaseUrl baseUrl: String
    ): AIService = QwenAIService(apiKey, baseUrl)

    // ─── M8: Resource Abstractions ───

    @Provides
    @Singleton
    fun provideResourceProvider(impl: AndroidResourceProvider): ResourceProvider = impl

    @Provides
    @Singleton
    fun provideUriInputStreamProvider(impl: AndroidUriInputStreamProvider): UriInputStreamProvider = impl
}
