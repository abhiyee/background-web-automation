package com.webjs.injector.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.http.SslError
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
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
import com.webjs.injector.shizuku.ShizukuHelper
import com.webjs.injector.ui.MainActivity

class AutomationService : Service() {

    companion object {
        const val ACTION_START = "com.webjs.injector.ACTION_START"
        const val ACTION_STOP = "com.webjs.injector.ACTION_STOP"
        const val ACTION_RELOAD = "com.webjs.injector.ACTION_RELOAD"
        const val ACTION_LOG = "com.webjs.injector.ACTION_LOG"
        const val ACTION_STATE = "com.webjs.injector.ACTION_STATE"
        const val ACTION_URL_CHANGED = "com.webjs.injector.ACTION_URL_CHANGED"
        const val EXTRA_URL = "extra_url"
        const val EXTRA_SCRIPT = "extra_script"
        const val EXTRA_USER_AGENT = "extra_user_agent"
        const val EXTRA_DESKTOP_MODE = "extra_desktop_mode"
        const val EXTRA_LOG_MSG = "extra_log_msg"
        const val EXTRA_LOG_LEVEL = "extra_log_level"
        const val EXTRA_IS_RUNNING = "extra_is_running"
        const val EXTRA_RUNTIME = "extra_runtime"
        const val EXTRA_CURRENT_URL = "extra_current_url"
        const val EXTRA_SHIZUKU = "extra_shizuku"
        const val DEFAULT_URL = "https://example.com"
        const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        const val MOBILE_UA = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Mobile Safari/537.36"
        private const val WAKE_LOCK_TAG = "WebJsInjector:Wake"
        private const val NOTIFICATION_ID = 1

        var webView: WebView? = null
            private set
        var currentUrl = DEFAULT_URL
            private set
        var isRunning = false
            private set
        var startTime = 0L
            private set

        fun runtimeSeconds(): Long {
            if (startTime == 0L) return 0
            return (System.currentTimeMillis() - startTime) / 1000
        }

        fun formatRuntime(seconds: Long): String {
            val h = seconds / 3600
            val m = (seconds % 3600) / 60
            val s = seconds % 60
            return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
            else String.format("%02d:%02d", m, s)
        }

        fun broadcastState(context: Context) {
            val intent = Intent(ACTION_STATE).apply {
                putExtra(EXTRA_IS_RUNNING, isRunning)
                putExtra(EXTRA_RUNTIME, runtimeSeconds())
                putExtra(EXTRA_CURRENT_URL, currentUrl)
                putExtra(EXTRA_SHIZUKU, ShizukuHelper.hasPermission())
                setPackage(context.packageName)
            }
            context.sendBroadcast(intent)
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private val engine = UserScriptEngine()
    private val handler = Handler(Looper.getMainLooper())
    private var runtimeTimer: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        acquireWakeLock()
        engine.setOnLogCallback { broadcastLog(it.level, it.message) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopRuntimeTimer()
                ShizukuHelper.resetDisplay()
                cleanup()
                isRunning = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                broadcastState(this)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RELOAD -> {
                val url = intent.getStringExtra(EXTRA_URL) ?: currentUrl
                currentUrl = url
                webView?.loadUrl(url)
                broadcastUrlChanged()
                return START_STICKY
            }
        }

        startForeground(NOTIFICATION_ID, createNotification())
        createWebView()

        val url = intent?.getStringExtra(EXTRA_URL) ?: PrefsManager.getUrl(this)
        val ua = intent?.getStringExtra(EXTRA_USER_AGENT) ?: PrefsManager.getUserAgent(this)
        val desktop = intent?.getBooleanExtra(EXTRA_DESKTOP_MODE, PrefsManager.getDesktopMode(this))

        currentUrl = url
        webView?.settings?.userAgentString = ua.ifBlank {
            if (desktop) DESKTOP_UA else MOBILE_UA
        }
        webView?.loadUrl(url)

        val script = intent?.getStringExtra(EXTRA_SCRIPT) ?: PrefsManager.getScript(this)
        engine.clearScripts()
        engine.clearConsoleLogs()
        if (script.isNotBlank()) engine.registerScript(script)

        isRunning = true
        startTime = System.currentTimeMillis()

        // Use Shizuku to keep display on
        if (ShizukuHelper.hasPermission()) {
            ShizukuHelper.keepDisplayOn()
            ShizukuHelper.protectService(packageName)
        }

        startRuntimeTimer()
        broadcastState(this)

        return START_STICKY
    }

    override fun onDestroy() {
        stopRuntimeTimer()
        ShizukuHelper.resetDisplay()
        cleanup()
        super.onDestroy()
    }

    private fun broadcastLog(level: String, message: String) {
        sendBroadcast(Intent(ACTION_LOG).apply {
            putExtra(EXTRA_LOG_MSG, message)
            putExtra(EXTRA_LOG_LEVEL, level)
            setPackage(packageName)
        })
    }

    private fun broadcastUrlChanged() {
        sendBroadcast(Intent(ACTION_URL_CHANGED).apply {
            putExtra(EXTRA_CURRENT_URL, currentUrl)
            setPackage(packageName)
        })
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            acquire(24 * 60 * 60 * 1000L)
        }
    }

    private fun createNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPi = PendingIntent.getService(
            this, 1,
            Intent(this, AutomationService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, App.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("WebJs Injector")
            .setContentText("Running — JS controls the WebView")
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setContentIntent(pi)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPi)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView() {
        webView?.let { try { it.destroy() } catch (_: Exception) {} }

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
                defaultZoom = WebSettings.ZoomDensity.FAR
                textZoom = 100
                mediaPlaybackRequiresUserGesture = false
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    currentUrl = url ?: currentUrl
                    broadcastUrlChanged()
                    view?.postDelayed({
                        engine.injectAllScripts(view)
                        broadcastLog("INFO", "JS injected: $url")
                    }, 500)
                }
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) = false
                override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                    handler?.proceed()
                }
            }

            webChromeClient = engine.createConsoleChromeClient()
        }

        webView = wv
    }

    private fun cleanup() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        webView?.apply {
            stopLoading()
            destroy()
        }
        webView = null
    }

    private fun startRuntimeTimer() {
        runtimeTimer = object : Runnable {
            override fun run() {
                broadcastState(this@AutomationService)
                handler.postDelayed(this, 1000)
            }
        }
        handler.postDelayed(runtimeTimer!!, 1000)
    }

    private fun stopRuntimeTimer() {
        runtimeTimer?.let { handler.removeCallbacks(it) }
        runtimeTimer = null
    }
}
