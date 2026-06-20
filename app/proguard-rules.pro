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
# MediaPipe Tasks Vision
# ==========================================
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# ==========================================
# Hilt / DI (compile-time processed, ensure reflection compat)
# ==========================================
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# ==========================================
# Room (generated classes — entities + database only)
# ==========================================
-keep @androidx.room.Entity class com.example.freshscan.data.history.** { *; }
-keep class * extends androidx.room.RoomDatabase

# ==========================================
# Data classes (may be used reflectively)
# ==========================================
-keep class com.example.freshscan.domain.model.** { *; }
-keep class com.example.freshscan.ui.state.** { *; }

# ==========================================
# OkHttp (v3 AI service)
# ==========================================
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn okio.**
-keep class okhttp3.OkHttpClient { *; }
-keep class okhttp3.Response { *; }
-keep class okhttp3.Request { *; }
-keep class okhttp3.ResponseBody { *; }
-keep class okhttp3.MediaType { *; }
-keep class okhttp3.RequestBody { *; }
-keepclassmembers class okhttp3.** {
    public *;
}
-keepattributes Signature

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
