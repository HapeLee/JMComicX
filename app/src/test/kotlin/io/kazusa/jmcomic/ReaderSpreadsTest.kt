package io.kazusa.jmcomic

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class ReaderSpreadsTest {
    private fun pages(chapter: Int = 0, count: Int = 6) = (0 until count).map { index ->
        ReaderWindowItem.Page(chapter, ReaderPageAddress("ch-$chapter", index),
            ReaderPage(index, ReaderPageResource.RestoredLocal(ReaderOfflineFile(index, File("$chapter-$index.png"), 10, 20))))
    }
    private val pages = pages()
    private fun build(
        items: List<ReaderWindowItem> = pages,
        mode: ReaderMode = ReaderMode.LEFT_TO_RIGHT,
        double: ReaderDoublePageMode = ReaderDoublePageMode.AUTO,
        width: Float = 844f,
        height: Float = 390f,
        cover: Boolean = true,
        aspects: Map<ReaderPageAddress, Float> = items.filterIsInstance<ReaderWindowItem.Page>().associate { it.address to 1.42f },
    ) = readerBuildSpreads(items, mode, double, aspects, width, height, cover)

    @Test fun defaultsToAutomatic() {
        assertEquals(ReaderDoublePageMode.AUTO, ReaderDoublePageMode.fromPreference(null))
        assertEquals(ReaderDoublePageMode.AUTO, ReaderDoublePageMode.fromPreference("future"))
        ReaderDoublePageMode.entries.forEach { assertEquals(it, ReaderDoublePageMode.fromPreference(it.name)) }
    }
    @Test fun coverPairsAndOddTail() {
        assertEquals(listOf(listOf(0), listOf(1, 2), listOf(3, 4), listOf(5)), build().map { s -> s.pages.map { it.address.pageIndex } })
        assertEquals(listOf(2, 2, 2), build(cover = false).map { it.pages.size })
    }
    @Test fun usesActualFitWidthRatherThanOrientation() {
        assertEquals(6, build(width = 390f, height = 844f).size)
        assertEquals(4, build(width = 844f, height = 390f).size)
        assertEquals(4, build(width = 800f, height = 1280f).size)
        assertEquals(6, build(width = 480f, height = 280f).size)
        assertEquals(6, build(width = 1000f, height = 200f).size)
    }
    @Test fun offAndVerticalModesKeepOriginalPages() {
        assertTrue(build(double = ReaderDoublePageMode.OFF).all { it.pages.size == 1 })
        listOf(ReaderMode.TOP_TO_BOTTOM, ReaderMode.WEBTOON).forEach { mode ->
            assertTrue(build(mode = mode, double = ReaderDoublePageMode.ALWAYS).all { it.pages.size == 1 })
        }
    }
    @Test fun forcedModeStillProtectsWideAndUnknownPages() {
        val aspects = pages.associate { it.address to 1.42f }.toMutableMap()
        aspects[pages[1].address] = .7f
        aspects.remove(pages[3].address)
        val spreads = build(double = ReaderDoublePageMode.ALWAYS, width = 390f, aspects = aspects)
        assertTrue(spreads.first { it.contains(pages[1].address) }.pages.size == 1)
        assertTrue(spreads.first { it.contains(pages[3].address) }.pages.size == 1)
        assertEquals(2, spreads.last().pages.size)
    }
    @Test fun neverPairsAcrossChaptersBoundariesOrGaps() {
        val boundary = ReaderWindowItem.Boundary(2, "ch-2", ReaderChapterState.Loading)
        val items = pages(count = 2) + pages(chapter = 1, count = 3) + boundary + ReaderWindowItem.End
        val spreads = build(items = items)
        assertEquals(listOf(1, 1, 1, 2, 0, 0), spreads.map { it.pages.size })
        assertEquals(boundary, spreads[4].items.single())
        assertEquals(2, build(items = listOf(pages[1], pages[3])).size)
    }
    @Test fun chapterSeparatorOwnsItsSpreadAndNeverPairs() {
        val separator = ReaderWindowItem.Separator(1, "ch-1", 0, "ch-0")
        val items = pages(count = 2) + separator + pages(chapter = 1, count = 2)
        val spreads = build(items = items, double = ReaderDoublePageMode.ALWAYS, cover = false)
        assertEquals(listOf(2, 0, 2), spreads.map { it.pages.size })
        assertEquals(separator, spreads[1].items.single())
    }

    @Test fun physicalOrderChangesWithoutChangingLogicalIdentity() {
        val ltr = build()
        val rtl = build(mode = ReaderMode.RIGHT_TO_LEFT)
        assertEquals(ltr, rtl)
        assertEquals(listOf(2, 1), rtl[1].displayPages(ReaderMode.RIGHT_TO_LEFT).map { it.address.pageIndex })
        assertEquals(listOf(1, 2), ltr[1].displayPages(ReaderMode.LEFT_TO_RIGHT).map { it.address.pageIndex })
    }
    @Test fun seekAndReflowKeepExactLeafAddress() {
        val spreads = build()
        val secondLeaf = pages[2].address
        assertEquals(1, readerSpreadIndex(spreads, secondLeaf))
        assertEquals(pages[2], readerSpreadFocus(spreads[1], secondLeaf, 0))
        assertEquals(2, readerSpreadIndex(build(width = 390f), secondLeaf))
        val appended = build(items = pages + pages(chapter = 1))
        assertEquals(spreads.map { it.key }, appended.take(spreads.size).map { it.key })
    }
    @Test fun backwardsEntryFocusesLastLeafAndForwardEntryFirst() {
        val spread = build()[1]
        assertEquals(pages[1], readerSpreadFocus(spread, pages[0].address, 0))
        assertEquals(pages[2], readerSpreadFocus(spread, pages[5].address, 0))
        assertEquals(pages[2], readerSpreadFocus(spread, ReaderPageAddress("ch-1", 0), 1))
    }
    @Test fun invalidDimensionsDoNotMerge() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, 0f, -1f).forEach { aspect ->
            assertEquals(6, build(aspects = pages.associate { it.address to aspect }).size)
        }
    }
}
