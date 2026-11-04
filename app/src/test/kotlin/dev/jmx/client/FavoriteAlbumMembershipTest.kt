package dev.jmx.client

import dev.jmx.client.core.result.JmxError
import dev.jmx.client.core.result.JmxResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FavoriteAlbumMembershipTest {
    private fun page(vararg ids: String, total: Int? = null) = JmxResult.Success(
        AccountAlbumPage(ids.map { HomeAlbum(it, it, "", "", "") }, total),
    )

    @Test
    fun readsEveryPageUntilTotalAndDeduplicatesIds() = runBlocking {
        val requests = mutableListOf<Int>()
        val ids = loadFavoriteAlbumIds {
            requests += it
            if (it == 1) page("1", "2", total = 3) else page("2", "3", total = 3)
        }
        assertEquals(setOf("1", "2", "3"), ids)
        assertEquals(listOf(1, 2), requests)
    }

    @Test
    fun unknownTotalRequiresAnEmptyFinalPage() = runBlocking {
        assertEquals(setOf("1"), loadFavoriteAlbumIds { if (it == 1) page("1") else page() })
    }

    @Test
    fun failedOrTruncatedRefreshKeepsKnownMembership() = runBlocking {
        val membership = FavoriteAlbumMembership()
        membership.setFavorite("known", true)
        membership.refresh {
            if (it == 1) page("new", total = 3) else JmxResult.Failure(JmxError.Schema("offline"))
        }
        assertEquals(setOf("known"), membership.ids.value)
        membership.refresh { if (it == 1) page("new", total = 3) else page(total = 3) }
        assertEquals(setOf("known"), membership.ids.value)
        membership.refresh { page(total = 0) }
        assertEquals(emptySet<String>(), membership.ids.value)
    }

    @Test
    fun repeatedPagesAndPageLimitDoNotPublishPartialMembership() = runBlocking {
        assertNull(loadFavoriteAlbumIds { page("1", total = 20) })
        assertNull(loadFavoriteAlbumIds { page(it.toString()) })
    }

    @Test
    fun localMutationsWinOverAnInFlightServerSnapshot() = runBlocking {
        val membership = FavoriteAlbumMembership()
        membership.refresh {
            membership.setFavorite("removed", false)
            membership.setFavorite("added", true)
            page("removed", "unchanged", total = 2)
        }
        assertEquals(setOf("added", "unchanged"), membership.ids.value)
        // 下一轮仍能感知其他设备的变更，不把旧的本机操作永久盖在服务端之上。
        membership.refresh { page("remote", total = 1) }
        assertEquals(setOf("remote"), membership.ids.value)
    }

    @Test
    fun accountSwitchAndCancelledRequestCannotPublishOldMembership() = runBlocking {
        val oldAccount = FavoriteAlbumMembership()
        val newAccount = FavoriteAlbumMembership()
        val started = CompletableDeferred<Unit>()
        val pending = launch {
            oldAccount.refresh {
                started.complete(Unit)
                CompletableDeferred<Unit>().await()
                page("old", total = 1)
            }
        }
        started.await()
        newAccount.refresh { page("new", total = 1) }
        pending.cancelAndJoin()
        assertEquals(emptySet<String>(), oldAccount.ids.value)
        assertEquals(setOf("new"), newAccount.ids.value)
    }
}
