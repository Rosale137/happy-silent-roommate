package io.github.rosale137.happysilentroommate

import io.github.rosale137.happysilentroommate.audio.FLOOR_DBFS
import io.github.rosale137.happysilentroommate.audio.averageDb
import io.github.rosale137.happysilentroommate.audio.meanSquare
import io.github.rosale137.happysilentroommate.audio.movingAverageDb
import io.github.rosale137.happysilentroommate.audio.powerToDb
import io.github.rosale137.happysilentroommate.audio.averageDbOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun averageOfNothingIsTheFloor() {
        assertEquals(FLOOR_DBFS, averageDb(emptyList(), 5), 0.0)
    }

    @Test
    fun averageUsesOnlyTheNewestChunks() {
        val powers = listOf(1.0, 1.0, 1e-6, 1e-6)
        assertEquals(powerToDb(1e-6), averageDb(powers, 2), 0.001)
        assertEquals(powerToDb((2.0 + 2e-6) / 4), averageDb(powers, 10), 0.001)
    }

    @Test
    fun movingAverageForgetsOldChunksAndHandlesTheStart() {
        val series = movingAverageDb(listOf(1.0, 1.0, 1e-6, 1e-6), windowChunks = 2)
        assertEquals(4, series.size)
        assertEquals(0.0, series[0], 0.001)
        assertEquals(0.0, series[1], 0.001)
        assertEquals(powerToDb((1.0 + 1e-6) / 2), series[2], 0.001)
        assertEquals(powerToDb(1e-6), series[3], 0.001)
    }

    @Test
    fun ignoredReadingsAreLeftOutOfTheAverage() {
        val powers = listOf(1e-6, Double.NaN, Double.NaN, 1e-6)
        assertEquals(powerToDb(1e-6), averageDb(powers, 4), 0.001)
        assertEquals(powerToDb(1e-6), movingAverageDb(powers, 4).last(), 0.001)
    }

    @Test
    fun onlyIgnoredReadingsGiveNoLevel() {
        val powers = listOf(1e-6, Double.NaN, Double.NaN)
        assertNull(averageDbOrNull(powers, 2))
        assertEquals(FLOOR_DBFS, averageDb(powers, 2), 0.0)
        assertTrue(movingAverageDb(powers, 2).last().isNaN())
    }

    @Test
    fun movingAverageLastValueMatchesAverageDb() {
        val powers = List(50) { (it % 7 + 1) * 1e-4 }
        assertEquals(averageDb(powers, 12), movingAverageDb(powers, 12).last(), 1e-9)
    }
}
