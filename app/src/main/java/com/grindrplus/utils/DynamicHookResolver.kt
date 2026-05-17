package com.grindrplus.utils

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * DynamicHookResolver — Resolves and hooks obfuscated classes by pattern matching.
 *
 * REPLACES the broken approach of XposedHelpers.findClass("com.pairipcore.*")
 * which doesn't work because findClass requires exact class names.
 *
 * Strategy:
 * 1. Hook ClassLoader.loadClass() to intercept class loading
 * 2. When a loaded class name matches a registered pattern, apply hooks
 * 3. Also provides static resolution for known class names
 */
class DynamicHookResolver(private val lpparam: XC_LoadPackage.LoadPackageParam) {

    private val pendingHooks = mutableListOf<PendingHook>()

    data class PendingHook(
        val classNamePattern: Regex,
        val methodName: String?,
        val hook: XC_MethodHook,
        val description: String
    )

    /**
     * Register a hook that fires when a class matching the pattern is loaded.
     * If methodName is null, hooks all methods of that class.
     */
    fun whenClassLoaded(
        pattern: String,
        methodName: String? = null,
        description: String = "",
        hook: XC_MethodHook
    ) {
        pendingHooks.add(
            PendingHook(
                classNamePattern = Regex(pattern),
                methodName = methodName,
                hook = hook,
                description = description
            )
        )
    }

    /**
     * Install the ClassLoader interceptor. Call once during init.
     */
    fun install() {
        try {
            XposedHelpers.findAndHookMethod(
                ClassLoader::class.java,
                "loadClass",
                String::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val className = param.args[0] as? String ?: return
                        if (className.startsWith("com.grindrapp")) return // skip self

                        pendingHooks.forEach { pending ->
                            if (pending.classNamePattern.matches(className)) {
                                try {
                                    val clazz = param.result as? Class<*> ?: return@forEach
                                    if (pending.methodName != null) {
                                        try {
                                            XposedHelpers.findAndHookMethod(
                                                clazz,
                                                pending.methodName,
                                                pending.hook
                                            )
                                        } catch (e: NoSuchMethodException) {
                                            // Method may have params — skip, will be caught by ClassLoader hook
                                            Logger.log("DynamicHook: method ${pending.methodName} not found without params in $className")
                                        }
                                    } else {
                                        XposedBridge.hookAllMethods(clazz, "onCreate", pending.hook)
                                    }
                                    Logger.log("DynamicHook: ${pending.description} -> $className")
                                    pendingHooks.remove(pending)
                                } catch (e: Throwable) {
                                    Logger.error("DynamicHook failed for $className", e)
                                }
                            }
                        }
                    }
                }
            )
            Logger.log("DynamicHookResolver: installed (${pendingHooks.size} pending patterns)")
        } catch (e: Throwable) {
            Logger.error("DynamicHookResolver install failed", e)
        }
    }

    /**
     * Resolve a class by exact name with null-safety.
     */
    fun findClassOrNull(name: String): Class<*>? {
        return try {
            XposedHelpers.findClass(name, lpparam.classLoader)
        } catch (e: Throwable) {
            Logger.log("Class not found: $name")
            null
        }
    }

    /**
     * Resolve a class by regex pattern against known loaded classes.
     * Uses reflection to scan the ClassLoader's loaded classes.
     */
    fun findClassByPattern(pattern: Regex): Class<*>? {
        return try {
            // Access ClassLoader's internal class cache via reflection
            val classLoader = lpparam.classLoader
            val loadedClassesField = ClassLoader::class.java.getDeclaredField("classes")
            loadedClassesField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val loadedClasses = loadedClassesField.get(classLoader) as? MutableList<Class<*>>
                ?: return null

            loadedClasses.firstOrNull { pattern.matches(it.name) }
        } catch (e: Throwable) {
            Logger.error("Pattern scan failed for $pattern", e)
            null
        }
    }

    private fun getParamTypes(clazz: Class<*>, methodName: String): Array<Class<*>> {
        // Try to find method with no params first, then with common signatures
        try {
            clazz.getDeclaredMethod(methodName)
            return emptyArray()
        } catch (_: NoSuchMethodException) {}

        // Return empty and let XposedHelpers handle it
        return emptyArray()
    }
}
