package io.kazusa.jmcomic

import kotlin.math.min

internal enum class ReaderDoublePageMode(val label: String) {
    OFF("关闭"), AUTO("空间足够时"), ALWAYS("始终双页");

    companion object {
        fun fromPreference(value: String?): ReaderDoublePageMode = entries.firstOrNull { it.name == value } ?: AUTO
    }
}

/** 只投影显示分组，资源、历史和进度仍使用原始页地址。 */
internal data class ReaderSpread(val items: List<ReaderWindowItem>) {
    val pages = items.filterIsInstance<ReaderWindowItem.Page>()
    val key = items.joinToString("|") { it.key }
    fun contains(address: ReaderPageAddress): Boolean = pages.any { it.address == address }
    fun displayPages(mode: ReaderMode): List<ReaderWindowItem.Page> =
        if (mode == ReaderMode.RIGHT_TO_LEFT) pages.reversed() else pages
}

internal fun readerBuildSpreads(
    items: List<ReaderWindowItem>,
    mode: ReaderMode,
    doublePage: ReaderDoublePageMode,
    aspects: Map<ReaderPageAddress, Float>,
    widthDp: Float,
    heightDp: Float,
    coverSingle: Boolean = true,
): List<ReaderSpread> = buildList {
    val horizontal = mode == ReaderMode.LEFT_TO_RIGHT || mode == ReaderMode.RIGHT_TO_LEFT
    fun fits(page: ReaderWindowItem.Page): Boolean {
        val aspect = aspects[page.address] ?: return false // 高/宽；未知尺寸不猜测，也不误合并跨页大图。
        if (!aspect.isFinite() || aspect < 1f) return false
        return doublePage == ReaderDoublePageMode.ALWAYS ||
            min(widthDp / 2f, heightDp / aspect) >= 240f
    }
    var index = 0
    while (index < items.size) {
        val first = items[index]
        val second = items.getOrNull(index + 1)
        val pair = horizontal && doublePage != ReaderDoublePageMode.OFF &&
            first is ReaderWindowItem.Page && second is ReaderWindowItem.Page &&
            first.address.chapterId == second.address.chapterId &&
            second.address.pageIndex == first.address.pageIndex + 1 &&
            (!coverSingle || first.address.pageIndex != 0) && fits(first) && fits(second)
        add(ReaderSpread(if (pair) listOf(first, second) else listOf(first)))
        index += if (pair) 2 else 1
    }
}

internal fun readerSpreadIndex(spreads: List<ReaderSpread>, address: ReaderPageAddress): Int =
    spreads.indexOfFirst { it.contains(address) }

/** 优先保留精确叶页，尤其是拖动进度条定位到双页中的第二页。 */
internal fun readerSpreadFocus(spread: ReaderSpread, previous: ReaderPageAddress?, previousChapter: Int): ReaderWindowItem.Page? =
    spread.pages.firstOrNull { it.address == previous } ?: if (
        spread.pages.firstOrNull()?.let {
            it.chapterIndex < previousChapter || (it.chapterIndex == previousChapter && it.address.pageIndex < (previous?.pageIndex ?: 0))
        } == true
    ) spread.pages.lastOrNull() else spread.pages.firstOrNull()
