package com.webautomation.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.http.SslError
import android.os.Build
import android.os.IBinder
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
import com.webautomation.R
import com.webautomation.WebAutomationApp
import com.webautomation.engine.UserScriptEngine
import com.webautomation.ui.MainActivity

class AutomationService : Service() {

    companion object {
        const val ACTION_START = "com.webautomation.ACTION_START"
        const val ACTION_STOP = "com.webautomation.ACTION_STOP"
        const val ACTION_SHOW_OVERLAY = "com.webautomation.ACTION_SHOW_OVERLAY"
        const val ACTION_HIDE_OVERLAY = "com.webautomation.ACTION_HIDE_OVERLAY"
        const val ACTION_SET_SCRIPT = "com.webautomation.ACTION_SET_SCRIPT"
        const val ACTION_RELOAD_URL = "com.webautomation.ACTION_RELOAD_URL"
        const val EXTRA_URL = "extra_url"
        const val EXTRA_SCRIPT = "extra_script"
        const val DEFAULT_URL = "https://example.com"
        private const val WAKE_LOCK_TAG = "WebAutomation:ServiceWakeLock"
        private const val NOTIFICATION_ID = 1

        private const val OVERLAY_SIZE_HIDDEN = 1
        private const val OVERLAY_SIZE_VISIBLE = 800

        var isOverlayVisible = false
            private set
        var currentUrl = DEFAULT_URL
            private set
        var currentScript = ""
            private set
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var webView: WebView? = null
    private var windowManager: WindowManager? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private val userScriptEngine = UserScriptEngine()
    private var targetUrl = DEFAULT_URL

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                targetUrl = intent.getStringExtra(EXTRA_URL) ?: DEFAULT_URL
                currentUrl = targetUrl
                val script = intent.getStringExtra(EXTRA_SCRIPT)
                if (!script.isNullOrBlank()) {
                    userScriptEngine.clearScripts()
                    userScriptEngine.registerScript(script)
                    currentScript = script
                }
            }
            ACTION_SHOW_OVERLAY -> {
                isOverlayVisible = true
                updateOverlaySize(OVERLAY_SIZE_VISIBLE)
                return START_STICKY
            }
            ACTION_HIDE_OVERLAY -> {
                isOverlayVisible = false
                updateOverlaySize(OVERLAY_SIZE_HIDDEN)
                return START_STICKY
            }
            ACTION_SET_SCRIPT -> {
                val script = intent.getStringExtra(EXTRA_SCRIPT) ?: ""
                userScriptEngine.clearScripts()
                if (script.isNotBlank()) {
                    userScriptEngine.registerScript(script)
                    currentScript = script
                } else {
                    currentScript = ""
                }
                webView?.let { userScriptEngine.injectAllScripts(it) }
                return START_STICKY
            }
            ACTION_RELOAD_URL -> {
                targetUrl = intent.getStringExtra(EXTRA_URL) ?: targetUrl
                currentUrl = targetUrl
                webView?.loadUrl(targetUrl)
                return START_STICKY
            }
        }

        startForeground(NOTIFICATION_ID, createNotification())
        createOverlayWebView()
        loadUrl(targetUrl)

        return START_STICKY
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            WAKE_LOCK_TAG
        ).apply {
            acquire(10 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, AutomationService::class.java).apply {
                action = ACTION_STOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, WebAutomationApp.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText("Running: $currentUrl")
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createOverlayWebView() {
        val context = this

        webView = WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                loadWithOverviewMode = true
                useWideViewPort = true
                cacheMode = WebSettings.LOAD_DEFAULT
                userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    view?.let { userScriptEngine.injectAllScripts(it) }
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    return false
                }

                override fun onReceivedSslError(
                    view: WebView?,
                    handler: SslErrorHandler?,
                    error: SslError?
                ) {
                    handler?.proceed()
                }
            }

            webChromeClient = WebChromeClient()
        }

        val initialSize = if (isOverlayVisible) OVERLAY_SIZE_VISIBLE else OVERLAY_SIZE_HIDDEN

        layoutParams = WindowManager.LayoutParams().apply {
            width = initialSize
            height = initialSize
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

        try {
            windowManager?.addView(webView, layoutParams)
        } catch (e: Exception) {
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
        webView?.loadUrl(url)
    }

    private fun cleanup() {
        releaseWakeLock()

        webView?.apply {
            stopLoading()
            loadDataWithBaseURL(null, "", "text/html", "utf-8", null)
            clearHistory()
            destroy()
        }
        webView = null

        try {
            windowManager?.removeView(webView)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
