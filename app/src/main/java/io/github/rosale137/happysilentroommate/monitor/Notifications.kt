package io.github.rosale137.happysilentroommate.monitor

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.rosale137.happysilentroommate.MainActivity
import io.github.rosale137.happysilentroommate.R
import io.github.rosale137.happysilentroommate.schedule.QuietWindow
import io.github.rosale137.happysilentroommate.settings.Settings

/** Notification channels and the one-tap reminders. */
object Notifications {

    const val CHANNEL_LISTENING = "listening"
    const val CHANNEL_WAITING = "waiting"
    const val CHANNEL_REMINDERS = "reminders"

    const val ID_SERVICE = 1
    private const val ID_REMINDER = 2

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_LISTENING, "Listening", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shown while the app listens in the background"
                    setShowBadge(false)
                },
                NotificationChannel(CHANNEL_WAITING, "Waiting for quiet time", NotificationManager.IMPORTANCE_MIN).apply {
                    description = "Shown all day in standby; the microphone is off"
                    setShowBadge(false)
                },
                NotificationChannel(CHANNEL_REMINDERS, "Quiet time reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Tap to start listening"
                    setShowBadge(false)
                },
            ),
        )
    }

    /** Reminder mode: the quiet time has started, one tap starts listening. */
    fun showStartReminder(context: Context, settings: Settings) = showReminder(
        context,
        title = "Quiet time – tap to start listening",
        text = "Until ${QuietWindow.format(settings.quietEndMin)}. Stops on its own.",
    )

    /** Standby after a phone restart or app update: one tap switches it on again. */
    fun showArmReminder(context: Context) = showReminder(
        context,
        title = "Tap to switch the quiet time on again",
        text = "After a restart, Android needs one tap before the app may use the microphone.",
    )

    fun cancelReminder(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID_REMINDER)
    }

    @SuppressLint("MissingPermission") // Without the permission the reminder is simply not shown.
    private fun showReminder(context: Context, title: String, text: String) {
        createChannels(context)
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        // Opening the app counts as the user's action, so it may then start the microphone.
        val open = PendingIntent.getActivity(
            context, 2,
            Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_START_QUIET_TIME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        manager.notify(ID_REMINDER, notification)
    }
}
