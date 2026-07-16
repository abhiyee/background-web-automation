package com.webjs.injector.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.webjs.injector.PrefsManager
import com.webjs.injector.service.AutomationService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    var targetUrl by remember { mutableStateOf(PrefsManager.getUrl(context)) }
    var showUrlInput by remember { mutableStateOf(false) }
    var targetAdded by remember { mutableStateOf(PrefsManager.getTargetAdded(context)) }
    var scriptText by remember { mutableStateOf(PrefsManager.getScript(context)) }
    var userAgent by remember {
        mutableStateOf(
            PrefsManager.getUserAgent(context).ifBlank {
                if (PrefsManager.getDesktopMode(context)) AutomationService.DESKTOP_UA else AutomationService.MOBILE_UA
            }
        )
    }
    var selectedPreset by remember { mutableStateOf("Mobile Chrome") }
    var expanded by remember { mutableStateOf(false) }
    var showCustomUA by remember { mutableStateOf(false) }
    var desktopMode by remember { mutableStateOf(PrefsManager.getDesktopMode(context)) }

    val presets = mapOf(
        "Mobile Chrome" to AutomationService.MOBILE_UA,
        "Desktop Chrome" to AutomationService.DESKTOP_UA,
        "Mobile Firefox" to "Mozilla/5.0 (Android 13; Mobile; rv:109.0) Gecko/118.0 Firefox/119.0",
        "Desktop Firefox" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/120.0",
        "Mobile Safari" to "Mozilla/5.0 (iPhone; CPU iPhone OS 17_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.1 Mobile/15E148 Safari/604.1",
        "Custom" to ""
    )

    val urlFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.bufferedReader()?.readText()?.trim()?.let { text ->
                targetUrl = text.lines().firstOrNull { line -> line.isNotBlank() } ?: ""
                targetAdded = true
                showUrlInput = false
                PrefsManager.saveUrl(context, targetUrl)
                PrefsManager.saveTargetAdded(context, true)
            }
        }
    }

    val jsFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.bufferedReader()?.readText()?.let { text ->
                scriptText = text
                PrefsManager.saveScript(context, scriptText)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

        // Desktop Mode
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Desktop Mode", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(
                        if (desktopMode) "Desktop viewport & user agent" else "Mobile viewport & user agent",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Switch(
                    checked = desktopMode,
                    onCheckedChange = {
                        desktopMode = it
                        PrefsManager.saveDesktopMode(context, it)
                        userAgent = if (it) AutomationService.DESKTOP_UA else AutomationService.MOBILE_UA
                        PrefsManager.saveUserAgent(context, userAgent)
                        selectedPreset = if (it) "Desktop Chrome" else "Mobile Chrome"
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF1976D2))
                )
            }
        }

        // Target URL
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Target URL", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    OutlinedButton(
                        onClick = { showUrlInput = !showUrlInput; targetAdded = false },
                        modifier = Modifier.height(32.dp)
                    ) { Text(if (showUrlInput) "Cancel" else "Add Target", fontSize = 11.sp) }
                }

                if (targetAdded && !showUrlInput) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Target set (URL hidden)", fontSize = 13.sp, color = Color(0xFF4CAF50))
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { targetAdded = false; targetUrl = ""; showUrlInput = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Add New Target") }
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
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { urlFilePicker.launch(arrayOf("text/plain")) },
                            modifier = Modifier.weight(1f)
                        ) { Text("Load .txt", fontSize = 12.sp) }
                        Button(
                            onClick = {
                                if (targetUrl.isNotBlank()) {
                                    targetAdded = true
                                    showUrlInput = false
                                    PrefsManager.saveUrl(context, targetUrl)
                                    PrefsManager.saveTargetAdded(context, true)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                        ) { Text("Set Target", color = Color.White, fontSize = 12.sp) }
                    }
                }
            }
        }

        // JavaScript
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                Text("Inject JavaScript", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Tampermonkey script — JS controls the WebView", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = scriptText,
                    onValueChange = {
                        scriptText = it
                        PrefsManager.saveScript(context, it)
                    },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    label = { Text("// Your script here...") },
                    maxLines = 10
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { jsFilePicker.launch(arrayOf("application/javascript", "text/javascript", "*/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Load .js File") }
            }
        }

        // User Agent
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                Text("User Agent", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(8.dp))

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedPreset,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        label = { Text("Preset") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        presets.keys.forEach { name ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    selectedPreset = name
                                    if (name == "Custom") {
                                        showCustomUA = true
                                    } else {
                                        showCustomUA = false
                                        userAgent = presets[name] ?: ""
                                        PrefsManager.saveUserAgent(context, userAgent)
                                    }
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                if (showCustomUA || selectedPreset == "Custom") {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = userAgent,
                        onValueChange = {
                            userAgent = it
                            PrefsManager.saveUserAgent(context, it)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Custom User Agent") },
                        maxLines = 2
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text("Active UA:", fontSize = 11.sp, color = Color.Gray)
                Text(
                    text = userAgent.take(80) + if (userAgent.length > 80) "..." else "",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }

        // Battery
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                Text("Battery Optimization", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(6.dp))
                Text("Request exemption so the service is not killed.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                Spacer(modifier = Modifier.height(10.dp))
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

        Spacer(modifier = Modifier.height(16.dp))
    }
}
