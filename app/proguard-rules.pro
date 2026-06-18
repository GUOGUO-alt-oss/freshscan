# ==========================================
# ProGuard / R8 Rules for FreshScan
# ==========================================

# Keep line numbers for debugging stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ==========================================
# TensorFlow Lite
# ==========================================
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**

# TFLite GPU delegate
-keep class org.tensorflow.lite.gpu.** { *; }
-dontwarn org.tensorflow.lite.gpu.**

# ==========================================
# Hilt / DI (compile-time processed, ensure reflection compat)
# ==========================================
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# ==========================================
# Room (generated classes)
# ==========================================
-keep class com.example.freshscan.data.history.** { *; }

# ==========================================
# Data classes (may be used reflectively)
# ==========================================
-keep class com.example.freshscan.domain.model.** { *; }
-keep class com.example.freshscan.ui.state.** { *; }

# ==========================================
# Compose
# ==========================================
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ==========================================
# CameraX
# ==========================================
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# ==========================================
# General optimizations
# ==========================================
# Remove debug logging in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
