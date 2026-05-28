package com.grindrplus.utils

import com.grindrplus.core.Config
import com.grindrplus.core.HookStateStore
import com.grindrplus.core.RemoteConfig
import com.grindrplus.hooks.*
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HookManager {
    private val hooks = mutableListOf<Hook>()
    private val results = mutableMapOf<String, Boolean>()

    fun registerDefaults() {
        register(AntiDetection())
        register(PairIPBlocker())
        register(GMSSpoof())
        register(FeatureGranting())
        register(EnableUnlimited())
        register(BanManagement())
        register(TimberLogging())
        register(CertPinBypass())
    }

    private fun register(hook: Hook) {
        hooks.add(hook)
        Logger.d("Registered: ${hook.name}")
    }

    /**
     * Fix #1: HookStateStore.init(context) is now called from GrindrPlus.kt
     * BEFORE this method is invoked, so prefs is guaranteed non-null here.
     * The old init(null) call has been removed.
     */
    fun initAll(lpparam: XC_LoadPackage.LoadPackageParam): Map<String, Boolean> {
        Config.init()
        HookStateStore.incrementInitCount()
        // NOTE: HookStateStore.init(context) is called in GrindrPlus.kt — do NOT call init(null) here.

        // Fetch remote config overrides
        if (Config.isRemoteConfigEnabled()) {
            RemoteConfig.fetchAsync { remoteJson ->
                val remoteFeatures = RemoteConfig.getFeatureOverrides()
                if (remoteFeatures.isNotEmpty()) {
                    Config.applyRemoteFeatures(remoteFeatures)
                    Logger.log("RemoteConfig: Applied ${remoteFeatures.size} feature overrides")
                }
            }
        }

        // Check safe mode (now reads from real SharedPreferences)
        if (HookStateStore.isSafeMode()) {
            Logger.warn("SAFE MODE: Only essential hooks will load")
        }

        hooks.forEach { hook ->
            try {
                hook.setLoadPackageParam(lpparam)

                // Skip disabled hooks
                if (!Config.isHookEnabled(hook.name)) {
                    Logger.d("Skipped (disabled): ${hook.name}")
                    results[hook.name] = false
                    return@forEach
                }

                // In safe mode, only load essential hooks
                if (HookStateStore.isSafeMode() && !isEssentialHook(hook.name)) {
                    Logger.log("Skipped (safe mode): ${hook.name}")
                    results[hook.name] = false
                    return@forEach
                }

                // Self-healing: skip hooks that have been failing repeatedly (now functional)
                if (HookStateStore.isFailing(hook.name)) {
                    Logger.warn("Skipped (failing): ${hook.name} — will retry next session")
                    results[hook.name] = false
                    return@forEach
                }

                hook.init()
                results[hook.name] = true
                HookStateStore.recordSuccess(hook.name)
                Logger.hookResult(hook.name, true)

            } catch (e: Throwable) {
                results[hook.name] = false
                HookStateStore.recordFailure(hook.name)
                Logger.hookResult(hook.name, false, e.message ?: e.javaClass.simpleName)
                Logger.error("Failed: ${hook.name}", e)
            }
        }

        val success = results.count { it.value }
        val total = results.size
        Logger.log("Hooks: $success/$total initialized successfully")

        return results
    }

    private fun isEssentialHook(name: String): Boolean {
        return name == "PairIP Blocker" || name == "Anti-Detection"
    }

    fun getResults(): Map<String, Boolean> = results.toMap()

    fun getTestReport(): String {
        val sb = StringBuilder()
        sb.appendLine("=== Hook Test Report ===")
        sb.appendLine("Timestamp: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
        sb.appendLine()

        hooks.forEach { hook ->
            val result = results[hook.name]
            val status = when {
                result == true -> "✓ PASS"
                result == false -> "✗ FAIL"
                else -> "? SKIP"
            }
            val successCount = HookStateStore.getSuccessCount(hook.name)
            val failCount = HookStateStore.getFailureCount(hook.name)
            sb.appendLine("$status ${hook.name} (history: ${successCount}✓ ${failCount}✗)")
        }

        sb.appendLine()
        sb.append(HookStateStore.getReport())
        return sb.toString()
    }
}
