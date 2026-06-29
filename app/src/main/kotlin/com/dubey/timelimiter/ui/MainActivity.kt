package com.dubey.timelimiter.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.dubey.timelimiter.DubeyApplication
import com.dubey.timelimiter.data.entity.MonitoredApp
import com.dubey.timelimiter.service.MonitoringService
import com.dubey.timelimiter.ui.dashboard.DashboardScreen
import com.dubey.timelimiter.ui.selector.AppSelectorActivity
import com.dubey.timelimiter.ui.settings.AppSettingsActivity
import com.dubey.timelimiter.ui.settings.GlobalSettingsActivity
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Start monitoring service
        startForegroundService(Intent(this, MonitoringService::class.java))

        val db = (application as DubeyApplication).database

        setContent {
            var apps by remember { mutableStateOf<List<MonitoredApp>>(emptyList()) }

            LaunchedEffect(Unit) {
                db.monitoredAppDao().getAllApps().collect {
                    apps = it
                }
            }

            DashboardScreen(
                apps = apps,
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
}
