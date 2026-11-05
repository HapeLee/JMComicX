package io.kazusa.jmcomic.effect

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.dp
import io.kazusa.jmcomic.effect.liquid.FloatingCategoryMenuState
import io.kazusa.jmcomic.effect.liquid.MatterBarMotion
import io.kazusa.jmcomic.effect.liquid.matterButtonFrame
import io.kazusa.jmcomic.effect.liquid.matterCapsuleScaleTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatterBarMotionTest {
    @Test
    fun capsuleAndButtonKeepTheSameGapDuringCompressionAndSpringOvershoot() {
        val scale = mutableStateOf(1f)
        val motion = MatterBarMotion(360.dp, 216.dp, 36.dp, scale, mutableStateOf(1f))
        for (value in listOf(1f, 0.95f, 0.90f, 0.88f, 0.9074f, 1.04f)) {
            scale.value = value
            val capsuleRight = (motion.screenWidth.value + motion.capsuleWidth.value) / 2f
            val buttonLeft = motion.screenWidth.value - motion.buttonEndInset.value - 56f
            assertEquals(10f, buttonLeft - capsuleRight, 0.0001f)
        }
    }

    @Test
    fun compactScreenReservesButtonAndEdgeMarginButWideScreenKeepsFullWidth() {
        val compactScale = matterCapsuleScaleTarget(360f, 216f)
        val buttonRight = (360f + 216f * compactScale) / 2f + 10f + 56f
        assertEquals(344f, buttonRight, 0.0001f)
        assertEquals(1f, matterCapsuleScaleTarget(600f, 216f), 0f)
        assertEquals(0.7f, matterCapsuleScaleTarget(240f, 216f), 0f)
    }

    @Test
    fun revealRetainsBounceBeyondFullyVisibleWhileOpacityStaysValid() {
        val frame = matterButtonFrame(1.08f)
        assertEquals(1f, frame.alpha, 0f)
        assertTrue(frame.scale > 1f)
        assertTrue(frame.offsetX < 0f && frame.offsetY < 0f)
        val settled = matterButtonFrame(1f)
        assertEquals(1f, settled.scale, 0f)
        assertEquals(0f, settled.offsetX, 0f)
        assertEquals(0f, settled.offsetY, 0f)
        val exiting = matterButtonFrame(-0.04f)
        assertEquals(0f, exiting.alpha, 0f)
        assertTrue(exiting.scale >= 0f)
    }

    @Test
    fun hiddenOrReplacedMenuCannotInvokeAnOldSelection() {
        val controller = FloatingCategoryMenuState()
        val oldSelections = mutableListOf<Int>()
        val newSelections = mutableListOf<Int>()
        controller.update(listOf("全部", "分组"), 0, true) { oldSelections += it }
        controller.select(1)
        controller.update(listOf("全部"), 0, false) { newSelections += it }
        controller.select(0)
        controller.update(listOf("全部"), 0, true) { newSelections += it }
        controller.select(1)
        controller.select(0)
        assertEquals(listOf(1), oldSelections)
        assertEquals(listOf(0), newSelections)
    }
}
