package dev.jmx.client

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.jmx.client.core.image.ImageUrl
import dev.jmx.client.effect.BlurredBar
import dev.jmx.client.effect.TopBarBlurStyle
import dev.jmx.client.effect.liquid.BindTopBarGlassScroll
import dev.jmx.client.effect.liquid.FloatingCategoryMenu
import dev.jmx.client.effect.liquid.GlassTopBarActions
import dev.jmx.client.effect.liquid.GlassTopBarHost
import dev.jmx.client.effect.liquid.currentTopBarGlassAlpha
import dev.jmx.client.effect.rememberBarBackdrop
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.theme.MiuixTheme
import dev.jmx.client.effect.liquid.GlassTopBarDropdownMenu as SortMenu
import dev.jmx.client.effect.liquid.GlassTopBarIcon as Icon
import dev.jmx.client.effect.liquid.GlassTopBarIconButton as IconButton

@Composable
internal fun AlbumUpdatesScreen(
    records: Map<String, AlbumUpdateRecord>,
    bookshelfRepository: BookshelfRepository,
    detailRepository: AlbumDetailRepository,
    bookshelfRevision: Int,
    favoriteIds: Set<String>,
    accountIdentity: String?,
    imageHost: String,
    scanning: Boolean,
    liftedAlbumId: String?,
    onBack: () -> Unit,
    onRequireLogin: () -> Unit,
    onAlbumSelected: (HomeAlbum, Rect) -> Unit,
    onRefresh: suspend () -> AlbumUpdateScanResult?,
    topBarBlurStyle: TopBarBlurStyle = TopBarBlurStyle.GAUSSIAN,
) {
    val backdrop = rememberBarBackdrop()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val shelfAlbums = remember(bookshelfRepository, bookshelfRevision) {
        bookshelfRepository.entries().associate { it.albumId to it.toHomeAlbum() }
    }
    var source by rememberSaveable { mutableStateOf(AlbumUpdateSource.ALL) }
    var sort by rememberSaveable { mutableStateOf(AlbumUpdateSort.RECENT) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var message by remember(accountIdentity) { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var albums by remember(accountIdentity, imageHost) { mutableStateOf(emptyMap<String, HomeAlbum>()) }
    var loading by remember { mutableStateOf(false) }
    var failedIds by remember(accountIdentity) { mutableStateOf(emptySet<String>()) }
    // 详情转场抬起封面时保留来源行，直到详情关闭，避免“点开即已读”导致来源瞬间消失。
    var visibleRecords by remember { mutableStateOf(records) }
    LaunchedEffect(records, liftedAlbumId) {
        if (liftedAlbumId == null) visibleRecords = records
    }
    val updates = remember(visibleRecords, shelfAlbums, favoriteIds, source, sort) {
        pendingAlbumUpdates(visibleRecords, shelfAlbums.keys, favoriteIds, source, sort)
    }
    val pendingIds = updates.mapTo(linkedSetOf()) { it.albumId }
    // 书架已有封面和标题；仅为收藏独有的更新补详情，并限制并发。
    LaunchedEffect(pendingIds, shelfAlbums, detailRepository, accountIdentity, imageHost, retry) {
        val missing = pendingIds - shelfAlbums.keys - albums.keys
        failedIds = emptySet()
        loading = missing.isNotEmpty()
        try {
            val limit = Semaphore(4)
            coroutineScope {
                missing.forEach { id ->
                    launch {
                        limit.withPermit {
                            val result = detailRepository.load(id)
                            if (result is AlbumDetailUiState.Content) {
                                albums = albums + (id to result.detail.toHomeAlbum(imageHost))
                            } else {
                                failedIds = failedIds + id
                            }
                        }
                    }
                }
            }
        } finally {
            loading = false
        }
    }

    fun refresh() {
        if (refreshing || scanning) return
        refreshing = true
        message = null
        coroutineScope.launch {
            try {
                onRefresh()?.let { result ->
                    message = buildString {
                        append("已检查 ${result.scanned} 部")
                        if (result.failed > 0) append("，${result.failed} 部检查失败，可刷新重试")
                        if (result.favoritesFailed) append("；收藏同步失败，保留上次结果")
                    }
                }
                retry++
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                message = "检查更新失败，请稍后重试"
            } finally {
                refreshing = false
            }
        }
    }

    val scrollBehavior = MiuixScrollBehavior(canScroll = { liftedAlbumId == null && !sortMenuExpanded })
    GlassTopBarHost {
        BindTopBarGlassScroll(listState)
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
                containerColor = Color.Transparent,
                topBar = {
                    BlurredBar(backdrop = backdrop, style = topBarBlurStyle) {
                        TopAppBar(
                            title = "更新列表",
                            subtitle = source.label,
                            scrollBehavior = scrollBehavior,
                            // 随滚动补足标题底色，保持文字在书籍内容上方的可读性。
                            color = MiuixTheme.colorScheme.surface.copy(
                                alpha = if (backdrop == null) 1f else currentTopBarGlassAlpha() * 0.94f,
                            ),
                            navigationIcon = {
                                IconButton(onClick = onBack) {
                                    Icon(MiuixIcons.Back, "返回", tint = MiuixTheme.colorScheme.onBackground)
                                }
                            },
                            actions = {
                                GlassTopBarActions {
                                    SortMenu(
                                        onExpandedChange = { sortMenuExpanded = it },
                                        entry = DropdownEntry(items = AlbumUpdateSort.entries.map { order ->
                                            DropdownItem(text = order.label, selected = sort == order, onClick = { sort = order })
                                        }),
                                    ) {
                                        Icon(MiuixIcons.Sort, "更新排序", tint = MiuixTheme.colorScheme.onBackground)
                                    }
                                    IconButton(onClick = ::refresh, enabled = !refreshing && !scanning) {
                                        if (refreshing || scanning) CircularProgressIndicator(size = 22.dp, strokeWidth = 2.dp)
                                        else Icon(MiuixIcons.Refresh, "检查更新", tint = MiuixTheme.colorScheme.onBackground)
                                    }
                                }
                            },
                        )
                    }
                },
            ) { padding ->
                PullToRefresh(
                    isRefreshing = refreshing || scanning,
                    onRefresh = ::refresh,
                    pullToRefreshState = rememberPullToRefreshState(),
                    refreshTexts = listOf("下拉检查更新", "松开检查", "正在检查", "检查完成"),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                            .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier),
                        contentPadding = PaddingValues(
                            start = 12.dp, end = 12.dp,
                            top = padding.calculateTopPadding() + 12.dp,
                            // 末行可滚到悬浮按钮上方，避免按钮挡住书籍信息和点击区域。
                            bottom = padding.calculateBottomPadding() + 96.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item(key = "summary") {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("${updates.size} 部有更新 · ${sort.label}", style = MiuixTheme.textStyles.headline1)
                                Text(
                                    "仅显示未查看的新增章节，时间为本机发现时间。首次检查会建立基线。",
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                                message?.let { Text(it, style = MiuixTheme.textStyles.footnote1) }
                                if (loading) Text("正在加载书籍信息…", style = MiuixTheme.textStyles.footnote1)
                                if (failedIds.isNotEmpty()) {
                                    TextButton(text = "${failedIds.size} 部书籍信息加载失败，点击重试", onClick = { retry++ })
                                }
                                if (accountIdentity == null) {
                                    TextButton(text = "登录后查看收藏更新", onClick = onRequireLogin)
                                }
                            }
                        }
                        if (updates.isEmpty()) {
                            item(key = "empty") {
                                Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(24.dp)) {
                                    Text(if (scanning || refreshing) "正在检查更新…" else "暂无未查看的更新")
                                    TextButton(text = "检查更新", onClick = ::refresh, enabled = !scanning && !refreshing)
                                }
                            }
                        }
                        items(updates, key = AlbumUpdateRecord::albumId) { record ->
                            val id = record.albumId
                            val album = shelfAlbums[id] ?: albums[id] ?: HomeAlbum(
                                id, "JM$id", "书籍信息待加载", ImageUrl.albumCover(imageHost, id), imageHost,
                            )
                            AlbumUpdateRow(
                                album = album,
                                record = record,
                                inBookshelf = id in shelfAlbums,
                                inFavorites = id in favoriteIds,
                                lifted = id == liftedAlbumId,
                                onSelected = onAlbumSelected,
                            )
                        }
                    }
                }
            }
            // 与首页一致：菜单放在 Scaffold 的全屏兄弟层，展开后覆盖整个页面。
            FloatingCategoryMenu(
                items = AlbumUpdateSource.entries.map { scope ->
                    val count = pendingAlbumUpdates(visibleRecords, shelfAlbums.keys, favoriteIds, scope).size
                    "${scope.label} $count"
                },
                selectedIndex = source.ordinal,
                onSelect = { index ->
                    source = AlbumUpdateSource.entries[index]
                    coroutineScope.launch { listState.scrollToItem(0) }
                },
                backdrop = backdrop,
                description = "更新来源",
                visible = liftedAlbumId == null && !sortMenuExpanded,
                closeKey = accountIdentity,
            )
        }
    }
}

@Composable
private fun AlbumUpdateRow(
    album: HomeAlbum,
    record: AlbumUpdateRecord,
    inBookshelf: Boolean,
    inFavorites: Boolean,
    lifted: Boolean,
    onSelected: (HomeAlbum, Rect) -> Unit,
) {
    var bounds by remember(album.id) { mutableStateOf(Rect.Zero) }
    val discovered = remember(record.detectedAt) {
        if (record.detectedAt > 0L) SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(record.detectedAt))
        else "时间未知"
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable {
            if (bounds.width > 0 && bounds.height > 0) onSelected(album, bounds)
        },
        insideMargin = PaddingValues(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(78.dp)) {
                AlbumCover(album, visible = !lifted, retryEnabled = !lifted, onBoundsChanged = { bounds = it })
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(album.name, style = MiuixTheme.textStyles.headline1, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(album.author, style = MiuixTheme.textStyles.footnote1, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Text("新增 ${record.pendingChapters} 章 · 共 ${record.latestChapterCount} 章",
                    style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.primary)
                Text(
                    listOfNotNull("书架".takeIf { inBookshelf }, "收藏".takeIf { inFavorites }).joinToString(" · ") + " · 发现于 $discovered",
                    style = MiuixTheme.textStyles.footnote2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}
