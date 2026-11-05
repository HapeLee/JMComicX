package io.kazusa.jmcomic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AlbumUpdatesTest {
    private val records = listOf(
        AlbumUpdateRecord("shelf", 2, 4, 900, detectedAt = 100),
        AlbumUpdateRecord("favorite", 3, 4, 900, detectedAt = 300),
        AlbumUpdateRecord("both", 1, 6, 900, detectedAt = 200),
        AlbumUpdateRecord("seen", 4, 4, 900, detectedAt = 400),
        AlbumUpdateRecord("removed", 1, 9, 900, detectedAt = 500),
    ).associateBy { it.albumId }
    private val shelf = setOf("shelf", "both", "seen")
    private val favorites = setOf("favorite", "both", "seen")

    @Test
    fun unionDeduplicatesAndExcludesSeenAndUnfollowedBooks() {
        assertEquals(listOf("favorite", "both", "shelf"), pendingAlbumUpdates(records, shelf, favorites).map { it.albumId })
    }

    @Test
    fun eachSourceOnlyIncludesItsOwnMembers() {
        assertEquals(listOf("both", "shelf"), pendingAlbumUpdates(records, shelf, favorites, AlbumUpdateSource.BOOKSHELF).map { it.albumId })
        assertEquals(listOf("favorite", "both"), pendingAlbumUpdates(records, shelf, favorites, AlbumUpdateSource.FAVORITES).map { it.albumId })
        assertEquals(emptyList<AlbumUpdateRecord>(), pendingAlbumUpdates(records, shelf, emptySet(), AlbumUpdateSource.FAVORITES))
    }

    @Test
    fun chapterSortUsesPendingCountAndBreaksTiesByDiscoveryTimeThenId() {
        val ties = records + ("tie" to AlbumUpdateRecord("tie", 7, 9, 1000, detectedAt = 100))
        assertEquals(listOf("both", "shelf", "tie", "favorite"),
            pendingAlbumUpdates(ties, shelf + "tie", favorites, sort = AlbumUpdateSort.CHAPTERS).map { it.albumId })
    }

    @Test
    fun firstObservationCreatesBaselineWithoutReportingAnUpdate() {
        val first = observeAlbumUpdate(null, "new", 20, 100)
        assertFalse(first.hasUpdate)
        assertEquals(0L, first.detectedAt)
    }

    @Test
    fun repeatedScansDoNotMoveOlderUpdatesAheadOfNewerUpdates() {
        val original = records.getValue("shelf")
        val rescanned = observeAlbumUpdate(original, "shelf", 4, 1000)
        assertEquals(100L, rescanned.detectedAt)
        assertEquals(1000L, rescanned.checkedAt)
        assertEquals("favorite", pendingAlbumUpdates(records + ("shelf" to rescanned), shelf, favorites).first().albumId)
        assertEquals(1100L, observeAlbumUpdate(rescanned, "shelf", 5, 1100).detectedAt)
        assertEquals(100L, observeAlbumUpdate(original, "shelf", 3, 1200).detectedAt)
    }

    @Test
    fun seeingAnAlbumRemovesItFromEverySource() {
        val seen = records.getValue("both").copy(seenChapterCount = 6)
        AlbumUpdateSource.entries.forEach { source ->
            assertFalse(pendingAlbumUpdates(records + ("both" to seen), shelf, favorites, source).any { it.albumId == "both" })
        }
    }

    @Test
    fun discoveryTimeSurvivesPersistenceAndOldRecordsRemainReadable() {
        assertEquals(records, decodeAlbumUpdateRecords(encodeAlbumUpdateRecords(records)))
        val legacy = decodeAlbumUpdateRecords("""[{"id":"old","seen":1,"latest":3,"checkedAt":123}]""").getValue("old")
        assertEquals(0L, legacy.detectedAt)
        assertEquals(2, legacy.pendingChapters)
    }
}
