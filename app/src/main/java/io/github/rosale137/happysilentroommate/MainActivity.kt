package io.github.rosale137.happysilentroommate

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.os.SystemClock
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import io.github.rosale137.happysilentroommate.ui.theme.Coral
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.rosale137.happysilentroommate.alerts.AlertPolicy
import io.github.rosale137.happysilentroommate.alerts.AlertStrength
import io.github.rosale137.happysilentroommate.audio.AudioLevelMeter
import io.github.rosale137.happysilentroommate.audio.averageDb
import io.github.rosale137.happysilentroommate.haptics.Haptics
import io.github.rosale137.happysilentroommate.settings.Settings
import io.github.rosale137.happysilentroommate.settings.SettingsStore
import io.github.rosale137.happysilentroommate.ui.HISTORY_CHUNKS
import io.github.rosale137.happysilentroommate.ui.MonitorScreen
import io.github.rosale137.happysilentroommate.ui.theme.HappysilentroommateTheme
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** Step 2: vibrates when the integrated level goes above the limit; settings are saved. */
class MainActivity : ComponentActivity() {

    private val meter = AudioLevelMeter()
    private val alertPolicy = AlertPolicy()
    private lateinit var haptics: Haptics
    private lateinit var settingsStore: SettingsStore

    private var micGranted by mutableStateOf(false)
    private var micError by mutableStateOf<String?>(null)

    /** One mean-square power per 100 ms, oldest first. Numbers only, never audio. */
    private var powers by mutableStateOf<List<Double>>(emptyList())
    private var settings by mutableStateOf(Settings())

    /** Goes up on every buzz (alerts and test); the screen flashes coral each time it changes. */
    private var flashCount by mutableIntStateOf(0)
    private var flashStrength by mutableStateOf(AlertStrength.Gentle)
    private var alertCount by mutableIntStateOf(0)
    private var lastAlertAt by mutableStateOf<Long?>(null)
    private var lastAlertStrength by mutableStateOf<AlertStrength?>(null)

    /** The test button plays the patterns in turn. */
    private var nextTestStrength by mutableStateOf(AlertStrength.Gentle)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { micGranted = it }

    @OptIn(FlowPreview::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        haptics = Haptics(this)
        settingsStore = SettingsStore(applicationContext)
        micGranted = hasMicPermission()

        // Load saved settings, then save changes once dragging has paused for a moment.
        lifecycleScope.launch {
            settings = settingsStore.load()
            snapshotFlow { settings }
                .drop(1)
                .debounce(SAVE_DELAY_MS)
                .collect { settingsStore.save(it) }
        }

        // Listen only while the screen is visible. Background listening comes in step 3.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                snapshotFlow { micGranted }.collectLatest { granted ->
                    if (!granted) return@collectLatest
                    micError = null
                    powers = emptyList()
                    alertPolicy.reset()
                    meter.powers()
                        .catch { micError = it.message ?: "Microphone error" }
                        .collect { power ->
                            powers = (powers + power).takeLast(HISTORY_CHUNKS)
                            checkForAlert()
                        }
                }
            }
        }

        setContent {
            HappysilentroommateTheme {
                // Visual twin of the vibration: a coral wash over the whole screen that fades out.
                val flash = remember { Animatable(0f) }
                LaunchedEffect(flashCount) {
                    if (flashCount == 0) return@LaunchedEffect
                    flash.snapTo(
                        when (flashStrength) {
                            AlertStrength.Gentle -> 0.3f
                            AlertStrength.Medium -> 0.45f
                            AlertStrength.Strong -> 0.6f
                        },
                    )
                    flash.animateTo(0f, tween(FLASH_FADE_MS))
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
                        micError = micError,
                        powers = powers,
                        settings = settings,
                        onSettingsChange = { settings = it },
                        onRequestMic = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                        nextTestStrength = nextTestStrength,
                        onTestVibration = {
                            buzz(nextTestStrength)
                            nextTestStrength = AlertStrength.entries[
                                (nextTestStrength.ordinal + 1) % AlertStrength.entries.size,
                            ]
                        },
                        alertCount = alertCount,
                        lastAlertAt = lastAlertAt,
                        lastAlertStrength = lastAlertStrength,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }

    private fun checkForAlert() {
        val s = settings
        val strength = alertPolicy.update(
            levelDb = averageDb(powers, s.windowChunks),
            limitDb = s.limitDb.toDouble(),
            hysteresisDb = s.hysteresisDb.toDouble(),
            firstRemindMs = (s.remindSeconds * 1000).toLong(),
            fastestRemindMs = (s.fastestRemindSeconds * 1000).toLong(),
            nowMs = SystemClock.elapsedRealtime(),
        )
        if (strength != null) {
            alertCount++
            lastAlertAt = System.currentTimeMillis()
            lastAlertStrength = strength
            buzz(strength)
        }
    }

    /** Vibrate and flash the screen together; stronger patterns flash brighter. */
    private fun buzz(strength: AlertStrength) {
        haptics.buzz(strength)
        flashStrength = strength
        flashCount++
    }

    private fun hasMicPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        const val SAVE_DELAY_MS = 500L
        const val FLASH_FADE_MS = 1200
    }
}
