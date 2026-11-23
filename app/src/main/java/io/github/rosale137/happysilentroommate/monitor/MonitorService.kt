package io.github.rosale137.happysilentroommate.monitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.rosale137.happysilentroommate.MainActivity
import io.github.rosale137.happysilentroommate.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps listening with the screen off. Android requires a visible notification while the
 * microphone is used in the background; it shows the buzz count, turns coral while too loud
 * (where the system allows coloured notifications) and has a Stop button.
 *
 * Must be started while the app is on screen: Android does not let a background app start
 * using the microphone.
 */
class MonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (started) return START_NOT_STICKY
        started = true

        MonitorEngine.init(this)
        createChannel()
        try {
            val notification = buildNotification(MonitorEngine.state.value)
            // The microphone service type exists from Android 11; older versions take no type.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            // E.g. started from the background, or the microphone permission is missing.
            Log.w(TAG, "Could not start listening in the background", e)
            stopSelf()
            return START_NOT_STICKY
        }

        // Keep the CPU running with the screen off, so the level keeps being checked.
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HappySilentRoommate:listening")
            .apply { acquire(MAX_LISTEN_MS) }

        MonitorEngine.serviceRunning.value = true
        scope.launch { MonitorEngine.listen() }
        scope.launch {
            // Only refresh the notification when something it shows changes.
            MonitorEngine.state
                .map { NotificationContent(it.aboveLimit, it.alertCount, it.lastAlertAt, it.micError) }
                .distinctUntilChanged()
                .collect { notify(buildNotification(MonitorEngine.state.value)) }
        }
        // Not restarted by the system if it gets killed: it could not use the mic from the background.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        MonitorEngine.serviceRunning.value = false
        super.onDestroy()
    }

    private data class NotificationContent(
        val aboveLimit: Boolean,
        val alertCount: Int,
        val lastAlertAt: Long?,
        val micError: String?,
    )

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "Listening", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shown while the app listens in the background"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(state: MonitorState): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, MonitorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = when {
            state.micError != null -> "Microphone problem"
            state.aboveLimit -> "Too loud – please speak quieter"
            else -> "Listening"
        }
        val text = state.micError ?: if (state.lastAlertAt == null) {
            "No buzz yet"
        } else {
            val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(state.lastAlertAt))
            val count = if (state.alertCount == 1) "1 buzz" else "${state.alertCount} buzzes"
            "$count · last at $time"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp)
            .addAction(0, "Stop", stop)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setColor(if (state.aboveLimit) CORAL else AMBER)
            .setColorized(state.aboveLimit)
            .build()
    }

    @Suppress("MissingPermission") // Without the permission the update is simply not shown.
    private fun notify(notification: Notification) {
        if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val TAG = "MonitorService"
        private const val CHANNEL_ID = "listening"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "io.github.rosale137.happysilentroommate.STOP"

        /** Safety net: the wake lock is released after this even if Stop is never pressed. */
        private const val MAX_LISTEN_MS = 12 * 60 * 60 * 1000L

        private const val CORAL = 0xFFFF8A7A.toInt()
        private const val AMBER = 0xFFF5B454.toInt()

        /** Call only while the app is on screen. */
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, MonitorService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MonitorService::class.java))
        }
    }
}
