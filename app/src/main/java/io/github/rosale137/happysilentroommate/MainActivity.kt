package io.github.rosale137.happysilentroommate

import android.Manifest
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
import io.github.rosale137.happysilentroommate.ui.MonitorScreen
import io.github.rosale137.happysilentroommate.ui.theme.Coral
import io.github.rosale137.happysilentroommate.ui.theme.HappysilentroommateTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Step 3: the screen shows and adjusts what [MonitorEngine] does. While the app is visible it
 * listens itself; "Start listening" hands the microphone to [MonitorService], which keeps going
 * with the screen off.
 */
class MainActivity : ComponentActivity() {

    private var micGranted by mutableStateOf(false)

    /** The test button plays the patterns in turn. */
    private var nextTestStrength by mutableStateOf(AlertStrength.Gentle)

    private val micPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { micGranted = it }

    /** Asked once before the first background start; the service starts either way. */
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { MonitorService.start(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        MonitorEngine.init(this)
        micGranted = hasPermission(Manifest.permission.RECORD_AUDIO)

        // While visible and the service is not running, the screen listens itself.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(snapshotFlow { micGranted }, MonitorEngine.serviceRunning) { granted, running ->
                    granted && !running
                }.collectLatest { listenHere -> if (listenHere) MonitorEngine.listen() }
            }
        }

        setContent {
            HappysilentroommateTheme {
                val state by MonitorEngine.state.collectAsState()
                val settings by MonitorEngine.settings.collectAsState()
                val listening by MonitorEngine.serviceRunning.collectAsState()

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
                        onSettingsChange = MonitorEngine::updateSettings,
                        onRequestMic = { micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                        listening = listening,
                        onStartListening = ::startListening,
                        onStopListening = { MonitorService.stop(this) },
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

    private fun startListening() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            MonitorService.start(this)
        }
    }

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val FLASH_FADE_MS = 1200
    }
}
