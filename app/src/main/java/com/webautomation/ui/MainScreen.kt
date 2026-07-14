package com.webautomation.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.webautomation.service.AutomationService

@Composable
fun MainScreen() {
    val context = LocalContext.current
    var hasOverlayPermission by remember {
        mutableStateOf(Settings.canDrawOverlays(context))
    }
    var isServiceRunning by remember {
        mutableStateOf(false)
    }
    var targetUrl by remember { mutableStateOf(AutomationService.DEFAULT_URL) }
    var customScript by remember { mutableStateOf("") }
    var isOverlayVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Web Automation",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        StatusCard(
            hasOverlayPermission = hasOverlayPermission,
            isServiceRunning = isServiceRunning,
            isOverlayVisible = isOverlayVisible
        )

        if (!hasOverlayPermission) {
            PermissionWarningCard(
                title = "Overlay Permission Required",
                description = "This app needs \"Draw over other apps\" permission to display the headed WebView in the background. This is essential for bypassing headless browser detection.",
                onRequestPermission = {
                    requestOverlayPermission(context)
                }
            )
        }

        // URL Input
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Target Website",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "URL to load in the background WebView",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = targetUrl,
                    onValueChange = { targetUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("https://example.com") },
                    singleLine = true
                )
            }
        }

        // Script Input
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Custom Script",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "JavaScript to inject on page load. Leave empty for default demo script.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = customScript,
                    onValueChange = { customScript = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    label = { Text("// Your JavaScript here...") },
                    maxLines = 10
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        applyScript(context, customScript)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isServiceRunning
                ) {
                    Text("Apply Script Now")
                }
            }
        }

        // Service Controls
        ServiceControlSection(
            hasOverlayPermission = hasOverlayPermission,
            isServiceRunning = isServiceRunning,
            isOverlayVisible = isOverlayVisible,
            onStartService = {
                startAutomationService(context, targetUrl, customScript)
                isServiceRunning = true
            },
            onStopService = {
                stopAutomationService(context)
                isServiceRunning = false
                isOverlayVisible = false
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
            }
        )

        BatteryOptimizationCard(
            onRequestExemption = {
                requestBatteryOptimizationExemption(context)
            }
        )

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "Works with screen locked (foreground service + wake lock)",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun StatusCard(
    hasOverlayPermission: Boolean,
    isServiceRunning: Boolean,
    isOverlayVisible: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Service Status",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            StatusRow(
                label = "Overlay Permission",
                isGranted = hasOverlayPermission
            )
            StatusRow(
                label = "Service Running",
                isGranted = isServiceRunning
            )
            StatusRow(
                label = "WebView Visible",
                isGranted = isOverlayVisible
            )
        }
    }
}

@Composable
fun StatusRow(label: String, isGranted: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 14.sp)
        Icon(
            imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Close,
            contentDescription = if (isGranted) "Active" else "Inactive",
            tint = if (isGranted) Color(0xFF4CAF50) else Color(0xFFF44336)
        )
    }
}

@Composable
fun PermissionWarningCard(
    title: String,
    description: String,
    onRequestPermission: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFF3E0)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Warning",
                    tint = Color(0xFFFF9800)
                )
                Text(
                    text = title,
                    modifier = Modifier.padding(start = 8.dp),
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE65100)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = description, color = Color(0xFF424242))
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onRequestPermission,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
            ) {
                Text("Grant Overlay Permission", color = Color.White)
            }
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
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onStartService,
            modifier = Modifier.fillMaxWidth(),
            enabled = hasOverlayPermission && !isServiceRunning,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
        ) {
            Text("Start Service", color = Color.White)
        }

        OutlinedButton(
            onClick = onStopService,
            modifier = Modifier.fillMaxWidth(),
            enabled = isServiceRunning,
            colors = ButtonDefaults.buttonColors(contentColor = Color(0xFFF44336))
        ) {
            Text("Stop Service")
        }

        if (isServiceRunning) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onToggleOverlay,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isOverlayVisible) "Hide WebView" else "Show WebView")
                }
                OutlinedButton(
                    onClick = onReloadUrl,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Reload URL")
                }
            }
        }
    }
}

@Composable
fun BatteryOptimizationCard(onRequestExemption: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Battery Optimization",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "To ensure the service runs continuously, request battery optimization exemption.",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onRequestExemption,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Request Battery Exemption")
            }
        }
    }
}

private fun requestOverlayPermission(context: Context) {
    val intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}")
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

private fun startAutomationService(context: Context, url: String, script: String) {
    val intent = Intent(context, AutomationService::class.java).apply {
        action = AutomationService.ACTION_START
        putExtra(AutomationService.EXTRA_URL, url)
        putExtra(AutomationService.EXTRA_SCRIPT, script)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
}

private fun stopAutomationService(context: Context) {
    val intent = Intent(context, AutomationService::class.java).apply {
        action = AutomationService.ACTION_STOP
    }
    context.startService(intent)
}

private fun showOverlay(context: Context) {
    val intent = Intent(context, AutomationService::class.java).apply {
        action = AutomationService.ACTION_SHOW_OVERLAY
    }
    context.startService(intent)
}

private fun hideOverlay(context: Context) {
    val intent = Intent(context, AutomationService::class.java).apply {
        action = AutomationService.ACTION_HIDE_OVERLAY
    }
    context.startService(intent)
}

private fun applyScript(context: Context, script: String) {
    val intent = Intent(context, AutomationService::class.java).apply {
        action = AutomationService.ACTION_SET_SCRIPT
        putExtra(AutomationService.EXTRA_SCRIPT, script)
    }
    context.startService(intent)
}

private fun reloadUrl(context: Context, url: String) {
    val intent = Intent(context, AutomationService::class.java).apply {
        action = AutomationService.ACTION_RELOAD_URL
        putExtra(AutomationService.EXTRA_URL, url)
    }
    context.startService(intent)
}

private fun requestBatteryOptimizationExemption(context: Context) {
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:${context.packageName}")
    }
    context.startActivity(intent)
}
