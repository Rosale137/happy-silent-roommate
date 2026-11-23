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
import androidx.compose.foundation.Canvas
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import io.github.rosale137.happysilentroommate.ui.theme.OnAmber
import androidx.compose.material3.SliderDefaults
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
import io.github.rosale137.happysilentroommate.audio.averageDb
import io.github.rosale137.happysilentroommate.settings.Settings
import io.github.rosale137.happysilentroommate.ui.theme.Amber
import io.github.rosale137.happysilentroommate.ui.theme.Coral
import io.github.rosale137.happysilentroommate.ui.theme.HappysilentroommateTheme
import io.github.rosale137.happysilentroommate.monitor.MonitorState
import io.github.rosale137.happysilentroommate.monitor.ServiceStatus
import io.github.rosale137.happysilentroommate.schedule.QuietWindow
import io.github.rosale137.happysilentroommate.ui.theme.Surface1
import io.github.rosale137.happysilentroommate.ui.theme.Surface2
import io.github.rosale137.happysilentroommate.ui.theme.TextPrimary
import io.github.rosale137.happysilentroommate.ui.theme.Teal
import io.github.rosale137.happysilentroommate.ui.theme.TextMuted
import io.github.rosale137.happysilentroommate.ui.theme.Track
import io.github.rosale137.happysilentroommate.alerts.AlertPolicy
import io.github.rosale137.happysilentroommate.alerts.AlertStrength
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sin

private const val SCOPE_HELP =
    "The plot shows the last 30 seconds, newest on the right.\n\n" +
        "Thin grey line: the volume in each tenth of a second.\n" +
        "Thick line: the integrated (averaged) volume. It turns coral when it is above the limit.\n\n" +
        "Drag the dashed amber line up or down to set the limit. " +
        "The faint dotted line below it is where the hysteresis ends: you count as quieter " +
        "again only once the level drops under it.\n" +
        "Drag the left edge of the shaded band left or right to set the integration time. " +
        "The band is the stretch that is averaged: longer ignores short laughs and coughs, " +
        "shorter reacts faster.\n\n" +
        "Values are dBFS, relative to the loudest the microphone can capture (0 is the maximum). " +
        "The plot shows -80 to -10: quieter values sit on the bottom edge, louder ones on the top edge."

private const val ALERTS_HELP =
    "The phone vibrates as soon as the thick line goes above the limit.\n\n" +
        "Reminders: while you stay too loud, the next buzz comes after the upper value " +
        "(right handle). Every further reminder comes a bit sooner (each gap 30% shorter), " +
        "until they come as often as the lower value (left handle). The arrows show this " +
        "speed-up. Put both handles together for steady reminders.\n\n" +
        "The buzzes also get stronger: first a soft single tap, then two firm taps, " +
        "then a long buzz. The screen flashes along with them.\n\n" +
        "Hysteresis: how many dB below the limit you have to get to count as quieter again " +
        "(the dotted line on the plot). Dipping just under the limit and back up does not " +
        "cause an extra buzz and keeps the speed-up.\n\n" +
        "Calming down: once you are quieter, the urgency fades one step every 10 seconds. " +
        "If you get loud again soon, the reminders continue fast and strong where they were; " +
        "after a longer quiet time they start gently again.\n\n" +
        "Tip: a shorter integration time (the shaded band) makes the buzzes stop sooner " +
        "once you are quieter."

