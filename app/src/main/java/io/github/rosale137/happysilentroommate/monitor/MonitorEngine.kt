package io.github.rosale137.happysilentroommate.monitor

import android.content.Context
import android.os.SystemClock
import io.github.rosale137.happysilentroommate.alerts.AlertPolicy
import io.github.rosale137.happysilentroommate.alerts.AlertStrength
import io.github.rosale137.happysilentroommate.audio.AudioLevelMeter
import io.github.rosale137.happysilentroommate.audio.HISTORY_SECONDS
import io.github.rosale137.happysilentroommate.audio.averageDbOrNull
import io.github.rosale137.happysilentroommate.haptics.Haptics
import io.github.rosale137.happysilentroommate.settings.Settings
import io.github.rosale137.happysilentroommate.settings.SettingsStore
import io.github.rosale137.happysilentroommate.schedule.QuietTimeScheduler
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What the background service is doing. */
enum class ServiceStatus {
    Off,

    /** Running in standby, outside the quiet time: microphone off. */
    Waiting,

    /** Started by hand with "Start listening". */
    ListeningManual,

    /** Started by the quiet time; stops on its own at the end. */
    ListeningAuto;

    val listening: Boolean get() = this == ListeningManual || this == ListeningAuto
}

/** Everything the screen and the notification show. Numbers only, never audio. */
data class MonitorState(
    /** One mean-square power per 100 ms, oldest first. */
    val powers: List<Double> = emptyList(),
    val micError: String? = null,
    /** The integrated level is above the limit right now. */
    val aboveLimit: Boolean = false,
    val alertCount: Int = 0,
    /** Wall-clock time of the last buzz. */
    val lastAlertAt: Long? = null,
    val lastAlertStrength: AlertStrength? = null,
)

/**
 * The one place that listens, decides and buzzes. Lives as long as the app process, so the
 * screen and the background service share it: the escalation and the buzz counter carry over
 * when the screen goes off.
 *
 * Only one caller holds the microphone at a time ([listen] waits for the previous one to let go).
 */
object MonitorEngine {

    private const val SAVE_DELAY_MS = 500L
    private const val HISTORY_CHUNKS = HISTORY_SECONDS * 1000 / AudioLevelMeter.CHUNK_MS

    /** A gap longer than this in the level readings means a fresh start, not a handover. */
    private const val STALE_MS = 2_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val meter = AudioLevelMeter()
    private val policy = AlertPolicy()
    private val micLock = Mutex()
    private var lastPowerAtMs = 0L

    /** Readings before this time are ignored: they contain our own vibration. */
    private var ignoreUntilMs = 0L

    /**
     * Extra time ignored after the vibration ends: the motor rings out, and each reading
     * arrives a little after the sound it contains.
     */
    private const val IGNORE_AFTER_BUZZ_MS = 400L

    private lateinit var haptics: Haptics
    private var initialized = false

    private val _state = MutableStateFlow(MonitorState())
    val state: StateFlow<MonitorState> = _state.asStateFlow()

    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    /** Set by [MonitorService]. */
    val serviceStatus = MutableStateFlow(ServiceStatus.Off)

    private val settingsLoaded = CompletableDeferred<Unit>()

    private val _buzzes = MutableSharedFlow<AlertStrength>(extraBufferCapacity = 8)

    /** Every buzz (alerts and tests), so the screen can flash along. */
    val buzzes: SharedFlow<AlertStrength> = _buzzes

    /** Call before anything else; cheap to call again. Loads the saved settings. */
    @OptIn(FlowPreview::class)
    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val app = context.applicationContext
        haptics = Haptics(app)
        val store = SettingsStore(app)
        scope.launch {
            _settings.value = store.load()
            settingsLoaded.complete(Unit)
            _settings.drop(1).debounce(SAVE_DELAY_MS).collect { store.save(it) }
        }
        // Keep the quiet-time alarm in step with the settings.
        scope.launch {
            settingsLoaded.await()
            _settings
                .map { listOf(it.autoEnabled, it.quietStartMin, it.quietEndMin, it.autoMode) }
                .distinctUntilChanged()
                .collect { QuietTimeScheduler.update(app, _settings.value) }
        }
    }

    /** The saved settings, once they are loaded. Call [init] first. */
    suspend fun awaitSettings(): Settings {
        settingsLoaded.await()
        return _settings.value
    }

    fun updateSettings(settings: Settings) {
        _settings.value = settings
    }

    fun testBuzz(strength: AlertStrength) = buzz(strength)

    /**
     * Listens until the calling coroutine is cancelled. Needs the RECORD_AUDIO permission.
     * If another caller is listening, waits until it stops.
     */
    suspend fun listen() = micLock.withLock {
        if (SystemClock.elapsedRealtime() - lastPowerAtMs > STALE_MS) {
            _state.update { it.copy(powers = emptyList(), aboveLimit = false) }
            policy.reset()
        }
        _state.update { it.copy(micError = null) }
        meter.powers()
            .catch { e -> _state.update { it.copy(micError = e.message ?: "Microphone error") } }
            .collect { power -> onPower(power) }
    }

    private fun onPower(measured: Double) {
        lastPowerAtMs = SystemClock.elapsedRealtime()
        // The microphone hears our own vibration (about -25 dBFS with the phone on a table),
        // which would look like loud talking and trigger the next buzz. Ignore it.
        val ignored = lastPowerAtMs < ignoreUntilMs
        val power = if (ignored) Double.NaN else measured
        val s = _settings.value
        val powers = (_state.value.powers + power).takeLast(HISTORY_CHUNKS)
        val level = averageDbOrNull(powers, s.windowChunks)
        if (ignored || level == null) {
            _state.update { it.copy(powers = powers) }
            return
        }
        val strength = policy.update(
            levelDb = level,
            limitDb = s.limitDb.toDouble(),
            hysteresisDb = s.hysteresisDb.toDouble(),
            firstRemindMs = (s.remindSeconds * 1000).toLong(),
            fastestRemindMs = (s.fastestRemindSeconds * 1000).toLong(),
            nowMs = lastPowerAtMs,
        )
        _state.update {
            val base = it.copy(powers = powers, aboveLimit = level > s.limitDb)
            if (strength == null) {
                base
            } else {
                base.copy(
                    alertCount = it.alertCount + 1,
                    lastAlertAt = System.currentTimeMillis(),
                    lastAlertStrength = strength,
                )
            }
        }
        if (strength != null) buzz(strength)
    }

    private fun buzz(strength: AlertStrength) {
        ignoreUntilMs = SystemClock.elapsedRealtime() + Haptics.durationMs(strength) + IGNORE_AFTER_BUZZ_MS
        haptics.buzz(strength)
        _buzzes.tryEmit(strength)
    }
}
