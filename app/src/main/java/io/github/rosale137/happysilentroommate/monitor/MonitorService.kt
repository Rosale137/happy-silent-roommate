package io.github.rosale137.happysilentroommate.monitor

import android.app.Notification
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
import io.github.rosale137.happysilentroommate.schedule.QuietWindow
import io.github.rosale137.happysilentroommate.settings.AutoMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Listens with the screen off. Two modes:
 * - by hand ("Start listening"): listens until Stop;
 * - automatic: listens during the quiet time. In standby mode it waits in the background the rest
 *   of the day with the microphone off; in reminder mode it only runs during the quiet time.
 *
 * Must be started while the app is on screen: Android lets a service use the microphone in the
 * background only if it was started from the foreground. Once running, it may switch the
 * microphone on and off by itself (tested on Android 15).
 */
class MonitorService : Service() {

    private enum class Mode { Manual, Auto }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mode: Mode? = null
    private var inForeground = false
    private var listenJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    /** "Stop for tonight": no automatic listening before this time. */
    private var skipUntilMs = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        MonitorEngine.init(this)
        Notifications.createChannels(this)
        // Re-check whenever the settings change (quiet time, mode, switched off).
        scope.launch { MonitorEngine.settings.collect { if (mode != null) evaluate() } }
        scope.launch {
            combine(MonitorEngine.state, MonitorEngine.serviceStatus, MonitorEngine.settings) { state, status, s ->
                NotificationContent(
                    status, state.aboveLimit, state.alertCount, state.lastAlertAt, state.micError,
                    s.quietStartMin, s.quietEndMin,
                )
            }.distinctUntilChanged().collect { if (inForeground) notify(buildNotification(it)) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_MANUAL -> {
                if (!goForeground()) return START_NOT_STICKY
                mode = Mode.Manual
                evaluate()
            }
            ACTION_START_AUTO -> {
                if (!goForeground()) return START_NOT_STICKY
                if (mode == null) mode = Mode.Auto
                skipUntilMs = 0L
                evaluate()
            }
            ACTION_RECHECK -> if (mode != null) evaluate() else stopSelf()
            ACTION_STOP -> stopByHand()
            ACTION_STOP_TONIGHT -> {
                val s = MonitorEngine.settings.value
                skipUntilMs = s.quietWindow.nextEndMillis(System.currentTimeMillis(), TimeZone.getDefault())
                mode = Mode.Auto
                evaluate()
            }
            ACTION_TURN_OFF -> {
                MonitorEngine.updateSettings(MonitorEngine.settings.value.copy(autoEnabled = false))
                stopSelf()
            }
            else -> if (mode == null) stopSelf()
        }
        // Not restarted by the system if it gets killed: it could not use the mic from the background.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        stopListening()
        MonitorEngine.serviceStatus.value = ServiceStatus.Off
        super.onDestroy()
    }

    /** Stop pressed: back to standby if the quiet time is on, otherwise end the service. */
    private fun stopByHand() {
        val s = MonitorEngine.settings.value
        if (mode != null && s.autoEnabled && s.autoMode == AutoMode.Standby) {
            val now = System.currentTimeMillis()
            if (s.quietWindow.contains(now, TimeZone.getDefault())) {
                skipUntilMs = s.quietWindow.nextEndMillis(now, TimeZone.getDefault())
            }
            mode = Mode.Auto
            evaluate()
        } else {
            stopSelf()
        }
    }

    /** Decide from mode, settings and clock whether to listen, wait, or stop. */
    private fun evaluate() {
        val s = MonitorEngine.settings.value
        val now = System.currentTimeMillis()
        val inQuietTime = s.autoEnabled && now >= skipUntilMs &&
            s.quietWindow.contains(now, TimeZone.getDefault())
        val listen = mode == Mode.Manual || (mode == Mode.Auto && inQuietTime)
        val keepRunning = mode == Mode.Manual ||
            (mode == Mode.Auto && s.autoEnabled && (s.autoMode == AutoMode.Standby || listen))
        if (!keepRunning) {
            stopSelf()
            return
        }
        if (listen) {
            MonitorEngine.serviceStatus.value =
                if (mode == Mode.Manual) ServiceStatus.ListeningManual else ServiceStatus.ListeningAuto
            Notifications.cancelReminder(this)
            startListening()
        } else {
            stopListening()
            MonitorEngine.serviceStatus.value = ServiceStatus.Waiting
        }
    }

    private fun startListening() {
        if (listenJob?.isActive == true) return
        // Keep the CPU running with the screen off, so the level keeps being checked.
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HappySilentRoommate:listening")
            .apply { acquire(MAX_LISTEN_MS) }
        listenJob = scope.launch { MonitorEngine.listen() }
    }

