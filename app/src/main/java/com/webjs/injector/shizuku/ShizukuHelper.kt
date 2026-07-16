package com.webjs.injector.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import com.webjs.injector.BuildConfig
import moe.shizuku.api.Shizuku
import moe.shizuku.api.ShizukuBinderReceivedListener
import moe.shizuku.api.ShizukuPermissionResult
import moe.shizuku.api.ShizukuRequestPermissionResultListener
import moe.shizuku.api.ShizukuProvider
import moe.shizuku.api.ShizukuService
import java.io.DataOutputStream

object ShizukuHelper {
    private const val TAG = "ShizukuHelper"
    var isAvailable = false
        private set
    var isActive = false
        private set
    var onStatusChanged: ((Boolean) -> Unit)? = null

    private val binderReceivedListener = ShizukuBinderReceivedListener {
        isAvailable = true
        Log.d(TAG, "Shizuku binder received")
        onStatusChanged?.invoke(true)
    }

    private val permissionResultListener = ShizukuRequestPermissionResultListener { requestCode, grantResult ->
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            isActive = true
            Log.d(TAG, "Shizuku permission granted")
            onStatusChanged?.invoke(true)
        }
    }

    fun init(context: Context) {
        try {
            Shizuku.addBinderReceivedListener(binderReceivedListener)
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
            if (Shizuku.pingBinder()) {
                isAvailable = true
                isActive = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }
        } catch (e: Exception) {
            Log.w(TAG, "Shizuku not available: ${e.message}")
            isAvailable = false
        }
    }

    fun requestPermission(activity: android.app.Activity) {
        if (isAvailable) {
            Shizuku.requestPermission(1001)
        }
    }

    fun hasPermission(): Boolean {
        return isAvailable && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Keep display on via ADB command (requires Shizuku)
     * Sets screen brightness to prevent display timeout
     */
    fun keepDisplayOn() {
        if (!hasPermission()) return
        try {
            val process = Shizuku.newProcess(arrayOf("sh"), null, null)
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("settings put system screen_off_timeout 2147483647\n")
            os.writeBytes("settings put system screen_brightness 1\n")
            os.flush()
            os.close()
            process.waitFor()
        } catch (e: Exception) {
            Log.w(TAG, "keepDisplayOn failed: ${e.message}")
        }
    }

    /**
     * Reset display settings
     */
    fun resetDisplay() {
        if (!hasPermission()) return
        try {
            val process = Shizuku.newProcess(arrayOf("sh"), null, null)
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("settings put system screen_off_timeout 60000\n")
            os.flush()
            os.close()
            process.waitFor()
        } catch (e: Exception) {
            Log.w(TAG, "resetDisplay failed: ${e.message}")
        }
    }

    /**
     * Prevent service from being killed by system
     */
    fun protectService(packageName: String) {
        if (!hasPermission()) return
        try {
            val process = Shizuku.newProcess(arrayOf("sh"), null, null)
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("dumpsys deviceidle whitelist +$packageName\n")
            os.flush()
            os.close()
            process.waitFor()
        } catch (e: Exception) {
            Log.w(TAG, "protectService failed: ${e.message}")
        }
    }

    fun destroy() {
        try {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeRequestPermissionResultListener(permissionResultListener)
        } catch (_: Exception) {}
    }
}
