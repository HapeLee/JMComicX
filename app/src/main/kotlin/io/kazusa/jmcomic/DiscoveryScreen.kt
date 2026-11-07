package io.kazusa.jmcomic

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.kazusa.jmcomic.effect.BlurredBar
import io.kazusa.jmcomic.effect.TopBarBlurStyle
import io.kazusa.jmcomic.effect.liquid.FloatingCategoryMenu
import io.kazusa.jmcomic.effect.liquid.LiquidGlassIcon
import io.kazusa.jmcomic.effect.rememberBarBackdrop
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun DiscoveryScreen(
    kind: DiscoveryKind,
    repository: DiscoveryRepository,
    onBack: () -> Unit,
    onAlbumSelected: (HomeAlbum, Rect) -> Unit,
    modifier: Modifier = Modifier,
    liftedAlbumId: String? = null,
    topBarBlurStyle: TopBarBlurStyle = TopBarBlurStyle.GAUSSIAN,
) {
    val scope = rememberCoroutineScope()
    var savedWeekId by rememberSaveable { mutableStateOf("") }
    var savedTypeId by rememberSaveable { mutableStateOf("") }
    val weekly = remember(repository, kind, scope) {
        if (kind == DiscoveryKind.WEEKLY) {
            WeeklyDiscoveryCoordinator(
                scope, repository::week, repository::page,
                initialCatalog = repository.catalogSnapshot,
                initialWeekId = savedWeekId, initialTypeId = savedTypeId,
            )
        } else {
            null
        }
    }
    val ranking = remember(repository, kind, scope) {
        if (kind == DiscoveryKind.RANKING) DiscoveryFeedController(scope, repository::page, snapshotLimit = 4) else null
    }
    DisposableEffect(weekly, ranking) { onDispose { weekly?.close(); ranking?.close() } }
    val weeklyFlow = weekly?.state ?: remember { kotlinx.coroutines.flow.MutableStateFlow(WeeklyDiscoveryState()) }
    val weekState by weeklyFlow.collectAsState()
    val controller = weekly?.feed ?: requireNotNull(ranking)
    val state by controller.state.collectAsState()
    var periodValue by rememberSaveable { mutableStateOf(RankingPeriod.WEEK.value) }
    var categoryId by rememberSaveable { mutableStateOf(rankingCategories.first().id) }
    val period = RankingPeriod.entries.firstOrNull { it.value == periodValue } ?: RankingPeriod.WEEK
    val category = rankingCategories.firstOrNull { it.id == categoryId } ?: rankingCategories.first()
    val query = weekly?.let { weekState.query }
        ?: if (kind == DiscoveryKind.RANKING) DiscoveryQuery.Ranking(period, category.id) else null
    val feed = if (query != null && state.query != query) DiscoveryFeedState(query = query, loading = true) else state
    val refreshing = weekly?.let { weekState.refreshing } ?: (feed.loading && feed.nextPage == 1 && feed.forceRefresh)
    val backdrop = rememberBarBackdrop()
    val density = LocalDensity.current
    val gridState = rememberLazyGridState()
    // 期刊 / 排行分类不再放顶栏下拉，改成与首页分类一致的右下角悬浮按钮。
    val filterItems = if (weekly != null) {
        weekState.catalog?.categories.orEmpty().map { item ->
            item.title?.takeIf(String::isNotBlank) ?: item.time?.takeIf(String::isNotBlank) ?: "第 ${item.id} 期"
        }
    } else {
        rankingCategories.map { it.title }
    }
    val filterSelectedIndex = if (weekly != null) {
        weekState.catalog?.categories.orEmpty().indexOfFirst { it.id == weekState.weekId }
    } else {
        rankingCategories.indexOfFirst { it.id == category.id }
    }.coerceAtLeast(0)
    val filterBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 12.dp

    LaunchedEffect(weekly) { weekly?.activate() }
    LaunchedEffect(weekState.weekId, weekState.typeId) {
        savedWeekId = weekState.weekId
        savedTypeId = weekState.typeId
    }
    LaunchedEffect(ranking, period, category.id) { ranking?.select(DiscoveryQuery.Ranking(period, category.id)) }

    fun refresh() {
        if (weekly != null) weekly.refresh() else if (!refreshing) controller.refresh()
    }

    fun selectRanking(nextPeriod: RankingPeriod = period, nextCategory: String = category.id) {
        ranking?.select(DiscoveryQuery.Ranking(nextPeriod, nextCategory))
        periodValue = nextPeriod.value
        categoryId = nextCategory
    }

    // 只有筛选变化才重置/恢复滚动位置；同一查询的刷新不会跳回顶部。
    val positions = remember { LinkedHashMap<DiscoveryQuery, Pair<Int, Int>>() }
    var displayedQuery by remember { mutableStateOf<DiscoveryQuery?>(null) }
    LaunchedEffect(query) {
        if (query != null && query != displayedQuery) {
            displayedQuery?.let {
                positions.remove(it)
                positions[it] = gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset
                while (positions.size > 4) positions.remove(positions.keys.first())
            }
            displayedQuery = query
            val position = positions[query]?.takeIf { feed.firstLoaded && it.first <= feed.albums.size }
            gridState.scrollToItem(position?.first ?: 0, position?.second ?: 0)
        }
    }
    val footerVisible by remember(gridState) {
        derivedStateOf { gridState.layoutInfo.visibleItemsInfo.any { it.key == "discovery-footer" } }
    }
    var moreArmed by remember(feed.query) { mutableStateOf(true) }
    LaunchedEffect(footerVisible, feed.nextPage, feed.loading, refreshing) {
        if (!footerVisible) moreArmed = true
        if (footerVisible && moreArmed && !refreshing && !feed.loading && feed.firstLoaded && feed.error == null && feed.end == null) {
            moreArmed = false
            controller.loadMore()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            BlurredBar(backdrop = backdrop, style = topBarBlurStyle) {
                SmallTopAppBar(
                    title = kind.title,
                    subtitle = if (weekly != null) {
                        weekState.week?.title?.takeIf(String::isNotBlank) ?: weekState.week?.time.orEmpty()
                    } else {
                        category.title + " · 观看排行"
                    },
                    color = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(MiuixIcons.Back, contentDescription = "返回", tint = MiuixTheme.colorScheme.onSurface)
                        }
                    },
                    bottomContent = {
                        Box(Modifier.fillMaxWidth().heightIn(min = 58.dp)) {
                            if (weekly != null) {
                                val types = weekState.catalog?.types.orEmpty()
                                DiscoveryTabRow(
                                    tabs = types.map { it.displayTitle() },
                                    selectedIndex = types.indexOfFirst { it.id == weekState.typeId }.coerceAtLeast(0),
                                    onTabSelected = { weekly.selectType(types[it].id) },
                                )
                            } else {
                                DiscoveryTabRow(
                                    tabs = RankingPeriod.entries.map { it.title },
                                    selectedIndex = period.ordinal,
                                    onTabSelected = { selectRanking(nextPeriod = RankingPeriod.entries[it]) },
                                )
                            }
                        }
                    },
                )
            }
        },
    ) { padding ->
        // Miuix 在刷新时会吞掉嵌套滚动：外层手势宿主驱动同一个网格状态，内层网格手势关闭。
        // 这样同一时刻只有一个启用的滚动所有者，保留原生拖拽/惯性，不需要原始增量补丁，
        // 也不需要对指示器做分叉或伪造 220ms 刷新完成信号。
        Box(
            Modifier.fillMaxSize().scrollable(
                state = gridState,
                orientation = Orientation.Vertical,
                reverseDirection = true,
                enabled = refreshing && feed.albums.isNotEmpty(),
            )
        ) {
            PullToRefresh(
                isRefreshing = refreshing,
                onRefresh = ::refresh,
                pullToRefreshState = rememberPullToRefreshState(),
                contentPadding = padding,
                refreshTexts = listOf("下拉更新", "松开更新", "正在更新", "更新结束"),
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    weekly != null && feed.albums.isEmpty() && weekState.catalogError != null ->
                        DiscoveryMessage("期刊未能更新", weekState.catalogError!!, Modifier.padding(padding))

                    weekly != null && !weekState.catalogLoading &&
                        weekState.catalog?.let { it.categories.isEmpty() || it.types.isEmpty() } == true ->
                        DiscoveryMessage("暂无每周必看", "当前没有可选的期刊或类型", Modifier.padding(padding))

                    feed.albums.isEmpty() && (weekState.catalogLoading || !feed.firstLoaded && feed.error == null) ->
                        DiscoveryMessage("正在加载", "", Modifier.padding(padding), loading = true)

                    feed.albums.isEmpty() ->
                        DiscoveryMessage(
                            if (feed.error != null) "暂时无法加载" else "暂无作品",
                            feed.error ?: "当前筛选下没有作品",
                            Modifier.padding(padding),
                        )

                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(104.dp * density.fontScale.coerceAtLeast(1f)),
                        state = gridState,
                        userScrollEnabled = !refreshing,
                        modifier = Modifier.fillMaxSize()
                            .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier),
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            end = 12.dp,
                            top = padding.calculateTopPadding() + 12.dp,
                            bottom = padding.calculateBottomPadding() + 24.dp,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        if (weekState.catalogError != null) {
                            item(key = "catalog-error", span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    "期刊未能更新，已保留现有内容。",
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    style = MiuixTheme.textStyles.footnote1,
                                )
                            }
                        }
                        itemsIndexed(feed.albums, key = { _, album -> album.id }) { index, album ->
                            Box {
                                AlbumItem(album = album, coverLifted = album.id == liftedAlbumId, onSelected = onAlbumSelected)
                                if (kind == DiscoveryKind.RANKING && album.id != liftedAlbumId) {
                                    Surface(
                                        modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                                        color = if (index < 3) {
                                            MiuixTheme.colorScheme.primary
                                        } else {
                                            MiuixTheme.colorScheme.surfaceContainerHigh
                                        },
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                                    ) {
                                        Text(
                                            "${index + 1}",
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                            style = MiuixTheme.textStyles.footnote1,
                                            color = if (index < 3) {
                                                MiuixTheme.colorScheme.onPrimary
                                            } else {
                                                MiuixTheme.colorScheme.onSurface
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        item(key = "discovery-footer", span = { GridItemSpan(maxLineSpan) }) {
                            Column(
                                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                if (feed.error != null) {
                                    Text(
                                        feed.error,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        style = MiuixTheme.textStyles.footnote1,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                                when {
                                    feed.loading && feed.nextPage > 1 -> CircularProgressIndicator(size = 20.dp)
                                    feed.error != null && feed.nextPage > 1 -> QuietTextAction("重试这一页", controller::retry)
                                    feed.error != null -> Text(
                                        "下拉重新更新",
                                        style = MiuixTheme.textStyles.footnote1,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )

                                    feed.end != null -> Text(
                                        feed.end.message,
                                        style = MiuixTheme.textStyles.footnote1,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        textAlign = TextAlign.Center,
                                    )

                                    !refreshing -> QuietTextAction("加载更多", controller::loadMore)
                                }
                            }
                        }
                    }
                }
            }
        }
        // 期刊 / 分类入口与首页一致：右下角悬浮玻璃按钮，顶栏不再占位。
        FloatingCategoryMenu(
            items = filterItems,
            selectedIndex = filterSelectedIndex,
            onSelect = { index ->
                if (weekly != null) {
                    weekState.catalog?.categories?.getOrNull(index)?.let { weekly.selectWeek(it.id) }
                } else {
                    rankingCategories.getOrNull(index)?.let { selectRanking(nextCategory = it.id) }
                }
            },
            backdrop = backdrop,
            description = if (weekly != null) "每周必看期刊" else "排行榜分类",
            bottomPadding = filterBottomPadding,
            visible = filterItems.isNotEmpty(),
            closeKey = query,
            iconOverride = if (weekly != null) {
                {
                    LiquidGlassIcon(
                        imageVector = MiuixIcons.Notes,
                        contentDescription = "选择期刊",
                        tint = MiuixTheme.colorScheme.onSurface,
                    )
                }
            } else {
                null
            },
        )
    }
}

/**
 * 顶栏标签行：直接用 Miuix 的 TabRowWithContour，底边多留一段空白，
 * 让渐进模糊/遮罩在标签下方完成衰减，标签自身仍处在有模糊、有遮罩的区间内。
 */
@Composable
private fun DiscoveryTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
) {
    if (tabs.isEmpty()) return
    TabRowWithContour(
        tabs = tabs,
        selectedTabIndex = selectedIndex.coerceIn(0, tabs.lastIndex),
        onTabSelected = onTabSelected,
        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
    )
}

/** 空态/错误态同样参与嵌套滚动：下拉重试不需要额外按钮。 */
@Composable
private fun DiscoveryMessage(title: String, message: String, modifier: Modifier = Modifier, loading: Boolean = false) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val viewportHeight = maxHeight
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().heightIn(min = viewportHeight), contentAlignment = Alignment.Center) {
                Column(
                    Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (loading) CircularProgressIndicator(size = 24.dp)
                    Text(title, style = MiuixTheme.textStyles.title4)
                    if (message.isNotBlank()) {
                        Text(
                            message,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            style = MiuixTheme.textStyles.footnote1,
                            textAlign = TextAlign.Center,
                        )
                    }
                    if (!loading) {
                        Text(
                            "下拉更新",
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            style = MiuixTheme.textStyles.footnote1,
                        )
                    }
                }
            }
        }
    }
}
