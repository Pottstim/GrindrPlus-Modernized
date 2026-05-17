package com.grindrplus.utils

import de.robv.android.xposed.XposedBridge

object Logger {
    private const val TAG = "GrindrPlus"

    fun log(msg: String) {
        try {
            XposedBridge.log("[$TAG] $msg")
        } catch (_: Throwable) {}
    }

    fun error(msg: String, t: Throwable? = null) {
        try {
            XposedBridge.log("[$TAG] ERROR: $msg")
            t?.let { XposedBridge.log(it) }
        } catch (_: Throwable) {}
    }
}
