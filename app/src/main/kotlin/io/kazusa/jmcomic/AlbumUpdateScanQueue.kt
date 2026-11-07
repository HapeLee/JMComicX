package io.kazusa.jmcomic

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch

/**
 * 单个 App 级扫描 worker + 一次合并的补扫。
 *
 * 调用方取消（比如用户离开书架页）不会把已经排队的刷新一起丢掉；
 * 同时到达的多个请求合并成一轮，避免翻页/下拉刷新各打一遍 `/album`。
 *
 * 新鲜度判定 [isStale] 也归这里管，而不是留在调用方：入队前和出队时都得判，且必须是同一条规则。
 * 一轮是"每部漫画一次 `/album`"，可能几十秒，排队期间上一轮完全可能已经扫完——
 * 只有入队前判、出队不判，就会因为一次前台切换或书架改动把同一批漫画再扫一遍。
 *
 * [force] 随批次排队（按"有则强制"合并）：强制请求永不被新鲜度挡掉。
 * 被判为过期的批次不扫描，所有等待方拿到 null。
 */
internal class AlbumUpdateScanQueue<T>(
    private val scope: CoroutineScope,
    private val scanningChanged: (Boolean) -> Unit,
    private val isStale: () -> Boolean,
    private val runRound: suspend (includeFavorites: Boolean) -> T,
) {
    private data class Pending<T>(
        var includeFavorites: Boolean,
        var force: Boolean,
        val waiters: MutableList<CompletableDeferred<T?>>,
    )

    private val lock = Any()
    private var pending: Pending<T>? = null
    private var worker: Job? = null

    /** 返回 null 表示这一批（含它合并进来的其它请求）最终没扫。 */
    suspend fun request(includeFavorites: Boolean, force: Boolean): T? {
        // 入队前先挡一层：明显不该扫就别唤醒 worker，让调用方白白挂到一轮扫描结束。
        if (!force && !isStale()) return null
        val reply = CompletableDeferred<T?>()
        var start: Job? = null
        synchronized(lock) {
            val queued = pending
            if (queued == null) {
                pending = Pending(includeFavorites, force, mutableListOf(reply))
            } else {
                queued.includeFavorites = queued.includeFavorites || includeFavorites
                queued.force = queued.force || force
                queued.waiters += reply
            }
            if (worker == null) {
                val job = scope.launch(start = CoroutineStart.LAZY) { drain() }
                worker = job
                scanningChanged(true)
                // 父作用域在 lazy worker 启动前就被取消时也要收尾。
                job.invokeOnCompletion { cause ->
                    synchronized(lock) {
                        if (worker === job) {
                            pending?.waiters?.forEach { it.cancel(CancellationException("Scan worker stopped", cause)) }
                            pending = null
                            worker = null
                            scanningChanged(false)
                        }
                    }
                }
                start = job
            }
        }
        start?.start()
        return reply.await()
    }

    private suspend fun drain() {
        val owner = currentCoroutineContext()[Job]
        var active: Pending<T>? = null
        try {
            while (true) {
                val batch = synchronized(lock) {
                    pending.also {
                        pending = null
                        if (it == null && worker === owner) {
                            worker = null
                            scanningChanged(false)
                        }
                    }
                } ?: return
                active = batch
                // 出队重判：排队期间上一轮可能已经把新鲜度刷掉了。
                if (!batch.force && !isStale()) {
                    batch.waiters.forEach { it.complete(null) }
                    active = null
                    continue
                }
                try {
                    val result = runRound(batch.includeFavorites)
                    batch.waiters.forEach { it.complete(result) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    batch.waiters.forEach { it.completeExceptionally(error) }
                }
                active = null
            }
        } finally {
            active?.waiters?.forEach { it.cancel() }
        }
    }
}
