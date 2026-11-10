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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.rosale137.happysilentroommate.audio.AudioLevelMeter
import io.github.rosale137.happysilentroommate.audio.FLOOR_DBFS
import io.github.rosale137.happysilentroommate.audio.PowerAverager
import io.github.rosale137.happysilentroommate.audio.powerToDb
import io.github.rosale137.happysilentroommate.haptics.Haptics
import io.github.rosale137.happysilentroommate.ui.theme.Amber
import io.github.rosale137.happysilentroommate.ui.theme.Coral
import io.github.rosale137.happysilentroommate.ui.theme.HappysilentroommateTheme
import io.github.rosale137.happysilentroommate.ui.theme.Surface1
import io.github.rosale137.happysilentroommate.ui.theme.Teal
import io.github.rosale137.happysilentroommate.ui.theme.TextMuted
import io.github.rosale137.happysilentroommate.ui.theme.Track
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Step 1: live level, an adjustable limit, and a button to test the vibration. */
class MainActivity : ComponentActivity() {

    private val meter = AudioLevelMeter()
    private val averager = PowerAverager(size = AVERAGE_SECONDS * 1000 / AudioLevelMeter.CHUNK_MS)
    private lateinit var haptics: Haptics

    private var micGranted by mutableStateOf(false)
    private var micError by mutableStateOf<String?>(null)
    private var instantDb by mutableDoubleStateOf(FLOOR_DBFS)
    private var averageDb by mutableDoubleStateOf(FLOOR_DBFS)
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
                    averager.reset()
                    micError = null
                    meter.powers()
                        .catch { micError = it.message ?: "Microphone error" }
                        .collect { power ->
                            instantDb = powerToDb(power)
                            averageDb = averager.add(power)
                        }
                }
            }
        }

        setContent {
            HappysilentroommateTheme {
                Scaffold(modifier = Modifier.fillMaxSize(), containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
                    MonitorScreen(
                        micGranted = micGranted,
                        micError = micError,
                        instantDb = instantDb,
                        averageDb = averageDb,
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
        const val AVERAGE_SECONDS = 8
        const val DEFAULT_LIMIT_DB = -35f
    }
}

private const val METER_MIN_DB = -90f
private const val METER_MAX_DB = 0f

private fun dbToFraction(db: Float) =
    ((db - METER_MIN_DB) / (METER_MAX_DB - METER_MIN_DB)).coerceIn(0f, 1f)

@Composable
fun MonitorScreen(
    micGranted: Boolean,
    micError: String?,
    instantDb: Double,
    averageDb: Double,
    limitDb: Float,
    onLimitChange: (Float) -> Unit,
    onRequestMic: () -> Unit,
    onTestVibration: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val above = averageDb > limitDb
    val levelColor = if (above) Coral else Teal

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Happy Silent Roommate", color = TextMuted, fontSize = 13.sp)
        Text("Level check", fontSize = 24.sp, fontWeight = FontWeight.Bold)

        if (!micGranted) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Surface1)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("The microphone is needed to measure the volume.", fontSize = 16.sp)
                Text(
                    "Nothing is recorded or stored. Only a level number is calculated, on this phone.",
                    color = TextMuted,
                    fontSize = 13.sp,
                )
                Button(onClick = onRequestMic) { Text("Allow microphone") }
            }
            return@Column
        }

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Surface1)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                Text(
                    averageDb.roundToInt().toString(),
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Bold,
                    color = levelColor,
                )
                Text("  dBFS (avg 8 s)", color = TextMuted, fontSize = 14.sp, modifier = Modifier.padding(bottom = 10.dp))
            }

            LevelBar(averageDb = averageDb.toFloat(), limitDb = limitDb, color = levelColor)

            Text(
                if (above) "Above the limit" else "Below the limit",
                color = levelColor,
                fontWeight = FontWeight.SemiBold,
            )
            Text("Right now: ${instantDb.roundToInt()} dBFS", color = TextMuted, fontSize = 13.sp)
            if (micError != null) {
                Text("Microphone problem: $micError", color = Coral, fontSize = 13.sp)
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Surface1)
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Limit", fontWeight = FontWeight.SemiBold)
                Text("${limitDb.roundToInt()} dBFS", color = Amber, fontWeight = FontWeight.SemiBold)
            }
            Slider(
                value = limitDb,
                onValueChange = onLimitChange,
                valueRange = -80f..-10f,
                colors = SliderDefaults.colors(thumbColor = Amber, activeTrackColor = Amber),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("strict", color = TextMuted, fontSize = 12.sp)
                Text("relaxed", color = TextMuted, fontSize = 12.sp)
            }
        }

        Button(
            onClick = onTestVibration,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = MaterialTheme.colorScheme.onPrimary),
        ) { Text("Test vibration", fontWeight = FontWeight.Bold) }

        Text(
            "Tip: put the phone where it usually lies, talk normally, and move the limit until the label flips just above your normal voice.",
            color = TextMuted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun LevelBar(averageDb: Float, limitDb: Float, color: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(28.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Track),
        )
        Box(
            Modifier
                .fillMaxWidth(dbToFraction(averageDb))
                .padding(top = 8.dp)
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(color),
        )
        // Limit marker. A weight-based Row keeps it proportional to the bar width.
        Row(Modifier.fillMaxSize()) {
            val f = dbToFraction(limitDb).coerceIn(0.001f, 0.999f)
            Box(Modifier.weight(f))
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(Amber),
            )
            Box(Modifier.weight(1f - f))
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F1424)
@Composable
fun MonitorScreenPreview() {
    HappysilentroommateTheme {
        MonitorScreen(
            micGranted = true,
            micError = null,
            instantDb = -42.0,
            averageDb = -47.0,
            limitDb = -35f,
            onLimitChange = {},
            onRequestMic = {},
            onTestVibration = {},
        )
    }
}
