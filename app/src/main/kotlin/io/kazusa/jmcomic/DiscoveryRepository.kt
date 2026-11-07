package io.kazusa.jmcomic

import io.kazusa.jmcomic.core.api.CategoryFilter
import io.kazusa.jmcomic.core.api.WeekInfo
import io.kazusa.jmcomic.core.protocol.JmxMagicConstants
import io.kazusa.jmcomic.core.result.JmxResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** 只读发现页，复用既有 core、图片线路健康度与漫画/详情契约。 */
internal class DiscoveryRepository(private val home: HomeRepository) {
    private val catalogLock = Any()
    private var catalogEpoch = 0L

    @Volatile
    private var rememberedCatalog: WeekInfo? = null

    /** 仅作界面占位快照，不代表新鲜度；激活时仍会用元数据校准。 */
    val catalogSnapshot: WeekInfo? get() = rememberedCatalog

    suspend fun week(forceRefresh: Boolean): JmxResult<WeekInfo> = withContext(Dispatchers.IO) {
        val ticket = synchronized(catalogLock) { ++catalogEpoch }
        val result = home.core.libraryApi.week(forceRefresh)
        currentCoroutineContext().ensureActive()
        synchronized(catalogLock) {
            if (ticket == catalogEpoch && result is JmxResult.Success) rememberedCatalog = result.value
        }
        result
    }

    suspend fun page(query: DiscoveryQuery, page: Int, forceRefresh: Boolean): JmxResult<DiscoveryPage> =
        withContext(Dispatchers.IO) {
            val result = when (query) {
                is DiscoveryQuery.Weekly -> home.core.libraryApi.weekFilter(
                    page = page,
                    categoryId = query.weekId,
                    typeId = query.typeId,
                    forceRefresh = forceRefresh,
                    strictList = true,
                )
                is DiscoveryQuery.Ranking -> home.core.libraryApi.categoriesFilter(
                    CategoryFilter(
                        page = page,
                        time = query.period.value,
                        category = query.categoryId,
                        order = JmxMagicConstants.ORDER_BY_VIEW,
                    ),
                    forceRefresh = forceRefresh,
                    strictList = true,
                )
            }
            when (result) {
                is JmxResult.Failure -> result
                is JmxResult.Success -> JmxResult.Success(
                    DiscoveryPage(
                        albums = result.value.content.map { it.toHomeAlbum(home.currentImageHost) },
                        total = result.value.total,
                    )
                )
            }
        }
}
