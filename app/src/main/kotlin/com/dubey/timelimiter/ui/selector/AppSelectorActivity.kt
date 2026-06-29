package com.dubey.timelimiter.ui.selector

import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.lifecycleScope
import com.dubey.timelimiter.DubeyApplication
import com.dubey.timelimiter.data.entity.MonitoredApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppInfo(
    val packageName: String,
    val appName: String
)

class AppSelectorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = (application as DubeyApplication).database

        setContent {
            var allApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
            var selectedApps by remember { mutableStateOf<Set<String>>(emptySet()) }
            var isLoading by remember { mutableStateOf(true) }

            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    // Load installed user apps
                    val pm = packageManager
                    val installedApps = pm.getInstalledApplications(0)
                        .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
                        .filter { it.packageName != packageName }
                        .map {
                            AppInfo(
                                packageName = it.packageName,
                                appName = pm.getApplicationLabel(it).toString()
                            )
                        }
                        .sortedBy { it.appName }

                    // Use .first() to get a one-shot snapshot from Flow (not blocking loop)
                    val existingMonitored = db.monitoredAppDao().getAllApps().first()
                    val existingPkgs = existingMonitored.map { it.packageName }.toSet()

                    withContext(Dispatchers.Main) {
                        allApps = installedApps
                        selectedApps = existingPkgs
                        isLoading = false
                    }
                }
            }

            AppSelectorScreen(
                apps = allApps,
                selectedApps = selectedApps,
                isLoading = isLoading,
                onToggleApp = { app ->
                    selectedApps = if (app.packageName in selectedApps) {
                        selectedApps - app.packageName
                    } else {
                        selectedApps + app.packageName
                    }
                },
                onSave = {
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            // Get currently monitored apps
                            val existingMonitored = db.monitoredAppDao().getAllAppsOnce()
                            val existingPkgs = existingMonitored.map { it.packageName }.toSet()
                            val pm = packageManager

                            // Add newly selected apps with defaults
                            val newlySelected = selectedApps - existingPkgs
                            for (pkg in newlySelected) {
                                val appInfo = allApps.find { it.packageName == pkg }
                                if (appInfo != null) {
                                    db.monitoredAppDao().insertApp(
                                        MonitoredApp(
                                            packageName = appInfo.packageName,
                                            appName = appInfo.appName,
                                            dailyLimitMinutes = 120, // Default 2 hours
                                            secretExtraMinutes = 30  // Default 30 min bonus
                                        )
                                    )
                                }
                            }

                            // Remove deselected apps
                            val deselected = existingPkgs - selectedApps
                            for (pkg in deselected) {
                                val app = existingMonitored.find { it.packageName == pkg }
                                if (app != null) {
                                    db.monitoredAppDao().deleteApp(app)
                                }
                            }
                        }
                        finish()
                    }
                },
                onCancel = { finish() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectorScreen(
    apps: List<AppInfo>,
    selectedApps: Set<String>,
    isLoading: Boolean,
    onToggleApp: (AppInfo) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ऐप चुनें") },
                navigationIcon = {
                    TextButton(onClick = onCancel) { Text("रद्द करें") }
                },
                actions = {
                    TextButton(onClick = onSave) {
                        Text("सहेजें", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(8.dp)
            ) {
                items(apps, key = { it.packageName }) { app ->
                    AppSelectItem(
                        app = app,
                        isSelected = app.packageName in selectedApps,
                        onToggle = { onToggleApp(app) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectItem(app: AppInfo, isSelected: Boolean, onToggle: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val pm = context.packageManager

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        onClick = onToggle,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val iconBitmap = remember(app.packageName) {
                try {
                    pm.getApplicationIcon(app.packageName).toBitmap(48, 48).asImageBitmap()
                } catch (e: Exception) {
                    null
                }
            }

            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp)
                )
            } else {
                Box(modifier = Modifier.size(40.dp))
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = app.appName,
                modifier = Modifier.weight(1f),
                fontSize = 16.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            )

            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
