package dev.jmx.client

import androidx.compose.ui.geometry.Rect
import dev.jmx.client.core.api.ActionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class AlbumDetailTransitionTest {
    @Test
    fun actionResultRejectsNonSuccessStatus() {
        assertEquals(
            null,
            ActionResult("ok", "done", null, emptyMap()).rejectionMessageOrNull(),
        )
        assertEquals(
            "账号权限不足",
            ActionResult("error", "账号权限不足", null, emptyMap()).rejectionMessageOrNull(),
        )
        assertEquals(
            "勿短时间重复留言,请重新确认留言内容",
            ActionResult(null, "勿短时间重复留言,请重新确认留言内容", null, emptyMap())
                .rejectionMessageOrNull(),
        )
    }

    @Test
    fun curvedCoverBoundsKeepsEndpointsAndBendsAwayFromStraightLine() {
        val start = Rect(left = 720f, top = 1500f, right = 1020f, bottom = 1900f)
        val end = Rect(left = 44f, top = 280f, right = 396f, bottom = 749f)

        val atStart = curvedCoverBounds(start, end, progress = 0f, maxBend = 280f)
        val atMiddle = curvedCoverBounds(start, end, progress = 0.5f, maxBend = 280f)
        val atEnd = curvedCoverBounds(start, end, progress = 1f, maxBend = 280f)

        assertRectEquals(start, atStart)
        assertRectEquals(end, atEnd)

        val linearMidX = (start.left + end.left) / 2f
        val linearMidY = (start.top + end.top) / 2f
        val bendDistance = hypot(atMiddle.left - linearMidX, atMiddle.top - linearMidY)
        assertTrue("Bezier midpoint must visibly deviate from a straight path", bendDistance > 40f)
    }

    @Test
    fun panelMorphFrameInterpolatesBoundsAndCorners() {
        val start = Rect(100f, 200f, 300f, 500f)
        val end = Rect(0f, 0f, 1080f, 2400f)

        val frameStart = dev.jmx.client.effect.computePanelMorphFrame(
            progress = 0f,
            start = start,
            end = end,
            startCornerRadiusPx = 24f,
            endCornerRadiusPx = 0f,
        )
        val frameMid = dev.jmx.client.effect.computePanelMorphFrame(
            progress = 0.5f,
            start = start,
            end = end,
            startCornerRadiusPx = 24f,
            endCornerRadiusPx = 0f,
        )
        val frameEnd = dev.jmx.client.effect.computePanelMorphFrame(
            progress = 1f,
            start = start,
            end = end,
            startCornerRadiusPx = 24f,
            endCornerRadiusPx = 0f,
        )

        requireNotNull(frameStart)
        requireNotNull(frameMid)
        requireNotNull(frameEnd)

        assertRectEquals(start, frameStart.bounds)
        assertEquals(24f, frameStart.cornerRadiusPx, 0.001f)

        assertRectEquals(end, frameEnd.bounds)
        assertEquals(0f, frameEnd.cornerRadiusPx, 0.001f)

        assertEquals((start.width + end.width) / 2f, frameMid.bounds.width, 0.001f)
        assertEquals((start.height + end.height) / 2f, frameMid.bounds.height, 0.001f)
        assertEquals(12f, frameMid.cornerRadiusPx, 0.001f)
    }

    @Test
    fun coverMorphFrameAppliesDualEasingCurves() {
        val start = Rect(left = 720f, top = 1500f, right = 1020f, bottom = 1900f)
        val end = Rect(left = 44f, top = 280f, right = 396f, bottom = 749f)

        val frameStart = dev.jmx.client.effect.computeCoverMorphFrame(
            progress = 0f,
            start = start,
            end = end,
            startCornerRadiusPx = 24f,
            endCornerRadiusPx = 24f,
        )
        val frameMid = dev.jmx.client.effect.computeCoverMorphFrame(
            progress = 0.5f,
            start = start,
            end = end,
            startCornerRadiusPx = 24f,
            endCornerRadiusPx = 24f,
        )
        val frameEnd = dev.jmx.client.effect.computeCoverMorphFrame(
            progress = 1f,
            start = start,
            end = end,
            startCornerRadiusPx = 24f,
            endCornerRadiusPx = 24f,
        )

        requireNotNull(frameStart)
        requireNotNull(frameMid)
        requireNotNull(frameEnd)

        assertRectEquals(start, frameStart.bounds)
        assertRectEquals(end, frameEnd.bounds)

        // At progress = 0.5:
        // e1 = 1 - (1 - 0.5)^2 = 0.75 (position ease: moves 75% horizontally)
        // e2 = 0.5^2 = 0.25 (size ease: moves only 25% vertically)
        val expectedCenterX = start.center.x + (end.center.x - start.center.x) * 0.75f
        val expectedCenterY = start.center.y + (end.center.y - start.center.y) * 0.25f
        assertEquals(expectedCenterX, frameMid.bounds.center.x, 0.01f)
        assertEquals(expectedCenterY, frameMid.bounds.center.y, 0.01f)

        // Width and height grow with e2 (slow at start)
        val expectedWidth = start.width + (end.width - start.width) * 0.25f
        assertEquals(expectedWidth, frameMid.bounds.width, 0.01f)
    }

    @Test
    fun morphVeilStaysZeroInStartWindowThenFadesSmoothly() {
        assertEquals(0f, dev.jmx.client.effect.computeMorphVeil(0f), 0.001f)
        assertEquals(0f, dev.jmx.client.effect.computeMorphVeil(0.15f), 0.001f)
        assertEquals(0f, dev.jmx.client.effect.computeMorphVeil(0.20f), 0.001f)
        assertEquals(1f, dev.jmx.client.effect.computeMorphVeil(0.65f), 0.001f)
        assertEquals(1f, dev.jmx.client.effect.computeMorphVeil(1f), 0.001f)

        val midVeil = dev.jmx.client.effect.computeMorphVeil(0.425f)
        assertEquals(0.5f, midVeil, 0.05f)

        assertTrue(dev.jmx.client.effect.morphContentSettled(veil = 1f, progress = 0.8f))
        assertTrue(dev.jmx.client.effect.morphContentSettled(veil = 0f, progress = 0.0005f))
        org.junit.Assert.assertFalse(dev.jmx.client.effect.morphContentSettled(veil = 0.5f, progress = 0.4f))
    }

    @Test
    fun predictiveBackProgressAndVelocityLimitsWork() {
        val preview = dev.jmx.client.effect.computePredictiveMorphProgress(startProgress = 1f, backProgress = 0.5f)
        assertEquals(1f * (1f - 0.45f * 0.5f), preview, 0.001f)

        val limitedVelocity = dev.jmx.client.effect.computeMorphSettleVelocity(
            current = 0.1f,
            target = 0f,
            velocity = 50f,
        )
        assertTrue(limitedVelocity <= 3f)
    }

    @Test
    fun boundsVisibilityDetectsScreenIntersection() {
        val screen = Rect(0f, 0f, 1080f, 2400f)
        val inside = Rect(100f, 100f, 400f, 500f)
        val offscreen = Rect(0f, 3000f, 400f, 3500f)

        assertTrue(dev.jmx.client.effect.isBoundsVisibleInScreen(inside, screen))
        org.junit.Assert.assertFalse(dev.jmx.client.effect.isBoundsVisibleInScreen(offscreen, screen))
    }

    private fun assertRectEquals(expected: Rect, actual: Rect) {
        assertEquals(expected.left, actual.left, 0.001f)
        assertEquals(expected.top, actual.top, 0.001f)
        assertEquals(expected.right, actual.right, 0.001f)
        assertEquals(expected.bottom, actual.bottom, 0.001f)
    }
}
