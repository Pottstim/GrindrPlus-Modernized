# Issue #19: ProGuard rules to prevent obfuscation from breaking reflection-based hooks

# Keep all GrindrPlus classes (they're accessed via Xposed reflection)
-keep class com.grindrplus.** { *; }
-keepclassmembers class com.grindrplus.** { *; }

# Keep Xposed entry point
-keep class com.grindrplus.GrindrPlus { *; }

# Keep Hook base class and all subclasses
-keep class com.grindrplus.utils.Hook { *; }
-keep class com.grindrplus.utils.HookManager { *; }
-keep class com.grindrplus.utils.DynamicHookResolver { *; }
-keep class com.grindrplus.utils.Logger { *; }

# Keep Config and core classes
-keep class com.grindrplus.core.** { *; }

# Keep all hook classes (they use reflection internally)
-keep class com.grindrplus.hooks.** { *; }
-keepclassmembers class com.grindrplus.hooks.** {
    public void init();
    public void onConfigChanged();
}

# Keep Xposed bridge classes from being stripped
-keep class de.robv.android.xposed.** { *; }
-keep class de.robv.android.xposed.callbacks.** { *; }

# Don't warn about Xposed classes
-dontwarn de.robv.android.xposed.**

# Keep Android framework classes used via reflection
-keep class android.content.pm.PackageManager { *; }
-keep class android.content.pm.ApplicationPackageManager { *; }
-keep class android.content.pm.Signature { *; }

# Keep OkHttp/Retrofit classes for network interception
-keep class okhttp3.** { *; }
-keep class retrofit2.** { *; }
-dontwarn okhttp3.**
-dontwarn retrofit2.**

# Keep SettingsActivity
-keep class com.grindrplus.ui.SettingsActivity { *; }

# Preserve line numbers for debugging
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
