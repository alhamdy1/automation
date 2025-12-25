# Add project specific ProGuard rules here.

# Keep security-critical classes
-keep class com.passphoto.processor.security.** { *; }

# Prevent class name obfuscation for reflection
-keepnames class com.passphoto.processor.** { *; }

# OkHttp rules
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# TensorFlow Lite
-keep class org.tensorflow.** { *; }
-dontwarn org.tensorflow.**

# ML Kit
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# SQLCipher
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# Anti-Tamper: Keep signature verification classes
-keep class com.passphoto.processor.security.TamperDetection { *; }

# Prevent reverse engineering insights
-repackageclasses 'p'
-allowaccessmodification
-overloadaggressively

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
