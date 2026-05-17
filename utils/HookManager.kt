package com.grindrplus.utils

import com.grindrplus.hooks.*
import de.robv.android.xposed.callbacks.XC_LoadPackage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * HookManager — Central registry and lifecycle manager for all GrindrPlus hooks.
 *
 * Manages hook registration, initialization, config-driven enable/disable,
 * and graceful error recovery with per-hook isolation.
 */
class HookManager {

    private val hooks = mutableMapOf<Class<*>, Hook>()
    private val hookResults = mutableMapOf<String, Boolean>()

    fun register(hook: Hook) {
        hooks[hook::class.java] = hook
        Logger.log("Registered: ${hook.hookName} — ${hook.hookDesc}")
    }

    fun initAll(lpparam: XC_LoadPackage.LoadPackageParam): Map<String, Boolean> {
        hooks.values.forEach { hook ->
            try {
                hook.setLoadPackageParam(lpparam)

                if (!Config.isHookEnabled(hook.hookName)) {
                    Logger.log("Skipped (disabled): ${hook.hookName}")
                    hookResults[hook.hookName] = false
                    return@forEach
                }

                runBlocking(Dispatchers.IO) {
                    hook.init()
                }

                hookResults[hook.hookName] = true
                Logger.log("Initialized: ${hook.hookName}")
            } catch (e: Throwable) {
                hookResults[hook.hookName] = false
                Logger.error("Failed to initialize: ${hook.hookName}", e)
            }
        }
        return hookResults
    }

    fun getHook(name: String): Hook? {
        return hooks.values.find { it.hookName == name }
    }

    fun getResults(): Map<String, Boolean> = hookResults.toMap()

    fun getHookCount(): Int = hooks.size

    fun getActiveHookCount(): Int = hookResults.count { it.value }
}