private const val LISTENING_HELP =
    "Start listening keeps the app checking the volume and buzzing when the screen is off " +
        "or you switch to another app.\n\n" +
        "Android shows a notification while the microphone is in use. It shows the number of " +
        "buzzes, turns coral while you are too loud (on phones that allow coloured notifications) " +
        "and has a Stop button.\n\n" +
        "With the quiet time switched on, this starts and stops on its own. " +
        "\"Stop for tonight\" ends it until the next quiet time.\n\n" +
        "With Do Not Disturb on, the phone does not vibrate; the notification and the screen " +
        "still show when you are too loud.\n\n" +
        "Still nothing is recorded: only the level number is calculated, on this phone."

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
    state: MonitorState,
    settings: Settings,
    onSettingsChange: (Settings) -> Unit,
    onRequestMic: () -> Unit,
    status: ServiceStatus,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onStopForTonight: () -> Unit,
    nextTestStrength: AlertStrength,
    onTestVibration: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val powers = state.powers
    val micError = state.micError
    val alertCount = state.alertCount
    val lastAlertAt = state.lastAlertAt
    val lastAlertStrength = state.lastAlertStrength
    val average = averageDb(powers, settings.windowChunks)
    val above = average > settings.limitDb
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

        ListeningCard(
            status = status,
            settings = settings,
            onStartListening = onStartListening,
            onStopListening = onStopListening,
            onStopForTonight = onStopForTonight,
        )

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
            Text(
                when {
                    lastAlertAt == null -> "No buzz yet"
                    else -> {
                        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(lastAlertAt))
                        val count = if (alertCount == 1) "1 buzz" else "$alertCount buzzes"
                        val strength = lastAlertStrength?.let { ", ${it.label}" } ?: ""
                        "$count · last at $time$strength"
                    }
                },
                color = TextMuted,
                fontSize = 13.sp,
            )

            ScopeView(
                powers = powers,
                windowSeconds = settings.windowSeconds,
                onWindowSecondsChange = { onSettingsChange(settings.copy(windowSeconds = it)) },
                limitDb = settings.limitDb,
                onLimitChange = { onSettingsChange(settings.copy(limitDb = it)) },
                hysteresisDb = settings.hysteresisDb,
            )

            if (micError != null) {
                Text("Microphone problem: $micError", color = Coral, fontSize = 13.sp)
            }
        }

        Card(spacing = 4.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Alerts", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                InfoButton(
                    title = "When the phone vibrates",
                    text = ALERTS_HELP +
                        "\n\nWith your settings, while too loud: buzz, then after " +
                        reminderSequenceText(settings),
                )
            }
            LabeledSlider(
                label = "Hysteresis",
                valueText = "${settings.hysteresisDb.roundToInt()} dB",
                value = settings.hysteresisDb,
                range = 0f..Settings.HYSTERESIS_MAX_DB,
                steps = Settings.HYSTERESIS_MAX_DB.roundToInt() - 1,
                onChange = { onSettingsChange(settings.copy(hysteresisDb = it.roundToInt().toFloat())) },
            )
            ReminderRangeSlider(
                fastestSeconds = settings.fastestRemindSeconds,
                firstSeconds = settings.remindSeconds,
                onChange = { fastest, first ->
                    onSettingsChange(settings.copy(fastestRemindSeconds = fastest, remindSeconds = first))
                },
            )
        }

        QuietTimeCard(settings = settings, onSettingsChange = onSettingsChange)

        Button(
            onClick = onTestVibration,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Amber,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) { Text("Test vibration: ${nextTestStrength.label}", fontWeight = FontWeight.Bold) }
    }
}

private val AlertStrength.label: String
    get() = when (this) {
        AlertStrength.Gentle -> "gentle"
        AlertStrength.Medium -> "medium"
        AlertStrength.Strong -> "strong"
    }

/** E.g. "8 → 5.6 → 3.9 → 2.7 → 2 s, then every 2 s". */
private fun reminderSequenceText(settings: Settings): String {
    val gaps = AlertPolicy.reminderGapsMs(
        (settings.remindSeconds * 1000).toLong(),
        (settings.fastestRemindSeconds * 1000).toLong(),
    )
    fun s(ms: Long) = String.format(Locale.getDefault(), "%.1f", ms / 1000.0)
        .removeSuffix("${DecimalFormatSymbols.getInstance().decimalSeparator}0")
    return gaps.joinToString(" → ") { s(it) } + " s, then every ${s(gaps.last())} s"
}

