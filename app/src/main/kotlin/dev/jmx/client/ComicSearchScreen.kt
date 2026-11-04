package dev.jmx.client

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.houbb.opencc4j.util.ZhConverterUtil
import dev.jmx.client.core.api.AlbumDetail
import dev.jmx.client.core.api.SearchQueryComposer
import dev.jmx.client.core.protocol.JmxMagicConstants
import dev.jmx.client.core.result.JmxResult
import dev.jmx.client.effect.BlurredBar
import dev.jmx.client.effect.TopBarBlurStyle
import dev.jmx.client.effect.liquid.BindTopBarGlassScroll
import dev.jmx.client.effect.liquid.GlassTopBarHost
import dev.jmx.client.effect.liquid.LiquidGlassCapsule
import dev.jmx.client.effect.liquid.LiquidGlassIconButton
import dev.jmx.client.effect.liquid.currentTopBarGlassAlpha
import dev.jmx.client.effect.rememberBarBackdrop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.BadgedBox
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Filter
import top.yukonga.miuix.kmp.icon.extended.Recent
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.popup.WindowDropdownPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import dev.jmx.client.effect.liquid.GlassTopBarIcon as Icon

private const val INITIAL_SEARCH_FOCUS_GUARD_MILLIS = 320L
private val JM_SEARCH_ID_REGEX = Regex("(?i)^(?:JM)?\\s*(\\d+)$")

