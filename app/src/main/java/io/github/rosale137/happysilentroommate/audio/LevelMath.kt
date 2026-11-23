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
 *
 * NaN entries (ignored while the phone vibrated) are left out of the average. A window with
 * nothing but NaN gives NaN.
 */
fun movingAverageDb(powers: List<Double>, windowChunks: Int): List<Double> {
    require(windowChunks > 0)
    val sums = DoubleArray(powers.size + 1)
    val counts = IntArray(powers.size + 1)
    for (i in powers.indices) {
        val valid = !powers[i].isNaN()
        sums[i + 1] = sums[i] + if (valid) powers[i] else 0.0
        counts[i + 1] = counts[i] + if (valid) 1 else 0
    }
    return List(powers.size) { i ->
        val from = maxOf(0, i - windowChunks + 1)
        val n = counts[i + 1] - counts[from]
        if (n == 0) Double.NaN else powerToDb((sums[i + 1] - sums[from]) / n)
    }
}

/**
 * Integrated level right now: the average over the newest [windowChunks] chunks, leaving out NaN
 * entries. Null if there is nothing to average.
 */
fun averageDbOrNull(powers: List<Double>, windowChunks: Int): Double? {
    require(windowChunks > 0)
    var sum = 0.0
    var n = 0
    for (i in maxOf(0, powers.size - windowChunks) until powers.size) {
        if (powers[i].isNaN()) continue
        sum += powers[i]
        n++
    }
    return if (n == 0) null else powerToDb(sum / n)
}

/** Like [averageDbOrNull], but [FLOOR_DBFS] when there is nothing to average. */
fun averageDb(powers: List<Double>, windowChunks: Int): Double =
    averageDbOrNull(powers, windowChunks) ?: FLOOR_DBFS
