package com.dubey.timelimiter.ui

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.dubey.timelimiter.DubeyApplication
import com.dubey.timelimiter.data.entity.MonitoredApp
import com.dubey.timelimiter.service.MonitoringService
import com.dubey.timelimiter.ui.dashboard.DashboardScreen
import com.dubey.timelimiter.ui.selector.AppSelectorActivity
import com.dubey.timelimiter.ui.settings.AppSettingsActivity
import com.dubey.timelimiter.ui.settings.GlobalSettingsActivity

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = (application as DubeyApplication).database

        setContent {
            var apps by remember { mutableStateOf<List<MonitoredApp>>(emptyList()) }

            // Re-check permissions every time the screen resumes (e.g. after the
            // user returns from the system permission settings).
            var hasUsageAccess by remember { mutableStateOf(false) }
            var hasOverlay by remember { mutableStateOf(false) }

            fun refreshPermissions() {
                hasUsageAccess = hasUsageStatsPermission()
                hasOverlay = Settings.canDrawOverlays(this@MainActivity)
                // Only run the monitor once it can actually see app usage.
                if (hasUsageAccess) {
                    startForegroundService(Intent(this@MainActivity, MonitoringService::class.java))
                }
            }

            LaunchedEffect(Unit) {
                refreshPermissions()
                db.monitoredAppDao().getAllApps().collect {
                    apps = it
                }
            }

            // Surface a warning whenever a required permission is missing —
            // without these, blocking silently does nothing.
            val permissionsMissing = !hasUsageAccess || !hasOverlay

            DashboardScreen(
                apps = apps,
                permissionsMissing = permissionsMissing,
                hasUsageAccess = hasUsageAccess,
                hasOverlay = hasOverlay,
                onFixUsageAccess = {
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                },
                onFixOverlay = {
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                    )
                },
                onAddApp = {
                    startActivity(Intent(this, AppSelectorActivity::class.java))
                },
                onSettings = {
                    startActivity(Intent(this, GlobalSettingsActivity::class.java))
                },
                onEditApp = { app ->
                    val intent = Intent(this, AppSettingsActivity::class.java).apply {
                        putExtra("package_name", app.packageName)
                    }
                    startActivity(intent)
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // Returning from system settings: restart the service if usage access
        // was just granted. (Compose state above also refreshes on recomposition.)
        if (hasUsageStatsPermission()) {
            startForegroundService(Intent(this, MonitoringService::class.java))
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
