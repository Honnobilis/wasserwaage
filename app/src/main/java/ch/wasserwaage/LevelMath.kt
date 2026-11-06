package ch.wasserwaage

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Reine Winkelberechnung aus dem Beschleunigungsvektor (ax, ay, az) in Gerätekoordinaten.
 * Android-Konvention: x nach rechts, y nach oben (zur Oberkante), z aus dem Display heraus.
 * In Ruhe zeigt der gemessene Vektor nach "oben" (entgegen der Schwerkraft).
 */
object LevelMath {

    /** Ergebnis für den Wand-Modus. */
    data class Wall(
        /**
         * Auf 0/90/180/270° gerundete Richtung von "oben" auf dem Display (im Uhrzeigersinn).
         * Die Anzeige wird um diesen Winkel gedreht, damit sie aufrecht lesbar bleibt.
         */
        val snapDeg: Int,
        /**
         * Abweichung der nächstliegenden Gerätekante von waagrecht/senkrecht.
         * Positiv = in Leserichtung (so wie die Anzeige gedreht ist) ist die rechte Seite höher.
         */
        val deviationDeg: Double,
        /** Wie stark das Display aus der Senkrechten gekippt ist (0° = Handy senkrecht an der Wand). */
        val outOfPlaneDeg: Double,
    )

    /** Ergebnis für den Flächen-Modus. */
    data class Surface(
        /** Neigung entlang der langen Achse, positiv = Oberkante höher. */
        val longitudinalDeg: Double,
        /** Neigung entlang der kurzen Achse, positiv = rechte Kante höher. */
        val transverseDeg: Double,
        /** Gesamtneigung des Displays gegenüber waagrecht (> 90° = Display zeigt nach unten). */
        val tiltDeg: Double,
    )

    /**
     * @param previousSnap bisherige Ausrichtung, für eine Hysterese um die 45°-Grenzen,
     *                     damit die Anzeige nicht hin- und herspringt.
     */
    fun wall(ax: Double, ay: Double, az: Double, previousSnap: Int? = null): Wall {
        // Richtung von "oben" auf dem Display, im Uhrzeigersinn ab der Oberkante gemessen, -180..180
        val phi = deg(atan2(ax, ay))
        var snap = normalize360(((phi / 90.0).roundToInt() * 90))
        if (previousSnap != null && abs(signedDiff(phi, previousSnap.toDouble())) < 50.0) {
            snap = previousSnap
        }
        val deviation = signedDiff(phi, snap.toDouble())
        val inPlane = sqrt(ax * ax + ay * ay)
        val outOfPlane = deg(atan2(abs(az), inPlane))
        return Wall(snap, deviation, outOfPlane)
    }

    fun surface(ax: Double, ay: Double, az: Double): Surface {
        val longitudinal = deg(atan2(ay, sqrt(ax * ax + az * az)))
        val transverse = deg(atan2(ax, sqrt(ay * ay + az * az)))
        val tilt = deg(atan2(sqrt(ax * ax + ay * ay), az))
        return Surface(longitudinal, transverse, tilt)
    }

    /** Höhenunterschied in Millimeter pro Meter Länge. */
    fun mmPerMeter(angleDeg: Double): Double = tan(Math.toRadians(angleDeg)) * 1000.0

    /** Differenz a - b, auf -180..180 gebracht. */
    fun signedDiff(a: Double, b: Double): Double {
        var d = (a - b) % 360.0
        if (d > 180.0) d -= 360.0
        if (d < -180.0) d += 360.0
        return d
    }

    private fun normalize360(v: Int): Int = ((v % 360) + 360) % 360

    private fun deg(rad: Double): Double = Math.toDegrees(rad)
}
