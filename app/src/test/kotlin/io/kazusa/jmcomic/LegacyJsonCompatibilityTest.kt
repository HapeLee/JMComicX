package io.kazusa.jmcomic

import io.kazusa.jmcomic.core.json.JmxJson
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class LegacyJsonCompatibilityTest {
    @Test fun readsLegacyProfileAndCredentialsWithUnicodeAndOmittedNullFields() {
        val profile = JmxJson.decodeFromString<AccountProfile>(
            """{"id":123,"username":"中文使用者","coin":7,"futureField":true}""",
        )
        assertEquals("中文使用者", profile.username)
        assertEquals(7, profile.coin)
        assertNull(profile.avatar)
        assertEquals(profile, JmxJson.decodeFromString<AccountProfile>(JmxJson.encodeToString(profile)))

        val credentials = JmxJson.decodeFromString<AccountCredentials>(
            """{"username":"测试","password":"p\"ass\\word\n"}""",
        )
        assertEquals("p\"ass\\word\n", credentials.password)
        assertEquals(credentials, JmxJson.decodeFromString<AccountCredentials>(JmxJson.encodeToString(credentials)))
    }

    @Test fun readsLegacyBookshelfExportWithNumericStringsNullsAndExtraFields() {
        val snapshot = requireNotNull(decodeBookshelfSnapshot("""{
            "format":"jmcomicx-bookshelf","version":1,"futureField":true,
            "entries":[{"album_id":"10","name":"旧书架","added_at":"123",
                "group_ids":["g"],"last_read_at":null,"last_page_index":"2"}],
            "groups":[{"id":"g","name":"分组","match_favorites_by_tags":"true","tag_rules":[]}]
        }"""))
        val entry = snapshot.entries.single()
        assertEquals(123L, entry.addedAt)
        assertEquals(2, entry.lastPageIndex)
        assertNull(entry.lastReadAt)
        assertTrue(snapshot.groups.single().matchFavoritesByTags)
        assertEquals(snapshot, decodeBookshelfSnapshot(encodeBookshelfSnapshot(snapshot, 456L)))
    }

    @Test fun readsLegacyOfflineWhitelistAndDoesNotWriteSessionFields() {
        val legacy = """{"version":1,"albums":[{
            "id":"10","title":"本地漫画","status":"PAUSED","updatedAt":123,
            "detail":{"id":"10","name":null,"authors":[],"tags":[],"actors":[],"works":[],
                "series":[{"id":"11","name":"第一话","sort":null}],"raw":{"token":"secret"}},
            "chapters":[{"id":"11","name":"第一话","expected":1,
                "pages":[{"index":0,"path":"10/11/000000.png","bytes":123,"sha256":"digest"}]}]
        }]}"""
        val albums = OfflineMetadata.decode(legacy)
        val encoded = OfflineMetadata.encode(albums)
        assertEquals(albums, OfflineMetadata.decode(encoded))
        assertFalse(encoded.contains("secret"))
        assertFalse(encoded.contains("raw"))
        assertNull(albums.single().detail.name)
        assertEquals(123L, albums.single().chapters.single().pages.single().byteCount)
    }
}
