package ch.wasserwaage

import ch.wasserwaage.HapticLogic.Event
import ch.wasserwaage.HapticLogic.Mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HapticLogicTest {

    @Test
    fun farAway_isSilent() {
        val h = HapticLogic()
        for (t in 0L..2000L step 20) assertEquals(Event.NONE, h.update(true, 5.0, t))
    }

    @Test
    fun reachingLevel_givesOneLevelEventThenSilence() {
        val h = HapticLogic()
        assertEquals(Event.LEVEL, h.update(true, 0.1, 0))
        for (t in 20L..2000L step 20) assertEquals(Event.NONE, h.update(true, 0.15, t))
    }

    @Test
    fun noiseAtBorder_doesNotRetrigger() {
        val h = HapticLogic().apply { mode = Mode.LEVEL_ONLY }
        assertEquals(Event.LEVEL, h.update(true, 0.19, 0))
        assertEquals(Event.NONE, h.update(true, 0.25, 20))
        assertEquals(Event.NONE, h.update(true, 0.18, 40))
        // deutlich raus und wieder rein → erneut
        assertEquals(Event.NONE, h.update(true, 0.5, 60))
        assertEquals(Event.LEVEL, h.update(true, 0.1, 80))
    }

    @Test
    fun ticksGetFasterWhenCloser() {
        assertEquals(600L, HapticLogic.tickIntervalMs(2.0))
        assertEquals(120L, HapticLogic.tickIntervalMs(0.2))
        assertTrue(HapticLogic.tickIntervalMs(0.5) < HapticLogic.tickIntervalMs(1.5))
    }

    @Test
    fun approach_ticksWhileMoving_andGoesQuietWhenStill() {
        val h = HapticLogic()
        var ticks = 0
        // langsam von 1,9° auf 1,0° bewegen → tickt
        var dev = 1.9
        var last = dev
        for (t in 0L..2000L step 20) {
            if (h.update(true, dev, t) == Event.TICK) ticks++
            last = dev
            dev -= 0.009
        }
        assertTrue(ticks > 2)
        // dann still liegen lassen: nach der Ruhepause keine Ticks mehr
        ticks = 0
        for (t in 6000L..9000L step 20) if (h.update(true, last, t) == Event.TICK) ticks++
        assertEquals(0, ticks)
    }

    @Test
    fun levelOnly_neverTicks() {
        val h = HapticLogic().apply { mode = Mode.LEVEL_ONLY }
        for (t in 0L..1000L step 20) assertEquals(Event.NONE, h.update(true, 1.0 - t / 2000.0, t))
    }

    @Test
    fun off_isAlwaysSilent() {
        val h = HapticLogic().apply { mode = Mode.OFF }
        assertEquals(Event.NONE, h.update(true, 1.0, 0))
        assertEquals(Event.NONE, h.update(true, 0.1, 500))
    }
}
