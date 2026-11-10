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

/** How much level history the app keeps (numbers only, never audio). */
const val HISTORY_SECONDS = 30

/** Longest integration time offered: the whole history. Must not exceed [HISTORY_SECONDS]. */
const val MAX_WINDOW_SECONDS = HISTORY_SECONDS

/**
 * Integrated level at every point of [powers]: for each chunk, the average power over the last
 * [windowChunks] chunks (fewer at the start), as dBFS. Power is averaged, not dB, so a short loud
 * burst counts the way the ear would.
 */
fun movingAverageDb(powers: List<Double>, windowChunks: Int): List<Double> {
    require(windowChunks > 0)
    val prefix = DoubleArray(powers.size + 1)
    for (i in powers.indices) prefix[i + 1] = prefix[i] + powers[i]
    return List(powers.size) { i ->
        val from = maxOf(0, i - windowChunks + 1)
        powerToDb((prefix[i + 1] - prefix[from]) / (i + 1 - from))
    }
}

/** Integrated level right now: the average over the newest [windowChunks] chunks. */
fun averageDb(powers: List<Double>, windowChunks: Int): Double {
    require(windowChunks > 0)
    if (powers.isEmpty()) return FLOOR_DBFS
    val n = minOf(windowChunks, powers.size)
    var sum = 0.0
    for (i in powers.size - n until powers.size) sum += powers[i]
    return powerToDb(sum / n)
}
