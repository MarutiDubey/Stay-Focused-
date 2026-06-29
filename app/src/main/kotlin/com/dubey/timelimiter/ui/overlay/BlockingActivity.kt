package com.dubey.timelimiter.ui.overlay

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.dubey.timelimiter.DubeyApplication
import com.dubey.timelimiter.R
import com.dubey.timelimiter.data.AppDatabase
import kotlinx.coroutines.*

class BlockingActivity : Activity() {

    private var tapCount = 0
    private var blockedPackage = ""
    private lateinit var database: AppDatabase
    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make it full-screen and show over lock screen
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        setContentView(R.layout.activity_blocking)

        database = (application as DubeyApplication).database
        blockedPackage = intent.getStringExtra(EXTRA_BLOCKED_PACKAGE) ?: ""

        val messageText = findViewById<TextView>(R.id.blocking_message)
        val appNameText = findViewById<TextView>(R.id.blocked_app_name)
        val closeButton = findViewById<Button>(R.id.close_button)

        // Load custom blocking message and app name from DB
        activityScope.launch {
            val customMsg = withContext(Dispatchers.IO) {
                database.settingDao().getSetting("blocking_message")?.value
            } ?: getString(R.string.default_blocking_message)

            messageText.text = customMsg

            // Get app display name
            try {
                val appInfo = packageManager.getApplicationInfo(blockedPackage, 0)
                val appName = packageManager.getApplicationLabel(appInfo).toString()
                appNameText.text = "$appName समय समाप्त"
            } catch (e: Exception) {
                appNameText.text = "ऐप समय समाप्त"
            }
        }

        // Secret tap detection — 10 taps grants extra time
        messageText.setOnClickListener {
            tapCount++
            if (tapCount >= 10) {
                tapCount = 0
                grantSecretExtraTime()
            }
        }

        closeButton.setOnClickListener {
            goHome()
        }
    }

    private fun grantSecretExtraTime() {
        activityScope.launch {
            val result = withContext(Dispatchers.IO) {
                val app = database.monitoredAppDao().getApp(blockedPackage)
                if (app != null && !app.secretExtraUsedToday && app.secretExtraMinutes > 0) {
                    // Grant the extra time: extend daily limit by secretExtraMinutes
                    database.monitoredAppDao().updateApp(
                        app.copy(
                            dailyLimitMinutes = app.dailyLimitMinutes + app.secretExtraMinutes,
                            secretExtraUsedToday = true
                        )
                    )
                    "granted:${app.secretExtraMinutes}"
                } else if (app?.secretExtraUsedToday == true) {
                    "already_used"
                } else {
                    "not_available"
                }
            }

            when {
                result.startsWith("granted") -> {
                    val mins = result.split(":")[1]
                    Toast.makeText(
                        this@BlockingActivity,
                        "✅ $mins मिनट अतिरिक्त समय मिला!",
                        Toast.LENGTH_LONG
                    ).show()
                    // Small delay to show the toast, then dismiss
                    delay(1500)
                    finish()
                }
                result == "already_used" -> {
                    Toast.makeText(
                        this@BlockingActivity,
                        "गुप्त समय आज पहले ही उपयोग किया जा चुका है।",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                else -> {
                    Toast.makeText(
                        this@BlockingActivity,
                        "गुप्त समय उपलब्ध नहीं है।",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun goHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }

    override fun onBackPressed() {
        // Disable back button — user must use "बंद करें" button
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }

    companion object {
        const val EXTRA_BLOCKED_PACKAGE = "blocked_package"
    }
}
