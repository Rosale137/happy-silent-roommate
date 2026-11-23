package io.github.rosale137.happysilentroommate.haptics

import android.content.Context
import android.os.Build
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
     */
    fun buzz(strength: AlertStrength) {
        val (timings, amplitudes) = when (strength) {
            AlertStrength.Gentle -> longArrayOf(0, 120) to intArrayOf(0, 110)
            AlertStrength.Medium -> longArrayOf(0, 150, 120, 150) to intArrayOf(0, 200, 0, 200)
            AlertStrength.Strong -> longArrayOf(0, 700) to intArrayOf(0, 255)
        }
        val v = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = if (v.hasAmplitudeControl()) {
                VibrationEffect.createWaveform(timings, amplitudes, -1)
            } else {
                VibrationEffect.createWaveform(timings, -1)
            }
            v.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(timings, -1)
        }
    }
}
