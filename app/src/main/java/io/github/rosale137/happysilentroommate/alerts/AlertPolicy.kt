package io.github.rosale137.happysilentroommate.alerts

import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToLong

/** How insistent a buzz is. Rises with the urgency, see [AlertPolicy]. */
enum class AlertStrength { Gentle, Medium, Strong }

/**
 * Decides when to vibrate and how strongly. Pure logic, no Android, so it can be unit-tested.
 *
 * A "loud stretch" starts when the integrated level goes above the limit and ends only when it
 * drops below `limit - hysteresis`. Dipping into that band and back up is the same stretch.
 *
 * Urgency is a stage number, 0 = calm:
 * - Start of a stretch: buzz right away at the current stage.
 * - Still too loud: the next buzz comes after the gap for the current stage, one stage higher.
 *   The gap is `firstRemindMs * SPEED_UP^stage`, never shorter than `fastestRemindMs`, so the
 *   buzzes get faster. The strength rises with the stage too (gentle, medium, strong).
 * - Quiet: the urgency fades slowly, one stage per [CALM_DOWN_STEP_MS]. Getting loud again soon
 *   after a reminder continues where it was instead of starting gently again.
 * - Never two buzzes closer than `fastestRemindMs`.
 */
class AlertPolicy {

    private var inLoudStretch = false
    private var lastAlertMs: Long? = null
    private var quietSinceMs: Long? = null

    /** Urgency stage of the next buzz. */
    var stage = 0
        private set

    /** Forget the current loud stretch, e.g. when the microphone restarts. Urgency is kept. */
    fun reset() {
        inLoudStretch = false
    }

    /** Feed the newest integrated level. Returns the strength to buzz with now, or null. */
    fun update(
        levelDb: Double,
        limitDb: Double,
        hysteresisDb: Double,
        firstRemindMs: Long,
        fastestRemindMs: Long,
        nowMs: Long,
    ): AlertStrength? {
        val fastest = minOf(fastestRemindMs, firstRemindMs)
        if (levelDb < limitDb - hysteresisDb && inLoudStretch) {
            inLoudStretch = false
            quietSinceMs = nowMs
        }
        if (levelDb <= limitDb) return null

        val sinceLast = lastAlertMs?.let { nowMs - it } ?: Long.MAX_VALUE
        if (!inLoudStretch) {
            if (sinceLast < fastest) return null
            quietSinceMs?.let { stage = max(0, stage - ((nowMs - it) / CALM_DOWN_STEP_MS).toInt()) }
            quietSinceMs = null
            inLoudStretch = true
        } else {
            // Buzzing at `stage - 1` set the gap we are now waiting for.
            if (sinceLast < gapMs(stage - 1, firstRemindMs, fastest)) return null
        }
        lastAlertMs = nowMs
        val strength = strengthFor(stage)
        stage = minOf(stage + 1, MAX_STAGE)
        return strength
    }

    companion object {
        /** Each reminder gap is this fraction of the previous one. */
        const val SPEED_UP = 0.7

        /** While quiet, the urgency drops one stage per this time. */
        const val CALM_DOWN_STEP_MS = 10_000L

        /**
         * Urgency never goes above this, so calming down takes at most 100 s. High enough that
         * the gaps can shrink from 30 s to 1 s (0.7^10 is about 1/35).
         */
        const val MAX_STAGE = 10

        /** Gap after a buzz at [stage]. */
        fun gapMs(stage: Int, firstRemindMs: Long, fastestRemindMs: Long): Long =
            max(minOf(fastestRemindMs, firstRemindMs), (firstRemindMs * SPEED_UP.pow(max(0, stage))).roundToLong())

        fun strengthFor(stage: Int): AlertStrength = when {
            stage < 2 -> AlertStrength.Gentle
            stage < 4 -> AlertStrength.Medium
            else -> AlertStrength.Strong
        }

        /**
         * The gaps between reminders while staying loud from calm, until they reach the fastest,
         * e.g. 8000, 5600, 3920, 2744, 2000. For showing the sequence on screen.
         */
        fun reminderGapsMs(firstRemindMs: Long, fastestRemindMs: Long): List<Long> {
            val fastest = minOf(fastestRemindMs, firstRemindMs)
            val gaps = mutableListOf<Long>()
            var s = 0
            do gaps += gapMs(s++, firstRemindMs, fastest) while (gaps.last() > fastest)
            return gaps
        }
    }
}
