package com.webjs.injector.ui

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.webjs.injector.PrefsManager
import com.webjs.injector.service.AutomationService

private val ConsoleGreen = Color(0xFF00E676)
private val ConsoleYellow = Color(0xFFFFD600)
private val ConsoleRed = Color(0xFFFF1744)
private val ConsoleCyan = Color(0xFF00E5FF)
private val ConsoleMagenta = Color(0xFFE040FB)
private val ConsoleOrange = Color(0xFFFF9100)
private val ConsoleBg = Color(0xFF0D1117)

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var isRunning by remember { mutableStateOf(AutomationService.isRunning) }
    var runtime by remember { mutableStateOf(AutomationService.runtimeSeconds()) }
    var currentUrl by remember { mutableStateOf(AutomationService.currentUrl) }
    val consoleLogs = remember { mutableStateListOf<String>() }
    val listState = rememberLazyListState()

    val logReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                intent?.let {
                    val msg = it.getStringExtra(AutomationService.EXTRA_LOG_MSG) ?: return
                    val level = it.getStringExtra(AutomationService.EXTRA_LOG_LEVEL) ?: "LOG"
                    consoleLogs.add("[$level] $msg")
                    if (consoleLogs.size > 500) consoleLogs.removeAt(0)
                }
            }
        }
    }

    val stateReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                intent?.let {
                    isRunning = it.getBooleanExtra(AutomationService.EXTRA_IS_RUNNING, false)
                    runtime = it.getLongExtra(AutomationService.EXTRA_RUNTIME, 0)
                    currentUrl = it.getStringExtra(AutomationService.EXTRA_CURRENT_URL) ?: currentUrl
                }
            }
        }
    }

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
        val stateFilter = IntentFilter(AutomationService.ACTION_STATE)
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

    LaunchedEffect(isRunning) {
        while (isRunning) {
            runtime = AutomationService.runtimeSeconds()
            kotlinx.coroutines.delay(1000)
        }
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
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("WebJs Injector", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("by abhiram79", fontSize = 10.sp, color = Color.Gray)
            }
            if (isRunning) {
                RuntimeBadge(runtime)
            }
        }

        // Status bar
        if (isRunning) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StatusDot("Service", true, ConsoleGreen)
                StatusDot("JS", true, ConsoleCyan)
            }
        }

        // Status card
        if (isRunning) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111318)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.PlayArrow, null, tint = ConsoleGreen, modifier = Modifier.height(32.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("WebView Running in Background", color = ConsoleGreen, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(currentUrl, color = Color.Gray, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        // Controls
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
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
                        isRunning = true
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isRunning,
                    colors = ButtonDefaults.buttonColors(containerColor = ConsoleGreen)
                ) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.height(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Start")
                }

                OutlinedButton(
                    onClick = {
                        context.startService(
                            Intent(context, AutomationService::class.java).apply { action = AutomationService.ACTION_STOP }
                        )
                        isRunning = false
                    },
                    modifier = Modifier.weight(1f),
                    enabled = isRunning,
                    colors = ButtonDefaults.buttonColors(contentColor = ConsoleRed)
                ) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.height(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Stop")
                }
            }

            if (isRunning) {
                OutlinedButton(
                    onClick = {
                        context.startService(
                            Intent(context, AutomationService::class.java).apply {
                                action = AutomationService.ACTION_RELOAD
                                putExtra(AutomationService.EXTRA_URL, AutomationService.currentUrl)
                            }
                        )
                        consoleLogs.clear()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.height(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reload", fontSize = 12.sp)
                }
                }
            }
        }

        // Console
        if (isRunning) {
            Card(
                modifier = Modifier.fillMaxWidth().weight(1f),
                colors = CardDefaults.cardColors(containerColor = ConsoleBg),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Console", color = ConsoleGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedButton(
                                onClick = {
                                    val clip = ClipboardManager::class.java.cast(
                                        context.getSystemService(Context.CLIPBOARD_SERVICE)
                                    )
                                    clip?.setPrimaryClip(ClipData.newPlainText("Logs", consoleLogs.joinToString("\n")))
                                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.height(24.dp)
                            ) { Text("Copy", fontSize = 9.sp) }
                            OutlinedButton(
                                onClick = { consoleLogs.clear() },
                                modifier = Modifier.height(24.dp)
                            ) { Text("Clear", fontSize = 9.sp) }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    if (consoleLogs.isEmpty()) {
                        Text("Waiting for logs...", color = Color.DarkGray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    } else {
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                            items(consoleLogs) { log ->
                                ConsoleLine(log)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConsoleLine(log: String) {
    val color = when {
        log.startsWith("[ERROR]") -> ConsoleRed
        log.startsWith("[WARNING]") -> ConsoleOrange
        log.contains("Injector") -> ConsoleCyan
        log.contains("info", true) -> ConsoleGreen
        log.contains("warn", true) -> ConsoleYellow
        log.contains("error", true) -> ConsoleRed
        log.contains("debug", true) -> ConsoleMagenta
        else -> Color(0xFF8BC34A)
    }

    Text(
        text = log,
        color = color,
        fontSize = 9.sp,
        fontFamily = FontFamily.Monospace,
        lineHeight = 13.sp,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(vertical = 1.dp)
    )
}

@Composable
fun RuntimeBadge(seconds: Long) {
    val formatted = AutomationService.formatRuntime(seconds)
    Box(
        modifier = Modifier
            .background(
                brush = Brush.horizontalGradient(listOf(ConsoleGreen.copy(alpha = 0.2f), ConsoleCyan.copy(alpha = 0.2f))),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(ConsoleGreen)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = formatted,
                color = ConsoleGreen,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun StatusDot(label: String, active: Boolean, color: Color) {
    val dotColor by animateColorAsState(if (active) color else Color.Gray, label = "")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(label, fontSize = 10.sp, color = dotColor)
    }
}
