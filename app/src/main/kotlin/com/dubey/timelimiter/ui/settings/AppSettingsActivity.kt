package com.dubey.timelimiter.ui.settings

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.dubey.timelimiter.DubeyApplication
import com.dubey.timelimiter.data.entity.MonitoredApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val packageName = intent.getStringExtra("package_name") ?: run {
            finish()
            return
        }

        val db = (application as DubeyApplication).database

        setContent {
            var app by remember { mutableStateOf<MonitoredApp?>(null) }
            var dailyLimit by remember { mutableStateOf(120) }
            var secretExtra by remember { mutableStateOf(30) }

            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    val loadedApp = db.monitoredAppDao().getApp(packageName)
                    app = loadedApp
                    dailyLimit = loadedApp?.dailyLimitMinutes ?: 120
                    secretExtra = loadedApp?.secretExtraMinutes ?: 30
                }
            }

            app?.let {
                AppSettingsScreen(
                    app = it,
                    dailyLimit = dailyLimit,
                    secretExtra = secretExtra,
                    onDailyLimitChange = { dailyLimit = it },
                    onSecretExtraChange = { secretExtra = it },
                    onSave = {
                        lifecycleScope.launch {
                            withContext(Dispatchers.IO) {
                                db.monitoredAppDao().updateApp(
                                    it.copy(
                                        dailyLimitMinutes = dailyLimit,
                                        secretExtraMinutes = secretExtra
                                    )
                                )
                            }
                            finish()
                        }
                    },
                    onDelete = {
                        lifecycleScope.launch {
                            withContext(Dispatchers.IO) {
                                db.monitoredAppDao().deleteApp(it)
                            }
                            finish()
                        }
                    },
                    onCancel = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(
    app: MonitoredApp,
    dailyLimit: Int,
    secretExtra: Int,
    onDailyLimitChange: (Int) -> Unit,
    onSecretExtraChange: (Int) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(app.appName) },
                navigationIcon = {
                    TextButton(onClick = onCancel) {
                        Text("Cancel")
                    }
                },
                actions = {
                    TextButton(onClick = onSave) {
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Daily Limit
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("दैनिक समय सीमा", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${dailyLimit} मिनट", fontSize = 24.sp, modifier = Modifier.weight(1f))
                        Column {
                            Button(onClick = { onDailyLimitChange(dailyLimit + 15) }) {
                                Text("+15")
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(onClick = { if (dailyLimit > 15) onDailyLimitChange(dailyLimit - 15) }) {
                                Text("-15")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("${dailyLimit / 60} घंटे ${dailyLimit % 60} मिनट")
                }
            }

            // Secret Extra Time
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("गुप्त अतिरिक्त समय", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${secretExtra} मिनट", fontSize = 24.sp, modifier = Modifier.weight(1f))
                        Column {
                            Button(onClick = { onSecretExtraChange(secretExtra + 10) }) {
                                Text("+10")
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(onClick = { if (secretExtra > 10) onSecretExtraChange(secretExtra - 10) }) {
                                Text("-10")
                            }
                        }
                    }
                }
            }

            // Today's Usage
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("आज का उपयोग", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("${app.todayUsageMinutes} मिनट", fontSize = 24.sp)
                    if (app.secretExtraUsedToday) {
                        Text("गुप्त समय उपयोग किया गया ✓", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Delete button
            Button(
                onClick = onDelete,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("निगरानी से हटाएं")
            }
        }
    }
}
