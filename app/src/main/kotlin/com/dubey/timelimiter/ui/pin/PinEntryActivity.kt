package com.dubey.timelimiter.ui.pin

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dubey.timelimiter.DubeyApplication
import com.dubey.timelimiter.data.entity.Setting
import com.dubey.timelimiter.ui.MainActivity
import com.dubey.timelimiter.utils.SecurityUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PinEntryActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = (application as DubeyApplication).database

        setContent {
            var screenMode by remember { mutableStateOf<PinMode>(PinMode.Loading) }
            val scope = rememberCoroutineScope()

            // Determine mode on launch: setup or unlock
            LaunchedEffect(Unit) {
                val pinSetting = withContext(Dispatchers.IO) {
                    db.settingDao().getSetting("pin_hash")
                }
                screenMode = if (pinSetting == null) PinMode.Setup else PinMode.Unlock
            }

            when (screenMode) {
                PinMode.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A2E)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFFE040FB))
                    }
                }

                PinMode.Setup -> {
                    SetupPinScreen(
                        onPinSet = { newPin ->
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    val (hash, salt) = SecurityUtils.hashPin(newPin)
                                    db.settingDao().saveSetting(Setting("pin_hash", hash))
                                    db.settingDao().saveSetting(
                                        Setting("pin_salt", salt.joinToString(",") { it.toString() })
                                    )
                                    // Set default recovery PIN as "000000"
                                    val (recHash, recSalt) = SecurityUtils.hashPin("000000")
                                    db.settingDao().saveSetting(Setting("recovery_pin_hash", recHash))
                                    db.settingDao().saveSetting(
                                        Setting("recovery_pin_salt", recSalt.joinToString(",") { it.toString() })
                                    )
                                    // Save default blocking message
                                    db.settingDao().saveSetting(
                                        Setting("blocking_message", "आज का समय समाप्त हो गया है।\nकृपया मोबाइल का उपयोग बंद करें।")
                                    )
                                }
                                startActivity(Intent(this@PinEntryActivity, MainActivity::class.java))
                                finish()
                            }
                        }
                    )
                }

                PinMode.Unlock -> {
                    PinEntryScreen(
                        onPinCorrect = {
                            startActivity(Intent(this@PinEntryActivity, MainActivity::class.java))
                            finish()
                        },
                        onRecoveryPin = { recoveryPin ->
                            scope.launch {
                                val isValid = withContext(Dispatchers.IO) {
                                    val recHash = db.settingDao().getSetting("recovery_pin_hash")?.value ?: return@withContext false
                                    val recSaltStr = db.settingDao().getSetting("recovery_pin_salt")?.value ?: return@withContext false
                                    val salt = recSaltStr.split(",").map { it.toByte() }.toByteArray()
                                    SecurityUtils.verifyPin(recoveryPin, recHash, salt)
                                }
                                if (isValid) {
                                    // Reset PIN — go to setup
                                    withContext(Dispatchers.IO) {
                                        db.settingDao().deleteSetting("pin_hash")
                                        db.settingDao().deleteSetting("pin_salt")
                                    }
                                    screenMode = PinMode.Setup
                                }
                            }
                        },
                        checkPin = { enteredPin ->
                            withContext(Dispatchers.IO) {
                                val hash = db.settingDao().getSetting("pin_hash")?.value ?: return@withContext false
                                val saltStr = db.settingDao().getSetting("pin_salt")?.value ?: return@withContext false
                                val salt = saltStr.split(",").map { it.toByte() }.toByteArray()
                                SecurityUtils.verifyPin(enteredPin, hash, salt)
                            }
                        }
                    )
                }
            }
        }
    }
}

sealed class PinMode {
    object Loading : PinMode()
    object Setup : PinMode()
    object Unlock : PinMode()
}

