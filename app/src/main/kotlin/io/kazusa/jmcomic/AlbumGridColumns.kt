package io.kazusa.jmcomic

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 漫画封面是 3:4 的固定宽高比，卡片本身没有固定宽度，列宽可以随屏幕自由变化。
 * 100dp 是让 360dp 手机在 4dp / 8dp 两种现有间距下都维持原先 3 列的最小列宽；
 * 平板、折叠屏展开态和横屏按可用宽度逐步增列，上限 8 列避免超宽屏把卡片排得过密。
 */
internal val GRID_MIN_COLUMN_WIDTH: Dp = 100.dp
internal const val GRID_MAX_COLUMNS = 8

/** 只做整数运算，便于单测覆盖手机、平板、分屏与横屏等不同可用宽度。 */
internal fun adaptiveGridColumnCount(
    availableSizePx: Int,
    spacingPx: Int,
    minColumnWidthPx: Int,
    maxColumns: Int = GRID_MAX_COLUMNS,
): Int {
    if (availableSizePx <= 0 || minColumnWidthPx <= 0 || spacingPx < 0) return 1
    val stride = minColumnWidthPx + spacingPx
    val count = (availableSizePx + spacingPx) / stride
    return count.coerceIn(1, maxColumns.coerceAtLeast(1))
}

/** 把除不尽的像素分给前几列，保证列宽总和加间距精确等于可用宽度。 */
internal fun adaptiveGridCellSizes(availableSizePx: Int, spacingPx: Int, columnCount: Int): List<Int> {
    if (columnCount <= 0 || availableSizePx <= 0) return emptyList()
    val totalSpacing = spacingPx * (columnCount - 1)
    if (availableSizePx <= totalSpacing) return List(columnCount) { 0 }
    val contentSize = availableSizePx - totalSpacing
    val base = contentSize / columnCount
    val remainder = contentSize % columnCount
    return List(columnCount) { index -> base + if (index < remainder) 1 else 0 }
}

/** 组合列数与列宽，GridCells 与单测共用同一条计算路径。 */
internal fun adaptiveGridColumnSizes(
    availableSizePx: Int,
    spacingPx: Int,
    minColumnWidthPx: Int,
    maxColumns: Int = GRID_MAX_COLUMNS,
): List<Int> {
    val count = adaptiveGridColumnCount(availableSizePx, spacingPx, minColumnWidthPx, maxColumns)
    return adaptiveGridCellSizes(availableSizePx, spacingPx, count)
}

/**
 * 按 LazyVerticalGrid 扣除 contentPadding 后的可用宽度决定列数。
 * 用测量到的宽度而不是窗口宽度，分屏、小窗和侧边栏同时显示时也能正确适配。
 */
@Stable
internal class AdaptiveAlbumGridCells(
    private val minColumnWidth: Dp = GRID_MIN_COLUMN_WIDTH,
    private val maxColumns: Int = GRID_MAX_COLUMNS,
) : GridCells {
    override fun Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): List<Int> {
        val minWidthPx = (minColumnWidth.value * density).roundToInt().coerceAtLeast(1)
        return adaptiveGridColumnSizes(availableSize, spacing, minWidthPx, maxColumns)
    }
}

/** 五个漫画网格共用同一实例，重组时不会因为 columns 变化而重新测量。 */
internal val AlbumGridCells: GridCells = AdaptiveAlbumGridCells()
