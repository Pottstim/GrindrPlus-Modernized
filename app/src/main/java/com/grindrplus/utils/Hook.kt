package com.grindrplus.utils

import de.robv.android.xposed.callbacks.XC_LoadPackage

abstract class Hook(
    val name: String,
    val description: String
) {
    protected lateinit var lpparam: XC_LoadPackage.LoadPackageParam

    fun setLoadPackageParam(param: XC_LoadPackage.LoadPackageParam) {
        lpparam = param
    }

    abstract fun init()

    open fun onConfigChanged() {}
}
