package com.webjs.injector.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import com.webjs.injector.service.AutomationService

@Composable
fun MainScreen() {
    val context = LocalContext.current
    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var isServiceRunning by remember { mutableStateOf(false) }
    var isOverlayVisible by remember { mutableStateOf(false) }
    var isJsInjected by remember { mutableStateOf(false) }
    var targetUrl by remember { mutableStateOf("") }
    var showUrlInput by remember { mutableStateOf(false) }
    var targetAdded by remember { mutableStateOf(false) }
    var scriptText by remember { mutableStateOf("") }
    val consoleLogs = remember { mutableStateListOf<String>() }
    val listState = rememberLazyListState()

    // Console log receiver
    val logReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                intent?.let {
                    val msg = it.getStringExtra(AutomationService.EXTRA_LOG_MSG) ?: return
                    val level = it.getStringExtra(AutomationService.EXTRA_LOG_LEVEL) ?: "LOG"
                    consoleLogs.add("[$level] $msg")
                    if (consoleLogs.size > 100) consoleLogs.removeAt(0)
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

    // File picker for .txt URL
    val urlFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.bufferedReader()?.readText()?.trim()?.let { text ->
                targetUrl = text.lines().firstOrNull { line -> line.isNotBlank() } ?: ""
                targetAdded = true
                showUrlInput = false
            }
        }
    }

    // File picker for .js script
    val jsFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.bufferedReader()?.readText()?.let { text ->
                scriptText = text
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Title section - left aligned
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "WebJs Injector",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "by abhiram79",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }

        StatusCard(
            hasOverlayPermission = hasOverlayPermission,
            isServiceRunning = isServiceRunning,
            isOverlayVisible = isOverlayVisible,
            isJsInjected = isJsInjected
        )

        if (!hasOverlayPermission) {
            PermissionWarningCard(
                onRequestPermission = {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                }
            )
        }

        // Console Logs (shown only when service running)
        if (isServiceRunning) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Console Logs", color = Color(0xFF4CAF50), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        OutlinedButton(
                            onClick = { consoleLogs.clear() },
                            modifier = Modifier.height(30.dp)
                        ) { Text("Clear", fontSize = 10.sp) }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (consoleLogs.isEmpty()) {
                        Text(
                            text = "Waiting for logs...",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxWidth().height(180.dp)
                        ) {
                            items(consoleLogs) { log ->
                                Text(
                                    text = log,
                                    color = when {
                                        log.startsWith("[ERROR]") -> Color(0xFFF44336)
                                        log.startsWith("[WARNING]") -> Color(0xFFFF9800)
                                        else -> Color(0xFF8BC34A)
                                    },
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Target URL Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Add Target", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Switch(
                        checked = showUrlInput,
                        onCheckedChange = { showUrlInput = it }
                    )
                }

                if (targetAdded && !showUrlInput) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Target set (URL hidden)",
                        fontSize = 13.sp,
                        color = Color(0xFF4CAF50)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            targetAdded = false
                            targetUrl = ""
                            showUrlInput = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Add New Target")
                    }
                }

                if (showUrlInput) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = targetUrl,
                        onValueChange = { targetUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("https://example.com") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                urlFilePicker.launch(arrayOf("text/plain"))
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Load .txt", fontSize = 12.sp) }
                        Button(
                            onClick = {
                                if (targetUrl.isNotBlank()) {
                                    targetAdded = true
                                    showUrlInput = false
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                        ) { Text("Set Target", color = Color.White, fontSize = 12.sp) }
                    }
                }
            }
        }

        // Script Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Inject JavaScript", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Write or load a .js file. Leave empty for default script.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = scriptText,
                    onValueChange = { scriptText = it },
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                    label = { Text("// JavaScript here...") },
                    maxLines = 10
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { jsFilePicker.launch(arrayOf("application/javascript", "text/javascript", "*/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Load .js File") }
            }
        }

        // Service Controls
        ServiceControlSection(
            hasOverlayPermission = hasOverlayPermission,
            isServiceRunning = isServiceRunning,
            isOverlayVisible = isOverlayVisible,
            onStartService = {
                startService(context, targetUrl, scriptText)
                isServiceRunning = true
            },
            onStopService = {
                stopService(context)
                isServiceRunning = false
                isOverlayVisible = false
                isJsInjected = false
            },
            onToggleOverlay = {
                if (isOverlayVisible) {
                    hideOverlay(context)
                    isOverlayVisible = false
                } else {
                    showOverlay(context)
                    isOverlayVisible = true
                }
            },
            onReloadUrl = {
                reloadUrl(context, targetUrl)
                isJsInjected = false
                consoleLogs.clear()
            }
        )

        // Battery Optimization
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Battery Optimization", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Request exemption so the service is not killed.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Request Battery Exemption") }
            }
        }
    }
}

