package io.github.rosale137.happysilentroommate

import io.github.rosale137.happysilentroommate.alerts.AlertPolicy
import io.github.rosale137.happysilentroommate.alerts.AlertStrength
import io.github.rosale137.happysilentroommate.alerts.AlertStrength.Gentle
import io.github.rosale137.happysilentroommate.alerts.AlertStrength.Medium
import io.github.rosale137.happysilentroommate.alerts.AlertStrength.Strong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AlertPolicyTest {

    private val limit = -35.0
    private val hysteresis = 3.0
    private val first = 8_000L
    private val fastest = 2_000L

    private fun AlertPolicy.at(level: Double, nowMs: Long) =
        update(level, limit, hysteresis, first, fastest, nowMs)

    /** Feeds [level] every 100 ms over [range]; returns (time, strength) of each buzz. */
    private fun AlertPolicy.buzzes(level: Double, range: LongProgression): List<Pair<Long, AlertStrength>> =
        range.mapNotNull { t -> at(level, t)?.let { t to it } }

    @Test
    fun quietNeverBuzzes() {
        assertEquals(emptyList<Any>(), AlertPolicy().buzzes(-50.0, 0L..100_000L step 100))
    }

    @Test
    fun goingAboveTheLimitBuzzesGentlyRightAway() {
        val p = AlertPolicy()
        assertNull(p.at(-40.0, 0))
        assertEquals(Gentle, p.at(-30.0, 100))
        assertNull(p.at(-30.0, 200))
    }

    @Test
    fun stayingLoudRemindsFasterAndStronger() {
        val buzzes = AlertPolicy().buzzes(-30.0, 0L..30_000L step 100)
        // Sampled every 100 ms, so 3.92 s shows as 4.0 s and 2.74 s as 2.8 s.
        val gaps = buzzes.map { it.first }.zipWithNext { a, b -> b - a }
        assertEquals(listOf(8_000L, 5_600L, 4_000L, 2_800L, 2_000L, 2_000L, 2_000L, 2_000L), gaps)
        assertEquals(
            listOf(Gentle, Gentle, Medium, Medium, Strong, Strong, Strong, Strong, Strong),
            buzzes.map { it.second },
        )
    }

    @Test
    fun equalFirstAndFastestMeansSteadyReminders() {
        val p = AlertPolicy()
        val times = (0L..20_000L step 100).filter { p.update(-30.0, limit, hysteresis, 5_000, 5_000, it) != null }
        assertEquals(listOf(0L, 5_000L, 10_000L, 15_000L, 20_000L), times)
    }

    @Test
    fun dippingInsideTheHysteresisBandKeepsTheSpeedUp() {
        val p = AlertPolicy()
        assertNotNull(p.at(-34.0, 0))
        assertNotNull(p.at(-34.0, 8_000))
        assertNull(p.at(-37.0, 9_000))  // just under the limit, inside the band
        assertNull(p.at(-34.0, 10_000)) // back up: same stretch, no extra buzz
        assertNotNull(p.at(-34.0, 13_600)) // next gap is the shorter 5.6 s
    }

    @Test
    fun relapseSoonAfterContinuesAtTheHigherUrgency() {
        val p = AlertPolicy()
        p.buzzes(-30.0, 0L..20_400L step 100) // 5 buzzes: stage is now 5
        assertNull(p.at(-39.0, 21_000))      // quiet
        // Loud again 15 s later: one stage calmer (4), still strong, and the next gap is short.
        assertEquals(Strong, p.at(-30.0, 36_000))
        assertNull(p.at(-30.0, 37_900))
        assertNotNull(p.at(-30.0, 38_000))
    }

    @Test
    fun longQuietCalmsDownCompletely() {
        val p = AlertPolicy()
        p.buzzes(-30.0, 0L..30_000L step 100) // fully escalated
        assertNull(p.at(-39.0, 31_000))
        assertEquals(Gentle, p.at(-30.0, 31_000 + AlertPolicy.MAX_STAGE * AlertPolicy.CALM_DOWN_STEP_MS))
        assertEquals(1, p.stage)
    }

    @Test
    fun newStretchesNeverBuzzFasterThanTheFastestInterval() {
        val p = AlertPolicy()
        assertNotNull(p.at(-30.0, 0))
        assertNull(p.at(-45.0, 500))
        assertNull(p.at(-30.0, 1_000))
        assertNotNull(p.at(-30.0, 2_000))
    }

    @Test
    fun reminderGapsForDisplay() {
        assertEquals(listOf(8_000L, 5_600L, 3_920L, 2_744L, 2_000L), AlertPolicy.reminderGapsMs(8_000, 2_000))
        assertEquals(listOf(5_000L), AlertPolicy.reminderGapsMs(5_000, 5_000))
        assertEquals(listOf(3_000L), AlertPolicy.reminderGapsMs(3_000, 9_000))
    }
}
