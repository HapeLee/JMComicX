package io.kazusa.jmcomic

import io.kazusa.jmcomic.core.result.JmxResult
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 每个账号持有独立实例；只在主线程读写，旧账号的请求不会污染新账号的封面。 */
internal class FavoriteAlbumMembership {
    private val _ids = MutableStateFlow<Set<String>>(emptySet())
    val ids = _ids.asStateFlow()
    private var refreshGeneration = 0
    private var changesDuringRefresh: MutableMap<String, Boolean>? = null

    fun setFavorite(albumId: String, favorite: Boolean) {
        changesDuringRefresh?.set(albumId, favorite)
        _ids.value = if (favorite) _ids.value + albumId else _ids.value - albumId
    }

    /** 完整枚举成功才替换；分页期间的本机操作优先于服务端较早生成的快照。 */
    suspend fun refresh(loadPage: suspend (Int) -> JmxResult<AccountAlbumPage>) {
        val generation = ++refreshGeneration
        val changes = mutableMapOf<String, Boolean>()
        changesDuringRefresh = changes
        try {
            val loaded = loadFavoriteAlbumIds(loadPage) ?: return
            currentCoroutineContext().ensureActive()
            if (generation != refreshGeneration) return
            _ids.value = loaded.toMutableSet().apply {
                changes.forEach { (id, favorite) -> if (favorite) add(id) else remove(id) }
            }.toSet()
        } finally {
            if (generation == refreshGeneration) changesDuringRefresh = null
        }
    }
}

/** null 表示未得到完整列表，不能用半份列表或异常空页清掉已有角标。 */
internal suspend fun loadFavoriteAlbumIds(
    loadPage: suspend (Int) -> JmxResult<AccountAlbumPage>,
): Set<String>? {
    val ids = linkedSetOf<String>()
    for (page in 1..100) {
        val result = loadPage(page)
        val value = (result as? JmxResult.Success)?.value ?: return null
        if (value.albums.isEmpty()) {
            return ids.takeIf { value.total == null || ids.size >= value.total }
        }
        val previousSize = ids.size
        value.albums.mapTo(ids) { it.id }
        if (value.total != null && ids.size >= value.total) return ids
        // 重复页与防御上限都不能被当作成功，避免给出不完整的收藏状态。
        if (ids.size == previousSize) return null
    }
    return null
}
