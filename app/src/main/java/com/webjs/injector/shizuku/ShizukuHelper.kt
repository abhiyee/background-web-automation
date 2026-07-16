package com.webjs.injector.shizuku

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import java.io.DataOutputStream

object ShizukuHelper {
    private const val TAG = "ShizukuHelper"

    var isAvailable = false
        private set
    var isActive = false
        private set
    var onStatusChanged: ((Boolean) -> Unit)? = null

    fun init(context: Context) {
        try {
            val provider = context.packageManager.resolveContentProvider(
                "${context.packageName}.shizuku",
                PackageManager.MATCH_ALL
            )
            if (provider != null) {
                isAvailable = true
                Log.d(TAG, "Shizuku provider found")
            } else {
                val shizukuProvider = context.packageManager.resolveContentProvider(
                    "moe.shizuku.provider",
                    PackageManager.MATCH_ALL
                )
                isAvailable = shizukuProvider != null
                if (isAvailable) Log.d(TAG, "Shizuku app provider found")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Shizuku detection failed: ${e.message}")
            isAvailable = false
        }
    }

    fun requestPermission(activity: android.app.Activity) {
        if (isAvailable) {
            try {
                val intent = android.content.Intent("moe.shizuku.manager.REQUEST_PERMISSION").apply {
                    setPackage("moe.shizuku.manager")
                }
                activity.startActivity(intent)
                isActive = true
                onStatusChanged?.invoke(true)
            } catch (e: Exception) {
                Log.w(TAG, "Shizuku permission request failed: ${e.message}")
            }
        }
    }

    fun hasPermission(): Boolean = isActive

    fun keepDisplayOn() {
        if (!isActive) return
        try {
            Runtime.getRuntime().exec(arrayOf("sh", "-c", "settings put system screen_off_timeout 2147483647")).waitFor()
        } catch (e: Exception) {
            Log.w(TAG, "keepDisplayOn failed: ${e.message}")
        }
    }

    fun resetDisplay() {
        if (!isActive) return
        try {
            Runtime.getRuntime().exec(arrayOf("sh", "-c", "settings put system screen_off_timeout 60000")).waitFor()
        } catch (e: Exception) {
            Log.w(TAG, "resetDisplay failed: ${e.message}")
        }
    }

    fun protectService(packageName: String) {
        if (!isActive) return
        try {
            Runtime.getRuntime().exec(arrayOf("sh", "-c", "dumpsys deviceidle whitelist +$packageName")).waitFor()
        } catch (e: Exception) {
            Log.w(TAG, "protectService failed: ${e.message}")
        }
    }

    fun destroy() {}
}
