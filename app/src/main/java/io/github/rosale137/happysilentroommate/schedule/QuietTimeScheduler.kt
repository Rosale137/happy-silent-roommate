package io.github.rosale137.happysilentroommate.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import io.github.rosale137.happysilentroommate.settings.Settings
import java.util.TimeZone

/**
 * Sets one alarm for the next start or end of the quiet time. [QuietTimeReceiver] handles it
 * and sets the following one. Exact alarms, so it starts on the minute even when the phone sleeps.
 */
object QuietTimeScheduler {

    const val ACTION_BOUNDARY = "io.github.rosale137.happysilentroommate.QUIET_TIME_BOUNDARY"

    fun update(context: Context, settings: Settings) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = pendingIntent(context)
        alarms.cancel(intent)
        val window = settings.quietWindow
        if (!settings.autoEnabled || window.isEmpty) return

        val at = window.nextBoundaryMillis(System.currentTimeMillis(), TimeZone.getDefault())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarms.canScheduleExactAlarms()) {
            // Without the permission Android may deliver it a few minutes late.
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        } else {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        }
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, QuietTimeReceiver::class.java).setAction(ACTION_BOUNDARY),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