@Composable
fun SetupPinScreen(onPinSet: (String) -> Unit) {
    var step by remember { mutableStateOf(1) }
    var firstPin by remember { mutableStateOf("") }
    var secondPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    val currentPin = if (step == 1) firstPin else secondPin
    val onDigit: (String) -> Unit = { digit ->
        if (step == 1) {
            if (firstPin.length < 4) firstPin += digit
            if (firstPin.length == 4) step = 2
        } else {
            if (secondPin.length < 4) secondPin += digit
            if (secondPin.length == 4) {
                if (firstPin == secondPin) {
                    onPinSet(firstPin)
                } else {
                    error = "PIN मेल नहीं खाते। फिर से कोशिश करें।"
                    firstPin = ""; secondPin = ""; step = 1
                }
            }
        }
    }
    val onClear: () -> Unit = {
        error = ""
        if (step == 1) firstPin = "" else secondPin = ""
    }

    PinKeypad(
        title = if (step == 1) "नया PIN सेट करें" else "PIN दोबारा दर्ज करें",
        subtitle = if (step == 1) "4 अंकों का PIN चुनें" else "PIN की पुष्टि करें",
        pin = currentPin,
        error = error,
        onDigit = onDigit,
        onClear = onClear
    )
}

@Composable
fun PinEntryScreen(
    onPinCorrect: () -> Unit,
    onRecoveryPin: (String) -> Unit,
    checkPin: suspend (String) -> Boolean
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var showRecovery by remember { mutableStateOf(false) }
    var recoveryPin by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    if (showRecovery) {
        PinKeypad(
            title = "Recovery PIN",
            subtitle = "6 अंकों का recovery PIN दर्ज करें",
            pin = recoveryPin,
            error = error,
            maxLength = 6,
            onDigit = { digit ->
                if (recoveryPin.length < 6) recoveryPin += digit
                if (recoveryPin.length == 6) {
                    onRecoveryPin(recoveryPin)
                    recoveryPin = ""
                }
            },
            onClear = { recoveryPin = ""; error = "" },
            extraButton = {
                TextButton(onClick = { showRecovery = false; error = "" }) {
                    Text("वापस", color = Color(0xFFB0B0B0))
                }
            }
        )
    } else {
        PinKeypad(
            title = "Dubey",
            subtitle = "PIN दर्ज करें",
            pin = pin,
            error = error,
            onDigit = { digit ->
                error = ""
                if (pin.length < 4) pin += digit
                if (pin.length == 4) {
                    scope.launch {
                        val valid = checkPin(pin)
                        if (valid) {
                            onPinCorrect()
                        } else {
                            error = "गलत PIN"
                            pin = ""
                        }
                    }
                }
            },
            onClear = { pin = ""; error = "" },
            extraButton = {
                TextButton(onClick = { showRecovery = true; pin = ""; error = "" }) {
                    Text("PIN भूल गए?", color = Color(0xFFB0B0B0))
                }
            }
        )
    }
}

@Composable
fun PinKeypad(
    title: String,
    subtitle: String,
    pin: String,
    error: String,
    maxLength: Int = 4,
    onDigit: (String) -> Unit,
    onClear: () -> Unit,
    extraButton: (@Composable () -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF1A1A2E), Color(0xFF16213E))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE040FB),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                fontSize = 16.sp,
                color = Color(0xFFB0B0B0),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(40.dp))

            // PIN dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                repeat(maxLength) { idx ->
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(
                                if (idx < pin.length) Color(0xFFE040FB)
                                else Color(0xFF404060)
                            )
                    )
                }
            }

            if (error.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = error,
                    color = Color(0xFFFF5252),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Number pad: 1–9
            for (row in 0..2) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    for (col in 1..3) {
                        val num = (row * 3 + col).toString()
                        PinButton(label = num) { onDigit(num) }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Bottom row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (extraButton != null) {
                    Box(modifier = Modifier.size(80.dp), contentAlignment = Alignment.Center) {
                        extraButton()
                    }
                } else {
                    Spacer(modifier = Modifier.size(80.dp))
                }
                PinButton(label = "0") { onDigit("0") }
                Box(modifier = Modifier.size(80.dp), contentAlignment = Alignment.Center) {
                    TextButton(onClick = onClear) {
                        Text("⌫", fontSize = 24.sp, color = Color(0xFFE040FB))
                    }
                }
            }
        }
    }
}

@Composable
fun PinButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(80.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF2D2D4E),
            contentColor = Color.White
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
    ) {
        Text(
            text = label,
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
