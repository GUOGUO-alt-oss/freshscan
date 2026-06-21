import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.detekt)
}

// Read API key from local.properties (never committed to VCS)
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) load(file.inputStream())
}

android {
    namespace = "com.example.freshscan"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.freshscan"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "3.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Only keep Chinese and English resources (reduce APK size)
        resourceConfigurations += setOf("zh", "en")

        // NDK ABI filter: only arm64-v8a (covers 95%+ devices)
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    signingConfigs {
        create("release") {
            val keystorePath = localProperties.getProperty("KEYSTORE_PATH", "")
            if (keystorePath.isNotBlank()) {
                storeFile = file(keystorePath)
                storePassword = localProperties.getProperty("KEYSTORE_PASSWORD", "")
                keyAlias = localProperties.getProperty("KEY_ALIAS", "")
                keyPassword = localProperties.getProperty("KEY_PASSWORD", "")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isDebuggable = true
            buildConfigField("boolean", "ENABLE_VERBOSE_LOGGING", "true")
            buildConfigField("String", "AI_API_KEY", "\"${localProperties.getProperty("AI_API_KEY", "")}\"")
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("boolean", "ENABLE_VERBOSE_LOGGING", "false")
            val releaseKey = localProperties.getProperty("AI_API_KEY", "")
            if (releaseKey.isBlank()) {
                logger.warn("⚠️  AI_API_KEY is not set in local.properties — release build will have no API key!")
            }
            buildConfigField("String", "AI_API_KEY", "\"${releaseKey}\"")
            val keystorePath = localProperties.getProperty("KEYSTORE_PATH", "")
            if (keystorePath.isNotBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // Show deprecation details in generated Hilt/Dagger Java code
    tasks.withType(JavaCompile::class.java).configureEach {
        options.compilerArgs.add("-Xlint:deprecation")
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Kotlin 2.0+ has built-in Compose compiler plugin — no composeOptions block needed

    // JVM unit tests: prevent Android API stubs from throwing RuntimeException
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }

    // Packaging: exclude unnecessary metadata files
    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE.md",
                "META-INF/NOTICE.md",
                "META-INF/*.kotlin_module"
            )
        }
    }
}

dependencies {
    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose BOM
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // Lifecycle + ViewModel + Compose integration
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    // Navigation
    implementation(libs.navigation.compose)

    // TensorFlow Lite
    implementation(libs.tflite)
    implementation(libs.tflite.support)
    implementation(libs.tflite.gpu)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // MediaPipe Tasks Vision (EfficientDet-Lite0 object detection)
    implementation(libs.mediapipe.tasks.vision)

    // v3: Networking for AI API
    implementation(libs.okhttp)

    // Coil (Compose image loading)
    implementation(libs.coil.compose)

    // DataStore (Preferences)
    implementation(libs.datastore.preferences)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    testImplementation(libs.org.json) // real org.json for JVM tests (Android stub is non-functional)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.compose.ui.test)
    androidTestImplementation(platform(libs.compose.bom))
    testImplementation(libs.room.testing)
}
