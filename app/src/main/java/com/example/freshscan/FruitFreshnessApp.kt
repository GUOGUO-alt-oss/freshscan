package com.example.freshscan

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class for FreshScan. Serves as the Hilt DI entry point.
 *
 * Hilt initializes the DI container at app startup. All @Singleton-scoped
 * dependencies (CameraManager, TFLiteClassifier, RoomDatabase) are
 * created lazily on first access.
 */
@HiltAndroidApp
class FruitFreshnessApp : Application()
