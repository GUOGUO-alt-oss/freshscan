package com.example.freshscan.di

import javax.inject.Qualifier

/**
 * Qualifier for v1 model config (18-class freshness model).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ModelV1

/**
 * Qualifier for v2 model config (260-class Fruits-360 model).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ModelV2

/**
 * Qualifier for the detection engine (EfficientDet-Lite0).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DetectionModel

/**
 * Qualifier for the freshness classifier (v1 MobileNetV3 18-class).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class FreshnessModel

/**
 * Qualifier for the recipe JSON file path in assets.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RecipeJsonPath
