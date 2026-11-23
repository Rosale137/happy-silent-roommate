package io.github.rosale137.happysilentroommate

import io.github.rosale137.happysilentroommate.schedule.QuietWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class QuietWindowTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private fun at(h: Int, m: Int, s: Int = 0) = ((h * 60 + m) * 60 + s) * 1000L
    private val minute = 60_000L

    private val evening = QuietWindow(21 * 60, 23 * 60 + 30)
    private val overMidnight = QuietWindow(22 * 60, 1 * 60)

    @Test
    fun eveningWindowIncludesStartButNotEnd() {
        assertFalse(evening.contains(20 * 60 + 59))
        assertTrue(evening.contains(21 * 60))
        assertTrue(evening.contains(23 * 60 + 29))
        assertFalse(evening.contains(23 * 60 + 30))
    }

    @Test
    fun windowAcrossMidnight() {
        assertFalse(overMidnight.contains(21 * 60 + 59))
        assertTrue(overMidnight.contains(22 * 60))
        assertTrue(overMidnight.contains(0))
        assertTrue(overMidnight.contains(59))
        assertFalse(overMidnight.contains(60))
    }

    @Test
    fun equalStartAndEndIsNoQuietTime() {
        val none = QuietWindow(600, 600)
        assertTrue(none.isEmpty)
        for (m in 0 until 1440 step 7) assertFalse(none.contains(m))
    }

    @Test
    fun nextBoundaryBeforeTheWindowIsTheStart() {
        assertEquals(at(21, 0), evening.nextBoundaryMillis(at(20, 15, 30), utc))
    }

    @Test
    fun nextBoundaryInsideTheWindowIsTheEnd() {
        assertEquals(at(23, 30), evening.nextBoundaryMillis(at(21, 0), utc))
    }

    @Test
    fun nextBoundaryAfterTheWindowIsTomorrowsStart() {
        assertEquals(at(21, 0) + 1440 * minute, evening.nextBoundaryMillis(at(23, 30), utc))
    }

    @Test
    fun nextBoundaryAcrossMidnight() {
        assertEquals(at(1, 0) + 1440 * minute, overMidnight.nextBoundaryMillis(at(23, 0), utc))
        assertEquals(at(1, 0), overMidnight.nextBoundaryMillis(at(0, 30), utc))
    }

    @Test
    fun nextEndForStopForTonight() {
        assertEquals(at(23, 30), evening.nextEndMillis(at(22, 10, 5), utc))
        assertEquals(at(1, 0) + 1440 * minute, overMidnight.nextEndMillis(at(22, 10), utc))
    }

    @Test
    fun formatsTimes() {
        assertEquals("21:00", QuietWindow.format(21 * 60))
        assertEquals("07:05", QuietWindow.format(7 * 60 + 5))
    }
}
