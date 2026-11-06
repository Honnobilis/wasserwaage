package ch.wasserwaage

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class LevelMathTest {
    private val g = 9.81
    private fun rad(d: Double) = Math.toRadians(d)

    @Test
    fun wall_portraitUpright_isZero() {
        val r = LevelMath.wall(0.0, g, 0.0)
        assertEquals(0, r.snapDeg)
        assertEquals(0.0, r.deviationDeg, 1e-9)
        assertEquals(0.0, r.outOfPlaneDeg, 1e-9)
    }

    @Test
    fun wall_smallClockwiseRotation_isPositive() {
        // "Oben" liegt etwas rechts der Oberkante → rechte Seite ist höher
        val r = LevelMath.wall(g * sin(rad(2.0)), g * cos(rad(2.0)), 0.0)
        assertEquals(0, r.snapDeg)
        assertEquals(2.0, r.deviationDeg, 1e-9)
    }

    @Test
    fun wall_landscape_snapsTo90and270() {
        val right = LevelMath.wall(g * sin(rad(91.0)), g * cos(rad(91.0)), 0.0)
        assertEquals(90, right.snapDeg)
        assertEquals(1.0, right.deviationDeg, 1e-9)

        val left = LevelMath.wall(-g, 0.0, 0.0)
        assertEquals(270, left.snapDeg)
        assertEquals(0.0, left.deviationDeg, 1e-9)
    }

    @Test
    fun wall_upsideDown_snapsTo180() {
        val r = LevelMath.wall(g * sin(rad(178.0)), g * cos(rad(178.0)), 0.0)
        assertEquals(180, r.snapDeg)
        assertEquals(-2.0, r.deviationDeg, 1e-9)
    }

    @Test
    fun wall_hysteresisKeepsPreviousSnap() {
        // 47° würde normalerweise auf 90° gerundet, mit vorheriger Ausrichtung 0° bleibt es bei 0°
        val r = LevelMath.wall(g * sin(rad(47.0)), g * cos(rad(47.0)), 0.0, previousSnap = 0)
        assertEquals(0, r.snapDeg)
        assertEquals(47.0, r.deviationDeg, 1e-9)
        val r2 = LevelMath.wall(g * sin(rad(55.0)), g * cos(rad(55.0)), 0.0, previousSnap = 0)
        assertEquals(90, r2.snapDeg)
    }

    @Test
    fun surface_flat_isZero() {
        val r = LevelMath.surface(0.0, 0.0, g)
        assertEquals(0.0, r.longitudinalDeg, 1e-9)
        assertEquals(0.0, r.transverseDeg, 1e-9)
        assertEquals(0.0, r.tiltDeg, 1e-9)
    }

    @Test
    fun surface_rightEdgeUp_positiveTransverse() {
        val r = LevelMath.surface(g * sin(rad(3.0)), 0.0, g * cos(rad(3.0)))
        assertEquals(3.0, r.transverseDeg, 1e-9)
        assertEquals(0.0, r.longitudinalDeg, 1e-9)
    }

    @Test
    fun surface_topEdgeDown_negativeLongitudinal() {
        val r = LevelMath.surface(0.0, -g * sin(rad(1.5)), g * cos(rad(1.5)))
        assertEquals(-1.5, r.longitudinalDeg, 1e-9)
    }

    @Test
    fun mmPerMeter_45degIs1000() {
        assertEquals(1000.0, LevelMath.mmPerMeter(45.0), 1e-6)
        assertEquals(17.455, LevelMath.mmPerMeter(1.0), 1e-3)
    }
}