@Composable
fun StatusCard(
    hasOverlayPermission: Boolean,
    isServiceRunning: Boolean,
    isOverlayVisible: Boolean,
    isJsInjected: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Service Status", fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            StatusRow("Overlay Permission", hasOverlayPermission)
            StatusRow("Service Running", isServiceRunning)
            StatusRow("WebView Visible", isOverlayVisible)
            StatusRow("JS Injected", isJsInjected)
        }
    }
}

@Composable
fun StatusRow(label: String, active: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 14.sp)
        Icon(
            imageVector = if (active) Icons.Default.CheckCircle else Icons.Default.Close,
            contentDescription = if (active) "Active" else "Inactive",
            tint = if (active) Color(0xFF4CAF50) else Color(0xFFF44336)
        )
    }
}

@Composable
fun PermissionWarningCard(onRequestPermission: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, "Warning", tint = Color(0xFFFF9800))
                Text("Permission Required", modifier = Modifier.padding(start = 8.dp), fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Overlay permission is needed for the headed WebView.", color = Color(0xFF424242))
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onRequestPermission,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
            ) { Text("Grant Overlay Permission", color = Color.White) }
        }
    }
}

@Composable
fun ServiceControlSection(
    hasOverlayPermission: Boolean,
    isServiceRunning: Boolean,
    isOverlayVisible: Boolean,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onToggleOverlay: () -> Unit,
    onReloadUrl: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onStartService,
            modifier = Modifier.fillMaxWidth(),
            enabled = hasOverlayPermission && !isServiceRunning,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
        ) { Text("Start Service", color = Color.White) }

        OutlinedButton(
            onClick = onStopService,
            modifier = Modifier.fillMaxWidth(),
            enabled = isServiceRunning,
            colors = ButtonDefaults.buttonColors(contentColor = Color(0xFFF44336))
        ) { Text("Stop Service") }

        if (isServiceRunning) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onToggleOverlay, modifier = Modifier.weight(1f)) {
                    Text(if (isOverlayVisible) "Hide WebView" else "Show WebView")
                }
                OutlinedButton(onClick = onReloadUrl, modifier = Modifier.weight(1f)) {
                    Text("Reload URL")
                }
            }
        }
    }
}

private fun startService(context: Context, url: String, script: String) {
    val intent = Intent(context, AutomationService::class.java).apply {
        action = AutomationService.ACTION_START
        putExtra(AutomationService.EXTRA_URL, url.ifBlank { AutomationService.DEFAULT_URL })
        putExtra(AutomationService.EXTRA_SCRIPT, script)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
}

private fun stopService(context: Context) {
    context.startService(Intent(context, AutomationService::class.java).apply { action = AutomationService.ACTION_STOP })
}

private fun showOverlay(context: Context) {
    context.startService(Intent(context, AutomationService::class.java).apply { action = AutomationService.ACTION_SHOW_OVERLAY })
}

private fun hideOverlay(context: Context) {
    context.startService(Intent(context, AutomationService::class.java).apply { action = AutomationService.ACTION_HIDE_OVERLAY })
}

private fun reloadUrl(context: Context, url: String) {
    context.startService(Intent(context, AutomationService::class.java).apply {
        action = AutomationService.ACTION_RELOAD_URL
        putExtra(AutomationService.EXTRA_URL, url)
    })
}
