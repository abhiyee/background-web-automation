package com.webjs.injector.ui

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.webjs.injector.PrefsManager
import com.webjs.injector.service.AutomationService

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var isServiceRunning by remember { mutableStateOf(AutomationService.isRunning) }
    var isWebViewVisible by remember { mutableStateOf(true) }
    var currentUrl by remember { mutableStateOf(AutomationService.currentUrl) }
    var isJsInjected by remember { mutableStateOf(AutomationService.isJsInjected) }
    val consoleLogs = remember { mutableStateListOf<String>() }
    val listState = rememberLazyListState()

    // Log receiver
    val logReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                intent?.let {
                    val msg = it.getStringExtra(AutomationService.EXTRA_LOG_MSG) ?: return
                    val level = it.getStringExtra(AutomationService.EXTRA_LOG_LEVEL) ?: "LOG"
                    consoleLogs.add("[$level] $msg")
                    if (consoleLogs.size > 200) consoleLogs.removeAt(0)
                }
            }
        }
    }

    // State receiver
    val stateReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                intent?.let {
                    isServiceRunning = it.getBooleanExtra(AutomationService.EXTRA_IS_RUNNING, false)
                    currentUrl = it.getStringExtra(AutomationService.EXTRA_CURRENT_URL) ?: currentUrl
                }
            }
        }
    }

    // URL changed receiver
    val urlReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                intent?.let {
                    currentUrl = it.getStringExtra(AutomationService.EXTRA_CURRENT_URL) ?: currentUrl
                }
            }
        }
    }

    DisposableEffect(Unit) {
        val logFilter = IntentFilter(AutomationService.ACTION_LOG)
        val stateFilter = IntentFilter(AutomationService.ACTION_STATE_CHANGED)
        val urlFilter = IntentFilter(AutomationService.ACTION_URL_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(logReceiver, logFilter, Context.RECEIVER_NOT_EXPORTED)
            context.registerReceiver(stateReceiver, stateFilter, Context.RECEIVER_NOT_EXPORTED)
            context.registerReceiver(urlReceiver, urlFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(logReceiver, logFilter)
            context.registerReceiver(stateReceiver, stateFilter)
            context.registerReceiver(urlReceiver, urlFilter)
        }
        onDispose {
            try { context.unregisterReceiver(logReceiver) } catch (_: Exception) {}
            try { context.unregisterReceiver(stateReceiver) } catch (_: Exception) {}
            try { context.unregisterReceiver(urlReceiver) } catch (_: Exception) {}
        }
    }

    LaunchedEffect(Unit) {
        isServiceRunning = AutomationService.isRunning
        currentUrl = AutomationService.currentUrl
        isJsInjected = AutomationService.isJsInjected
    }

    LaunchedEffect(consoleLogs.size) {
        if (consoleLogs.isNotEmpty()) {
            listState.animateScrollToItem(consoleLogs.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Title + Status
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("WebJs Injector", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("by abhiram79", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusChip("Service", isServiceRunning)
                StatusChip("JS", isJsInjected)
            }
        }

        // WebView
        if (isServiceRunning) {
            Card(
                modifier = Modifier.fillMaxWidth().weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
            ) {
                if (isWebViewVisible) {
                    WebViewContainer(
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("WebView Hidden", color = Color.Gray, fontSize = 14.sp)
                        Text("Tap Show to display", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }
        }

        // Controls
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(
                onClick = {
                    val savedUrl = PrefsManager.getUrl(context)
                    val savedScript = PrefsManager.getScript(context)
                    val savedUA = PrefsManager.getUserAgent(context)
                    val savedDesktop = PrefsManager.getDesktopMode(context)
                    val savedTouch = PrefsManager.getTouchEnabled(context)
                    context.startForegroundService(
                        Intent(context, AutomationService::class.java).apply {
                            action = AutomationService.ACTION_START
                            putExtra(AutomationService.EXTRA_URL, savedUrl)
                            putExtra(AutomationService.EXTRA_SCRIPT, savedScript)
                            putExtra(AutomationService.EXTRA_USER_AGENT, savedUA)
                            putExtra(AutomationService.EXTRA_DESKTOP_MODE, savedDesktop)
                            putExtra(AutomationService.EXTRA_TOUCH_ENABLED, savedTouch)
                        }
                    )
                    isServiceRunning = true
                    isWebViewVisible = true
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isServiceRunning,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
            ) { Text("Start Service", color = Color.White) }

            OutlinedButton(
                onClick = {
                    context.startService(
                        Intent(context, AutomationService::class.java).apply { action = AutomationService.ACTION_STOP }
                    )
                    isServiceRunning = false
                    isWebViewVisible = true
                    isJsInjected = false
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = isServiceRunning,
                colors = ButtonDefaults.buttonColors(contentColor = Color(0xFFF44336))
            ) { Text("Stop Service") }

            if (isServiceRunning) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = { isWebViewVisible = !isWebViewVisible },
                        modifier = Modifier.weight(1f)
                    ) { Text(if (isWebViewVisible) "Hide WebView" else "Show WebView", fontSize = 12.sp) }

                    OutlinedButton(
                        onClick = {
                            context.startService(
                                Intent(context, AutomationService::class.java).apply {
                                    action = AutomationService.ACTION_RELOAD_URL
                                    putExtra(AutomationService.EXTRA_URL, AutomationService.currentUrl)
                                }
                            )
                            isJsInjected = false
                            consoleLogs.clear()
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Reload", fontSize = 12.sp) }
                }
            }
        }

        // Console Logs
        if (isServiceRunning) {
            Card(
                modifier = Modifier.fillMaxWidth().weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Console Logs", color = Color(0xFF4CAF50), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedButton(
                                onClick = {
                                    val clip = ClipboardManager::class.java.cast(
                                        context.getSystemService(Context.CLIPBOARD_SERVICE)
                                    )
                                    val text = consoleLogs.joinToString("\n")
                                    clip?.setPrimaryClip(ClipData.newPlainText("ConsoleLogs", text))
                                    Toast.makeText(context, "Logs copied", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.height(26.dp)
                            ) { Text("Copy", fontSize = 10.sp) }
                            OutlinedButton(
                                onClick = { consoleLogs.clear() },
                                modifier = Modifier.height(26.dp)
                            ) { Text("Clear", fontSize = 10.sp) }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    if (consoleLogs.isEmpty()) {
                        Text("Waiting for logs...", color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    } else {
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                            items(consoleLogs) { log ->
                                Text(
                                    text = log,
                                    color = when {
                                        log.startsWith("[ERROR]") -> Color(0xFFF44336)
                                        log.startsWith("[WARNING]") -> Color(0xFFFF9800)
                                        else -> Color(0xFF8BC34A)
                                    },
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WebViewContainer(modifier: Modifier = Modifier) {
    val webView = remember { AutomationService.webView }

    AndroidView(
        factory = { ctx ->
            webView ?: WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString = PrefsManager.getUserAgent(ctx)
            }
        },
        modifier = modifier,
        update = { wv ->
            if (wv != AutomationService.webView && AutomationService.webView != null) {
                // WebView was recreated in service, nothing to do
            }
        }
    )
}

@Composable
fun StatusChip(label: String, active: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            imageVector = if (active) Icons.Default.CheckCircle else Icons.Default.Close,
            contentDescription = null,
            tint = if (active) Color(0xFF4CAF50) else Color(0xFFF44336),
            modifier = Modifier.height(14.dp)
        )
        Text(label, fontSize = 11.sp, color = if (active) Color(0xFF4CAF50) else Color(0xFFF44336))
    }
}
