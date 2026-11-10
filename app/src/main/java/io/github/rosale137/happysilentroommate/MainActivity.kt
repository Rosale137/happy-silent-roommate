package io.github.rosale137.happysilentroommate

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.rosale137.happysilentroommate.audio.AudioLevelMeter
import io.github.rosale137.happysilentroommate.haptics.Haptics
import io.github.rosale137.happysilentroommate.ui.HISTORY_CHUNKS
import io.github.rosale137.happysilentroommate.ui.MonitorScreen
import io.github.rosale137.happysilentroommate.ui.theme.HappysilentroommateTheme
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Step 1b: scope of the last 30 s, adjustable integration time and limit, test vibration. */
class MainActivity : ComponentActivity() {

    private val meter = AudioLevelMeter()
    private lateinit var haptics: Haptics

    private var micGranted by mutableStateOf(false)
    private var micError by mutableStateOf<String?>(null)

    /** One mean-square power per 100 ms, oldest first. Numbers only, never audio. */
    private var powers by mutableStateOf<List<Double>>(emptyList())
    private var windowSeconds by mutableFloatStateOf(DEFAULT_WINDOW_SECONDS)
    private var limitDb by mutableFloatStateOf(DEFAULT_LIMIT_DB)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { micGranted = it }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        haptics = Haptics(this)
        micGranted = hasMicPermission()

        // Listen only while the screen is visible. Background listening comes in step 3.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                snapshotFlow { micGranted }.collectLatest { granted ->
                    if (!granted) return@collectLatest
                    micError = null
                    powers = emptyList()
                    meter.powers()
                        .catch { micError = it.message ?: "Microphone error" }
                        .collect { power -> powers = (powers + power).takeLast(HISTORY_CHUNKS) }
                }
            }
        }

        setContent {
            HappysilentroommateTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background,
                ) { innerPadding ->
                    MonitorScreen(
                        micGranted = micGranted,
                        micError = micError,
                        powers = powers,
                        windowSeconds = windowSeconds,
                        onWindowSecondsChange = { windowSeconds = it },
                        limitDb = limitDb,
                        onLimitChange = { limitDb = it },
                        onRequestMic = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                        onTestVibration = { haptics.buzz() },
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }

    private fun hasMicPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        const val DEFAULT_WINDOW_SECONDS = 4f
        const val DEFAULT_LIMIT_DB = -35f
    }
}
