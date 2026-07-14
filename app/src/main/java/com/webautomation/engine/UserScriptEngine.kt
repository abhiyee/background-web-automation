package com.webautomation.engine

import android.webkit.WebView

class UserScriptEngine {

    companion object {
        private const val DEFAULT_SCRIPT = """
(function() {
    'use strict';
    
    console.log('[Automation] Default script injected');
    
    function logPageInfo() {
        console.log('[Automation] Page title: ' + document.title);
        console.log('[Automation] URL: ' + window.location.href);
        console.log('[Automation] Form count: ' + document.forms.length);
        console.log('[Automation] Input count: ' + document.querySelectorAll('input').length);
    }
    
    logPageInfo();
})();
"""
    }

    private val registeredScripts = mutableListOf<String>()

    init {
        registeredScripts.add(DEFAULT_SCRIPT)
    }

    fun clearScripts() {
        registeredScripts.clear()
    }

    fun registerScript(script: String) {
        registeredScripts.add(script)
    }

    fun removeScript(index: Int): Boolean {
        return if (index in registeredScripts.indices) {
            registeredScripts.removeAt(index)
            true
        } else {
            false
        }
    }

    fun getScripts(): List<String> = registeredScripts.toList()

    fun injectAllScripts(webView: WebView) {
        registeredScripts.forEach { script ->
            injectScript(webView, script)
        }
    }

    fun injectScript(webView: WebView, script: String) {
        webView.evaluateJavascript(script, null)
    }
}
