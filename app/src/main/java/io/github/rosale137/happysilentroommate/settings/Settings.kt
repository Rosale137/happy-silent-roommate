package io.github.rosale137.happysilentroommate.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.rosale137.happysilentroommate.audio.AudioLevelMeter
import io.github.rosale137.happysilentroommate.schedule.MINUTES_PER_DAY
import io.github.rosale137.happysilentroommate.schedule.QuietWindow
import io.github.rosale137.happysilentroommate.settings.Settings.Companion.REMIND_MAX_S
import io.github.rosale137.happysilentroommate.settings.Settings.Companion.REMIND_MIN_S
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

/** How listening starts at the beginning of the quiet time. */
enum class AutoMode {
    /** The service waits in the background all day (small notification) and starts by itself. */
    Standby,

    /** A notification at the start time; one tap starts listening. */
    Reminder,
}

/** Everything the user can adjust on the monitor screen. Raw dBFS, as shown on the scope. */
data class Settings(
    val limitDb: Float = -35f,
    val windowSeconds: Float = 4f,
    /** First reminder while staying too loud. */
    val remindSeconds: Float = 8f,
    /** Reminders speed up until they come this often. Never more than [remindSeconds]. */
    val fastestRemindSeconds: Float = 2f,
    val hysteresisDb: Float = 3f,
    /** Listen on its own during the quiet time. */
    val autoEnabled: Boolean = false,
    /** Quiet time, in minutes after midnight. */
    val quietStartMin: Int = 21 * 60,
    val quietEndMin: Int = 23 * 60 + 30,
    val autoMode: AutoMode = AutoMode.Standby,
) {
    val quietWindow: QuietWindow get() = QuietWindow(quietStartMin, quietEndMin)

    /** The integration time in 100 ms chunks. */
    val windowChunks: Int
        get() = (windowSeconds * 1000 / AudioLevelMeter.CHUNK_MS).roundToInt().coerceAtLeast(1)

    companion object {
        const val REMIND_MIN_S = 1f
        const val REMIND_MAX_S = 30f
        const val HYSTERESIS_MAX_DB = 10f
    }
}

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Keeps [Settings] on this phone only (app-private storage). */
class SettingsStore(private val context: Context) {

    suspend fun load(): Settings {
        val defaults = Settings()
        return context.dataStore.data.map { p ->
            Settings(
                limitDb = p[LIMIT] ?: defaults.limitDb,
                windowSeconds = p[WINDOW] ?: defaults.windowSeconds,
                remindSeconds = (p[REMIND] ?: defaults.remindSeconds).coerceIn(REMIND_MIN_S, REMIND_MAX_S),
                fastestRemindSeconds = p[FASTEST] ?: defaults.fastestRemindSeconds,
                hysteresisDb = p[HYSTERESIS] ?: defaults.hysteresisDb,
                autoEnabled = p[AUTO] ?: defaults.autoEnabled,
                quietStartMin = (p[QUIET_START] ?: defaults.quietStartMin).mod(MINUTES_PER_DAY),
                quietEndMin = (p[QUIET_END] ?: defaults.quietEndMin).mod(MINUTES_PER_DAY),
                autoMode = AutoMode.entries.firstOrNull { it.name == p[AUTO_MODE] } ?: defaults.autoMode,
            )
        }.first()
    }

    suspend fun save(settings: Settings) {
        context.dataStore.edit { p ->
            p[LIMIT] = settings.limitDb
            p[WINDOW] = settings.windowSeconds
            p[REMIND] = settings.remindSeconds
            p[FASTEST] = settings.fastestRemindSeconds
            p[HYSTERESIS] = settings.hysteresisDb
            p[AUTO] = settings.autoEnabled
            p[QUIET_START] = settings.quietStartMin
            p[QUIET_END] = settings.quietEndMin
            p[AUTO_MODE] = settings.autoMode.name
        }
    }

    private companion object {
        val LIMIT = floatPreferencesKey("limit_db")
        val WINDOW = floatPreferencesKey("window_seconds")
        val REMIND = floatPreferencesKey("remind_seconds")
        val FASTEST = floatPreferencesKey("fastest_remind_seconds")
        val HYSTERESIS = floatPreferencesKey("hysteresis_db")
        val AUTO = booleanPreferencesKey("auto_enabled")
        val QUIET_START = intPreferencesKey("quiet_start_min")
        val QUIET_END = intPreferencesKey("quiet_end_min")
        val AUTO_MODE = stringPreferencesKey("auto_mode")
    }
}
