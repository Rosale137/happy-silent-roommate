package io.github.rosale137.happysilentroommate.ui

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rosale137.happysilentroommate.schedule.QuietWindow
import io.github.rosale137.happysilentroommate.settings.AutoMode
import io.github.rosale137.happysilentroommate.settings.Settings
import io.github.rosale137.happysilentroommate.ui.theme.Amber
import io.github.rosale137.happysilentroommate.ui.theme.OnAmber
import io.github.rosale137.happysilentroommate.ui.theme.TextMuted
import io.github.rosale137.happysilentroommate.ui.theme.TextPrimary
import io.github.rosale137.happysilentroommate.ui.theme.Track

private const val QUIET_TIME_HELP =
    "Listen automatically: the app starts listening at the start time and stops at the end " +
        "time on its own. The time may run past midnight, e.g. 22:00 to 01:00.\n\n" +
        "Standby: a service waits in the background all day, with a small silent notification " +
        "and the microphone off. At the start time it switches the microphone on by itself. " +
        "After a phone restart, one tap on a notification switches it on again (Android rule).\n\n" +
        "Reminder: nothing runs during the day. At the start time a notification appears; " +
        "one tap starts listening, and it stops on its own at the end time.\n\n" +
        "Outside the quiet time the microphone is never used."

/** Quiet time: switch, start and end time, and how listening starts. */
@Composable
fun QuietTimeCard(settings: Settings, onSettingsChange: (Settings) -> Unit) {
    val context = LocalContext.current
    fun pickTime(current: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(
            context,
            { _, hour, minute -> onPicked(hour * 60 + minute) },
            current / 60,
            current % 60,
            DateFormat.is24HourFormat(context),
        ).show()
    }

    Card(spacing = 8.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Quiet time", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            InfoButton(title = "Quiet time", text = QUIET_TIME_HELP)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Listen automatically", modifier = Modifier.weight(1f))
            Switch(
                checked = settings.autoEnabled,
                onCheckedChange = { onSettingsChange(settings.copy(autoEnabled = it)) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = OnAmber,
                    checkedTrackColor = Amber,
                    uncheckedTrackColor = Track,
                ),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("From", color = TextMuted)
            OutlinedButton(onClick = {
                pickTime(settings.quietStartMin) { onSettingsChange(settings.copy(quietStartMin = it)) }
            }) { Text(QuietWindow.format(settings.quietStartMin), color = Amber, fontWeight = FontWeight.Bold) }
            Text("to", color = TextMuted)
            OutlinedButton(onClick = {
                pickTime(settings.quietEndMin) { onSettingsChange(settings.copy(quietEndMin = it)) }
            }) { Text(QuietWindow.format(settings.quietEndMin), color = Amber, fontWeight = FontWeight.Bold) }
        }
        if (settings.quietWindow.isEmpty) {
            Text("Start and end are the same: no quiet time.", color = TextMuted, fontSize = 13.sp)
        }
        Column(Modifier.selectableGroup()) {
            ModeOption(
                selected = settings.autoMode == AutoMode.Standby,
                title = "Standby",
                detail = "Starts by itself. Small notification all day.",
                onSelect = { onSettingsChange(settings.copy(autoMode = AutoMode.Standby)) },
            )
            ModeOption(
                selected = settings.autoMode == AutoMode.Reminder,
                title = "Reminder",
                detail = "Tap a notification at the start time.",
                onSelect = { onSettingsChange(settings.copy(autoMode = AutoMode.Reminder)) },
            )
        }
    }
}

@Composable
private fun ModeOption(selected: Boolean, title: String, detail: String, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = Amber, unselectedColor = TextMuted),
        )
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary)
            Text(detail, color = TextMuted, fontSize = 13.sp)
        }
    }
}
