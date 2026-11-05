package io.kazusa.jmcomic

import org.junit.Assert.*
import org.junit.Test

class ReaderPageColorsTest {
    private val white = 0xffffffff.toInt()
    private val black = 0xff000000.toInt()
    @Test fun solidPaperAndDarkPageKeepExactColors() {
        listOf(white, black, 0xffedcf93.toInt()).forEach { color ->
            assertEquals(ReaderEdgeColors(color, color), readerEdgeColors(800, 1200) { _, _ -> color })
        }
    }
    @Test fun topAndBottomAreIndependent() {
        assertEquals(ReaderEdgeColors(white, black), readerEdgeColors(800, 1200) { _, y -> if (y < 600) white else black })
    }
    @Test fun ignoresSparseTextAndThinOutsideBorder() {
        val colors = readerEdgeColors(800, 1200) { x, y ->
            if (y == 0 || y == 1199 || x == 0 || x % 31 == 0) black else white
        }
        assertEquals(ReaderEdgeColors(white, white), colors)
    }
    @Test fun transparentPixelsFallBackToTheme() {
        assertEquals(ReaderEdgeColors(null, null), readerEdgeColors(80, 120) { _, _ -> 0x00ffffff })
        assertEquals(ReaderEdgeColors(null, null), readerEdgeColors(80, 120) { x, _ -> if (x % 20 == 0) white else 0 })
    }
    @Test fun tinyAndEmptyImagesAreSafe() {
        assertEquals(ReaderEdgeColors(black, black), readerEdgeColors(1, 1) { _, _ -> black })
        assertEquals(ReaderEdgeColors(null, null), readerEdgeColors(0, 1) { _, _ -> error("must not sample") })
    }
    @Test fun samplingWorkIsBoundedForHugePages() {
        var count = 0
        assertEquals(ReaderEdgeColors(white, white), readerEdgeColors(4000, 100000) { _, _ -> count++; white })
        assertTrue(count <= 96 * 8 * 2)
    }
}
