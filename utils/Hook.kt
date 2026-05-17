package com.grindrplus.utils

import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Base hook class for GrindrPlus.
 *
 * Each hook must implement init() where it registers its Xposed hooks.
 * The lpparam is provided by the HookManager during initialization.
 */
abstract class Hook(
    val hookName: String,
    val hookDesc: String
) {
    protected lateinit var lpparam: XC_LoadPackage.LoadPackageParam

    fun setLoadPackageParam(param: XC_LoadPackage.LoadPackageParam) {
        lpparam = param
    }

    abstract fun init()

    open fun onConfigChanged() {
        // Override to react to runtime config changes
    }
}
