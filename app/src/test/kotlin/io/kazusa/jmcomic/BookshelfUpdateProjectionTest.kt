package io.kazusa.jmcomic

import org.junit.Assert.assertEquals
import org.junit.Test

class BookshelfUpdateProjectionTest {
    @Test
    fun updateAcknowledgementPreservesLaterChaptersAndOtherBooks() {
        val clicked = AlbumUpdateRecord("1", 10, 12, 1)
        val newer = clicked.copy(latestChapterCount = 13, checkedAt = 2)
        val other = AlbumUpdateRecord("2", 3, 4, 1)
        val result = acknowledgeAlbumUpdates(mapOf("1" to newer, "2" to other), mapOf("1" to clicked))
        assertEquals(1, result.getValue("1").pendingChapters)
        assertEquals(other, result["2"])
        assertEquals(2L, result.getValue("1").checkedAt)
        // 同一份旧快照再点一次不会继续推进基线。
        assertEquals(result, acknowledgeAlbumUpdates(result, mapOf("1" to clicked)))
    }

    @Test
    fun oldAcknowledgementCannotTouchRecreatedRecord() {
        val old = AlbumUpdateRecord("1", 2, 3, 1)
        val recreated = AlbumUpdateRecord("1", 4, 5, 10)
        assertEquals(recreated, acknowledgeAlbumUpdates(mapOf("1" to recreated), mapOf("1" to old))["1"])
    }

    @Test
    fun staleObservationCannotOverwriteNewDetailButFreshDeletionIsAllowed() {
        val current = AlbumUpdateRecord("1", 13, 13, 20)
        assertEquals(current, observeAlbumUpdate(current, "1", 12, 10))
        assertEquals(11, observeAlbumUpdate(current, "1", 11, 30).latestChapterCount)
    }

    @Test
    fun pendingUpdatesStayAheadInBothDirections() {
        val old = BookshelfEntry("1", "Old", "", "", "", 1)
        val newer = old.copy(albumId = "2", name = "New", addedAt = 10, updatedAt = 10)
        val record = mapOf("1" to AlbumUpdateRecord("1", 1, 2, 20))
        BookshelfSortDirection.entries.forEach { direction ->
            assertEquals(
                "1",
                projectBookshelf(
                    listOf(newer, old),
                    ALL_BOOKSHELF_GROUP_ID,
                    BookshelfSortOrder.UPDATED,
                    direction,
                    record,
                ).first().albumId,
            )
        }
        assertEquals(
            "2",
            projectBookshelf(
                listOf(newer, old),
                ALL_BOOKSHELF_GROUP_ID,
                BookshelfSortOrder.ADDED,
                BookshelfSortDirection.ASCENDING,
                record,
            ).first().albumId,
        )
    }
}
