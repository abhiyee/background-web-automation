package com.webjs.injector.engine

import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import java.util.concurrent.CopyOnWriteArrayList

class UserScriptEngine {

    companion object {
        private const val ANTI_DETECTION_SCRIPT = """
(function() {
    'use strict';

    // 1. Hide navigator.webdriver
    Object.defineProperty(navigator, 'webdriver', { get: () => false });

    // 2. Remove automation-related properties
    delete window.cdc_adoQpoasnfa76pfcZLmcfl_Array;
    delete window.cdc_adoQpoasnfa76pfcZLmcfl_Promise;
    delete window.cdc_adoQpoasnfa76pfcZLmcfl_Symbol;

    // 3. Override plugins to look real
    Object.defineProperty(navigator, 'plugins', {
        get: () => {
            const plugins = [
                { name: 'Chrome PDF Plugin', filename: 'internal-pdf-viewer', description: 'Portable Document Format' },
                { name: 'Chrome PDF Viewer', filename: 'mhjfbmdgcfjbbpaeojofohoefgiehjai', description: '' },
                { name: 'Native Client', filename: 'internal-nacl-plugin', description: '' }
            ];
            plugins.refresh = () => {};
            return plugins;
        }
    });

    // 4. Override languages
    Object.defineProperty(navigator, 'languages', { get: () => ['en-US', 'en'] });

    // 5. Fix permissions API
    const originalQuery = window.navigator.permissions.query;
    window.navigator.permissions.query = (params) => (
        params.name === 'notifications' ?
            Promise.resolve({ state: Notification.permission }) :
            originalQuery(params)
    );

    // 6. Hide headless user agent indicators
    Object.defineProperty(navigator, 'userAgent', {
        get: () => navigator.userAgent.replace('HeadlessChrome', 'Chrome')
    });

    // 7. Simulate realistic mouse movements (for Turnstile)
    function simulateMouse() {
        const events = ['mousemove', 'mouseover', 'mouseenter', 'mouseleave'];
        const targets = document.querySelectorAll('input, button, a, div, span, body');
        let moveCount = 0;

        function randomMove() {
            if (moveCount > 50) return;
            moveCount++;

            const target = targets[Math.floor(Math.random() * targets.length)];
            const rect = target ? target.getBoundingClientRect() : { left: 0, top: 0, width: 100, height: 100 };
            const x = rect.left + Math.random() * rect.width;
            const y = rect.top + Math.random() * rect.height;

            const event = new MouseEvent('mousemove', {
                bubbles: true,
                cancelable: true,
                clientX: x,
                clientY: y,
                screenX: x,
                screenY: y
            });
            document.dispatchEvent(event);

            setTimeout(randomMove, 100 + Math.random() * 400);
        }

        // Start after small delay
        setTimeout(randomMove, 500);

        // Also simulate occasional clicks
        function randomClick() {
            const target = targets[Math.floor(Math.random() * targets.length)];
            if (target) {
                const rect = target.getBoundingClientRect();
                const x = rect.left + Math.random() * rect.width;
                const y = rect.top + Math.random() * rect.height;

                ['mousedown', 'mouseup', 'click'].forEach(type => {
                    target.dispatchEvent(new MouseEvent(type, {
                        bubbles: true,
                        cancelable: true,
                        clientX: x,
                        clientY: y,
                        view: window
                    }));
                });
            }
            setTimeout(randomClick, 2000 + Math.random() * 5000);
        }
        setTimeout(randomClick, 1000);
    }

    // 8. WebGL fingerprint spoofing
    const getParameter = WebGLRenderingContext.prototype.getParameter;
    WebGLRenderingContext.prototype.getParameter = function(param) {
        if (param === 37445) return 'Intel Inc.';
        if (param === 37446) return 'Intel Iris OpenGL Engine';
        return getParameter.apply(this, arguments);
    };

    // 9. Canvas fingerprint noise
    const toDataURL = HTMLCanvasElement.prototype.toDataURL;
    HTMLCanvasElement.prototype.toDataURL = function(type) {
        if (type === 'image/png' && this.width === 16 && this.height === 16) {
            return toDataURL.apply(this, arguments);
        }
        const context = this.getContext('2d');
        if (context) {
            const shift = { r: Math.floor(Math.random() * 10) - 5, g: Math.floor(Math.random() * 10) - 5, b: Math.floor(Math.random() * 10) - 5 };
            const width = this.width, height = this.height;
            if (width && height) {
                const imageData = context.getImageData(0, 0, width, height);
                for (let i = 0; i < imageData.data.length; i += 4) {
                    imageData.data[i] += shift.r;
                    imageData.data[i+1] += shift.g;
                    imageData.data[i+2] += shift.b;
                }
                context.putImageData(imageData, 0, 0);
            }
        }
        return toDataURL.apply(this, arguments);
    };

    // 10. Chrome runtime spoofing
    window.chrome = { runtime: {}, loadTimes: function(){}, csi: function(){} };

    console.log('[Injector] Anti-detection patches applied');
    simulateMouse();
})();
"""

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
        // Always inject anti-detection first
        injectScript(webView, ANTI_DETECTION_SCRIPT)

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
