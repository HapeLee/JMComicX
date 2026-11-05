package io.kazusa.jmcomic.effect

import io.kazusa.jmcomic.effect.liquid.matterTabPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class MatterTabPositionTest {
    @Test
    fun crossingFormerPressThresholdDoesNotJumpToDragPosition() {
        val before = matterTabPosition(0.25f, 1.75f, 0.0099f)
        val after = matterTabPosition(0.25f, 1.75f, 0.0101f)

        // The previous threshold switched between positions 1.5 pages apart in one frame.
        assertTrue(abs(after - before) < 0.001f)
    }

    @Test
    fun releaseReturnsContinuouslyToPager() {
        val positions = (100 downTo 0).map {
            matterTabPosition(0.25f, 1.75f, it / 100f)
        }
        assertEquals(1.75f, positions.first(), 0f)
        assertEquals(0.25f, positions.last(), 0f)
        assertTrue(positions.zipWithNext().all { (a, b) -> a >= b && a - b < 0.016f })
    }

    @Test
    fun absentPagerUsesDragThroughoutPressAndRelease() {
        for (press in listOf(-0.1f, 0f, 0.5f, 1f, 1.1f)) {
            assertEquals(1.25f, matterTabPosition(null, 1.25f, press), 0f)
        }
    }
}
