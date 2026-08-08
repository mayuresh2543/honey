# Proguard rules for Honeyfile Security Android app
-keep class com.sun.mail.** { *; }
-keep class javax.mail.** { *; }
-keep class javax.activation.** { *; }

# Keep Room Database entities and DAOs
-keep class com.honeyfile.security.data.** { *; }

# Keep Firebase Models
-keep class com.google.firebase.** { *; }

# Strip all android.util.Log logging methods completely for maximum performance & minimum APK size
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}
