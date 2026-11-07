package io.kazusa.jmcomic

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumUpdateScanQueueTest {
    @Test
    fun concurrentRequestsCoalesceIntoASingleFollowUpRound() = runBlocking {
        val rounds = mutableListOf<Boolean>()
        val gate = CompletableDeferred<Unit>()
        val queue = AlbumUpdateScanQueue(
            scope = this,
            scanningChanged = {},
            isStale = { true },
            runRound = { includeFavorites ->
                rounds += includeFavorites
                if (rounds.size == 1) gate.await()
                "round-${rounds.size}"
            },
        )

        val first = async { queue.request(includeFavorites = false, force = false) }
        withTimeout(5_000) { while (rounds.isEmpty()) yield() }
        val followers = List(5) { async { queue.request(includeFavorites = true, force = true) } }
        yield()

        gate.complete(Unit)
        assertEquals("round-1", first.await())
        followers.forEach { assertEquals("round-2", it.await()) }
        // 五个并发请求合并成一轮，且收藏范围按"有则包含"合并。
        assertEquals(listOf(false, true), rounds)
    }

    @Test
    fun cancellingCallerDoesNotCancelQueuedRoundOrScanningFlag() = runBlocking {
        val scanning = mutableListOf<Boolean>()
        val rounds = mutableListOf<Int>()
        val gate = CompletableDeferred<Unit>()
        var started = false
        val queue = AlbumUpdateScanQueue(
            scope = this,
            scanningChanged = { scanning += it },
            isStale = { true },
            runRound = {
                rounds += rounds.size + 1
                if (!started) {
                    started = true
                    gate.await()
                }
                "ok"
            },
        )

        val first = launch { queue.request(includeFavorites = false, force = false) }
        withTimeout(5_000) { while (rounds.isEmpty()) yield() }
        val second = async { queue.request(includeFavorites = true, force = false) }
        first.cancel()
        yield()
        gate.complete(Unit)

        assertEquals("ok", second.await())
        assertEquals(2, rounds.size)
        // 只有 worker 启动/结束时各通知一次，被取消的调用方不参与。
        assertEquals(listOf(true, false), scanning)
        assertFalse(scanning.last())
        assertTrue(scanning.first())
    }

    @Test
    fun failingRoundFailsOnlyItsWaitersAndKeepsTheQueueAlive() = runBlocking {
        val scanning = mutableListOf<Boolean>()
        var calls = 0
        val queue = AlbumUpdateScanQueue(
            scope = this,
            scanningChanged = { scanning += it },
            isStale = { true },
            runRound = {
                calls++
                if (calls == 1) throw IllegalStateException("boom")
                "ok"
            },
        )

        val failure = runCatching { queue.request(includeFavorites = false, force = false) }
        assertTrue(failure.exceptionOrNull() is IllegalStateException)
        // 一轮失败不影响后续：状态要回落，下一轮照跑。
        assertEquals("ok", queue.request(includeFavorites = false, force = false))
        assertEquals(2, calls)
        assertEquals(listOf(true, false, true, false), scanning)
    }

    /**
     * 排队请求在出队时已经过期的，不能再扫一遍。
     *
     * 这是"前台切一下就把整本架子重扫一轮"的回归保护：第一轮跑完会刷新 `lastScanAt`，
     * 排队期间进来的非强制请求到这一步就该被丢掉。
     */
    @Test
    fun queuedRoundIsSkippedOnceAnotherRoundMadeItFresh() = runBlocking {
        var stale = true
        val rounds = mutableListOf<Int>()
        val gate = CompletableDeferred<Unit>()
        val queue = AlbumUpdateScanQueue(
            scope = this,
            scanningChanged = {},
            isStale = { stale },
            runRound = {
                rounds += rounds.size + 1
                if (rounds.size == 1) {
                    gate.await()
                    // 一轮扫完写 lastScanAt：从这一刻起非强制请求都算新鲜。
                    stale = false
                }
                "round-${rounds.size}"
            },
        )

        val first = async { queue.request(includeFavorites = false, force = false) }
        withTimeout(5_000) { while (rounds.isEmpty()) yield() }
        val queued = async { queue.request(includeFavorites = true, force = false) }
        yield()
        gate.complete(Unit)

        assertEquals("round-1", first.await())
        assertNull(queued.await())
        assertEquals(1, rounds.size)
    }

    @Test
    fun forcedRequestRunsEvenWhenTheQueueJustWentFresh() = runBlocking {
        var stale = true
        val rounds = mutableListOf<Int>()
        val gate = CompletableDeferred<Unit>()
        val queue = AlbumUpdateScanQueue(
            scope = this,
            scanningChanged = {},
            isStale = { stale },
            runRound = {
                rounds += rounds.size + 1
                if (rounds.size == 1) {
                    gate.await()
                    stale = false
                }
                "round-${rounds.size}"
            },
        )

        val first = async { queue.request(includeFavorites = false, force = false) }
        withTimeout(5_000) { while (rounds.isEmpty()) yield() }
        // 下拉刷新：用户明确要求"现在就看"，不能被新鲜度挡掉。
        val forced = async { queue.request(includeFavorites = true, force = true) }
        yield()
        gate.complete(Unit)

        assertEquals("round-1", first.await())
        assertEquals("round-2", forced.await())
        assertEquals(2, rounds.size)
    }
}
