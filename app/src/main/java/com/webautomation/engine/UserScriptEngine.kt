package com.webautomation.engine

import android.webkit.WebView

class UserScriptEngine {

    companion object {
        private const val EXAMPLE_AUTOMATION_SCRIPT = """
(function() {
    'use strict';
    
    console.log('[Automation] Script injected successfully');
    
    function findAndFillInputs() {
        var inputs = document.querySelectorAll('input[type="text"], input[type="email"], input[type="search"]');
        for (var i = 0; i < inputs.length; i++) {
            var input = inputs[i];
            if (input.value === '' && !input.disabled) {
                input.value = 'Automated input ' + (i + 1);
                input.dispatchEvent(new Event('input', { bubbles: true }));
                input.dispatchEvent(new Event('change', { bubbles: true }));
                console.log('[Automation] Filled input: ' + (input.name || input.id || 'unnamed'));
            }
        }
    }
    
    function findAndClickButtons() {
        var buttons = document.querySelectorAll('button[type="submit"], input[type="submit"]');
        for (var i = 0; i < buttons.length; i++) {
            console.log('[Automation] Found submit button: ' + (buttons[i].textContent || buttons[i].value));
        }
    }
    
    function logPageInfo() {
        console.log('[Automation] Page title: ' + document.title);
        console.log('[Automation] URL: ' + window.location.href);
        console.log('[Automation] Form count: ' + document.forms.length);
        console.log('[Automation] Input count: ' + document.querySelectorAll('input').length);
    }
    
    function initAutomation() {
        logPageInfo();
        findAndFillInputs();
        findAndClickButtons();
    }
    
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initAutomation);
    } else {
        initAutomation();
    }
})();
"""
    }

    private val registeredScripts = mutableListOf<String>()

    init {
        registeredScripts.add(EXAMPLE_AUTOMATION_SCRIPT)
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

    fun injectCustomScript(webView: WebView, script: String) {
        injectScript(webView, script)
    }
}
