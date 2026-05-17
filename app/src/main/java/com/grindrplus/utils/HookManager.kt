package com.grindrplus.utils

import com.grindrplus.core.Config
import com.grindrplus.hooks.*
import de.robv.android.xposed.callbacks.XC_LoadPackage

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
    }

    private fun register(hook: Hook) {
        hooks.add(hook)
        Logger.log("Registered: ${hook.name}")
    }

    fun initAll(lpparam: XC_LoadPackage.LoadPackageParam): Map<String, Boolean> {
        Config.init()
        hooks.forEach { hook ->
            try {
                hook.setLoadPackageParam(lpparam)
                if (!Config.isHookEnabled(hook.name)) {
                    Logger.log("Skipped (disabled): ${hook.name}")
                    results[hook.name] = false
                    return@forEach
                }
                hook.init()
                results[hook.name] = true
                Logger.log("Initialized: ${hook.name}")
            } catch (e: Throwable) {
                results[hook.name] = false
                Logger.error("Failed: ${hook.name}", e)
            }
        }
        return results
    }
}
