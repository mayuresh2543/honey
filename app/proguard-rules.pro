# Proguard rules for Honeyfile Security Android app
-keep class com.sun.mail.** { *; }
-keep class javax.mail.** { *; }
-keep class javax.activation.** { *; }

# Keep Room Database entities and DAOs
-keep class com.honeyfile.security.data.** { *; }
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**

# Keep ML Kit & Play Services
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.** { *; }

# Keep Firebase Models
-keep class com.google.firebase.** { *; }

# Keep CameraX
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# Keep Coroutines
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# Keep Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Strip Compose debug tracing & source location overhead
-assumenosideeffects class androidx.compose.runtime.ComposerKt {
    void traceEventStart(int, int, int, java.lang.String);
    void traceEventEnd();
}
