package io.kazusa.jmcomic

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumUpdateScanQueueTest {
    @Test
    fun concurrentRequestsCoalesceIntoASingleFollowUpRound() = runBlocking {
        val rounds = mutableListOf<Pair<Boolean, Boolean>>()
        val gate = CompletableDeferred<Unit>()
        val queue = AlbumUpdateScanQueue(this, {}, { includeFavorites, force ->
            rounds += includeFavorites to force
            if (rounds.size == 1) gate.await()
            "round-${rounds.size}"
        })

        val first = async { queue.request(includeFavorites = false, force = false) }
        withTimeout(5_000) { while (rounds.isEmpty()) yield() }
        val followers = List(5) { async { queue.request(includeFavorites = true, force = true) } }
        yield()

        gate.complete(Unit)
        assertEquals("round-1", first.await())
        followers.forEach { assertEquals("round-2", it.await()) }
        // 五个并发请求合并成一轮，收藏范围与强制标记都按"有则包含"合并。
        assertEquals(listOf(false to false, true to true), rounds)
    }

    @Test
    fun cancellingCallerDoesNotCancelQueuedRoundOrScanningFlag() = runBlocking {
        val scanning = mutableListOf<Boolean>()
        val rounds = mutableListOf<Int>()
        val gate = CompletableDeferred<Unit>()
        var started = false
        val queue = AlbumUpdateScanQueue(this, { scanning += it }, { _, _ ->
            rounds += rounds.size + 1
            if (!started) {
                started = true
                gate.await()
            }
            "ok"
        })

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
        val queue = AlbumUpdateScanQueue(this, { scanning += it }, { _, _ ->
            calls++
            if (calls == 1) throw IllegalStateException("boom")
            "ok"
        })

        val failure = runCatching { queue.request(includeFavorites = false, force = false) }
        assertTrue(failure.exceptionOrNull() is IllegalStateException)
        // 一轮失败不影响后续：状态要回落，下一轮照跑。
        assertEquals("ok", queue.request(includeFavorites = false, force = false))
        assertEquals(2, calls)
        assertEquals(listOf(true, false, true, false), scanning)
    }
}
