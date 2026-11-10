package io.github.rosale137.happysilentroommate

import io.github.rosale137.happysilentroommate.audio.FLOOR_DBFS
import io.github.rosale137.happysilentroommate.audio.PowerAverager
import io.github.rosale137.happysilentroommate.audio.meanSquare
import io.github.rosale137.happysilentroommate.audio.powerToDb
import org.junit.Assert.assertEquals
import org.junit.Test

class LevelMathTest {

    @Test
    fun silenceIsAtTheFloor() {
        assertEquals(FLOOR_DBFS, powerToDb(meanSquare(ShortArray(100), 100)), 0.0)
    }

    @Test
    fun fullScaleSquareWaveIsZeroDb() {
        val samples = ShortArray(100) { if (it % 2 == 0) Short.MAX_VALUE else Short.MIN_VALUE }
        assertEquals(0.0, powerToDb(meanSquare(samples, 100)), 0.01)
    }

    @Test
    fun halvingTheAmplitudeIsAboutMinusSixDb() {
        val samples = ShortArray(100) { if (it % 2 == 0) 16384 else -16384 }
        assertEquals(-6.02, powerToDb(meanSquare(samples, 100)), 0.05)
    }

    @Test
    fun averagerForgetsOldChunks() {
        val averager = PowerAverager(size = 2)
        averager.add(1.0)
        averager.add(1.0)
        val quiet = averager.add(1e-6)
        val quieter = averager.add(1e-6)
        assertEquals(powerToDb(1e-6), quieter, 0.001)
        assertEquals(powerToDb((1.0 + 1e-6) / 2), quiet, 0.001)
    }
}
