package com.webjs.injector.ui

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
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
import androidx.compose.material.icons.filled.Warning
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
import com.webjs.injector.PrefsManager
import com.webjs.injector.service.AutomationService

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var hasOverlayPermission by remember { mutableStateOf(android.provider.Settings.canDrawOverlays(context)) }
    var isServiceRunning by remember { mutableStateOf(false) }
    var isOverlayVisible by remember { mutableStateOf(false) }
    var isJsInjected by remember { mutableStateOf(AutomationService.isJsInjected) }
    val consoleLogs = remember { mutableStateListOf<String>() }
    val listState = rememberLazyListState()

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

    DisposableEffect(Unit) {
        val filter = IntentFilter(AutomationService.ACTION_LOG)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(logReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(logReceiver, filter)
        }
        onDispose {
            try { context.unregisterReceiver(logReceiver) } catch (_: Exception) {}
        }
    }

    LaunchedEffect(consoleLogs.size) {
        if (consoleLogs.isNotEmpty()) {
            listState.animateScrollToItem(consoleLogs.size - 1)
        }
    }

    LaunchedEffect(Unit) {
        isOverlayVisible = AutomationService.isOverlayVisible
        isJsInjected = AutomationService.isJsInjected
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("WebJs Injector", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("by abhiram79", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Service Status", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(6.dp))
                StatusRow("Overlay Permission", hasOverlayPermission)
                StatusRow("Service Running", isServiceRunning)
                StatusRow("WebView Visible", isOverlayVisible)
                StatusRow("JS Injected", isJsInjected)
            }
        }

        if (!hasOverlayPermission) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = Color(0xFFFF9800))
                        Text(" Permission Required", fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Overlay permission is needed for the headed WebView.", fontSize = 13.sp, color = Color(0xFF424242))
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            context.startActivity(
                                Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    android.net.Uri.parse("package:${context.packageName}")).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                    ) { Text("Grant Overlay Permission", color = Color.White) }
                }
            }
        }

        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val savedUrl = PrefsManager.getUrl(context)
                    val savedScript = PrefsManager.getScript(context)
                    val savedUA = PrefsManager.getUserAgent(context)
                    val savedDesktop = PrefsManager.getDesktopMode(context)
                    context.startForegroundService(
                        Intent(context, AutomationService::class.java).apply {
                            action = AutomationService.ACTION_START
                            putExtra(AutomationService.EXTRA_URL, savedUrl)
                            putExtra(AutomationService.EXTRA_SCRIPT, savedScript)
                            putExtra(AutomationService.EXTRA_USER_AGENT, savedUA)
                            putExtra(AutomationService.EXTRA_DESKTOP_MODE, savedDesktop)
                        }
                    )
                    isServiceRunning = true
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = hasOverlayPermission && !isServiceRunning,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
            ) { Text("Start Service", color = Color.White) }

            OutlinedButton(
                onClick = {
                    context.startService(
                        Intent(context, AutomationService::class.java).apply { action = AutomationService.ACTION_STOP }
                    )
                    isServiceRunning = false
                    isOverlayVisible = false
                    isJsInjected = false
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = isServiceRunning,
                colors = ButtonDefaults.buttonColors(contentColor = Color(0xFFF44336))
            ) { Text("Stop Service") }

            if (isServiceRunning) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val action = if (isOverlayVisible) AutomationService.ACTION_HIDE_OVERLAY else AutomationService.ACTION_SHOW_OVERLAY
                            context.startService(Intent(context, AutomationService::class.java).apply { this.action = action })
                            isOverlayVisible = !isOverlayVisible
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(if (isOverlayVisible) "Hide WebView" else "Show WebView", fontSize = 12.sp) }

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

        if (isServiceRunning) {
            Card(
                modifier = Modifier.fillMaxWidth().weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Console Logs", color = Color(0xFF4CAF50), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = {
                                    val clip = ClipboardManager::class.java.cast(
                                        context.getSystemService(Context.CLIPBOARD_SERVICE)
                                    )
                                    val text = consoleLogs.joinToString("\n")
                                    clip?.setPrimaryClip(ClipData.newPlainText("ConsoleLogs", text))
                                    Toast.makeText(context, "Logs copied", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.height(28.dp)
                            ) { Text("Copy", fontSize = 10.sp) }
                            OutlinedButton(
                                onClick = { consoleLogs.clear() },
                                modifier = Modifier.height(28.dp)
                            ) { Text("Clear", fontSize = 10.sp) }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    if (consoleLogs.isEmpty()) {
                        Text("Waiting for logs...", color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
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
                                    fontSize = 10.sp,
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
fun StatusRow(label: String, active: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 13.sp)
        Icon(
            imageVector = if (active) Icons.Default.CheckCircle else Icons.Default.Close,
            contentDescription = null,
            tint = if (active) Color(0xFF4CAF50) else Color(0xFFF44336)
        )
    }
}
