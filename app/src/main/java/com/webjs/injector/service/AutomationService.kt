package com.webjs.injector.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.http.SslError
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.view.Gravity
import android.view.WindowManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.NotificationCompat
import com.webjs.injector.App
import com.webjs.injector.PrefsManager
import com.webjs.injector.R
import com.webjs.injector.engine.UserScriptEngine
import com.webjs.injector.ui.MainActivity

class AutomationService : Service() {

    companion object {
        const val ACTION_START = "com.webjs.injector.ACTION_START"
        const val ACTION_STOP = "com.webjs.injector.ACTION_STOP"
        const val ACTION_SHOW_OVERLAY = "com.webjs.injector.ACTION_SHOW"
        const val ACTION_HIDE_OVERLAY = "com.webjs.injector.ACTION_HIDE"
        const val ACTION_SET_SCRIPT = "com.webjs.injector.ACTION_SCRIPT"
        const val ACTION_RELOAD_URL = "com.webjs.injector.ACTION_RELOAD"
        const val ACTION_LOG = "com.webjs.injector.ACTION_LOG"
        const val ACTION_STATE_CHANGED = "com.webjs.injector.ACTION_STATE_CHANGED"
        const val ACTION_VERIFYING = "com.webjs.injector.ACTION_VERIFYING"
        const val EXTRA_URL = "extra_url"
        const val EXTRA_SCRIPT = "extra_script"
        const val EXTRA_USER_AGENT = "extra_user_agent"
        const val EXTRA_DESKTOP_MODE = "extra_desktop_mode"
        const val EXTRA_TOUCH_ENABLED = "extra_touch_enabled"
        const val EXTRA_LOG_MSG = "extra_log_msg"
        const val EXTRA_LOG_LEVEL = "extra_log_level"
        const val EXTRA_IS_RUNNING = "extra_is_running"
        const val EXTRA_IS_VERIFYING = "extra_is_verifying"
        const val DEFAULT_URL = "https://example.com"
        const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        const val MOBILE_UA = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Mobile Safari/537.36"
        private const val WAKE_LOCK_TAG = "WebJsInjector:WakeLock"
        private const val NOTIFICATION_ID = 1
        private const val OVERLAY_HIDDEN = 1
        private const val OVERLAY_FULL = 1000

        var isOverlayVisible = false
            private set
        var currentUrl = DEFAULT_URL
        var isJsInjected = false
            private set
        var activeUserAgent = MOBILE_UA
            private set
        var isDesktopMode = false
            private set
        var isTouchEnabled = false
            private set
        var isRunning = false
            private set

        fun broadcastState(context: Context) {
            val intent = Intent(ACTION_STATE_CHANGED).apply {
                putExtra(EXTRA_IS_RUNNING, isRunning)
                setPackage(context.packageName)
            }
            context.sendBroadcast(intent)
        }

        fun broadcastVerifying(context: Context, verifying: Boolean) {
            val intent = Intent(ACTION_VERIFYING).apply {
                putExtra(EXTRA_IS_VERIFYING, verifying)
                setPackage(context.packageName)
            }
            context.sendBroadcast(intent)
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var webView: WebView? = null
    private var windowManager: WindowManager? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private val userScriptEngine = UserScriptEngine()
    private var targetUrl = DEFAULT_URL
    private var userAgent = MOBILE_UA
    private var desktopMode = false
    private var touchEnabled = false
    private val handler = Handler(Looper.getMainLooper())
    private var verificationTimer: Runnable? = null
    var isVerifying = false
        private set

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        acquireWakeLock()
        userScriptEngine.setOnLogCallback { entry ->
            broadcastLog(entry.level, entry.message)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                isJsInjected = false
                isRunning = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                broadcastState(this)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                targetUrl = intent.getStringExtra(EXTRA_URL) ?: PrefsManager.getUrl(this)
                userAgent = intent.getStringExtra(EXTRA_USER_AGENT) ?: PrefsManager.getUserAgent(this)
                desktopMode = intent.getBooleanExtra(EXTRA_DESKTOP_MODE, PrefsManager.getDesktopMode(this))
                touchEnabled = intent.getBooleanExtra(EXTRA_TOUCH_ENABLED, PrefsManager.getTouchEnabled(this))
                activeUserAgent = userAgent
                currentUrl = targetUrl
                isDesktopMode = desktopMode
                isTouchEnabled = touchEnabled
                val script = intent.getStringExtra(EXTRA_SCRIPT) ?: PrefsManager.getScript(this)
                userScriptEngine.clearScripts()
                userScriptEngine.clearConsoleLogs()
                if (script.isNotBlank()) {
                    userScriptEngine.registerScript(script)
                }
                isJsInjected = false
            }
            ACTION_SHOW_OVERLAY -> {
                isOverlayVisible = true
                updateOverlaySize(OVERLAY_FULL)
                return START_STICKY
            }
            ACTION_HIDE_OVERLAY -> {
                isOverlayVisible = false
                updateOverlaySize(OVERLAY_HIDDEN)
                return START_STICKY
            }
            ACTION_SET_SCRIPT -> {
                val script = intent.getStringExtra(EXTRA_SCRIPT) ?: ""
                userScriptEngine.clearScripts()
                if (script.isNotBlank()) {
                    userScriptEngine.registerScript(script)
                    PrefsManager.saveScript(this, script)
                }
                isJsInjected = false
                webView?.let { userScriptEngine.injectAllScripts(it) }
                return START_STICKY
            }
            ACTION_RELOAD_URL -> {
                targetUrl = intent.getStringExtra(EXTRA_URL) ?: targetUrl
                currentUrl = targetUrl
                isJsInjected = false
                userScriptEngine.clearConsoleLogs()
                webView?.loadUrl(targetUrl)
                return START_STICKY
            }
        }

        startForeground(NOTIFICATION_ID, createNotification())
        createOverlayWebView()
        loadUrl(targetUrl)
        isRunning = true
        broadcastState(this)

        return START_STICKY
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun broadcastLog(level: String, message: String) {
        val intent = Intent(ACTION_LOG).apply {
            putExtra(EXTRA_LOG_MSG, message)
            putExtra(EXTRA_LOG_LEVEL, level)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            acquire(10 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, AutomationService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, App.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createOverlayWebView() {
        webView?.let { w ->
            try {
                w.stopLoading()
                windowManager?.removeView(w)
                w.destroy()
            } catch (_: Exception) {}
            webView = null
        }

        val wv = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                loadWithOverviewMode = true
                useWideViewPort = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = true
                cacheMode = WebSettings.LOAD_DEFAULT
                userAgentString = userAgent
                defaultZoom = WebSettings.ZoomDensity.FAR
                textZoom = 100
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    view?.postDelayed({
                        val zoomJs = "(function(){var s=document.createElement('style');s.textContent='html,body{max-width:100vw!important;overflow-x:hidden!important;zoom:1!important;}';document.head.appendChild(s);})();"
                        view.evaluateJavascript(zoomJs, null)
                        userScriptEngine.injectAllScripts(view)
                        isJsInjected = true
                        broadcastLog("INFO", "JS injected on: ${url ?: "unknown"}")

                        // Auto-show overlay for verification period (20s)
                        startVerificationPeriod()
                    }, 500)
                }
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = false
                override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                    handler?.proceed()
                }
            }

            webChromeClient = userScriptEngine.createConsoleChromeClient()

            if (!touchEnabled) {
                setOnTouchListener { _, _ -> true }
            }
        }

        webView = wv
        val initialSize = if (isOverlayVisible) OVERLAY_FULL else OVERLAY_HIDDEN

        layoutParams = WindowManager.LayoutParams().apply {
            width = initialSize
            height = initialSize
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            flags = if (touchEnabled) {
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            } else {
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            }
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

        try {
            windowManager?.addView(wv, layoutParams)
            broadcastLog("INFO", "WebView created")
        } catch (e: Exception) {
            broadcastLog("ERROR", "WebView create failed: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun updateOverlaySize(size: Int) {
        layoutParams?.let { params ->
            params.width = size
            params.height = size
            try {
                webView?.let { windowManager?.updateViewLayout(it, params) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun loadUrl(url: String) {
        broadcastLog("INFO", "Loading: $url")
        webView?.loadUrl(url)
    }

    private fun cleanup() {
        releaseWakeLock()
        stopVerificationPeriod()
        isJsInjected = false
        isRunning = false
        webView?.apply {
            stopLoading()
            loadDataWithBaseURL(null, "", "text/html", "utf-8", null)
            clearHistory()
            destroy()
        }
        webView = null
        try { windowManager?.removeView(webView) } catch (_: Exception) {}
    }

    private fun startVerificationPeriod() {
        stopVerificationPeriod()
        isVerifying = true
        broadcastVerifying(this, true)

        // Show full-size overlay during verification
        isOverlayVisible = true
        updateOverlaySize(OVERLAY_FULL)
        broadcastLog("INFO", "Verification mode: overlay visible for 20s")

        // Auto-hide after 20 seconds
        verificationTimer = Runnable {
            isVerifying = false
            isOverlayVisible = false
            updateOverlaySize(OVERLAY_HIDDEN)
            broadcastVerifying(this@AutomationService, false)
            broadcastLog("INFO", "Verification complete: overlay hidden")
        }
        handler.postDelayed(verificationTimer!!, 20_000)
    }

    private fun stopVerificationPeriod() {
        verificationTimer?.let { handler.removeCallbacks(it) }
        verificationTimer = null
        if (isVerifying) {
            isVerifying = false
            broadcastVerifying(this, false)
        }
    }
}
