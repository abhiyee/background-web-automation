package com.webjs.injector.engine

import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import java.util.concurrent.CopyOnWriteArrayList

class UserScriptEngine {

    companion object {
        const val DEFAULT_SCRIPT = """
(function() {
    'use strict';
    console.log('[Injector] Script loaded on: ' + window.location.href);
    console.log('[Injector] Page title: ' + document.title);
    console.log('[Injector] Forms: ' + document.forms.length + ' | Inputs: ' + document.querySelectorAll('input').length);
})();
"""
    }

    private val registeredScripts = mutableListOf<String>()
    private val consoleLogs = CopyOnWriteArrayList<ConsoleEntry>()
    private var onLogCallback: ((ConsoleEntry) -> Unit)? = null

    data class ConsoleEntry(
        val level: String,
        val message: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    fun setOnLogCallback(callback: (ConsoleEntry) -> Unit) {
        onLogCallback = callback
    }

    fun clearConsoleLogs() {
        consoleLogs.clear()
    }

    fun getConsoleLogs(): List<ConsoleEntry> = consoleLogs.toList()

    fun clearScripts() {
        registeredScripts.clear()
    }

    fun registerScript(script: String) {
        registeredScripts.add(script)
    }

    fun getScripts(): List<String> = registeredScripts.toList()

    fun hasCustomScript(): Boolean = registeredScripts.isNotEmpty()

    fun createConsoleChromeClient(): WebChromeClient {
        return object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                consoleMessage?.let {
                    val entry = ConsoleEntry(
                        level = it.messageLevel()?.name ?: "LOG",
                        message = it.message()
                    )
                    consoleLogs.add(entry)
                    if (consoleLogs.size > 200) {
                        consoleLogs.removeAt(0)
                    }
                    onLogCallback?.invoke(entry)
                }
                return true
            }
        }
    }

    fun injectAllScripts(webView: WebView) {
        if (registeredScripts.isEmpty()) {
            injectScript(webView, DEFAULT_SCRIPT)
        } else {
            registeredScripts.forEach { script ->
                injectScript(webView, script)
            }
        }
    }

    fun injectScript(webView: WebView, script: String) {
        webView.evaluateJavascript(script, null)
    }
}
