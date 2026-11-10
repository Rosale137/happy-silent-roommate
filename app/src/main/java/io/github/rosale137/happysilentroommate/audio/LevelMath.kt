package io.github.rosale137.happysilentroommate.audio

import kotlin.math.log10
import kotlin.math.max

/** Lowest level we ever report, in dBFS. Digital silence would otherwise be -infinity. */
const val FLOOR_DBFS = -100.0

/** Mean square of 16-bit samples, normalised so that a full-scale square wave is 1.0. */
fun meanSquare(samples: ShortArray, count: Int): Double {
    if (count <= 0) return 0.0
    var sum = 0.0
    for (i in 0 until count) {
        val s = samples[i] / 32768.0
        sum += s * s
    }
    return sum / count
}

/** Converts a normalised mean-square power to dBFS (0 dBFS = full scale). */
fun powerToDb(power: Double): Double = max(FLOOR_DBFS, 10.0 * log10(max(power, 1e-12)))

/**
 * Averages signal power over the last [size] chunks (power, not dB, so a short loud burst counts
 * the way the ear would). Only numbers are kept, never audio.
 */
class PowerAverager(private val size: Int) {
    private val window = ArrayDeque<Double>()
    private var sum = 0.0

    init {
        require(size > 0)
    }

    fun add(power: Double): Double {
        window.addLast(power)
        sum += power
        if (window.size > size) sum -= window.removeFirst()
        return powerToDb(sum / window.size)
    }

    fun reset() {
        window.clear()
        sum = 0.0
    }
}
