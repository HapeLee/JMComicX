package dev.jmx.client

import dev.jmx.client.core.api.AlbumChapter

internal enum class ReaderMode(val label: String, val summary: String) {
    LEFT_TO_RIGHT("从左到右", "向左滑动翻页"),
    RIGHT_TO_LEFT("从右到左", "向右滑动翻页"),
    TOP_TO_BOTTOM("从上到下", "向上滑动，单页翻阅"),
    WEBTOON("条漫", "图片连续排列，自由滚动");

    companion object {
        fun fromPreference(value: String?): ReaderMode = entries.firstOrNull { it.name == value } ?: WEBTOON
    }
}

internal data class ReaderPageAddress(val chapterId: String, val pageIndex: Int)

internal sealed interface ReaderWindowItem {
    val key: String

    data class Page(val chapterIndex: Int, val address: ReaderPageAddress, val page: ReaderPage) : ReaderWindowItem {
        override val key: String = "page:${address.chapterId}:${address.pageIndex}:${page.cacheKey}"
    }

    data class Boundary(val chapterIndex: Int, val chapterId: String, val state: ReaderChapterState) : ReaderWindowItem {
        override val key: String = "boundary:$chapterId"
    }

    data object End : ReaderWindowItem { override val key = "reader-end" }
}

/** 只接入直接相邻章节；缺章保留边界，不把更远的章节静默拼过来。 */
internal fun readerWindowIndices(center: Int, chapterCount: Int, seamless: Boolean): IntRange {
    if (chapterCount <= 0 || center !in 0 until chapterCount) return IntRange.EMPTY
    return if (seamless) maxOf(0, center - 1)..minOf(chapterCount - 1, center + 1) else center..center
}

internal fun readerWindowItems(
    chapters: List<AlbumChapter>,
    center: Int,
    states: Map<Int, ReaderChapterState>,
    seamless: Boolean,
): List<ReaderWindowItem> = buildList {
    for (index in readerWindowIndices(center, chapters.size, seamless)) {
        val chapter = chapters[index]
        when (val state = states[index] ?: ReaderChapterState.Loading) {
            is ReaderChapterState.Content -> state.pages.forEach { page ->
                add(ReaderWindowItem.Page(index, ReaderPageAddress(chapter.id, page.index), page))
            }
            else -> add(ReaderWindowItem.Boundary(index, chapter.id, state))
        }
    }
    if (seamless && chapters.isNotEmpty() && chapters.lastIndex in readerWindowIndices(center, chapters.size, true) &&
        states[chapters.lastIndex] is ReaderChapterState.Content
    ) add(ReaderWindowItem.End)
}

internal fun readerWindowPageIndex(items: List<ReaderWindowItem>, address: ReaderPageAddress): Int =
    items.indexOfFirst { it is ReaderWindowItem.Page && it.address == address }

/** 物理阅读方向不随系统语言改变，反向布局只作用于阅读内容。 */
internal fun readerPagerReverseLayout(mode: ReaderMode, systemRtl: Boolean): Boolean =
    when (mode) {
        ReaderMode.LEFT_TO_RIGHT -> systemRtl
        ReaderMode.RIGHT_TO_LEFT -> !systemRtl
        else -> false
    }
