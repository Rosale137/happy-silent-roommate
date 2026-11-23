package io.github.rosale137.happysilentroommate

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.rosale137.happysilentroommate.alerts.AlertStrength
import io.github.rosale137.happysilentroommate.monitor.MonitorEngine
import io.github.rosale137.happysilentroommate.monitor.MonitorService
import io.github.rosale137.happysilentroommate.monitor.ServiceStatus
import io.github.rosale137.happysilentroommate.settings.AutoMode
import io.github.rosale137.happysilentroommate.settings.Settings
import io.github.rosale137.happysilentroommate.ui.MonitorScreen
import io.github.rosale137.happysilentroommate.ui.theme.Coral
import io.github.rosale137.happysilentroommate.ui.theme.HappysilentroommateTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The screen shows and adjusts what [MonitorEngine] does. While the app is visible and the
 * service is not listening, the screen listens itself. [MonitorService] keeps going with the
 * screen off, started by hand or by the quiet time.
 */
class MainActivity : ComponentActivity() {

    private var micGranted by mutableStateOf(false)

    /** The test button plays the patterns in turn. */
    private var nextTestStrength by mutableStateOf(AlertStrength.Gentle)

    private val micPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { micGranted = it }

    /** What to do once the notification permission was answered; it goes ahead either way. */
    private var afterNotificationPermission: (() -> Unit)? = null
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            afterNotificationPermission?.invoke()
            afterNotificationPermission = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        MonitorEngine.init(this)
        micGranted = hasPermission(Manifest.permission.RECORD_AUDIO)
        handleIntent(intent)

        // While visible and the service is not listening, the screen listens itself.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(snapshotFlow { micGranted }, MonitorEngine.serviceStatus) { granted, status ->
                    granted && !status.listening
                }.collectLatest { listenHere -> if (listenHere) MonitorEngine.listen() }
            }
        }

        // Opening the app switches standby on again if it is not running (e.g. after a restart).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                val s = MonitorEngine.awaitSettings()
                if (micGranted && s.autoEnabled && s.autoMode == AutoMode.Standby &&
                    MonitorEngine.serviceStatus.value == ServiceStatus.Off
                ) {
                    MonitorService.startAuto(this@MainActivity)
                }
            }
        }

        setContent {
            HappysilentroommateTheme {
                val state by MonitorEngine.state.collectAsState()
                val settings by MonitorEngine.settings.collectAsState()
                val status by MonitorEngine.serviceStatus.collectAsState()

                // Visual twin of the vibration: a coral wash over the whole screen that fades out.
                val flash = remember { Animatable(0f) }
                LaunchedEffect(Unit) {
                    MonitorEngine.buzzes.collectLatest { strength ->
                        flash.snapTo(
                            when (strength) {
                                AlertStrength.Gentle -> 0.3f
                                AlertStrength.Medium -> 0.45f
                                AlertStrength.Strong -> 0.6f
                            },
                        )
                        flash.animateTo(0f, tween(FLASH_FADE_MS))
                    }
                }
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            drawContent()
                            if (flash.value > 0f) drawRect(Coral.copy(alpha = flash.value))
                        },
                    containerColor = MaterialTheme.colorScheme.background,
                ) { innerPadding ->
                    MonitorScreen(
                        micGranted = micGranted,
                        state = state,
                        settings = settings,
                        onSettingsChange = ::changeSettings,
                        onRequestMic = { micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                        status = status,
                        onStartListening = { withNotificationPermission { MonitorService.startManual(this) } },
                        onStopListening = { MonitorService.stop(this) },
                        onStopForTonight = { MonitorService.stopForTonight(this) },
                        nextTestStrength = nextTestStrength,
                        onTestVibration = {
                            MonitorEngine.testBuzz(nextTestStrength)
                            nextTestStrength = AlertStrength.entries[
                                (nextTestStrength.ordinal + 1) % AlertStrength.entries.size,
                            ]
                        },
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** The reminder notification opens the app with this action: start the quiet time now. */
    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ACTION_START_QUIET_TIME && micGranted) {
            MonitorService.startAuto(this)
            intent.action = null
        }
    }

    private fun changeSettings(new: Settings) {
        val old = MonitorEngine.settings.value
        MonitorEngine.updateSettings(new)
        val quietTimeChanged = new.autoEnabled != old.autoEnabled || new.autoMode != old.autoMode
        if (!quietTimeChanged || !new.autoEnabled || !micGranted) return
        // Both modes need notifications; standby starts its service now, while the app is open.
        withNotificationPermission {
            if (new.autoMode == AutoMode.Standby &&
                MonitorEngine.serviceStatus.value == ServiceStatus.Off
            ) {
                MonitorService.startAuto(this)
            }
        }
    }

    private fun withNotificationPermission(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            afterNotificationPermission = action
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            action()
        }
    }

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val ACTION_START_QUIET_TIME = "io.github.rosale137.happysilentroommate.START_QUIET_TIME"
        private const val FLASH_FADE_MS = 1200
    }
}