    private fun stopListening() {
        listenJob?.cancel()
        listenJob = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    /** Returns false (and stops) if Android refuses, e.g. when started from the background. */
    private fun goForeground(): Boolean {
        if (inForeground) return true
        try {
            val s = MonitorEngine.settings.value
            val notification = buildNotification(
                NotificationContent(ServiceStatus.Waiting, false, 0, null, null, s.quietStartMin, s.quietEndMin),
            )
            // The microphone service type exists from Android 11; older versions take no type.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startForeground(Notifications.ID_SERVICE, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(Notifications.ID_SERVICE, notification)
            }
            inForeground = true
            return true
        } catch (e: Exception) {
            Log.w(TAG, "Could not start listening in the background", e)
            stopSelf()
            return false
        }
    }

    private data class NotificationContent(
        val status: ServiceStatus,
        val aboveLimit: Boolean,
        val alertCount: Int,
        val lastAlertAt: Long?,
        val micError: String?,
        val quietStartMin: Int,
        val quietEndMin: Int,
    )

    private fun buildNotification(c: NotificationContent): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val start = QuietWindow.format(c.quietStartMin)
        val end = QuietWindow.format(c.quietEndMin)

        if (c.status == ServiceStatus.Waiting || c.status == ServiceStatus.Off) {
            return NotificationCompat.Builder(this, Notifications.CHANNEL_WAITING)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Waiting for quiet time")
                .setContentText("Listens from $start to $end. Microphone off until then.")
                .setContentIntent(openApp)
                .addAction(0, "Turn off", command(ACTION_TURN_OFF, 3))
                .setOngoing(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build()
        }

        val auto = c.status == ServiceStatus.ListeningAuto
        val title = when {
            c.micError != null -> "Microphone problem"
            c.aboveLimit -> "Too loud – please speak quieter"
            auto -> "Listening until $end"
            else -> "Listening"
        }
        val text = c.micError ?: if (c.lastAlertAt == null) {
            "No buzz yet"
        } else {
            val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(c.lastAlertAt))
            val count = if (c.alertCount == 1) "1 buzz" else "${c.alertCount} buzzes"
            "$count · last at $time"
        }
        return NotificationCompat.Builder(this, Notifications.CHANNEL_LISTENING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp)
            .addAction(
                0,
                if (auto) "Stop for tonight" else "Stop",
                command(if (auto) ACTION_STOP_TONIGHT else ACTION_STOP, 1),
            )
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setColor(if (c.aboveLimit) CORAL else AMBER)
            .setColorized(c.aboveLimit)
            .build()
    }

    private fun command(action: String, requestCode: Int) = PendingIntent.getService(
        this, requestCode,
        Intent(this, MonitorService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    @Suppress("MissingPermission") // Without the permission the update is simply not shown.
    private fun notify(notification: Notification) {
        val manager = NotificationManagerCompat.from(this)
        if (manager.areNotificationsEnabled()) manager.notify(Notifications.ID_SERVICE, notification)
    }

    companion object {
        private const val TAG = "MonitorService"
        private const val ACTION_START_MANUAL = "io.github.rosale137.happysilentroommate.START_MANUAL"
        private const val ACTION_START_AUTO = "io.github.rosale137.happysilentroommate.START_AUTO"
        private const val ACTION_RECHECK = "io.github.rosale137.happysilentroommate.RECHECK"
        private const val ACTION_STOP = "io.github.rosale137.happysilentroommate.STOP"
        private const val ACTION_STOP_TONIGHT = "io.github.rosale137.happysilentroommate.STOP_TONIGHT"
        private const val ACTION_TURN_OFF = "io.github.rosale137.happysilentroommate.TURN_OFF"

        /** Safety net: the wake lock is released after this even if listening never stops. */
        private const val MAX_LISTEN_MS = 12 * 60 * 60 * 1000L

        private const val CORAL = 0xFFFF8A7A.toInt()
        private const val AMBER = 0xFFF5B454.toInt()

        /** "Start listening" by hand. Call only while the app is on screen. */
        fun startManual(context: Context) = startForeground(context, ACTION_START_MANUAL)

        /** Quiet time: standby, or listen now if inside it. Call only while the app is on screen. */
        fun startAuto(context: Context) = startForeground(context, ACTION_START_AUTO)

        /** Stop listening by hand (back to standby if the quiet time is on). */
        fun stop(context: Context) = send(context, ACTION_STOP)

        fun stopForTonight(context: Context) = send(context, ACTION_STOP_TONIGHT)

        /** Switch the quiet time off and end the service. */
        fun turnOff(context: Context) = send(context, ACTION_TURN_OFF)

        /** Re-check the clock and settings. Only works while the service runs. */
        fun recheck(context: Context) = send(context, ACTION_RECHECK)

        private fun startForeground(context: Context, action: String) {
            ContextCompat.startForegroundService(context, Intent(context, MonitorService::class.java).setAction(action))
        }

        private fun send(context: Context, action: String) {
            try {
                context.startService(Intent(context, MonitorService::class.java).setAction(action))
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Service not reachable for $action", e)
            }
        }
    }
}