/**
 * One slider for the reminder gaps: right handle = first reminder (upper limit), left handle =
 * fastest reminder (lower limit). The amber stretch between them carries arrows pointing to the
 * lower limit, because the reminders speed up from right to left.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderRangeSlider(
    fastestSeconds: Float,
    firstSeconds: Float,
    onChange: (fastest: Float, first: Float) -> Unit,
) {
    val range = Settings.REMIND_MIN_S..Settings.REMIND_MAX_S
    Column {
        Row {
            Text("Reminders speed up", modifier = Modifier.weight(1f))
            Text(
                "every ${fastestSeconds.roundToInt()} s ← ${firstSeconds.roundToInt()} s",
                color = Amber,
                fontWeight = FontWeight.Bold,
            )
        }
        RangeSlider(
            value = fastestSeconds..firstSeconds,
            onValueChange = { onChange(it.start.roundToInt().toFloat(), it.endInclusive.roundToInt().toFloat()) },
            valueRange = range,
            steps = (range.endInclusive - range.start).roundToInt() - 1,
            colors = SliderDefaults.colors(thumbColor = Amber),
            track = { state ->
                val span = state.valueRange.endInclusive - state.valueRange.start
                val from = (state.activeRangeStart - state.valueRange.start) / span
                val to = (state.activeRangeEnd - state.valueRange.start) / span
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(14.dp),
                ) {
                    val cy = size.height / 2
                    drawLine(Track, Offset(0f, cy), Offset(size.width, cy), 4.dp.toPx(), StrokeCap.Round)
                    val left = from * size.width
                    val right = to * size.width
                    if (right - left < 1f) return@Canvas
                    drawRoundRect(
                        Amber,
                        topLeft = Offset(left, 0f),
                        size = Size(right - left, size.height),
                        cornerRadius = CornerRadius(size.height / 2),
                    )
                    // Chevrons "<" pointing to the lower limit, spread over the amber stretch.
                    val step = 14.dp.toPx()
                    val half = 4.dp.toPx()
                    var x = right - step
                    while (x - half > left + 4.dp.toPx()) {
                        drawLine(OnAmber, Offset(x + half / 2, cy - half), Offset(x - half / 2, cy), 2.dp.toPx(), StrokeCap.Round)
                        drawLine(OnAmber, Offset(x - half / 2, cy), Offset(x + half / 2, cy + half), 2.dp.toPx(), StrokeCap.Round)
                        x -= step
                    }
                }
            },
        )
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
) {
    Column {
        Row {
            Text(label, modifier = Modifier.weight(1f))
            Text(valueText, color = Amber, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = Amber,
                activeTrackColor = Amber,
                inactiveTrackColor = Track,
                activeTickColor = Amber,
                inactiveTickColor = TextMuted,
            ),
        )
    }
}

/** Status line and the one button that fits it: start, stop, or stop for tonight. */
@Composable
private fun ListeningCard(
    status: ServiceStatus,
    settings: Settings,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onStopForTonight: () -> Unit,
) {
    val start = QuietWindow.format(settings.quietStartMin)
    val end = QuietWindow.format(settings.quietEndMin)
    val (title, detail) = when (status) {
        ServiceStatus.Off -> "Only while this screen is open" to
            "Start listening to keep going with the screen off."
        ServiceStatus.Waiting -> "Waiting for quiet time" to
            "Listens on its own from $start to $end. Microphone off until then."
        ServiceStatus.ListeningAuto -> "Quiet time: listening until $end" to
            "The screen can go off. It stops on its own at $end."
        ServiceStatus.ListeningManual -> "Listening in the background" to
            "The screen can go off. Stop it here or in the notification."
    }
    Card(spacing = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, color = if (status.listening) Teal else TextPrimary)
                Text(detail, color = TextMuted, fontSize = 13.sp)
            }
            InfoButton(title = "Listening in the background", text = LISTENING_HELP)
        }
        val (label, onClick) = when (status) {
            ServiceStatus.Off -> "Start listening" to onStartListening
            ServiceStatus.Waiting -> "Start listening now" to onStartListening
            ServiceStatus.ListeningAuto -> "Stop for tonight" to onStopForTonight
            ServiceStatus.ListeningManual -> "Stop listening" to onStopListening
        }
        Button(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (status.listening) Surface2 else Teal,
                contentColor = if (status.listening) TextPrimary else MaterialTheme.colorScheme.onPrimary,
            ),
        ) { Text(label, fontWeight = FontWeight.Bold) }
    }
}

@Composable
internal fun Card(
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
            state = MonitorState(
                powers = powers,
                alertCount = 3,
                lastAlertAt = 0L,
                lastAlertStrength = AlertStrength.Medium,
            ),
            settings = Settings(),
            onSettingsChange = {},
            onRequestMic = {},
            status = ServiceStatus.Off,
            onStartListening = {},
            onStopListening = {},
            onStopForTonight = {},
            nextTestStrength = AlertStrength.Gentle,
            onTestVibration = {},
        )
    }
}
