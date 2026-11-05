package dev.jmx.client

import dev.jmx.client.core.api.AlbumChapter
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderModesTest {
    private val chapters = (0..5).map { AlbumChapter("chapter-$it", "Chapter $it", "$it") }

    private fun content(count: Int = 3): ReaderChapterState.Content = ReaderChapterState.Content(
        (0 until count).map { index ->
            ReaderPage(index, ReaderPageResource.RestoredLocal(ReaderOfflineFile(index, File("same-$index.png"), 10L, 20L)))
        },
    )

    @Test
    fun oldAndUnknownModePreferencesKeepWebtoon() {
        assertEquals(ReaderMode.WEBTOON, ReaderMode.fromPreference(null))
        assertEquals(ReaderMode.WEBTOON, ReaderMode.fromPreference("unknown-newer-mode"))
        ReaderMode.entries.forEach { assertEquals(it, ReaderMode.fromPreference(it.name)) }
    }

    @Test
    fun windowIsBoundedAndNeverWrapsAroundBook() {
        assertEquals(0..1, readerWindowIndices(0, 6, true))
        assertEquals(1..3, readerWindowIndices(2, 6, true))
        assertEquals(4..5, readerWindowIndices(5, 6, true))
        assertEquals(2..2, readerWindowIndices(2, 6, false))
        assertTrue(readerWindowIndices(0, 0, true).isEmpty())
        assertTrue(readerWindowIndices(-1, 6, true).isEmpty())
        assertTrue(readerWindowIndices(6, 6, true).isEmpty())
        assertEquals(0..0, readerWindowIndices(0, 1, true))
    }

    @Test
    fun reverseDirectionRemainsPhysicalUnderRtlSystemLanguage() {
        assertFalse(readerPagerReverseLayout(ReaderMode.LEFT_TO_RIGHT, false))
        assertTrue(readerPagerReverseLayout(ReaderMode.LEFT_TO_RIGHT, true))
        assertTrue(readerPagerReverseLayout(ReaderMode.RIGHT_TO_LEFT, false))
        assertFalse(readerPagerReverseLayout(ReaderMode.RIGHT_TO_LEFT, true))
        assertFalse(readerPagerReverseLayout(ReaderMode.TOP_TO_BOTTOM, false))
        assertFalse(readerPagerReverseLayout(ReaderMode.TOP_TO_BOTTOM, true))
        assertFalse(readerPagerReverseLayout(ReaderMode.WEBTOON, false))
        assertFalse(readerPagerReverseLayout(ReaderMode.WEBTOON, true))
    }

    @Test
    fun normalReadingNeverInsertsNeighborPages() {
        val items = readerWindowItems(chapters, 2, (0..5).associateWith { content() }, false)
        assertEquals(3, items.size)
        assertTrue(items.all { it is ReaderWindowItem.Page && it.address.chapterId == "chapter-2" })
    }

    @Test
    fun appendingNextChapterKeepsLocalPageIdentityAndIndex() {
        val before = readerWindowItems(chapters, 2, mapOf(1 to content(), 2 to content()), true)
        val after = readerWindowItems(chapters, 2, mapOf(1 to content(), 2 to content(), 3 to content(400)), true)
        val address = ReaderPageAddress("chapter-2", 2)
        val index = readerWindowPageIndex(before, address)
        assertEquals(index, readerWindowPageIndex(after, address))
        assertEquals(before[index].key, after[index].key)
        assertEquals(400, after.filterIsInstance<ReaderWindowItem.Page>().count { it.address.chapterId == "chapter-3" })
    }

    @Test
    fun prependingChapterMovesIndexButKeepsStableKeyAndLocalProgress() {
        val before = readerWindowItems(chapters, 2, mapOf(2 to content(), 3 to content()), true)
        val after = readerWindowItems(chapters, 2, mapOf(1 to content(400), 2 to content(), 3 to content()), true)
        val address = ReaderPageAddress("chapter-2", 1)
        val old = before[readerWindowPageIndex(before, address)] as ReaderWindowItem.Page
        val new = after[readerWindowPageIndex(after, address)] as ReaderWindowItem.Page
        assertEquals(old.key, new.key)
        assertEquals(address, new.address)
        assertEquals(1, new.page.index)
        assertEquals(401, readerWindowPageIndex(after, address))
    }

    @Test
    fun windowEvictionDoesNotChangeTheVisiblePageIdentity() {
        val states = (0..5).associateWith { content(400) }
        val before = readerWindowItems(chapters, 2, states, true)
        val after = readerWindowItems(chapters, 3, states, true)
        val address = ReaderPageAddress("chapter-3", 0)
        assertEquals(before[readerWindowPageIndex(before, address)].key, after[readerWindowPageIndex(after, address)].key)
        assertEquals(400, readerWindowPageIndex(after, address))
        assertFalse(after.filterIsInstance<ReaderWindowItem.Page>().any { it.address.chapterId == "chapter-1" })
    }

    @Test
    fun missingOfflineChapterRemainsABoundaryInsteadOfSkippingContent() {
        val error = ReaderChapterState.Error("Missing offline chapter")
        val items = readerWindowItems(chapters, 0, mapOf(0 to content(), 1 to error, 2 to content()), true)
        val boundary = items.last() as ReaderWindowItem.Boundary
        assertEquals(1, boundary.chapterIndex)
        assertEquals(error, boundary.state)
        assertFalse(items.filterIsInstance<ReaderWindowItem.Page>().any { it.address.chapterId == "chapter-2" })
    }

    @Test
    fun identicalResourcesInDifferentChaptersNeverShareUiKeys() {
        val items = readerWindowItems(chapters, 2, (1..3).associateWith { content() }, true)
            .filterIsInstance<ReaderWindowItem.Page>()
        assertEquals(items.size, items.map { it.key }.distinct().size)
        assertEquals(items[0].page.cacheKey, items[3].page.cacheKey)
        assertNotEquals(items[0].key, items[3].key)
        assertEquals(-1, readerWindowPageIndex(items, ReaderPageAddress("chapter-0", 0)))
    }

    @Test
    fun endMarkerAppearsOnlyAfterTheLastChapterHasLoaded() {
        val loading = readerWindowItems(chapters, 4, mapOf(3 to content(), 4 to content()), true)
        assertFalse(loading.any { it == ReaderWindowItem.End })
        val loaded = readerWindowItems(chapters, 4, mapOf(3 to content(), 4 to content(), 5 to content()), true)
        assertEquals(ReaderWindowItem.End, loaded.last())
        assertFalse(readerWindowItems(chapters, 5, mapOf(5 to content()), false).contains(ReaderWindowItem.End))
        assertTrue(readerWindowItems(emptyList(), 0, emptyMap(), true).isEmpty())
    }
}
