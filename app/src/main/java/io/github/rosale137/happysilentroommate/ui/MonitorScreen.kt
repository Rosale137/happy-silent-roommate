package io.github.rosale137.happysilentroommate.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rosale137.happysilentroommate.audio.AudioLevelMeter
import io.github.rosale137.happysilentroommate.audio.averageDb
import io.github.rosale137.happysilentroommate.ui.theme.Amber
import io.github.rosale137.happysilentroommate.ui.theme.Coral
import io.github.rosale137.happysilentroommate.ui.theme.HappysilentroommateTheme
import io.github.rosale137.happysilentroommate.ui.theme.Surface1
import io.github.rosale137.happysilentroommate.ui.theme.Teal
import io.github.rosale137.happysilentroommate.ui.theme.TextMuted
import kotlin.math.roundToInt
import kotlin.math.sin

private const val SCOPE_HELP =
    "The plot shows the last 30 seconds, newest on the right.\n\n" +
        "Thin grey line: the volume in each tenth of a second.\n" +
        "Thick line: the integrated (averaged) volume. It turns coral when it is above the limit.\n\n" +
        "Drag the dashed amber line up or down to set the limit.\n" +
        "Drag the left edge of the shaded band left or right to set the integration time. " +
        "The band is the stretch that is averaged: longer ignores short laughs and coughs, " +
        "shorter reacts faster.\n\n" +
        "Values are dBFS, relative to the loudest the microphone can capture (0 is the maximum). " +
        "The plot shows -80 to -10: quieter values sit on the bottom edge, louder ones on the top edge."

private const val PRIVACY_HELP =
    "Nothing is recorded or stored. Only a level number is calculated, on this phone, " +
        "and it is gone a moment later."

/**
 * Level check: the scope is the whole control surface. [powers] holds one mean-square power per
 * 100 ms, oldest first.
 */
@Composable
fun MonitorScreen(
    micGranted: Boolean,
    micError: String?,
    powers: List<Double>,
    windowSeconds: Float,
    onWindowSecondsChange: (Float) -> Unit,
    limitDb: Float,
    onLimitChange: (Float) -> Unit,
    onRequestMic: () -> Unit,
    onTestVibration: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val windowChunks = (windowSeconds * 1000 / AudioLevelMeter.CHUNK_MS).roundToInt().coerceAtLeast(1)
    val average = averageDb(powers, windowChunks)
    val above = average > limitDb
    val levelColor = if (above) Coral else Teal

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Happy Silent Roommate", color = TextMuted, fontSize = 13.sp)
                Text("Level check", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            InfoButton(title = "How to read this", text = SCOPE_HELP)
        }

        if (!micGranted) {
            Card(spacing = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "The microphone is needed to measure the volume.",
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f),
                    )
                    InfoButton(title = "Privacy", text = PRIVACY_HELP)
                }
                Button(onClick = onRequestMic) { Text("Allow microphone") }
            }
            return@Column
        }

        Card(spacing = 12.dp, padding = 20.dp) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    average.roundToInt().toString(),
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Bold,
                    color = levelColor,
                )
                Text(
                    "  dBFS",
                    color = TextMuted,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
            Text(
                if (above) "Above the limit" else "Below the limit",
                color = levelColor,
                fontWeight = FontWeight.SemiBold,
            )

            ScopeView(
                powers = powers,
                windowSeconds = windowSeconds,
                onWindowSecondsChange = onWindowSecondsChange,
                limitDb = limitDb,
                onLimitChange = onLimitChange,
            )

            if (micError != null) {
                Text("Microphone problem: $micError", color = Coral, fontSize = 13.sp)
            }
        }

        Button(
            onClick = onTestVibration,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Amber,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) { Text("Test vibration", fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun Card(
    spacing: Dp = 4.dp,
    padding: Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Surface1)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) { content() }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F1424, heightDp = 900)
@Composable
fun MonitorScreenPreview() {
    // Quiet talk with one loud burst, 100 ms per sample.
    val powers = List(HISTORY_CHUNKS) { i ->
        val base = 1e-4 * (1.2 + sin(i / 6.0))
        if (i in 150..175) base * 40 else base
    }
    HappysilentroommateTheme {
        MonitorScreen(
            micGranted = true,
            micError = null,
            powers = powers,
            windowSeconds = 4f,
            onWindowSecondsChange = {},
            limitDb = -35f,
            onLimitChange = {},
            onRequestMic = {},
            onTestVibration = {},
        )
    }
}
