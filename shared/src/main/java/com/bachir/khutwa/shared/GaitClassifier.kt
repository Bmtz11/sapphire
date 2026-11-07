package com.bachir.khutwa.shared

/**
 * Decides walk vs run inside one session.
 * Speed is the main signal (your cadence is low, so cadence alone would misread runs as walks).
 * Hysteresis + confirmation windows stop it from flipping at traffic lights or short slowdowns.
 */
class GaitClassifier(
    var runSpeed: Double = 2.05,        // m/s ≈ 7.4 km/h ≈ 8:08 /km
    var walkSpeed: Double = 1.80,       // m/s ≈ 6.5 km/h
    var runCadence: Double = 140.0,     // spm, only used as a helper signal
    var confirmRunMs: Long = 8_000,
    var confirmWalkMs: Long = 12_000,
) {
    var mode = Mode.WALK
        private set
    private var candidateSince: Long? = null

    /** Returns the timestamp the switch really started at, or null if no switch. */
    fun update(t: Long, speed: Double, cadence: Double?): Long? {
        val looksRun = speed >= runSpeed ||
            (cadence != null && cadence >= runCadence && speed >= walkSpeed)
        val looksWalk = speed < walkSpeed && (cadence == null || cadence < runCadence)
        val wantsSwitch = if (mode == Mode.WALK) looksRun else looksWalk

        if (!wantsSwitch) { candidateSince = null; return null }
        val since = candidateSince ?: t.also { candidateSince = it }
        val need = if (mode == Mode.WALK) confirmRunMs else confirmWalkMs
        if (t - since >= need) {
            mode = if (mode == Mode.WALK) Mode.RUN else Mode.WALK
            candidateSince = null
            return since
        }
        return null
    }
}
