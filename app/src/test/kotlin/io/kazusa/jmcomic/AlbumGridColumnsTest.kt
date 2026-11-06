package io.kazusa.jmcomic

import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumGridColumnsTest {
    @Test
    fun phoneWidthKeepsTheOriginalThreeColumnsForBothExistingSpacings() {
        // 360dp 屏幕：普通页左右各 10dp + 4dp 间距；离线页左右各 12dp + 8dp 间距。
        assertEquals(3, adaptiveGridColumnCount(340, 4, 100))
        assertEquals(3, adaptiveGridColumnCount(336, 8, 100))
        // 大屏手机仍是 3 列，折叠屏外屏等窄宽度自动降到 2 列。
        assertEquals(3, adaptiveGridColumnCount(391, 4, 100))
        assertEquals(2, adaptiveGridColumnCount(300, 4, 100))
        assertEquals(2, adaptiveGridColumnCount(220, 4, 100))
    }

    @Test
    fun widerScreensAddColumnsButNeverExceedTheCeiling() {
        assertEquals(5, adaptiveGridColumnCount(580, 4, 100))
        assertEquals(7, adaptiveGridColumnCount(780, 4, 100))
        assertEquals(GRID_MAX_COLUMNS, adaptiveGridColumnCount(1004, 4, 100))
        assertEquals(GRID_MAX_COLUMNS, adaptiveGridColumnCount(1260, 4, 100))
        assertEquals(1, adaptiveGridColumnCount(50, 4, 100))
    }

    @Test
    fun cellSizesFillTheAvailableWidthAndRespectTheMinimumColumnWidth() {
        for (available in listOf(300, 340, 391, 580, 780, 1004, 1260)) {
            for (spacing in listOf(4, 8)) {
                val count = adaptiveGridColumnCount(available, spacing, 100)
                val sizes = adaptiveGridCellSizes(available, spacing, count)
                assertEquals(count, sizes.size)
                assertEquals(available, sizes.sum() + spacing * (count - 1))
                assertTrue(sizes.min() >= 100)
                assertTrue(sizes.max() - sizes.min() <= 1)
            }
        }
    }

    @Test
    fun densityScalingDoesNotChangeTheColumnCount() {
        // 100dp / 4dp / 8dp 按同一比例换算成像素后，列数不应随屏幕密度漂移。
        for (scale in listOf(1f, 2f, 2.75f, 3f)) {
            val minWidthPx = (100f * scale).roundToInt()
            val sizes = adaptiveGridColumnSizes(
                (340f * scale).roundToInt(),
                (4f * scale).roundToInt(),
                minWidthPx,
            )
            assertEquals(3, sizes.size)
            val offlineSizes = adaptiveGridColumnSizes(
                (336f * scale).roundToInt(),
                (8f * scale).roundToInt(),
                minWidthPx,
            )
            assertEquals(3, offlineSizes.size)
        }
    }

    @Test
    fun degenerateMeasurementsStaySafe() {
        assertEquals(1, adaptiveGridColumnCount(0, 4, 100))
        assertEquals(1, adaptiveGridColumnCount(-10, 4, 100))
        assertEquals(1, adaptiveGridColumnCount(340, 4, 0))
        assertTrue(adaptiveGridCellSizes(0, 4, 3).isEmpty())
        assertTrue(adaptiveGridCellSizes(340, 4, 0).isEmpty())
        assertEquals(List(3) { 0 }, adaptiveGridCellSizes(8, 4, 3))
    }
}
