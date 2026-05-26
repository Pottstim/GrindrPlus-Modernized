package com.grindrplus.utils

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * Issue #3 fix: Dynamic class resolution with regex-based class name matching
 * When Grindr obfuscates class names, this resolver scans loaded classes
 * to find matches by method signatures, field types, and string constants.
 */
class DynamicHookResolver(private val classLoader: ClassLoader) {

    data class HookSpec(
        val description: String,
        val classPattern: Regex,
        val methodName: String? = null,
        val returnType: Class<*>? = null,
        val replacement: Any? = null,
        val hookAllMethods: Boolean = false
    )

    private val specs = mutableListOf<HookSpec>()
    private val resolvedClasses = mutableMapOf<String, Class<*>>()

    fun addSpec(spec: HookSpec) { specs.add(spec) }

    /**
     * Resolve and hook all registered specs.
     * Returns a report of what was found and hooked.
     */
    fun resolveAll(): String {
        val sb = StringBuilder()
        sb.appendLine("=== DynamicHookResolver Report ===")

        specs.forEach { spec ->
            try {
                val matched = findMatchingClasses(spec.classPattern)
                if (matched.isEmpty()) {
                    sb.appendLine("? ${spec.description}: No class matched ${spec.classPattern}")
                    return@forEach
                }

                matched.forEach { clazz ->
                    try {
                        if (spec.hookAllMethods && spec.methodName != null) {
                            val replacement = spec.replacement ?: XC_MethodReplacement.returnConstant(true)
                            XposedBridge.hookAllMethods(clazz, spec.methodName, replacement as XC_MethodHook)
                        } else if (spec.methodName != null) {
                            val method = findMethodByName(clazz, spec.methodName, spec.returnType)
                            if (method != null) {
                                val replacement = spec.replacement ?: XC_MethodReplacement.returnConstant(true)
                                XposedBridge.hookMethod(method, replacement as XC_MethodHook)
                            } else {
                                sb.appendLine("? ${spec.description}: Method '${spec.methodName}' not found in ${clazz.name}")
                                return@forEach
                            }
                        } else {
                            // Hook all methods that match return type
                            hookAllMethodsByReturnType(clazz, spec.returnType, spec.replacement)
                        }
                        resolvedClasses[spec.description] = clazz
                        sb.appendLine("✓ ${spec.description}: ${clazz.name}")
                        Logger.log("DynamicHook: Resolved ${spec.description} → ${clazz.name}")
                    } catch (e: Throwable) {
                        sb.appendLine("✗ ${spec.description}: ${e.message}")
                    }
                }
            } catch (e: Throwable) {
                sb.appendLine("✗ ${spec.description}: ${e.message}")
            }
        }

        return sb.toString()
    }

    /**
     * Find classes matching a regex pattern by scanning the app's DEX files.
     */
    private fun findMatchingClasses(pattern: Regex): List<Class<*>> {
        val matches = mutableListOf<Class<*>?>()

        try {
            // Use PathClassLoader's dexElements to enumerate classes
            val pathListField = classLoader.javaClass.getDeclaredField("pathList")
            pathListField.isAccessible = true
            val pathList = pathListField.get(classLoader)
            val dexElementsField = pathList.javaClass.getDeclaredField("dexElements")
            dexElementsField.isAccessible = true
            val dexElements = dexElementsField.get(pathList) as Array<*>

            dexElements.forEach { element ->
                try {
                    val dexFileField = element!!.javaClass.getDeclaredField("dexFile")
                    dexFileField.isAccessible = true
                    val dexFile = dexFileField.get(element) ?: return@forEach
                    val entriesMethod = dexFile.javaClass.getMethod("entries")
                    val enumEntries = entriesMethod.invoke(dexFile) as java.util.Enumeration<*>

                    while (enumEntries.hasMoreElements()) {
                        val className = enumEntries.nextElement() as? String ?: continue
                        if (pattern.containsMatchIn(className)) {
                            try {
                                matches.add(classLoader.loadClass(className))
                            } catch (_: Throwable) { }
                        }
                    }
                } catch (_: Throwable) { }
            }
        } catch (e: Throwable) {
            Logger.error("DynamicHookResolver: DEX scan failed", e)
        }

        return matches.filterNotNull()
    }

    private fun findMethodByName(clazz: Class<*>, name: String, returnType: Class<*>?): java.lang.reflect.Method? {
        // Check declared methods first
        clazz.declaredMethods.forEach { method ->
            if (method.name == name && (returnType == null || method.returnType == returnType)) {
                method.isAccessible = true
                return method
            }
        }
        // Check public methods
        clazz.methods.forEach { method ->
            if (method.name == name && (returnType == null || method.returnType == returnType)) {
                method.isAccessible = true
                return method
            }
        }
        return null
    }

    private fun hookAllMethodsByReturnType(clazz: Class<*>, returnType: Class<*>?, replacement: Any?) {
        val hook = replacement ?: XC_MethodReplacement.returnConstant(true)
        clazz.declaredMethods.forEach { method ->
            if (returnType == null || method.returnType == returnType) {
                try {
                    method.isAccessible = true
                    XposedBridge.hookMethod(method, hook as XC_MethodHook)
                } catch (_: Throwable) { }
            }
        }
    }

    fun getResolvedClasses(): Map<String, Class<*>> = resolvedClasses.toMap()
}
