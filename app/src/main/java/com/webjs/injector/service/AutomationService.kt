package com.webjs.injector.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Build
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
import com.webjs.injector.ui.MainActivity
import java.io.File
import java.io.FileOutputStream

class AutomationService : Service() {

    companion object {
        const val ACTION_START = "com.webjs.injector.ACTION_START"
        const val ACTION_STOP = "com.webjs.injector.ACTION_STOP"
        const val ACTION_SET_SCRIPT = "com.webjs.injector.ACTION_SCRIPT"
        const val ACTION_RELOAD_URL = "com.webjs.injector.ACTION_RELOAD"
        const val ACTION_LOG = "com.webjs.injector.ACTION_LOG"
        const val ACTION_STATE_CHANGED = "com.webjs.injector.ACTION_STATE_CHANGED"
        const val ACTION_SCREENSHOT = "com.webjs.injector.ACTION_SCREENSHOT"
        const val ACTION_URL_CHANGED = "com.webjs.injector.ACTION_URL_CHANGED"
        const val EXTRA_URL = "extra_url"
        const val EXTRA_SCRIPT = "extra_script"
        const val EXTRA_USER_AGENT = "extra_user_agent"
        const val EXTRA_DESKTOP_MODE = "extra_desktop_mode"
        const val EXTRA_TOUCH_ENABLED = "extra_touch_enabled"
        const val EXTRA_LOG_MSG = "extra_log_msg"
        const val EXTRA_LOG_LEVEL = "extra_log_level"
        const val EXTRA_IS_RUNNING = "extra_is_running"
        const val EXTRA_CURRENT_URL = "extra_current_url"
        const val DEFAULT_URL = "https://example.com"
        const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        const val MOBILE_UA = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Mobile Safari/537.36"
        private const val WAKE_LOCK_TAG = "WebJsInjector:WakeLock"
        private const val NOTIFICATION_ID = 1

        var webView: WebView? = null
            private set
        var currentUrl = DEFAULT_URL
            private set
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
                putExtra(EXTRA_CURRENT_URL, currentUrl)
                setPackage(context.packageName)
            }
            context.sendBroadcast(intent)
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private val userScriptEngine = UserScriptEngine()
    private var targetUrl = DEFAULT_URL
    private var userAgent = MOBILE_UA
    private var desktopMode = false
    private var touchEnabled = false
    private val handler = Handler(Looper.getMainLooper())
    private var screenshotRunnable: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        acquireWakeLock()
        userScriptEngine.setOnLogCallback { entry ->
            broadcastLog(entry.level, entry.message)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopScreenshotCapture()
                cleanup()
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
                broadcastUrlChanged()
                return START_STICKY
            }
            ACTION_SCREENSHOT -> {
                captureScreenshot()
                return START_STICKY
            }
        }

        startForeground(NOTIFICATION_ID, createNotification())
        createWebView()
        loadUrl(targetUrl)
        isRunning = true
        startScreenshotCapture()
        broadcastState(this)

        return START_STICKY
    }

    override fun onDestroy() {
        stopScreenshotCapture()
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

    private fun broadcastUrlChanged() {
        val intent = Intent(ACTION_URL_CHANGED).apply {
            putExtra(EXTRA_CURRENT_URL, currentUrl)
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
    private fun createWebView() {
        webView?.let { w ->
            try { w.destroy() } catch (_: Exception) {}
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
                mediaPlaybackRequiresUserGesture = false
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    view?.postDelayed({
                        val zoomJs = "(function(){var s=document.createElement('style');s.textContent='html,body{max-width:100vw!important;overflow-x:hidden!important;zoom:1!important;}';document.head.appendChild(s);})();"
                        view.evaluateJavascript(zoomJs, null)
                        userScriptEngine.injectAllScripts(view)
                        isJsInjected = true
                        currentUrl = url ?: currentUrl
                        broadcastLog("INFO", "Page loaded: $url")
                        broadcastUrlChanged()
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
        broadcastLog("INFO", "WebView created")
    }

    private fun loadUrl(url: String) {
        broadcastLog("INFO", "Loading: $url")
        webView?.loadUrl(url)
    }

    private fun cleanup() {
        releaseWakeLock()
        isJsInjected = false
        isRunning = false
        webView?.apply {
            stopLoading()
            loadDataWithBaseURL(null, "", "text/html", "utf-8", null)
            clearHistory()
            destroy()
        }
        webView = null
    }

    private fun startScreenshotCapture() {
        stopScreenshotCapture()
        val intervalMs = PrefsManager.getScreenshotInterval(this) * 1000L
        if (intervalMs <= 0) return

        screenshotRunnable = object : Runnable {
            override fun run() {
                captureScreenshot()
                handler.postDelayed(this, intervalMs)
            }
        }
        handler.postDelayed(screenshotRunnable!!, intervalMs)
    }

    private fun stopScreenshotCapture() {
        screenshotRunnable?.let { handler.removeCallbacks(it) }
        screenshotRunnable = null
    }

    private fun captureScreenshot() {
        val wv = webView ?: return
        try {
            val bitmap = Bitmap.createBitmap(wv.width.coerceAtLeast(1), wv.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            wv.draw(canvas)

            val dir = File(cacheDir, "screenshots")
            if (!dir.exists()) dir.mkdirs()

            val timestamp = System.currentTimeMillis()
            val file = File(dir, "screenshot_$timestamp.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bitmap.recycle()

            broadcastLog("INFO", "Screenshot saved: ${file.name}")

            // Keep only last 50 screenshots
            val files = dir.listFiles()?.sortedByDescending { it.name } ?: emptyList()
            files.drop(50).forEach { it.delete() }
        } catch (e: Exception) {
            broadcastLog("ERROR", "Screenshot failed: ${e.message}")
        }
    }
}
