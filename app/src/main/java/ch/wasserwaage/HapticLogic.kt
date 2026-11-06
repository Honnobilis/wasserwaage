package ch.wasserwaage

import kotlin.math.abs

/**
 * Entscheidet, wann vibriert wird. Reine Logik ohne Android-Abhängigkeit, damit testbar.
 *
 * - Im Lot (< [LEVEL_ENTER_DEG]): einmal ein kräftiger Doppelpuls, danach Ruhe.
 *   Erst wenn die Abweichung wieder über [LEVEL_LEAVE_DEG] steigt, gilt es als "nicht mehr im Lot".
 * - Annäherung (nur im Modus [Mode.APPROACH]): unterhalb von [APPROACH_START_DEG] kurze Ticks,
 *   die schneller werden, je näher man dem Lot kommt – und wieder langsamer beim Entfernen.
 * - Ruhepause: Ändert sich der Messwert [QUIET_AFTER_MS] lang kaum, verstummen die Ticks,
 *   bis wieder justiert wird.
 */
class HapticLogic {

    enum class Mode { OFF, LEVEL_ONLY, APPROACH }
    enum class Event { NONE, TICK, LEVEL }

    var mode = Mode.APPROACH

    private var isLevel = false
    private var lastTickMs = Long.MIN_VALUE / 2
    private var refDeg = Double.NaN
    private var refTimeMs = 0L

    fun reset() {
        isLevel = false
        lastTickMs = Long.MIN_VALUE / 2
        refDeg = Double.NaN
    }

    /** Aufruf bei jeder neuen Messung. [deviationDeg] = größte Abweichung (Betrag). */
    fun update(valid: Boolean, deviationDeg: Double, nowMs: Long): Event {
        if (!valid) {
            reset()
            return Event.NONE
        }
        val dev = abs(deviationDeg)

        // Bewegung erkennen für die Ruhepause
        if (refDeg.isNaN() || abs(dev - refDeg) > MOVE_THRESHOLD_DEG) {
            refDeg = dev
            refTimeMs = nowMs
        }

        if (isLevel) {
            if (dev > LEVEL_LEAVE_DEG) isLevel = false else return Event.NONE
        }
        if (dev < LEVEL_ENTER_DEG) {
            isLevel = true
            lastTickMs = nowMs
            return if (mode == Mode.OFF) Event.NONE else Event.LEVEL
        }

        if (mode != Mode.APPROACH || dev >= APPROACH_START_DEG) return Event.NONE
        if (nowMs - refTimeMs > QUIET_AFTER_MS) return Event.NONE
        if (nowMs - lastTickMs >= tickIntervalMs(dev)) {
            lastTickMs = nowMs
            return Event.TICK
        }
        return Event.NONE
    }

    companion object {
        const val LEVEL_ENTER_DEG = LevelDraw.OK_DEG
        const val LEVEL_LEAVE_DEG = 0.35
        const val APPROACH_START_DEG = 2.0
        const val MIN_INTERVAL_MS = 120L
        const val MAX_INTERVAL_MS = 600L
        const val QUIET_AFTER_MS = 3000L
        const val MOVE_THRESHOLD_DEG = 0.1

        /** Linear von [MIN_INTERVAL_MS] (am Lot) bis [MAX_INTERVAL_MS] (bei [APPROACH_START_DEG]). */
        fun tickIntervalMs(dev: Double): Long {
            val f = ((dev - LEVEL_ENTER_DEG) / (APPROACH_START_DEG - LEVEL_ENTER_DEG)).coerceIn(0.0, 1.0)
            return (MIN_INTERVAL_MS + f * (MAX_INTERVAL_MS - MIN_INTERVAL_MS)).toLong()
        }
    }
}
