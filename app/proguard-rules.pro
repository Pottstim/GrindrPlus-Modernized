# GrindrPlus ProGuard Rules

# Keep Xposed entry point
-keep class com.grindrplus.GrindrPlus {
    public void handleLoadPackage(...);
}

# Keep all hook classes (Xposed needs to reflectively find them)
-keep class com.grindrplus.hooks.** { *; }
-keep class com.grindrplus.utils.** { *; }
-keep class com.grindrplus.core.** { *; }

# Keep Xposed API
-keep class de.robv.android.xposed.** { *; }
-dontwarn de.robv.android.xposed.**

# Keep Kotlin metadata
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Remove logging in release
-assumenosideeffects class com.grindrplus.utils.Logger {
    public static void d(...);
    public static void v(...);
}
