package com.dubey.timelimiter.ui.settings

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.dubey.timelimiter.DubeyApplication
import com.dubey.timelimiter.data.entity.Setting
import com.dubey.timelimiter.utils.SecurityUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GlobalSettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = (application as DubeyApplication).database

        setContent {
            var blockingMessage by remember {
                mutableStateOf("आज का समय समाप्त हो गया है।\nकृपया मोबाइल का उपयोग बंद करें।")
            }
            var showChangePinDialog by remember { mutableStateOf(false) }
            var hasUsageAccess by remember { mutableStateOf(false) }
            var hasOverlay by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    val msg = db.settingDao().getSetting("blocking_message")
                    if (msg != null) blockingMessage = msg.value
                }
                hasUsageAccess = hasUsageStatsPermission()
                hasOverlay = Settings.canDrawOverlays(this@GlobalSettingsActivity)
            }

            GlobalSettingsScreen(
                blockingMessage = blockingMessage,
                hasUsageAccess = hasUsageAccess,
                hasOverlay = hasOverlay,
                onMessageChange = { blockingMessage = it },
                onSaveMessage = {
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            db.settingDao().saveSetting(Setting("blocking_message", blockingMessage))
                        }
                        Toast.makeText(this@GlobalSettingsActivity, "संदेश सहेजा गया ✓", Toast.LENGTH_SHORT).show()
                    }
                },
                onOpenUsageSettings = {
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                },
                onOpenOverlaySettings = {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                },
                onChangePinClick = { showChangePinDialog = true },
                onBack = { finish() }
            )

            // Change PIN Dialog
            if (showChangePinDialog) {
                ChangePinDialog(
                    onDismiss = { showChangePinDialog = false },
                    onConfirm = { oldPin, newPin ->
                        lifecycleScope.launch {
                            val valid = withContext(Dispatchers.IO) {
                                val hash = db.settingDao().getSetting("pin_hash")?.value ?: return@withContext false
                                val saltStr = db.settingDao().getSetting("pin_salt")?.value ?: return@withContext false
                                val salt = saltStr.split(",").map { it.toByte() }.toByteArray()
                                SecurityUtils.verifyPin(oldPin, hash, salt)
                            }
                            if (valid) {
                                withContext(Dispatchers.IO) {
                                    val (newHash, newSalt) = SecurityUtils.hashPin(newPin)
                                    db.settingDao().saveSetting(Setting("pin_hash", newHash))
                                    db.settingDao().saveSetting(
                                        Setting("pin_salt", newSalt.joinToString(",") { it.toString() })
                                    )
                                }
                                showChangePinDialog = false
                                Toast.makeText(this@GlobalSettingsActivity, "PIN बदला गया ✓", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this@GlobalSettingsActivity, "पुराना PIN गलत है", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSettingsScreen(
    blockingMessage: String,
    hasUsageAccess: Boolean,
    hasOverlay: Boolean,
    onMessageChange: (String) -> Unit,
    onSaveMessage: () -> Unit,
    onOpenUsageSettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onChangePinClick: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("सेटिंग्स") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Permissions Status Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("अनुमतियाँ", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))

                    PermissionRow(
                        label = "Usage Access",
                        granted = hasUsageAccess,
                        onFix = onOpenUsageSettings
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    PermissionRow(
                        label = "Display Over Apps",
                        granted = hasOverlay,
                        onFix = onOpenOverlaySettings
                    )
                }
            }

            // Blocking Message Editor
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("ब्लॉकिंग संदेश", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("यह संदेश तब दिखेगा जब समय सीमा समाप्त हो:", fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = blockingMessage,
                        onValueChange = onMessageChange,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 6,
                        label = { Text("संदेश") }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onSaveMessage,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("संदेश सहेजें")
                    }
                }
            }

            // Change PIN
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("सुरक्षा", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onChangePinClick,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("PIN बदलें")
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionRow(label: String, granted: Boolean, onFix: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (granted) "✅" else "❌",
            fontSize = 18.sp
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = if (granted) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.error
        )
        if (!granted) {
            TextButton(onClick = onFix) {
                Text("ठीक करें")
            }
        }
    }
}

@Composable
fun ChangePinDialog(
    onDismiss: () -> Unit,
    onConfirm: (oldPin: String, newPin: String) -> Unit
) {
    var oldPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("PIN बदलें") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (error.isNotEmpty()) {
                    Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
                OutlinedTextField(
                    value = oldPin,
                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) oldPin = it },
                    label = { Text("पुराना PIN") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) newPin = it },
                    label = { Text("नया PIN") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) confirmPin = it },
                    label = { Text("नया PIN दोबारा") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    oldPin.length < 4 -> error = "पुराना PIN 4 अंकों का होना चाहिए"
                    newPin.length < 4 -> error = "नया PIN 4 अंकों का होना चाहिए"
                    newPin != confirmPin -> error = "नया PIN मेल नहीं खाता"
                    else -> onConfirm(oldPin, newPin)
                }
            }) {
                Text("बदलें")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("रद्द करें") }
        }
    )
}
