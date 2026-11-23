package io.github.rosale137.happysilentroommate.haptics

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import io.github.rosale137.happysilentroommate.alerts.AlertStrength

class Haptics(context: Context) {

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    /**
     * Distinct "too loud" patterns, so they are not mistaken for a message notification:
     * gentle = one soft short tap, medium = two firm taps, strong = a long full buzz.
     *
     * Marked as an alarm vibration, so it comes through with the screen off. With Do Not Disturb
     * on, the phone does not vibrate at all (seen on a Pixel 8 with /e/OS), which is fine:
     * Do Not Disturb then works as a deliberate "everything off".
     */
    fun buzz(strength: AlertStrength) {
        val (timings, amplitudes) = pattern(strength)
        val v = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = if (v.hasAmplitudeControl()) {
                VibrationEffect.createWaveform(timings, amplitudes, -1)
            } else {
                VibrationEffect.createWaveform(timings, -1)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(effect, ALARM_AUDIO)
            }
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(timings, -1, ALARM_AUDIO)
        }
    }

    companion object {
        private fun pattern(strength: AlertStrength) = when (strength) {
            AlertStrength.Gentle -> longArrayOf(0, 120) to intArrayOf(0, 110)
            AlertStrength.Medium -> longArrayOf(0, 150, 120, 150) to intArrayOf(0, 200, 0, 200)
            AlertStrength.Strong -> longArrayOf(0, 700) to intArrayOf(0, 255)
        }

        /** How long the pattern for [strength] vibrates, in ms. */
        fun durationMs(strength: AlertStrength): Long = pattern(strength).first.sum()

        private val ALARM_AUDIO: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }
}
