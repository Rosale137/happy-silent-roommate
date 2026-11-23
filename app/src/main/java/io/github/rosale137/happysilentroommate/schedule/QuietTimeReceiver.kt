package io.github.rosale137.happysilentroommate.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.rosale137.happysilentroommate.monitor.MonitorEngine
import io.github.rosale137.happysilentroommate.monitor.MonitorService
import io.github.rosale137.happysilentroommate.monitor.Notifications
import io.github.rosale137.happysilentroommate.monitor.ServiceStatus
import io.github.rosale137.happysilentroommate.settings.AutoMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.TimeZone

/**
 * Start or end of the quiet time, phone restart, or app update.
 *
 * - Standby: tells the waiting service to re-check. If the service is gone (after a restart
 *   or update), Android does not let us start the microphone from here, so a notification asks
 *   for one tap.
 * - Reminder: at the start, a notification "tap to listen"; at the end, the service stops itself.
 */
class QuietTimeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != QuietTimeScheduler.ACTION_BOUNDARY &&
            action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.Main.immediate).launch {
            try {
                MonitorEngine.init(context)
                val settings = MonitorEngine.awaitSettings()
                QuietTimeScheduler.update(context, settings)
                if (!settings.autoEnabled) return@launch

                val inside = settings.quietWindow.contains(System.currentTimeMillis(), TimeZone.getDefault())
                val serviceUp = MonitorEngine.serviceStatus.value != ServiceStatus.Off
                when {
                    serviceUp -> MonitorService.recheck(context)
                    settings.autoMode == AutoMode.Standby -> Notifications.showArmReminder(context)
                    inside -> Notifications.showStartReminder(context, settings)
                    else -> Notifications.cancelReminder(context)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
