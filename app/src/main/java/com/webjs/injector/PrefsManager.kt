package com.webjs.injector

import android.content.Context
import android.content.SharedPreferences

object PrefsManager {
    private const val PREFS_NAME = "webjs_injector_prefs"
    private const val KEY_URL = "target_url"
    private const val KEY_SCRIPT = "script_text"
    private const val KEY_USER_AGENT = "user_agent"
    private const val KEY_DESKTOP_MODE = "desktop_mode"
    private const val KEY_TARGET_ADDED = "target_added"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveUrl(context: Context, url: String) {
        prefs(context).edit().putString(KEY_URL, url).apply()
    }

    fun getUrl(context: Context): String =
        prefs(context).getString(KEY_URL, "https://example.com") ?: "https://example.com"

    fun saveScript(context: Context, script: String) {
        prefs(context).edit().putString(KEY_SCRIPT, script).apply()
    }

    fun getScript(context: Context): String =
        prefs(context).getString(KEY_SCRIPT, "") ?: ""

    fun saveUserAgent(context: Context, ua: String) {
        prefs(context).edit().putString(KEY_USER_AGENT, ua).apply()
    }

    fun getUserAgent(context: Context): String =
        prefs(context).getString(KEY_USER_AGENT, "") ?: ""

    fun saveDesktopMode(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_DESKTOP_MODE, enabled).apply()
    }

    fun getDesktopMode(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DESKTOP_MODE, false)

    fun saveTargetAdded(context: Context, added: Boolean) {
        prefs(context).edit().putBoolean(KEY_TARGET_ADDED, added).apply()
    }

    fun getTargetAdded(context: Context): Boolean =
        prefs(context).getBoolean(KEY_TARGET_ADDED, false)
}