@Composable
internal fun ComicSearchScreen(
    homeRepository: HomeRepository,
    initialQuery: String? = null,
    outerPadding: PaddingValues = PaddingValues(),
    topBarBlurStyle: TopBarBlurStyle = TopBarBlurStyle.GAUSSIAN,
    liftedAlbumId: String?,
    onDismiss: () -> Unit,
    onAlbumSelected: (HomeAlbum, Rect?) -> Unit,
    favoriteAlbumIds: Set<String> = emptySet(),
    bookshelfAlbumIds: Set<String> = emptySet(),
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    val repository = remember(homeRepository) { ComicSearchRepository(homeRepository) }
    val historyStore = remember(context) { SearchHistoryStore(context) }
    var query by remember { mutableStateOf("") }
    var submittedQuery by remember { mutableStateOf<String?>(null) }
    var searchRequestId by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<ComicSearchUiState>(ComicSearchUiState.Idle) }
    var history by remember(historyStore) { mutableStateOf(historyStore.load()) }
    var deletingHistory by rememberSaveable { mutableStateOf(false) }
    var initialQueryConsumed by rememberSaveable(initialQuery) { mutableStateOf(false) }
    var inputMode by rememberSaveable(initialQuery) { mutableStateOf(initialQuery.isNullOrBlank()) }
    var searchOrder by rememberSaveable { mutableStateOf(ComicSearchOrder.LATEST) }
    var tagFilter by remember { mutableStateOf(SearchTagFilter()) }
    var showTagFilter by remember { mutableStateOf(false) }
    val tagStore = remember(context) { SearchTagStore(context) }
    var userTags by remember(tagStore) { mutableStateOf(tagStore.load()) }

    val searchBackdrop = rememberBarBackdrop()
    val searchScrollBehavior = MiuixScrollBehavior()

    fun dismissSearch() {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        onDismiss()
    }

    fun cancelInputMode() {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        deletingHistory = false
        if (submittedQuery == null) {
            dismissSearch()
        } else {
            query = submittedQuery.orEmpty()
            inputMode = false
        }
    }

    fun openAlbum(album: HomeAlbum, sourceBounds: Rect?) {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        onAlbumSelected(album, sourceBounds)
    }

    fun submitSearch(value: String) {
        val normalizedQuery = value.trim()
        if (normalizedQuery.isEmpty()) return
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        query = normalizedQuery
        history = historyStore.record(history, normalizedQuery)
        deletingHistory = false
        submittedQuery = normalizedQuery
        inputMode = false
        searchRequestId++
    }

    LaunchedEffect(initialQuery) {
        val value = initialQuery?.trim().orEmpty()
        if (!initialQueryConsumed && value.isNotEmpty()) {
            initialQueryConsumed = true
            submitSearch(value)
            delay(INITIAL_SEARCH_FOCUS_GUARD_MILLIS)
        }
    }

    LaunchedEffect(submittedQuery, searchRequestId, searchOrder, tagFilter, repository) {
        val baseQuery = submittedQuery
        if (baseQuery == null && !tagFilter.enabled) return@LaunchedEffect
        val queryText = baseQuery.orEmpty()
        state = ComicSearchUiState.Loading
        when (val result = repository.search(
            queryText,
            page = 1,
            order = searchOrder,
            tagFilter = tagFilter,
        )) {
            is ComicSearchResult.Direct -> {
                state = ComicSearchUiState.Content(
                    query = queryText,
                    albums = listOf(result.album),
                    total = 1,
                    nextPage = 2,
                    order = searchOrder,
                    tagFilter = tagFilter,
                    isLoadingMore = false,
                    endReached = true,
                )
            }
            is ComicSearchResult.Page -> {
                state = if (result.albums.isEmpty()) {
                    ComicSearchUiState.Empty(queryText.ifBlank { tagFilterSummary(tagFilter) })
                } else {
                    ComicSearchUiState.Content(
                        query = queryText,
                        albums = result.albums,
                        total = result.total,
                        nextPage = 2,
                        order = searchOrder,
                        tagFilter = tagFilter,
                        isLoadingMore = false,
                        endReached = result.endReached,
                    )
                }
            }
            is ComicSearchResult.Error -> {
                state = ComicSearchUiState.Error(result.message)
            }
        }
    }

    fun loadMore() {
        val current = state as? ComicSearchUiState.Content ?: return
        if (current.isLoadingMore || current.endReached) return
        state = current.copy(isLoadingMore = true, loadMoreError = null)
        coroutineScope.launch {
            when (val result = repository.search(
                current.query,
                page = current.nextPage,
                order = current.order,
                tagFilter = current.tagFilter,
            )) {
                is ComicSearchResult.Page -> {
                    val merged = mergeSearchAlbums(
                        existing = current.albums,
                        incoming = result.albums,
                        sourceEndReached = result.endReached,
                    )
                    val latest = state as? ComicSearchUiState.Content ?: return@launch
                    state = latest.copy(
                        albums = merged.albums,
                        nextPage = current.nextPage + 1,
                        isLoadingMore = false,
                        endReached = merged.endReached,
                        loadMoreError = null,
                    )
                }
                is ComicSearchResult.Error -> {
                    val latest = state as? ComicSearchUiState.Content ?: return@launch
                    state = latest.copy(
                        isLoadingMore = false,
                        loadMoreError = result.message,
                    )
                }
                is ComicSearchResult.Direct -> {
                    val latest = state as? ComicSearchUiState.Content ?: return@launch
                    state = latest.copy(isLoadingMore = false)
                }
            }
        }
    }

    BackHandler {
        when {
            showTagFilter -> showTagFilter = false
            deletingHistory -> deletingHistory = false
            inputMode && submittedQuery != null -> cancelInputMode()
            else -> dismissSearch()
        }
    }

    GlassTopBarHost {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(searchScrollBehavior.nestedScrollConnection),
            containerColor = Color.Transparent,
            topBar = {
                BlurredBar(
                    backdrop = searchBackdrop,
                    style = topBarBlurStyle,
                ) {
                    TopAppBar(
                        title = "搜索",
                        subtitle = if (state is ComicSearchUiState.Content) "排序：${searchOrder.label}" else "",
                        color = if (searchBackdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
                        scrollBehavior = searchScrollBehavior,
                        navigationIcon = {
                            LiquidGlassIconButton(
                                onClick = ::dismissSearch,
                                backdrop = searchBackdrop,
                                glassAlpha = currentTopBarGlassAlpha(),
                                size = 44.dp,
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.ChevronBackward,
                                    contentDescription = "返回",
                                    tint = MiuixTheme.colorScheme.onSurface,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .offset(x = (-1).dp),
                                )
                            }
                        },
                        actions = {
                            SearchActionsCapsule(
                                showFilter = SEARCH_TAG_FILTER_UI_ENABLED,
                                activeFilterCount = tagFilter.activeCount,
                                showSort = state is ComicSearchUiState.Content,
                                selectedOrder = searchOrder,
                                backdrop = searchBackdrop,
                                onOpenFilter = { showTagFilter = true },
                                onSelectOrder = { order ->
                                    if (order != searchOrder) searchOrder = order
                                },
                            )
                        },
                        bottomContent = {
                            SearchInputRow(
                                query = query,
                                onQueryChange = {
                                    query = it
                                    inputMode = true
                                },
                                onSearch = { submitSearch(query) },
                                onClear = {
                                    query = ""
                                    inputMode = true
                                },
                                onCancel = ::cancelInputMode,
                                showCancel = inputMode && submittedQuery != null,
                            )
                        },
                    )
                }
            },
        ) { pagePadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (searchBackdrop != null) Modifier.layerBackdrop(searchBackdrop) else Modifier)
                    .background(MiuixTheme.colorScheme.surface),
            ) {
                val contentInnerPadding = PaddingValues(
                    top = pagePadding.calculateTopPadding(),
                    bottom = outerPadding.calculateBottomPadding(),
                )
                AnimatedContent(
                    targetState = inputMode,
                    transitionSpec = {
                        (fadeIn(tween(180)) + slideInHorizontally { if (targetState) -it / 10 else it / 10 }) togetherWith
                            (fadeOut(tween(140)) + slideOutHorizontally { if (targetState) it / 10 else -it / 10 })
                    },
                    label = "SearchContentMode",
                ) { editing ->
                    if (editing) {
                        SearchDiscovery(
                            innerPadding = contentInnerPadding,
                            history = history,
                            deletingHistory = deletingHistory,
                            onDeletingHistoryChange = { deletingHistory = it },
                            onSelectTag = ::submitSearch,
                            onDeleteHistoryItem = { historyQuery ->
                                history = historyStore.remove(history, historyQuery)
                                if (history.isEmpty()) deletingHistory = false
                            },
                            onClearAllHistory = {
                                history = historyStore.clear()
                                deletingHistory = false
                            },
                            onOpenTagFilter = { showTagFilter = true },
                            tagFilter = tagFilter,
                        )
                    } else {
                        when (val current = state) {
                            ComicSearchUiState.Idle -> SearchMessage("输入关键词后执行搜索", contentInnerPadding)
                            ComicSearchUiState.Loading -> SearchLoading(contentInnerPadding)
                            is ComicSearchUiState.Empty -> SearchMessage("未找到“${current.query}”相关漫画", contentInnerPadding)
                            is ComicSearchUiState.Error -> SearchError(
                                message = current.message,
                                onRetry = { searchRequestId++ },
                                innerPadding = contentInnerPadding,
                            )
                            is ComicSearchUiState.Content -> key(
                                current.query,
                                current.order,
                                current.tagFilter,
                            ) {
                                SearchResultGrid(
                                    content = current,
                                    innerPadding = contentInnerPadding,
                                    liftedAlbumId = liftedAlbumId,
                                    onLoadMore = ::loadMore,
                                    onAlbumSelected = { album, bounds -> openAlbum(album, bounds) },
                                    favoriteAlbumIds = favoriteAlbumIds,
                                    bookshelfAlbumIds = bookshelfAlbumIds,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (SEARCH_TAG_FILTER_UI_ENABLED && showTagFilter) {
        SearchTagFilterDialog(
            filter = tagFilter,
            builtInTags = DEFAULT_SEARCH_TAGS,
            userTags = userTags,
            onAddUserTag = { tag ->
                tagStore.add(tag)
                userTags = tagStore.load()
            },
            onRemoveUserTag = { tag ->
                tagStore.remove(tag)
                userTags = tagStore.load()
            },
            onApply = { selected ->
                tagFilter = selected
                showTagFilter = false
                if (submittedQuery != null || selected.enabled) searchRequestId++
            },
            onDismiss = { showTagFilter = false },
        )
    }
}

@Composable
private fun SearchInputRow(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onClear: () -> Unit,
    onCancel: () -> Unit,
    showCancel: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MiuixTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .weight(1f)
                .height(42.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = MiuixIcons.Basic.Search,
                    contentDescription = "搜索",
                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable(onClick = { onSearch(query) }),
                )
                Spacer(modifier = Modifier.width(8.dp))
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MiuixTheme.textStyles.body1.copy(
                        color = MiuixTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Search,
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = { onSearch(query) },
                    ),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (query.isEmpty()) {
                                Text(
                                    text = "搜索标题、标签或JM车号",
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                            innerTextField()
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = onClear,
                        minWidth = 28.dp,
                        minHeight = 28.dp,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.2f),
                            modifier = Modifier.size(18.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = MiuixIcons.Close,
                                    contentDescription = "清除",
                                    tint = MiuixTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(12.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showCancel,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally(),
        ) {
            Text(
                text = "取消",
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable(onClick = onCancel)
                    .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
            )
        }
    }
}

private const val SEARCH_TAG_FILTER_UI_ENABLED = true

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchTagFilterDialog(
    filter: SearchTagFilter,
    builtInTags: List<String>,
    userTags: List<String>,
    onAddUserTag: (String) -> Unit,
    onRemoveUserTag: (String) -> Unit,
    onApply: (SearchTagFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    var working by remember(filter) { mutableStateOf(filter) }
    var input by remember { mutableStateOf("") }
    val userTagSet = remember(userTags) { userTags.mapNotNull(::normalizeSearchTag).toSet() }
    val tagOptions = remember(builtInTags, userTags) {
        (builtInTags + userTags).mapNotNull(::normalizeSearchTag).distinct()
    }
    val chipShape = RoundedCornerShape(8.dp)

    fun cycle(tag: String) {
        working = when (working.stateOf(tag)) {
            SearchTagState.NONE -> working.toggleInclude(tag)
            SearchTagState.INCLUDE -> working.toggleExclude(tag)
            SearchTagState.EXCLUDE -> working.toggleExclude(tag)
        }
    }

    fun deleteUserTag(tag: String) {
        onRemoveUserTag(tag)
        working = working.copy(
            includeTags = working.includeTags.filter { normalizeSearchTag(it) != tag },
            excludeTags = working.excludeTags.filter { normalizeSearchTag(it) != tag },
        )
    }

    fun addInputTag() {
        val tag = normalizeSearchTag(input) ?: return
        onAddUserTag(tag)
        if (working.stateOf(tag) != SearchTagState.INCLUDE) {
            working = working.toggleInclude(tag)
        }
        input = ""
    }

    val includeCount = working.normalizedIncludeTags.size
    val excludeCount = working.normalizedExcludeTags.size

    WindowDialog(
        show = true,
        title = "标签过滤",
        summary = if (!working.enabled) {
            "点按标签切换：包含 → 排除 → 取消"
        } else {
            buildList {
                if (includeCount > 0) add("包含 $includeCount")
                if (excludeCount > 0) add("排除 $excludeCount")
            }.joinToString(" · ")
        },
        onDismissRequest = onDismiss,
    ) {
        if (working.enabled) {
            Text(
                text = "已选条件（点按移除）",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                working.normalizedIncludeTags.forEach { tag ->
                    SearchTagChip(
                        label = "＋$tag",
                        container = MiuixTheme.colorScheme.primaryContainer,
                        onContainer = MiuixTheme.colorScheme.onPrimaryContainer,
                        shape = chipShape,
                        onClick = { working = working.toggleInclude(tag) },
                    )
                }
                working.normalizedExcludeTags.forEach { tag ->
                    SearchTagChip(
                        label = "－$tag",
                        container = MiuixTheme.colorScheme.errorContainer,
                        onContainer = MiuixTheme.colorScheme.onErrorContainer,
                        shape = chipShape,
                        onClick = { working = working.toggleExclude(tag) },
                    )
                }
            }
        } else {
            Text(
                text = "尚未添加条件。＋ 表示结果必须包含，－ 表示结果必须排除。",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = input,
                onValueChange = { input = it },
                label = "新增自定义标签",
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = "添加",
                enabled = normalizeSearchTag(input) != null,
                onClick = ::addInputTag,
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "可选标签：点按依次切换 ＋包含 / －排除 / 取消，长按删除自定义标签",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 260.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tagOptions.forEach { tag ->
                    val tagState = working.stateOf(tag)
                    SearchTagChip(
                        label = when (tagState) {
                            SearchTagState.INCLUDE -> "＋$tag"
                            SearchTagState.EXCLUDE -> "－$tag"
                            SearchTagState.NONE -> tag
                        },
                        container = when (tagState) {
                            SearchTagState.INCLUDE -> MiuixTheme.colorScheme.primaryContainer
                            SearchTagState.EXCLUDE -> MiuixTheme.colorScheme.errorContainer
                            SearchTagState.NONE -> MiuixTheme.colorScheme.surfaceContainerHigh
                        },
                        onContainer = when (tagState) {
                            SearchTagState.INCLUDE -> MiuixTheme.colorScheme.onPrimaryContainer
                            SearchTagState.EXCLUDE -> MiuixTheme.colorScheme.onErrorContainer
                            SearchTagState.NONE -> MiuixTheme.colorScheme.onSurface
                        },
                        shape = chipShape,
                        onClick = { cycle(tag) },
                        onLongClick = if (tag in userTagSet) {
                            { deleteUserTag(tag) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                text = "清除",
                enabled = working.enabled,
                onClick = { working = SearchTagFilter.EMPTY },
            )
            TextButton(
                text = "应用",
                onClick = { onApply(working) },
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}

@Composable
private fun SearchTagChip(
    label: String,
    container: Color,
    onContainer: Color,
    shape: Shape,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Surface(shape = shape, color = container) {
        Box(
            modifier = Modifier
                .clip(shape)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 10.dp, vertical = 7.dp),
        ) {
            Text(
                text = label,
                style = MiuixTheme.textStyles.footnote1,
                color = onContainer,
            )
        }
    }
}

internal enum class ComicSearchOrder(
    val apiValue: String,
    val label: String,
) {
    LATEST(JmxMagicConstants.ORDER_BY_LATEST, "最新"),
    MOST_VIEWED(JmxMagicConstants.ORDER_BY_VIEW, "最多点击"),
    MOST_PICTURES(JmxMagicConstants.ORDER_BY_PICTURE, "最多图片"),
    MOST_LIKED(JmxMagicConstants.ORDER_BY_LIKE, "最多爱心"),
}

@Composable
private fun SearchActionsCapsule(
    showFilter: Boolean,
    activeFilterCount: Int,
    showSort: Boolean,
    selectedOrder: ComicSearchOrder,
    backdrop: LayerBackdrop?,
    onOpenFilter: () -> Unit,
    onSelectOrder: (ComicSearchOrder) -> Unit,
) {
    if (!showFilter && !showSort) return

    val haptic = LocalHapticFeedback.current
    val isDark = MiuixTheme.colorScheme.surface.luminance() < 0.5f
    var sortMenuExpanded by remember { mutableStateOf(false) }

    val filterInteraction = remember { MutableInteractionSource() }
    val filterPressed by filterInteraction.collectIsPressedAsState()
    val filterScale = remember { Animatable(1f) }

    LaunchedEffect(filterPressed) {
        filterScale.animateTo(
            targetValue = if (filterPressed) 0.88f else 1f,
            animationSpec = spring(stiffness = 600f, dampingRatio = 0.65f),
        )
    }

    val sortInteraction = remember { MutableInteractionSource() }
    val sortPressed by sortInteraction.collectIsPressedAsState()
    val sortScale = remember { Animatable(1f) }

    LaunchedEffect(sortPressed) {
        sortScale.animateTo(
            targetValue = if (sortPressed) 0.88f else 1f,
            animationSpec = spring(stiffness = 600f, dampingRatio = 0.65f),
        )
    }

    val sortEntry = remember(selectedOrder) {
        DropdownEntry(
            items = ComicSearchOrder.entries.map { order ->
                DropdownItem(
                    text = order.label,
                    selected = order == selectedOrder,
                    onClick = {
                        onSelectOrder(order)
                        sortMenuExpanded = false
                    },
                )
            },
        )
    }

    LiquidGlassCapsule(
        backdrop = backdrop,
        glassAlpha = currentTopBarGlassAlpha(),
        modifier = Modifier.animateContentSize(),
    ) {
        if (showFilter) {
            val filterActive = activeFilterCount > 0
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = filterInteraction,
                        indication = null,
                        role = Role.Button,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            onOpenFilter()
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                BadgedBox(
                    badge = {
                        if (filterActive) {
                            Badge(
                                containerColor = MiuixTheme.colorScheme.primary,
                                contentColor = MiuixTheme.colorScheme.onPrimary,
                            ) {
                                Text(text = activeFilterCount.toString())
                            }
                        }
                    },
                    modifier = Modifier.graphicsLayer {
                        scaleX = filterScale.value
                        scaleY = filterScale.value
                    },
                ) {
                    Icon(
                        imageVector = MiuixIcons.Filter,
                        contentDescription = if (filterActive) "标签过滤已启用，$activeFilterCount 个标签" else "标签过滤",
                        modifier = Modifier.size(20.dp),
                        tint = if (filterActive) {
                            MiuixTheme.colorScheme.primary
                        } else {
                            MiuixTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }

        if (showFilter && showSort) {
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(16.dp)
                    .background(
                        if (isDark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.12f),
                    ),
            )
        }

        if (showSort) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = sortInteraction,
                        indication = null,
                        role = Role.Button,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            sortMenuExpanded = true
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MiuixIcons.Sort,
                    contentDescription = "结果排序：${selectedOrder.label}",
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer {
                            scaleX = sortScale.value
                            scaleY = sortScale.value
                        },
                    tint = MiuixTheme.colorScheme.onSurface,
                )
            }
        }
    }

    if (showSort) {
        WindowDropdownPopup(
            entries = listOf(sortEntry),
            show = sortMenuExpanded,
            onDismiss = { sortMenuExpanded = false },
            onDismissFinished = {},
            maxHeight = null,
            dropdownColors = DropdownDefaults.dropdownColors(),
        )
    }
}

@Composable
private fun SearchResultGrid(
    content: ComicSearchUiState.Content,
    innerPadding: PaddingValues,
    liftedAlbumId: String?,
    onLoadMore: () -> Unit,
    onAlbumSelected: (HomeAlbum, Rect) -> Unit,
    favoriteAlbumIds: Set<String>,
    bookshelfAlbumIds: Set<String>,
) {
    val gridState = rememberLazyGridState()
    BindTopBarGlassScroll(gridState)
    val footerKey = "search-footer"
    val footerVisible by remember(gridState) {
        derivedStateOf {
            gridState.layoutInfo.visibleItemsInfo.any { item -> item.key == footerKey }
        }
    }
    var loadMoreArmed by remember(content.query, content.tagFilter) { mutableStateOf(true) }
    LaunchedEffect(footerVisible) {
        if (!footerVisible) loadMoreArmed = true
    }
    LaunchedEffect(content.nextPage) {
        loadMoreArmed = true
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 10.dp,
            top = innerPadding.calculateTopPadding() + 8.dp,
            end = 10.dp,
            bottom = innerPadding.calculateBottomPadding() + 28.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(items = content.albums, key = { it.id }) { album ->
            AlbumItem(
                album = album,
                coverLifted = album.id == liftedAlbumId,
                onSelected = onAlbumSelected,
                isFavorite = album.id in favoriteAlbumIds,
                inBookshelf = album.id in bookshelfAlbumIds,
            )
        }
        item(key = "search-footer", span = { GridItemSpan(maxLineSpan) }) {
            LaunchedEffect(
                content.nextPage,
                content.isLoadingMore,
                content.loadMoreError,
                content.endReached,
                footerVisible,
            ) {
                if (
                    footerVisible &&
                    loadMoreArmed &&
                    !content.isLoadingMore &&
                    content.loadMoreError == null &&
                    !content.endReached
                ) {
                    loadMoreArmed = false
                    onLoadMore()
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    content.isLoadingMore -> CircularProgressIndicator(size = 24.dp, strokeWidth = 3.dp)
                    content.loadMoreError != null -> TextButton(text = "加载失败，重试", onClick = onLoadMore)
                    content.endReached -> Text(
                        text = "已显示全部 ${content.albums.size} 部漫画",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchDiscovery(
    innerPadding: PaddingValues,
    history: List<String>,
    deletingHistory: Boolean,
    onDeletingHistoryChange: (Boolean) -> Unit,
    onSelectTag: (String) -> Unit,
    onDeleteHistoryItem: (String) -> Unit,
    onClearAllHistory: () -> Unit,
    onOpenTagFilter: () -> Unit,
    tagFilter: SearchTagFilter,
    modifier: Modifier = Modifier,
) {
    val pillShape = CircleShape
    val haptic = LocalHapticFeedback.current

    val discoveryScroll = rememberScrollState()
    BindTopBarGlassScroll(discoveryScroll)
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(
                top = innerPadding.calculateTopPadding() + 10.dp,
                bottom = innerPadding.calculateBottomPadding() + 16.dp,
            )
            .verticalScroll(discoveryScroll),
    ) {
        if (history.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 16.dp, top = 4.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = MiuixIcons.Recent,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    )
                    Text(
                        text = "搜索历史",
                        style = MiuixTheme.textStyles.title4,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                }
                if (!deletingHistory) {
                    IconButton(
                        onClick = { onDeletingHistoryChange(true) },
                        minWidth = 36.dp,
                        minHeight = 36.dp,
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Delete,
                            contentDescription = "管理搜索历史",
                            modifier = Modifier.size(18.dp),
                            tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        )
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            onClick = onClearAllHistory,
                            shape = CircleShape,
                            color = MiuixTheme.colorScheme.errorContainer.copy(alpha = 0.22f),
                        ) {
                            Text(
                                text = "清空全部",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            )
                        }
                        Surface(
                            onClick = { onDeletingHistoryChange(false) },
                            shape = CircleShape,
                            color = MiuixTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                text = "完成",
                                style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.SemiBold),
                                color = MiuixTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            )
                        }
                    }
                }
            }

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                history.forEach { item ->
                    val itemBorder = if (deletingHistory) {
                        BorderStroke(0.8.dp, MiuixTheme.colorScheme.error.copy(alpha = 0.35f))
                    } else {
                        BorderStroke(0.8.dp, MiuixTheme.colorScheme.outline.copy(alpha = 0.12f))
                    }
                    Surface(
                        shape = pillShape,
                        color = if (deletingHistory) {
                            MiuixTheme.colorScheme.errorContainer.copy(alpha = 0.22f)
                        } else {
                            MiuixTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f)
                        },
                        border = itemBorder,
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(pillShape)
                                .combinedClickable(
                                    onClick = {
                                        if (deletingHistory) {
                                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                            onDeleteHistoryItem(item)
                                        } else {
                                            onSelectTag(item)
                                        }
                                    },
                                    onLongClick = {
                                        if (!deletingHistory) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onDeletingHistoryChange(true)
                                        }
                                    },
                                )
                                .padding(
                                    start = 14.dp,
                                    end = if (deletingHistory) 10.dp else 14.dp,
                                    top = 7.dp,
                                    bottom = 7.dp,
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = item,
                                style = MiuixTheme.textStyles.body2,
                                color = if (deletingHistory) {
                                    MiuixTheme.colorScheme.error
                                } else {
                                    MiuixTheme.colorScheme.onSurface
                                },
                            )
                            if (deletingHistory) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(MiuixTheme.colorScheme.error.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = MiuixIcons.Close,
                                        contentDescription = "删除",
                                        tint = MiuixTheme.colorScheme.error,
                                        modifier = Modifier.size(11.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = MiuixIcons.Tune,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                )
                Text(
                    text = "常用标签",
                    style = MiuixTheme.textStyles.title4,
                    color = MiuixTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = "点击快速搜索",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }

        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DEFAULT_SEARCH_TAGS.forEach { tag ->
                Surface(
                    shape = pillShape,
                    color = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.75f),
                    border = BorderStroke(0.8.dp, MiuixTheme.colorScheme.outline.copy(alpha = 0.10f)),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        onSelectTag(tag)
                    },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "#",
                            style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.Bold),
                            color = MiuixTheme.colorScheme.primary.copy(alpha = 0.75f),
                        )
                        Text(
                            text = tag,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchMessage(
    message: String,
    innerPadding: PaddingValues = PaddingValues(),
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MiuixTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(64.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = MiuixIcons.Basic.Search,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    )
                }
            }
            Text(
                text = message,
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SearchLoading(
    innerPadding: PaddingValues = PaddingValues(),
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CircularProgressIndicator(size = 32.dp, strokeWidth = 3.dp)
            Text(
                text = "正在搜索漫画...",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun SearchError(
    message: String,
    onRetry: () -> Unit,
    innerPadding: PaddingValues = PaddingValues(),
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = message,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
            )
            TextButton(
                text = "重试",
                onClick = onRetry,
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}

internal class ComicSearchRepository(
    private val homeRepository: HomeRepository,
) {

    suspend fun search(
        query: String,
        page: Int,
        order: ComicSearchOrder = ComicSearchOrder.LATEST,
        tagFilter: SearchTagFilter = SearchTagFilter(),
    ): ComicSearchResult {
        val normalizedQuery = query.trim()
        val directId = normalizedQuery.toJmSearchIdOrNull()
        if (directId != null && !tagFilter.enabled) return loadDirectAlbum(directId)

        val composedQuery = SearchQueryComposer.compose(
            baseQuery = normalizedQuery,
            includeTags = tagFilter.normalizedIncludeTags,
            excludeTags = tagFilter.normalizedExcludeTags,
        )
        if (composedQuery.isBlank()) return ComicSearchResult.Error("请输入搜索关键词或至少一个包含标签。")

        return withContext(Dispatchers.IO) {
            try {
                when (val result = homeRepository.core.albumApi.searchResolved(
                    query = composedQuery,
                    page = page,
                    order = order.apiValue,
                    mainTag = JmxMagicConstants.MAIN_TAG_ALL,
                )) {
                    is JmxResult.Success -> {
                        val searchPage = result.value
                        val redirectId = searchPage.redirectAlbumId?.takeIf { it.isNotBlank() }
                        if (redirectId != null && !tagFilter.enabled && searchPage.content.size == 1) {
                            ComicSearchResult.Direct(
                                searchPage.content.first().toHomeAlbum(homeRepository.currentImageHost),
                            )
                        } else {
                            val albums = searchPage.content
                                .map { it.toHomeAlbum(homeRepository.currentImageHost) }
                                .distinctBy { it.id }
                            ComicSearchResult.Page(
                                albums = albums,
                                total = searchPage.total,
                                endReached = searchPage.content.isEmpty(),
                                tagFilter = tagFilter,
                            )
                        }
                    }
                    is JmxResult.Failure -> ComicSearchResult.Error(result.error.toUiMessage())
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                ComicSearchResult.Error(failure.message ?: "搜索失败，请重试")
            }
        }
    }

    private suspend fun loadDirectAlbum(albumId: String): ComicSearchResult {
        return withContext(Dispatchers.IO) {
            when (val result = homeRepository.core.albumApi.detailFull(albumId)) {
                is JmxResult.Success -> ComicSearchResult.Direct(
                    result.value.toHomeAlbum(homeRepository.currentImageHost),
                )
                is JmxResult.Failure -> ComicSearchResult.Error(result.error.toUiMessage())
            }
        }
    }
}

internal sealed interface ComicSearchResult {
    data class Direct(val album: HomeAlbum) : ComicSearchResult
    data class Page(
        val albums: List<HomeAlbum>,
        val total: Int?,
        val endReached: Boolean,
        val tagFilter: SearchTagFilter = SearchTagFilter(),
    ) : ComicSearchResult
    data class Error(val message: String) : ComicSearchResult
}

internal data class SearchAlbumMerge(
    val albums: List<HomeAlbum>,
    val endReached: Boolean,
)

internal fun mergeSearchAlbums(
    existing: List<HomeAlbum>,
    incoming: List<HomeAlbum>,
    sourceEndReached: Boolean,
): SearchAlbumMerge {
    val existingIds = existing.mapTo(hashSetOf()) { it.id }
    val newAlbums = incoming.filter { it.id !in existingIds }.distinctBy { it.id }
    return SearchAlbumMerge(
        albums = existing + newAlbums,
        endReached = sourceEndReached || newAlbums.isEmpty(),
    )
}

private sealed interface ComicSearchUiState {
    data object Idle : ComicSearchUiState
    data object Loading : ComicSearchUiState
    data class Empty(val query: String) : ComicSearchUiState
    data class Error(val message: String) : ComicSearchUiState
    data class Content(
        val query: String,
        val albums: List<HomeAlbum>,
        val total: Int?,
        val nextPage: Int,
        val order: ComicSearchOrder,
        val tagFilter: SearchTagFilter = SearchTagFilter(),
        val isLoadingMore: Boolean = false,
        val endReached: Boolean = false,
        val loadMoreError: String? = null,
    ) : ComicSearchUiState
}

internal fun tagFilterSummary(filter: SearchTagFilter): String {
    val parts = buildList {
        filter.normalizedIncludeTags.forEach { add("+$it") }
        filter.normalizedExcludeTags.forEach { add("-$it") }
    }
    return if (parts.isEmpty()) "标签过滤" else parts.joinToString(" ")
}

internal fun String.toJmSearchIdOrNull(): String? {
    return JM_SEARCH_ID_REGEX.matchEntire(trim())?.groupValues?.getOrNull(1)
}

internal fun AlbumDetail.toHomeAlbum(imageHost: String): HomeAlbum {
    return HomeAlbum(
        id = id,
        name = name?.takeIf { it.isNotBlank() } ?: "JM$id",
        author = authors.joinToString(" / ").ifBlank { "未知作者" },
        coverUrl = dev.jmx.client.core.image.ImageUrl.albumCover(imageHost, id),
        imageHost = imageHost,
    )
}

internal fun searchQueryVariants(
    query: String,
    toTraditional: (String) -> String,
): List<String> {
    val normalized = query.trim()
    if (normalized.isEmpty()) return emptyList()
    return listOf(normalized, toTraditional(normalized).trim())
        .filter { it.isNotEmpty() }
        .distinct()
}

internal fun toTraditionalChinese(text: String): String =
    runCatching { ZhConverterUtil.toTraditional(text) }.getOrDefault(text)
