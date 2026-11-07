package io.kazusa.jmcomic

import android.os.Build
import android.view.RoundedCorner
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import io.kazusa.jmcomic.core.api.ActionResult
import io.kazusa.jmcomic.core.api.AlbumChapter
import io.kazusa.jmcomic.core.api.AlbumDetail
import io.kazusa.jmcomic.core.api.CommentItem
import io.kazusa.jmcomic.core.api.CommentPage
import io.kazusa.jmcomic.core.result.JmxResult
import io.kazusa.jmcomic.core.runtime.JmxCore
import io.kazusa.jmcomic.effect.BlurredBar
import io.kazusa.jmcomic.effect.MorphPanelClipShape
import io.kazusa.jmcomic.effect.TopBarBlurStyle
import io.kazusa.jmcomic.effect.computeCoverMorphFrame
import io.kazusa.jmcomic.effect.computeMorphPanelAlpha
import io.kazusa.jmcomic.effect.computeMorphSettleVelocity
import io.kazusa.jmcomic.effect.computeMorphVeil
import io.kazusa.jmcomic.effect.computePanelMorphFrame
import io.kazusa.jmcomic.effect.computePredictiveMorphProgress
import io.kazusa.jmcomic.effect.isBoundsVisibleInScreen
import io.kazusa.jmcomic.effect.liquid.LiquidGlassIconButton
import io.kazusa.jmcomic.effect.liquid.animateTopBarGlassProgress
import io.kazusa.jmcomic.effect.morphContentSettled
import io.kazusa.jmcomic.effect.rememberBarBackdrop
import io.kazusa.jmcomic.effect.rememberPagerFlingPassThroughConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.FabPosition
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.ExpandMore
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Send
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.icon.extended.Store
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.springAnimateToPage
import top.yukonga.miuix.kmp.window.WindowDialog
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import io.kazusa.jmcomic.effect.liquid.GlassTopBarIcon as Icon

internal data class AlbumDetailTransitionRequest(
    val album: HomeAlbum,
    val sourceBounds: Rect?,
    val origin: AlbumDetailOrigin,
)

internal enum class AlbumDetailOrigin { HOME, SEARCH, ACCOUNT, BOOKSHELF, DOWNLOADS, DISCOVERY }

@Composable
internal fun AlbumDetailTransitionHost(
    request: AlbumDetailTransitionRequest,
    repository: AlbumDetailRepository,
    readerActive: Boolean,
    authenticated: Boolean,
    onRequireLogin: () -> Unit,
    bookshelfRepository: BookshelfRepository,
    onBookshelfChanged: () -> Unit,
    onFavoriteChanged: (Boolean) -> Unit,
    onSearchRequested: (String) -> Unit,
    onStartReading: (ReaderLaunchRequest) -> Unit,
    onDismiss: () -> Unit,
    onChaptersLoaded: (String, Int) -> Unit = { _, _ -> },
    favoriteRepository: AccountDataRepository,
    sessionRevision: Int,
    offlineAlbum: OfflineAlbum? = null,
    offlineManifest: ReaderOfflineManifest? = null,
    onDownload: (HomeAlbum, AlbumDetail, Set<String>) -> Unit,
    topBarBlurStyle: TopBarBlurStyle = TopBarBlurStyle.GAUSSIAN,
) {
    val transitionProgress = remember(request.album.id) { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    var targetBounds by remember(request.album.id) { mutableStateOf<Rect?>(null) }
    var hasEntered by remember(request.album.id) { mutableStateOf(false) }
    var isExiting by remember(request.album.id) { mutableStateOf(false) }
    var hasDismissed by remember(request.album.id) { mutableStateOf(false) }
    var searchAfterExit by remember(request.album.id) { mutableStateOf<String?>(null) }
    val view = LocalView.current
    val screenCornerRadiusPx = remember(view) {
        if (Build.VERSION.SDK_INT >= 31) {
            val insets = view.rootWindowInsets
            fun radius(position: Int): Float =
                insets?.getRoundedCorner(position)?.radius?.toFloat() ?: 0f
            maxOf(
                radius(RoundedCorner.POSITION_TOP_LEFT),
                radius(RoundedCorner.POSITION_TOP_RIGHT),
                radius(RoundedCorner.POSITION_BOTTOM_RIGHT),
                radius(RoundedCorner.POSITION_BOTTOM_LEFT),
            )
        } else 0f
    }

    fun dismissOnce() {
        if (!hasDismissed) {
            hasDismissed = true
            onDismiss()
        }
    }

    fun exitDetail(searchQuery: String? = null) {
        searchQuery?.trim()?.takeIf(String::isNotEmpty)?.let { searchAfterExit = it }
        if (isExiting) return
        isExiting = true
        coroutineScope.launch {
            try {
                transitionProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = DetailSpringSpec,
                )
            } finally {
                withContext(NonCancellable) {
                    transitionProgress.snapTo(0f)
                    dismissOnce()
                    searchAfterExit?.let(onSearchRequested)
                }
            }
        }
    }

    PredictiveBackHandler(enabled = !hasDismissed) { events ->
        if (isExiting) {
            events.collect { }
            return@PredictiveBackHandler
        }
        val startProgress = transitionProgress.value
        var lastProgress = startProgress
        var lastTimeNanos = 0L
        var releaseVelocity = 0f
        try {
            transitionProgress.stop()
            events.collect { event ->
                val preview = computePredictiveMorphProgress(startProgress, event.progress)
                val now = System.nanoTime()
                if (lastTimeNanos != 0L && now > lastTimeNanos) {
                    val dt = (now - lastTimeNanos) / 1_000_000_000f
                    if (dt > 0f) {
                        releaseVelocity = (preview - lastProgress) / dt
                    }
                }
                lastProgress = preview
                lastTimeNanos = now
                transitionProgress.snapTo(preview)
            }
            isExiting = true
            val settleVelocity = computeMorphSettleVelocity(transitionProgress.value, 0f, releaseVelocity)
            transitionProgress.animateTo(
                targetValue = 0f,
                animationSpec = DetailSpringSpec,
                initialVelocity = settleVelocity,
            )
            dismissOnce()
            searchAfterExit?.let(onSearchRequested)
        } catch (cancelled: CancellationException) {
            isExiting = false
            coroutineScope.launch {
                transitionProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = DetailSpringSpec,
                )
            }
            throw cancelled
        }
    }

    LaunchedEffect(targetBounds, request.sourceBounds) {
        if (!hasEntered && (targetBounds != null || request.sourceBounds == null)) {
            hasEntered = true
            transitionProgress.animateTo(
                targetValue = 1f,
                animationSpec = DetailSpringSpec,
            )
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val pageWidthPx = constraints.maxWidth.toFloat()
        val pageHeightPx = constraints.maxHeight.toFloat()
        val screenBounds = remember(pageWidthPx, pageHeightPx) {
            Rect(0f, 0f, pageWidthPx, pageHeightPx)
        }
        val progress = transitionProgress.value
        val startCornerRadiusPx = with(density) { 8.dp.toPx() }

        val source = request.sourceBounds
        val effectiveStartBounds = remember(source, screenBounds) {
            if (source != null && !source.isEmpty) {
                if (isBoundsVisibleInScreen(source, screenBounds)) {
                    source
                } else {
                    val safeWidth = (120f * density.density).coerceAtLeast(100f)
                    val safeHeight = safeWidth * (4f / 3f)
                    val center = screenBounds.center
                    Rect(
                        left = center.x - safeWidth / 2f,
                        top = center.y - safeHeight / 2f,
                        right = center.x + safeWidth / 2f,
                        bottom = center.y + safeHeight / 2f,
                    )
                }
            } else null
        }

        val panelFrame = remember(progress, effectiveStartBounds, screenBounds, screenCornerRadiusPx) {
            if (effectiveStartBounds != null) {
                computePanelMorphFrame(
                    progress = progress,
                    start = effectiveStartBounds,
                    end = screenBounds,
                    startCornerRadiusPx = startCornerRadiusPx,
                    endCornerRadiusPx = screenCornerRadiusPx,
                )
            } else null
        }

        val veil = computeMorphVeil(progress)
        val contentSettled = morphContentSettled(veil, progress)
        val isExpanded = hasEntered && progress >= 0.999f && !isExiting

        // 1. 动态裁剪背景层 (视觉面)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f)
                .graphicsLayer {
                    val frame = panelFrame
                    alpha = if (frame == null) veil else computeMorphPanelAlpha(progress)
                    clip = !isExpanded && frame != null
                    if (clip && frame != null) {
                        shape = MorphPanelClipShape(frame.bounds, frame.cornerRadii)
                    }
                }
                .background(MiuixTheme.colorScheme.surface),
        )

        // 2. 内容层 (内容层：无几何缩放，只吃透明度与轮廓裁剪，未完全显形前屏蔽交互)
        val contentSemantics = if (contentSettled) Modifier else Modifier.clearAndSetSemantics {}
        val inputGate = if (contentSettled) {
            Modifier
        } else {
            Modifier.pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(2f)
                .then(contentSemantics)
                .then(inputGate)
                .graphicsLayer {
                    val frame = panelFrame
                    clip = !isExpanded && frame != null
                    if (clip && frame != null) {
                        shape = MorphPanelClipShape(frame.bounds, frame.cornerRadii)
                    }
                    alpha = if (frame != null) veil else progress
                },
        ) {
            AlbumDetailScreen(
                album = request.album,
                repository = repository,
                readerActive = readerActive,
                authenticated = authenticated,
                onRequireLogin = onRequireLogin,
                bookshelfRepository = bookshelfRepository,
                onBookshelfChanged = onBookshelfChanged,
                onFavoriteChanged = onFavoriteChanged,
                onSearchRequested = { query -> exitDetail(query) },
                onStartReading = onStartReading,
                showCover = request.sourceBounds == null || isExpanded,
                onBack = { exitDetail() },
                onCoverTargetChanged = { bounds -> targetBounds = bounds },
                onChaptersLoaded = onChaptersLoaded,
                favoriteRepository = favoriteRepository,
                sessionRevision = sessionRevision,
                offline = request.origin == AlbumDetailOrigin.DOWNLOADS,
                offlineAlbum = offlineAlbum,
                offlineManifest = offlineManifest,
                onDownload = onDownload,
                topBarBlurStyle = topBarBlurStyle,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // 3. 飞行封面层 (采用 e1 位置先快 + e2 尺寸先慢双缓动自然飞入与生长)
        val context = LocalContext.current
        val flyingCoverRequest = remember(request.album.coverUrl) {
            buildCoverRequest(context, request.album.coverUrl)
        }
        val destination = targetBounds
        if (effectiveStartBounds != null && !isExpanded) {
            val coverFrame = if (destination != null) {
                computeCoverMorphFrame(
                    progress = progress,
                    start = effectiveStartBounds,
                    end = destination,
                    startCornerRadiusPx = startCornerRadiusPx,
                    endCornerRadiusPx = startCornerRadiusPx,
                )
            } else null

            val coverBounds = coverFrame?.bounds ?: effectiveStartBounds
            val coverShape = remember { RoundedCornerShape(8.dp) }

            AsyncImage(
                model = flyingCoverRequest,
                contentDescription = request.album.name,
                modifier = Modifier
                    .zIndex(4f)
                    .offset {
                        IntOffset(
                            x = coverBounds.left.roundToInt(),
                            y = coverBounds.top.roundToInt(),
                        )
                    }
                    .size(
                        width = with(density) { coverBounds.width.toDp() },
                        height = with(density) { coverBounds.height.toDp() },
                    )
                    .clip(coverShape),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun AlbumDetailScreen(
    album: HomeAlbum,
    repository: AlbumDetailRepository,
    readerActive: Boolean,
    authenticated: Boolean,
    onRequireLogin: () -> Unit,
    bookshelfRepository: BookshelfRepository,
    onBookshelfChanged: () -> Unit,
    onFavoriteChanged: (Boolean) -> Unit,
    onSearchRequested: (String) -> Unit,
    onStartReading: (ReaderLaunchRequest) -> Unit,
    showCover: Boolean,
    onBack: () -> Unit,
    onCoverTargetChanged: (Rect) -> Unit,
    onChaptersLoaded: (String, Int) -> Unit = { _, _ -> },
    favoriteRepository: AccountDataRepository,
    sessionRevision: Int,
    offline: Boolean,
    offlineAlbum: OfflineAlbum? = null,
    offlineManifest: ReaderOfflineManifest? = null,
    onDownload: (HomeAlbum, AlbumDetail, Set<String>) -> Unit,
    topBarBlurStyle: TopBarBlurStyle = TopBarBlurStyle.GAUSSIAN,
    modifier: Modifier = Modifier,
) {
    var state by remember(album.id) { mutableStateOf<AlbumDetailUiState>(AlbumDetailUiState.Loading) }
    val pagerState = rememberPagerState(initialPage = 0) { 3 }
    var retryKey by remember(album.id) { mutableIntStateOf(0) }
    var pageSummary by remember(album.id) { mutableStateOf<AlbumPageSummary>(AlbumPageSummary.Loading) }
    var pendingAction by remember(album.id) { mutableStateOf<DetailAccountAction?>(null) }
    var actionError by remember(album.id) { mutableStateOf<String?>(null) }
    var showCommentComposer by rememberSaveable(album.id) { mutableStateOf(false) }
    var replyCommentId by rememberSaveable(album.id) { mutableStateOf<String?>(null) }
    var replyCommentUsername by rememberSaveable(album.id) { mutableStateOf<String?>(null) }
    var inBookshelf by remember(album.id, bookshelfRepository) {
        mutableStateOf(bookshelfRepository.contains(album.id))
    }
    var resumeEntry by remember(album.id, bookshelfRepository) {
        mutableStateOf(bookshelfRepository.entry(album.id))
    }
    var showBookshelfGroupPicker by remember(album.id) { mutableStateOf(false) }
    var showFavoriteFolderPicker by remember(album.id) { mutableStateOf(false) }
    var favoriteFolderError by remember(album.id) { mutableStateOf<String?>(null) }
    var showDownloadPicker by remember(album.id) { mutableStateOf(false) }
    val applicationContext = LocalContext.current.applicationContext
    val offlineProgress = remember(applicationContext) { OfflineReadingProgressStore(applicationContext) }
    val coroutineScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    val pageBackdrop = rememberBarBackdrop()

    LaunchedEffect(album.id, repository, retryKey, offline, offlineAlbum?.detail) {
        if (offline) {
            state = if (offlineAlbum == null) AlbumDetailUiState.Error("本地下载已不可用，请返回下载页检查。")
            else AlbumDetailUiState.Content(
                detail = offlineAlbum.detail,
                comments = DetailCommentsState(endReached = true, error = "离线模式不加载评论"),
            )
            return@LaunchedEffect
        }
        val loaded = repository.load(album.id)
        state = loaded
        val content = loaded as? AlbumDetailUiState.Content ?: return@LaunchedEffect
        // 详情页拿到的章节列表比任何一次后台扫描都新，交给更新追踪对账并清掉这部漫画的提示。
        onChaptersLoaded(album.id, content.detail.readingChapters().size)
        val comments = repository.loadInitialComments(album.id)
        val latest = state as? AlbumDetailUiState.Content
        if (latest?.detail?.id == content.detail.id) {
            state = latest.copy(comments = comments)
        }
    }

    val detail = (state as? AlbumDetailUiState.Content)?.detail
    val visibleChapters = remember(detail, offline, offlineManifest) {
        if (offline) offlineManifest?.chapters.orEmpty().map { it.toReaderChapter() }
        else detail?.readingChapters().orEmpty()
    }
    LaunchedEffect(detail?.id, repository, readerActive, offline, offlineManifest) {
        pageSummary = if (detail == null) {
            AlbumPageSummary.Loading
        } else if (offline) {
            AlbumPageSummary.Ready(
                totalPages = offlineManifest?.chapters?.sumOf { it.pages.size } ?: 0,
                resolvedChapters = visibleChapters.size,
                totalChapters = visibleChapters.size,
            )
        } else if (readerActive) {
            pageSummary
        } else {
            repository.loadPageSummary(detail) { progress -> pageSummary = progress }
        }
    }

    fun loadMoreComments() {
        if (offline) return
        val content = state as? AlbumDetailUiState.Content ?: return
        if (content.comments.isLoading || content.comments.endReached) return
        state = content.copy(comments = content.comments.copy(isLoading = true, error = null))
        coroutineScope.launch {
            val updated = repository.loadMoreComments(album.id, content.comments)
            val latest = state as? AlbumDetailUiState.Content
            if (latest?.detail?.id == content.detail.id) {
                state = latest.copy(comments = updated)
            }
        }
    }

    fun likeAlbum() {
        if (offline) { actionError = "离线模式不执行在线操作"; return }
        if (!authenticated) {
            onRequireLogin()
            return
        }
        val content = state as? AlbumDetailUiState.Content ?: return
        if (pendingAction != null || content.detail.liked == true) return
        pendingAction = DetailAccountAction.LIKE
        actionError = null
        coroutineScope.launch {
            when (val result = repository.likeAlbum(album.id)) {
                is JmxResult.Success -> {
                    val rejection = result.value.rejectionMessageOrNull()
                    if (rejection != null) {
                        actionError = rejection
                    } else {
                        val latest = state as? AlbumDetailUiState.Content
                        if (latest != null) {
                            state = latest.copy(
                                detail = latest.detail.copy(
                                    liked = true,
                                    likes = (latest.detail.likes ?: 0) + 1,
                                ),
                            )
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        }
                    }
                }
                is JmxResult.Failure -> {
                    if (result.error.requiresLogin()) onRequireLogin()
                    else actionError = result.error.toUiMessage()
                }
            }
            pendingAction = null
        }
    }

    fun favoriteAlbum() {
        if (offline) { actionError = "离线模式不执行在线操作"; return }
        if (!authenticated) {
            onRequireLogin()
            return
        }
        if (pendingAction != null || state !is AlbumDetailUiState.Content) return
        if ((state as? AlbumDetailUiState.Content)?.detail?.isFavorite != true) {
            favoriteFolderError = null
            showFavoriteFolderPicker = true
            return
        }
        pendingAction = DetailAccountAction.FAVORITE
        actionError = null
        coroutineScope.launch {
            when (val result = repository.favoriteAlbum(album.id)) {
                is JmxResult.Success -> {
                    val rejection = result.value.rejectionMessageOrNull()
                    if (rejection != null) {
                        actionError = rejection
                    } else {
                        val latest = state as? AlbumDetailUiState.Content
                        if (latest != null) {
                            val wasFavorite = latest.detail.isFavorite == true
                            val favorite = when (result.value.type?.lowercase()) {
                                "add" -> true
                                "remove" -> false
                                else -> latest.detail.isFavorite != true
                            }
                            state = latest.copy(detail = latest.detail.copy(isFavorite = favorite))
                            if (favorite != wasFavorite) onFavoriteChanged(favorite)
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        }
                    }
                }
                is JmxResult.Failure -> {
                    if (result.error.requiresLogin()) onRequireLogin()
                    else actionError = result.error.toUiMessage()
                }
            }
            pendingAction = null
        }
    }

    fun addFavorite(folderId: Int) {
        if (pendingAction != null) return
        pendingAction = DetailAccountAction.FAVORITE
        favoriteFolderError = null
        coroutineScope.launch {
            try {
                when (val result = favoriteRepository.addFavoriteToFolder(album.id, folderId)) {
                    is JmxResult.Success -> {
                        val current = state as? AlbumDetailUiState.Content
                        if (current != null) {
                            val wasFavorite = current.detail.isFavorite == true
                            state = current.copy(detail = current.detail.copy(isFavorite = true))
                            if (!wasFavorite) onFavoriteChanged(true)
                        }
                        favoriteFolderError = result.value.folderError?.toUiMessage()
                        if (favoriteFolderError == null) {
                            showFavoriteFolderPicker = false
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        }
                    }
                    is JmxResult.Failure -> {
                        favoriteFolderError = result.error.toUiMessage()
                        if (result.error.requiresLogin()) onRequireLogin()
                    }
                }
            } finally {
                pendingAction = null
            }
        }
    }

    fun startReading(chapterId: String, pageIndex: Int = 0) {
        val content = state as? AlbumDetailUiState.Content ?: return
        val source = if (offline) {
            val manifest = offlineManifest
            val chapter = manifest?.chapters?.firstOrNull { it.id == chapterId }
            if (chapter == null || chapter.expectedPageCount <= 0 || chapter.pages.size != chapter.expectedPageCount) {
                actionError = "这一章尚未完整下载，请等待下载完成或重新下载"
                return
            }
            ReaderResourceSource.Offline(manifest)
        } else {
            ReaderResourceSource.OnlineJm
        }
        onStartReading(ReaderLaunchRequest(
            album = album,
            detail = content.detail,
            initialChapterId = chapterId,
            initialPageIndex = pageIndex,
            source = source,
        ))
    }

    fun requestDownload() {
        val content = state as? AlbumDetailUiState.Content ?: return
        val chapters = content.detail.readingChapters()
        if (chapters.size == 1) {
            onDownload(album, content.detail, setOf(chapters.single().id))
        } else {
            showDownloadPicker = true
        }
    }

    fun openCommentComposer(comment: CommentItem? = null) {
        if (offline) { actionError = "离线模式不加载评论"; return }
        if (!authenticated) {
            onRequireLogin()
            return
        }
        replyCommentId = comment?.id?.takeIf(String::isNotBlank)
        replyCommentUsername = comment?.username?.takeIf(String::isNotBlank)
        showCommentComposer = true
    }

    LaunchedEffect(album.id, bookshelfRepository, readerActive) {
        val latest = bookshelfRepository.entry(album.id)
        resumeEntry = latest
        inBookshelf = latest != null
    }

    fun toggleBookshelf() {
        if (inBookshelf) {
            bookshelfRepository.remove(album.id)
            inBookshelf = false
            resumeEntry = null
            onBookshelfChanged()
            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
            return
        }
        val groups = bookshelfRepository.groups()
        if (groups.isNotEmpty()) {
            showBookshelfGroupPicker = true
        } else {
            bookshelfRepository.add(if (offline) repository.bookshelfAlbum(album) else album)
            inBookshelf = true
            resumeEntry = bookshelfRepository.entry(album.id)
            onBookshelfChanged()
            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
        }
    }

    fun submitComment(comment: String) {
        if (!authenticated) {
            showCommentComposer = false
            onRequireLogin()
            return
        }
        if (comment.isBlank() || pendingAction != null) return
        pendingAction = DetailAccountAction.COMMENT
        actionError = null
        coroutineScope.launch {
            when (val result = repository.commentAlbum(album.id, comment.trim(), replyCommentId)) {
                is JmxResult.Success -> {
                    val rejection = result.value.rejectionMessageOrNull()
                    if (rejection != null) {
                        actionError = rejection
                    } else {
                        showCommentComposer = false
                        replyCommentId = null
                        replyCommentUsername = null
                        val latest = state as? AlbumDetailUiState.Content
                        if (latest != null) {
                            state = latest.copy(
                                comments = latest.comments.copy(isLoading = true, error = null),
                            )
                            state = latest.copy(
                                comments = repository.loadInitialComments(album.id),
                            )
                        }
                    }
                }
                is JmxResult.Failure -> {
                    if (result.error.requiresLogin()) {
                        showCommentComposer = false
                        replyCommentId = null
                        replyCommentUsername = null
                        onRequireLogin()
                    } else {
                        actionError = result.error.toUiMessage()
                    }
                }
            }
            pendingAction = null
        }
    }

    val detailTitle = when (val current = state) {
        is AlbumDetailUiState.Content -> current.detail.name ?: album.name
        else -> album.name
    }

    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        floatingActionButton = {
            val content = state as? AlbumDetailUiState.Content
            val chapters = visibleChapters
            val savedChapterId = if (offline) offlineProgress.chapterId(album.id) else resumeEntry?.lastChapterId
            val resumeChapter = savedChapterId?.let { id -> chapters.firstOrNull { it.id == id } }
            val firstChapter = resumeChapter ?: chapters.firstOrNull()

            FloatingActionButton(
                onClick = {
                    if (pagerState.currentPage == DETAIL_COMMENTS_TAB) {
                        openCommentComposer()
                    } else if (content != null && firstChapter != null) {
                        startReading(
                            firstChapter.id,
                            if (firstChapter.id == savedChapterId) {
                                if (offline) offlineProgress.pageIndex(album.id) else resumeEntry?.lastPageIndex ?: 0
                            } else 0,
                        )
                    } else if (offline) {
                        actionError = "还没有完整下载的章节"
                    }
                },
                minWidth = 136.dp,
                minHeight = 52.dp,
                shape = RoundedCornerShape(26.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (pagerState.currentPage == DETAIL_COMMENTS_TAB) MiuixIcons.Send else MiuixIcons.Play,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MiuixTheme.colorScheme.onPrimary,
                    )
                    Text(
                        text = when {
                            pagerState.currentPage == DETAIL_COMMENTS_TAB -> "发表评论"
                            resumeChapter != null -> resumeChapter.displayName(chapters.indexOf(resumeChapter))
                            else -> "开始阅读"
                        },
                        style = MiuixTheme.textStyles.button,
                        color = MiuixTheme.colorScheme.onPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        floatingActionButtonPosition = FabPosition.End,
    ) { innerPadding ->
        DetailBody(
            album = album,
            state = state,
            chapters = visibleChapters,
            pagerState = pagerState,
            pageSummary = pageSummary,
            showCover = showCover,
            innerPadding = innerPadding,
            backdrop = pageBackdrop,
            topBarBlurStyle = topBarBlurStyle,
            detailTitle = detailTitle,
            onBack = onBack,
            onCoverTargetChanged = onCoverTargetChanged,
            onChapterSelected = { chapter -> startReading(chapter.id) },
            onLoadMoreComments = ::loadMoreComments,
            onReplyComment = ::openCommentComposer,
            onLike = ::likeAlbum,
            onFavorite = ::favoriteAlbum,
            inBookshelf = inBookshelf,
            onBookshelf = ::toggleBookshelf,
            onDownload = ::requestDownload,
            downloadEnabled = state is AlbumDetailUiState.Content,
            showDownload = !offline,
            onSearchRequested = onSearchRequested,
            lastReadChapterId = resumeEntry?.lastChapterId,
            pendingAction = pendingAction,
            onRetry = {
                state = AlbumDetailUiState.Loading
                retryKey++
            },
        )
    }

    FavoriteFolderPicker(
        show = showFavoriteFolderPicker,
        repository = favoriteRepository,
        sessionRevision = sessionRevision,
        onDismiss = { if (pendingAction == null) showFavoriteFolderPicker = false },
        onConfirm = { folder -> folder.id.toIntOrNull()?.let(::addFavorite) },
        onDefault = { addFavorite(0) },
        busy = pendingAction == DetailAccountAction.FAVORITE,
        error = favoriteFolderError,
    )
    DownloadChapterPicker(
        show = showDownloadPicker,
        albumTitle = album.name,
        chapters = detail?.readingChapters().orEmpty().mapIndexed { index, chapter ->
            DownloadChapterUiModel(
                id = chapter.id,
                title = chapter.displayName(index),
                summary = if (offlineManifest?.chapters?.any {
                        it.id == chapter.id && it.expectedPageCount > 0 && it.pages.size == it.expectedPageCount
                    } == true) "已下载" else null,
            )
        },
        onDismiss = { showDownloadPicker = false },
        onDownload = { ids ->
            detail?.let { onDownload(album, it, ids) }
            showDownloadPicker = false
        },
    )
    CommentComposerDialog(
        show = showCommentComposer,
        submitting = pendingAction == DetailAccountAction.COMMENT,
        replyingTo = replyCommentUsername,
        onDismiss = { if (pendingAction == null) showCommentComposer = false },
        onSubmit = ::submitComment,
    )
    DetailActionErrorDialog(
        message = actionError,
        onDismiss = { actionError = null },
    )
    BookshelfGroupPickerDialog(
        show = showBookshelfGroupPicker,
        groups = bookshelfRepository.groups(),
        onDismiss = { showBookshelfGroupPicker = false },
        onConfirm = { selectedGroupIds ->
            bookshelfRepository.add(if (offline) repository.bookshelfAlbum(album) else album, selectedGroupIds)
            inBookshelf = true
            resumeEntry = bookshelfRepository.entry(album.id)
            showBookshelfGroupPicker = false
            onBookshelfChanged()
            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
        },
    )
}

@Composable
private fun DetailBody(
    album: HomeAlbum,
    state: AlbumDetailUiState,
    chapters: List<AlbumChapter>,
    pagerState: PagerState,
    pageSummary: AlbumPageSummary,
    showCover: Boolean,
    innerPadding: PaddingValues,
    backdrop: LayerBackdrop? = null,
    topBarBlurStyle: TopBarBlurStyle = TopBarBlurStyle.GAUSSIAN,
    detailTitle: String = album.name,
    onBack: () -> Unit,
    onCoverTargetChanged: (Rect) -> Unit,
    onChapterSelected: (AlbumChapter) -> Unit,
    onLoadMoreComments: () -> Unit,
    onReplyComment: (CommentItem) -> Unit,
    onLike: () -> Unit,
    onFavorite: () -> Unit,
    inBookshelf: Boolean,
    onBookshelf: () -> Unit,
    onDownload: () -> Unit,
    downloadEnabled: Boolean,
    showDownload: Boolean,
    onSearchRequested: (String) -> Unit,
    lastReadChapterId: String? = null,
    pendingAction: DetailAccountAction?,
    onRetry: () -> Unit,
) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val view = LocalView.current
    val systemStatusBarHeight = remember(view, density) {
        val topPx = if (Build.VERSION.SDK_INT >= 30) {
            view.rootWindowInsets?.getInsets(android.view.WindowInsets.Type.statusBars())?.top ?: 0
        } else {
            @Suppress("DEPRECATION")
            view.rootWindowInsets?.systemWindowInsetTop ?: 0
        }
        with(density) { topPx.toDp() }
    }
    val statusBarPadding = maxOf(
        innerPadding.calculateTopPadding(),
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
        systemStatusBarHeight,
    )
    val topBarHeightDp = statusBarPadding + 56.dp
    val topBarHeightPx = with(density) { topBarHeightDp.toPx() }
    val coverSpacingTopDp = 20.dp
    val coverSpacingTopPx = with(density) { coverSpacingTopDp.toPx() }
    val coverTopPx = topBarHeightPx + coverSpacingTopPx
    val coverTop = topBarHeightDp + coverSpacingTopDp
    val targetBounds = with(density) {
        Rect(
            left = DETAIL_HORIZONTAL_PADDING.toPx(),
            top = coverTop.toPx(),
            right = (DETAIL_HORIZONTAL_PADDING + DETAIL_COVER_WIDTH).toPx(),
            bottom = (coverTop + DETAIL_COVER_HEIGHT).toPx(),
        )
    }
    LaunchedEffect(targetBounds) { onCoverTargetChanged(targetBounds) }

    val detail = (state as? AlbumDetailUiState.Content)?.detail
    val comments = (state as? AlbumDetailUiState.Content)?.comments
    var catalogReversed by rememberSaveable { mutableStateOf(false) }

    val infoListState = rememberLazyListState()
    val catalogListState = rememberLazyListState()
    val commentsListState = rememberLazyListState()

    val surfaceColor = MiuixTheme.colorScheme.surface
    val context = LocalContext.current
    val ambientCoverRequest = remember(album.coverUrl) {
        buildCoverRequest(context, album.coverUrl)
    }

    val childPagerNestedScrollConnection = rememberPagerFlingPassThroughConnection(
        state = pagerState,
    )

    LaunchedEffect(commentsListState, pagerState.currentPage, comments?.nextPage, comments?.isLoading, comments?.endReached) {
        if (pagerState.currentPage != DETAIL_COMMENTS_TAB || comments == null || comments.isLoading || comments.endReached) {
            return@LaunchedEffect
        }
        snapshotFlow {
            val layout = commentsListState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            layout.totalItemsCount > 0 && lastVisible >= layout.totalItemsCount - 3
        }.filter { it }.first()
        onLoadMoreComments()
    }

    var summaryHeightPx by remember { mutableFloatStateOf(0f) }
    var tabBarHeightPx by remember { mutableFloatStateOf(0f) }
    var headerOffset by remember { mutableFloatStateOf(0f) }

    val maxScrollPx = summaryHeightPx + coverSpacingTopPx

    LaunchedEffect(maxScrollPx) {
        if (maxScrollPx > 0f && headerOffset < -maxScrollPx) {
            headerOffset = -maxScrollPx
        }
    }

    val nestedScrollConnection = remember(maxScrollPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // 手指向上划（页面内容向下滚动），优先收起并折叠封面区
                if (available.y < 0f && maxScrollPx > 0f && headerOffset > -maxScrollPx) {
                    val oldOffset = headerOffset
                    val newOffset = (oldOffset + available.y).coerceIn(-maxScrollPx, 0f)
                    headerOffset = newOffset
                    val consumedY = newOffset - oldOffset
                    return Offset(0f, consumedY)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                // 手指向下划（页面内容向上滚动），子列表触顶后拉回封面区
                if (available.y > 0f && maxScrollPx > 0f && headerOffset < 0f) {
                    val oldOffset = headerOffset
                    val newOffset = (oldOffset + available.y).coerceIn(-maxScrollPx, 0f)
                    headerOffset = newOffset
                    val consumedY = newOffset - oldOffset
                    return Offset(0f, consumedY)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                // 不拦截向上的惯性飞动，允许子列表（LazyColumn）接收完整的轻扫初速度。
                // 子列表惯性滑动过程中的每一帧，由 onPreScroll 优先消耗位移以收起并折叠封面区；
                // 封面完全折叠吸顶后，onPreScroll 自动停止消耗，后续帧的速度直接无缝驱动子列表继续快速滚动，中途绝不停滞。
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                // 向下快速轻扫时，列表惯性滑动到顶部（第 0 项）触顶后，将剩余惯性接力传递给封面区展开
                if (available.y > 0f && maxScrollPx > 0f && headerOffset < 0f) {
                    val animatable = Animatable(headerOffset)
                    try {
                        animatable.animateDecay(
                            initialVelocity = available.y,
                            animationSpec = exponentialDecay(),
                        ) {
                            if (value >= 0f) {
                                headerOffset = 0f
                                throw CancellationException()
                            } else {
                                headerOffset = value
                            }
                        }
                    } catch (_: CancellationException) {
                        headerOffset = 0f
                    }
                    // 衰减完成后若已接近顶部（展开超过 40%），平滑吸附完全展开到顶
                    if (headerOffset > -maxScrollPx * 0.6f && headerOffset < 0f) {
                        animatable.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) {
                            headerOffset = value
                        }
                    }
                    return Velocity(0f, available.y - animatable.velocity)
                }
                return Velocity.Zero
            }
        }
    }

    val headerDraggableState = rememberDraggableState { delta ->
        if (maxScrollPx > 0f) {
            headerOffset = (headerOffset + delta).coerceIn(-maxScrollPx, 0f)
        }
    }

    val currentActiveListState = when (pagerState.currentPage) {
        DETAIL_INFO_TAB -> infoListState
        DETAIL_COMMENTS_TAB -> commentsListState
        else -> catalogListState
    }

    val headerDraggable = Modifier.draggable(
        state = headerDraggableState,
        orientation = Orientation.Vertical,
        onDragStopped = { velocity ->
            if (maxScrollPx > 0f && velocity != 0f) {
                coroutineScope.launch {
                    if (velocity < 0f) {
                        // 向上快速轻扫封面：折叠封面，若有剩余速度继续驱动子列表向下滚动
                        var remainingVelocity = velocity
                        val animatable = Animatable(headerOffset)
                        try {
                            animatable.animateDecay(
                                initialVelocity = velocity,
                                animationSpec = exponentialDecay(),
                            ) {
                                if (value <= -maxScrollPx) {
                                    headerOffset = -maxScrollPx
                                    remainingVelocity = this.velocity
                                    throw CancellationException()
                                } else {
                                    headerOffset = value
                                }
                            }
                        } catch (_: CancellationException) {
                            headerOffset = -maxScrollPx
                        }
                        if (headerOffset > -maxScrollPx) {
                            headerOffset = -maxScrollPx
                        }
                        if (remainingVelocity < -200f) {
                            currentActiveListState.animateScrollBy(-remainingVelocity * 0.25f)
                        }
                    } else {
                        // 向下快速轻扫：展开封面
                        val animatable = Animatable(headerOffset)
                        try {
                            animatable.animateDecay(
                                initialVelocity = velocity,
                                animationSpec = exponentialDecay(),
                            ) {
                                if (value >= 0f) {
                                    headerOffset = 0f
                                    throw CancellationException()
                                } else {
                                    headerOffset = value
                                }
                            }
                        } catch (_: CancellationException) {
                            headerOffset = 0f
                        }
                        if (headerOffset > -maxScrollPx * 0.6f && headerOffset < 0f) {
                            animatable.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) {
                                headerOffset = value
                            }
                        }
                    }
                }
            }
        },
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(surfaceColor),
    ) {
        // 背景与滚动内容层（挂载 layerBackdrop 供浮动顶栏采样，独立于顶栏之外避免自循环递归）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier),
        ) {
            // 上半背景若隐若现的模糊封面与渐隐过渡（随滚动同步上滑并淡出）
            val ambientAlpha = if (maxScrollPx > 0f) {
                (1f + headerOffset / maxScrollPx).coerceIn(0f, 1f)
            } else 1f

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp)
                    .clipToBounds()
                    .graphicsLayer {
                        alpha = ambientAlpha
                        translationY = headerOffset * 0.4f
                    },
            ) {
                AsyncImage(
                    model = ambientCoverRequest,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(1.2f)
                        .blur(40.dp)
                        .graphicsLayer { alpha = 0.28f },
                    contentScale = ContentScale.Crop,
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.0f to Color.Transparent,
                                0.45f to surfaceColor.copy(alpha = 0.2f),
                                0.70f to surfaceColor.copy(alpha = 0.6f),
                                0.92f to surfaceColor.copy(alpha = 0.92f),
                                1.0f to surfaceColor,
                            ),
                        ),
                )
            }

            // 自定义布局：封面区（向上折叠划出屏幕）、Tab 栏（吸顶在顶栏下方）、HorizontalPager（占满剩余高度）
            Layout(
                content = {
                    // Measurable 0: 封面与操作按钮区（可向上折叠划出屏幕）
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = DETAIL_HORIZONTAL_PADDING)
                            .onSizeChanged { size ->
                                summaryHeightPx = size.height.toFloat()
                            }
                            .then(headerDraggable),
                    ) {
                        DetailSummary(
                            album = album,
                            detail = detail,
                            showCover = showCover,
                            onAuthorSelected = onSearchRequested,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        DetailActions(
                            detail = detail,
                            onLike = onLike,
                            onFavorite = onFavorite,
                            inBookshelf = inBookshelf,
                            onBookshelf = onBookshelf,
                            onDownload = onDownload,
                            downloadEnabled = downloadEnabled,
                            showDownload = showDownload,
                            pendingAction = pendingAction,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Measurable 1: Tab 栏（吸顶在顶栏下方）
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(1f)
                            .onSizeChanged { size ->
                                tabBarHeightPx = size.height.toFloat()
                            },
                    ) {
                        TabRowWithContour(
                            tabs = listOf(
                                "介绍",
                                "目录 ${chapters.size}",
                                "评论 ${detail?.commentTotal ?: comments?.total ?: 0}",
                            ),
                            selectedTabIndex = pagerState.currentPage,
                            onTabSelected = { index ->
                                coroutineScope.launch {
                                    pagerState.springAnimateToPage(index)
                                }
                            },
                            colors = TabRowDefaults.tabRowColors(
                                backgroundColor = Color.Transparent,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = DETAIL_HORIZONTAL_PADDING),
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Measurable 2: HorizontalPager 分页器
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxSize()
                            .clipToBounds(),
                        pageNestedScrollConnection = childPagerNestedScrollConnection,
                        key = { page -> page },
                    ) { page ->
                        when {
                            state is AlbumDetailUiState.Loading -> DetailLoading()
                            state is AlbumDetailUiState.Error -> DetailError(message = state.message, onRetry = onRetry)
                            page == DETAIL_INFO_TAB && detail != null -> {
                                LazyColumn(
                                    state = infoListState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(
                                        start = DETAIL_HORIZONTAL_PADDING,
                                        end = DETAIL_HORIZONTAL_PADDING,
                                        bottom = 108.dp,
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    detailInfoItems(
                                        detail = detail,
                                        pageSummary = pageSummary,
                                        onSearchRequested = onSearchRequested,
                                    )
                                }
                            }
                            page == DETAIL_CATALOG_TAB && detail != null -> {
                                LazyColumn(
                                    state = catalogListState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(
                                        start = DETAIL_HORIZONTAL_PADDING,
                                        end = DETAIL_HORIZONTAL_PADDING,
                                        bottom = 108.dp,
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    catalogItems(
                                        chapters = chapters,
                                        reversed = catalogReversed,
                                        lastReadChapterId = lastReadChapterId,
                                        onToggleReverse = { catalogReversed = !catalogReversed },
                                        onChapterSelected = onChapterSelected,
                                    )
                                }
                            }
                            page == DETAIL_COMMENTS_TAB -> {
                                LazyColumn(
                                    state = commentsListState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(
                                        start = DETAIL_HORIZONTAL_PADDING,
                                        end = DETAIL_HORIZONTAL_PADDING,
                                        bottom = 108.dp,
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    commentsItems(
                                        comments = comments,
                                        onRetry = onLoadMoreComments,
                                        onReplyComment = onReplyComment,
                                    )
                                }
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(nestedScrollConnection),
            ) { measurables, constraints ->
                val topBarHeight = topBarHeightPx.roundToInt()
                val coverTop = coverTopPx.roundToInt()

                val summaryPlaceable = measurables[0].measure(
                    constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
                )
                val sHeight = summaryPlaceable.height

                val tabBarPlaceable = measurables[1].measure(
                    constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
                )
                val tHeight = tabBarPlaceable.height

                val pagerHeight = (constraints.maxHeight - topBarHeight - tHeight).coerceAtLeast(0)
                val pagerPlaceable = measurables[2].measure(
                    constraints.copy(minHeight = pagerHeight, maxHeight = pagerHeight)
                )

                layout(constraints.maxWidth, constraints.maxHeight) {
                    val currentOffset = headerOffset.roundToInt()
                    val tabY = (coverTop + sHeight + currentOffset).coerceAtLeast(topBarHeight)
                    val summaryY = tabY - sHeight
                    val pagerY = tabY + tHeight

                    summaryPlaceable.placeRelative(0, summaryY)
                    tabBarPlaceable.placeRelative(0, tabY)
                    pagerPlaceable.placeRelative(0, pagerY)
                }
            }
        }

        // 沉浸式悬浮顶栏（位于最顶层，折叠时平滑浮现毛玻璃底色与小标题）
        val scrollProgress = if (maxScrollPx > 0f) (-headerOffset / maxScrollPx).coerceIn(0f, 1f) else 0f
        if (scrollProgress > 0.005f) {
            BlurredBar(
                backdrop = backdrop,
                style = topBarBlurStyle,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(topBarHeightDp)
                    .zIndex(10f)
                    .graphicsLayer { alpha = scrollProgress },
            ) {}
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = statusBarPadding)
                .height(64.dp)
                .padding(horizontal = DETAIL_HORIZONTAL_PADDING)
                .zIndex(11f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LiquidGlassIconButton(
                onClick = onBack,
                backdrop = backdrop,
                glassAlpha = animateTopBarGlassProgress(scrollProgress).value,
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
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = detailTitle,
                style = MiuixTheme.textStyles.title3,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer { alpha = scrollProgress },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailSummary(
    album: HomeAlbum,
    detail: AlbumDetail?,
    showCover: Boolean,
    onAuthorSelected: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val context = LocalContext.current
        val detailCoverRequest = remember(album.coverUrl) {
            buildCoverRequest(context, album.coverUrl)
        }
        Box(
            modifier = Modifier
                .width(DETAIL_COVER_WIDTH)
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(8.dp))
                .background(MiuixTheme.colorScheme.surfaceContainerHigh),
        ) {
            AsyncImage(
                model = detailCoverRequest,
                contentDescription = album.name,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = if (showCover) 1f else 0f
                    },
                contentScale = ContentScale.Crop,
            )
        }
        var titleExpanded by rememberSaveable(album.id) { mutableStateOf(false) }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // 漫画标题（主展示位，默认最多2行，点击可展开）
            Text(
                text = album.name,
                modifier = Modifier.clickable { titleExpanded = !titleExpanded },
                style = MiuixTheme.textStyles.title2,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = if (titleExpanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
            )

            // 作者（简洁文本行展示，支持点击搜索）
            val authors = (detail?.authors?.takeIf { it.isNotEmpty() } ?: listOf(album.author))
                .distinct()
                .filter(String::isNotBlank)
            if (authors.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    authors.forEach { author ->
                        Text(
                            text = author,
                            modifier = Modifier.clickable { onAuthorSelected(author) },
                            style = MiuixTheme.textStyles.footnote1,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // 规格与数据胶囊徽章行（车号、观看次数）
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DetailMiniBadge(text = "JM${album.id}")
                if ((detail?.totalViews ?: 0) > 0) {
                    DetailMiniBadge(text = "${compactCount(detail?.totalViews)} 次观看")
                }
            }
        }
    }
}

@Composable
private fun DetailMiniBadge(text: String) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MiuixTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
        )
    }
}

@Composable
private fun DetailActions(
    detail: AlbumDetail?,
    onLike: () -> Unit,
    onFavorite: () -> Unit,
    inBookshelf: Boolean,
    onBookshelf: () -> Unit,
    onDownload: () -> Unit,
    downloadEnabled: Boolean,
    showDownload: Boolean,
    pendingAction: DetailAccountAction?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 14.dp,
        insideMargin = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DetailActionItem(
                modifier = Modifier.weight(1f),
                icon = if (detail?.liked == true) MiuixIcons.FavoritesFill else MiuixIcons.Favorites,
                text = detail?.likes?.takeIf { it > 0 }?.let(::compactCount)
                    ?: if (detail?.liked == true) "已喜欢" else "喜欢",
                contentDescription = if (detail?.liked == true) "已喜欢" else "喜欢",
                enabled = detail != null && detail.liked != true && pendingAction == null,
                selected = detail?.liked == true,
                tintColor = if (detail?.liked == true) Color(0xFFE53935) else null,
                loading = pendingAction == DetailAccountAction.LIKE,
                onClick = onLike,
            )
            DetailActionDivider()
            DetailActionItem(
                modifier = Modifier.weight(1f),
                icon = MiuixIcons.Store,
                text = if (detail?.isFavorite == true) "已收藏" else "收藏",
                contentDescription = if (detail?.isFavorite == true) "已收藏" else "收藏",
                enabled = detail != null && pendingAction == null,
                selected = detail?.isFavorite == true,
                tintColor = if (detail?.isFavorite == true) Color(0xFFFFA000) else null,
                loading = pendingAction == DetailAccountAction.FAVORITE,
                onClick = onFavorite,
            )
            DetailActionDivider()
            DetailActionItem(
                modifier = Modifier.weight(1f),
                icon = MiuixIcons.Notes,
                text = if (inBookshelf) "已在书架" else "加入书架",
                contentDescription = if (inBookshelf) "已在书架" else "加入书架",
                enabled = detail != null,
                selected = inBookshelf,
                selectedPrimary = true,
                onClick = onBookshelf,
            )
            if (showDownload) {
                DetailActionDivider()
                DetailActionItem(
                    modifier = Modifier.weight(1f),
                    icon = MiuixIcons.Download,
                    text = "下载",
                    contentDescription = "下载",
                    enabled = downloadEnabled,
                    onClick = onDownload,
                )
            }
        }
    }
}

@Composable
private fun DetailActionDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(20.dp)
            .background(MiuixTheme.colorScheme.outline.copy(alpha = 0.2f)),
    )
}

@Composable
private fun DetailActionItem(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    text: String,
    contentDescription: String,
    enabled: Boolean,
    selected: Boolean = false,
    selectedPrimary: Boolean = false,
    tintColor: Color? = null,
    loading: Boolean = false,
    onClick: () -> Unit = {},
) {
    val targetContentColor = when {
        tintColor != null -> tintColor
        selected && selectedPrimary -> MiuixTheme.colorScheme.primary
        selected -> MiuixTheme.colorScheme.primary
        enabled -> MiuixTheme.colorScheme.onSurface
        else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
    }
    val contentColor by animateColorAsState(targetContentColor, label = "DetailActionColor")

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled && !loading, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (loading) {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(size = 20.dp, strokeWidth = 2.5.dp)
            }
        } else {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(22.dp),
                tint = contentColor,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote1,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CommentComposerDialog(
    show: Boolean,
    submitting: Boolean,
    replyingTo: String?,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var comment by remember(show) { mutableStateOf("") }
    WindowDialog(
        show = show,
        title = if (replyingTo == null) "发表评论" else "回复评论",
        onDismissRequest = { if (!submitting) onDismiss() },
    ) {
        replyingTo?.let { username ->
            Text(
                text = "回复 @$username",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        TextField(
            value = comment,
            onValueChange = { comment = it },
            label = "评论内容",
            minLines = 3,
            maxLines = 6,
            enabled = !submitting,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(
                text = "取消",
                enabled = !submitting,
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = if (submitting) "发表中" else "发表",
                enabled = comment.isNotBlank() && !submitting,
                onClick = { onSubmit(comment) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}

@Composable
private fun DetailActionErrorDialog(
    message: String?,
    onDismiss: () -> Unit,
) {
    WindowDialog(
        show = message != null,
        title = "操作失败",
        summary = message,
        onDismissRequest = onDismiss,
    ) {
        TextButton(
            text = "知道了",
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.textButtonColorsPrimary(),
        )
    }
}

@Composable
private fun BookshelfGroupPickerDialog(
    show: Boolean,
    groups: List<BookshelfGroup>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    var selectedGroupIds by remember(show, groups) {
        mutableStateOf(emptySet<String>())
    }
    WindowDialog(
        show = show,
        title = "选择书架分组",
        summary = "不选择分组时只加入“全部”，也可以同时加入多个自定义分组。",
        onDismissRequest = onDismiss,
    ) {
        // 高度封顶 + 内部滚动：分组数量没有上限，直接 forEach 会把下面的按钮挤出屏幕（issue #10 同类问题）。
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 260.dp),
        ) {
            items(items = groups, key = BookshelfGroup::id) { group ->
                val selected = group.id in selectedGroupIds
                val itemColor by animateColorAsState(
                    targetValue = if (selected) {
                        MiuixTheme.colorScheme.primaryContainer
                    } else {
                        MiuixTheme.colorScheme.surfaceContainerHigh
                    },
                    animationSpec = tween(180),
                    label = "DetailBookshelfGroupColor",
                )
                Surface(
                    onClick = {
                        selectedGroupIds = if (selected) {
                            selectedGroupIds - group.id
                        } else {
                            selectedGroupIds + group.id
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = itemColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = group.name,
                            style = MiuixTheme.textStyles.body2,
                            color = if (selected) {
                                MiuixTheme.colorScheme.onPrimaryContainer
                            } else {
                                MiuixTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.weight(1f),
                        )
                        DetailGroupSelectionIndicator(selected = selected)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(
                text = "取消",
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = if (selectedGroupIds.isEmpty()) "仅加入全部" else "加入书架",
                onClick = { onConfirm(selectedGroupIds) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}

@Composable
private fun DetailGroupSelectionIndicator(selected: Boolean) {
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) {
            MiuixTheme.colorScheme.primary
        } else {
            MiuixTheme.colorScheme.surface.copy(alpha = 0.9f)
        },
        animationSpec = tween(180),
        label = "DetailGroupSelectionIndicator",
    )
    Surface(
        modifier = Modifier.size(28.dp),
        shape = CircleShape,
        color = backgroundColor,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) {
                MiuixTheme.colorScheme.primary
            } else {
                MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.5f)
            },
        ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (selected) {
                Icon(
                    imageVector = MiuixIcons.Basic.Check,
                    contentDescription = "已选择",
                    modifier = Modifier.size(17.dp),
                    tint = MiuixTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.detailInfoItems(
    detail: AlbumDetail,
    pageSummary: AlbumPageSummary,
    onSearchRequested: (String) -> Unit,
) {
    item(key = "description") {
        val rawDescription = detail.description
            ?.decodeHtml()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "暂无介绍"

        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 14.dp,
            insideMargin = PaddingValues(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "漫画介绍",
                    style = MiuixTheme.textStyles.title3,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Text(
                    text = rawDescription,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurface,
                    lineHeight = 22.sp,
                )
            }
        }
    }

    if (detail.tags.isNotEmpty() || detail.works.isNotEmpty() || detail.actors.isNotEmpty()) {
        item(key = "tags-card") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 14.dp,
                insideMargin = PaddingValues(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (detail.tags.isNotEmpty()) {
                        DetailChipSection(
                            title = "分类与标签",
                            values = detail.tags,
                            onValueClick = onSearchRequested,
                        )
                    }
                    if (detail.works.isNotEmpty()) {
                        DetailChipSection(
                            title = "系列作品",
                            values = detail.works,
                            onValueClick = onSearchRequested,
                        )
                    }
                    if (detail.actors.isNotEmpty()) {
                        DetailChipSection(
                            title = "出场人物",
                            values = detail.actors,
                            onValueClick = onSearchRequested,
                        )
                    }
                }
            }
        }
    }

    item(key = "specs-card") {
        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 14.dp,
            insideMargin = PaddingValues(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "基本信息",
                    style = MiuixTheme.textStyles.title3,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                DetailSpecRow(label = "作品编号", value = "JM${detail.id}")
                DetailSpecRow(label = "章节规格", value = pageSummary.sectionText(detail))
                if ((detail.likes ?: 0) > 0) {
                    DetailSpecRow(label = "累计好评", value = "${detail.likes} 人喜欢")
                }
                if ((detail.totalViews ?: 0) > 0) {
                    DetailSpecRow(label = "累计阅读", value = "${compactCount(detail.totalViews)} 次观看")
                }
            }
        }
    }
}

@Composable
private fun DetailSpecRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.body2,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurface,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailChipSection(
    title: String,
    values: List<String>,
    onValueClick: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            values.distinct().filter(String::isNotBlank).forEach { value ->
                Surface(
                    onClick = { onValueClick(value) },
                    shape = RoundedCornerShape(8.dp),
                    color = MiuixTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.8f),
                ) {
                    Text(
                        text = "# $value",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MiuixTheme.textStyles.footnote1,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.catalogItems(
    chapters: List<AlbumChapter>,
    reversed: Boolean,
    lastReadChapterId: String?,
    onToggleReverse: () -> Unit,
    onChapterSelected: (AlbumChapter) -> Unit,
) {
    if (chapters.isEmpty()) {
        item(key = "catalog-empty") {
            Text(
                text = "暂无完整下载的章节，可返回离线下载页查看进度。",
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    item(key = "catalog-toolbar") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "共 ${chapters.size} 话",
                style = MiuixTheme.textStyles.body2,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Surface(
                onClick = onToggleReverse,
                shape = RoundedCornerShape(6.dp),
                color = MiuixTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Sort,
                        contentDescription = if (reversed) "倒序" else "正序",
                        modifier = Modifier.size(16.dp),
                        tint = MiuixTheme.colorScheme.primary,
                    )
                    Text(
                        text = if (reversed) "倒序" else "正序",
                        style = MiuixTheme.textStyles.footnote2,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.primary,
                    )
                }
            }
        }
    }

    val displayChapters = if (reversed) chapters.asReversed() else chapters
    itemsIndexed(
        items = displayChapters,
        key = { _, chapter -> chapter.id },
    ) { displayIndex, chapter ->
        val originalIndex = if (reversed) chapters.size - 1 - displayIndex else displayIndex
        val isLastRead = lastReadChapterId == chapter.id

        Surface(
            onClick = { onChapterSelected(chapter) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = if (isLastRead) {
                MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                MiuixTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f)
            },
            border = if (isLastRead) {
                BorderStroke(1.dp, MiuixTheme.colorScheme.primary.copy(alpha = 0.6f))
            } else null,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = chapter.displayName(originalIndex),
                            style = MiuixTheme.textStyles.body2,
                            fontWeight = if (isLastRead) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (isLastRead) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (isLastRead) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MiuixTheme.colorScheme.primary,
                            ) {
                                Text(
                                    text = "上次读到",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MiuixTheme.textStyles.footnote2,
                                    fontWeight = FontWeight.Bold,
                                    color = MiuixTheme.colorScheme.onPrimary,
                                )
                            }
                        }
                    }
                    Text(
                        text = "JM${chapter.id}",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                Icon(
                    imageVector = MiuixIcons.Play,
                    contentDescription = "观看${chapter.displayName(originalIndex)}",
                    modifier = Modifier.size(20.dp),
                    tint = if (isLastRead) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.commentsItems(
    comments: DetailCommentsState?,
    onRetry: () -> Unit,
    onReplyComment: (CommentItem) -> Unit,
) {
    when {
        comments?.error != null && comments.items.isEmpty() -> item(key = "comments-error") {
            DetailError(message = comments.error, onRetry = onRetry)
        }
        comments?.isLoading == true && comments.items.isEmpty() -> item(key = "comments-loading") {
            DetailLoading()
        }
        comments == null || comments.items.isEmpty() -> item(key = "comments-empty") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "暂无评论",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        else -> itemsIndexed(
            items = comments.items,
            key = { index, comment -> "${comment.id.orEmpty()}:$index" },
        ) { _, comment ->
            CommentCard(comment = comment, onReply = onReplyComment)
        }
    }
    if (comments != null && comments.items.isNotEmpty()) {
        item(key = "comments-footer") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    comments.isLoading -> CircularProgressIndicator(size = 24.dp, strokeWidth = 3.dp)
                    comments.error != null -> TextButton(text = "加载失败，重试", onClick = onRetry)
                    comments.endReached -> Text(
                        text = "已显示全部 ${comments.items.size} 条评论",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
    }
}

@Composable
private fun CommentCard(
    comment: CommentItem,
    onReply: (CommentItem) -> Unit,
) {
    var repliesExpanded by rememberSaveable(comment.id, comment.replies.size) { mutableStateOf(false) }
    val replyArrowRotation by animateFloatAsState(
        targetValue = if (repliesExpanded) 180f else 0f,
        label = "CommentReplyArrow",
    )
    val username = comment.username?.takeIf { it.isNotBlank() } ?: "匿名用户"
    val initial = username.firstOrNull()?.uppercase() ?: "匿"

    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 14.dp,
        insideMargin = PaddingValues(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = MiuixTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = initial,
                        style = MiuixTheme.textStyles.footnote1,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = username,
                        modifier = Modifier.clickable { onReply(comment) },
                        style = MiuixTheme.textStyles.body2,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    if ((comment.likes ?: 0) > 0) {
                        Text(
                            text = "${comment.likes} 赞",
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
                Text(
                    text = comment.content?.decodeHtml()?.trim().orEmpty().ifBlank { "评论内容为空" },
                    modifier = Modifier.clickable { onReply(comment) },
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurface,
                    lineHeight = 20.sp,
                )
                if (comment.replies.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .clickable { repliesExpanded = !repliesExpanded }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${comment.replies.size} 条回复",
                            style = MiuixTheme.textStyles.footnote1,
                            fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.primary,
                        )
                        Icon(
                            imageVector = MiuixIcons.ExpandMore,
                            contentDescription = if (repliesExpanded) "收起回复" else "展开回复",
                            modifier = Modifier
                                .size(18.dp)
                                .graphicsLayer { rotationZ = replyArrowRotation },
                            tint = MiuixTheme.colorScheme.primary,
                        )
                    }
                    AnimatedVisibility(
                        visible = repliesExpanded,
                        enter = expandVertically(animationSpec = tween(260)) + fadeIn(tween(180)),
                        exit = shrinkVertically(animationSpec = tween(210)) + fadeOut(tween(120)),
                    ) {
                        CommentReplies(replies = comment.replies, onReply = onReply)
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentReplies(
    replies: List<CommentItem>,
    onReply: (CommentItem) -> Unit,
) {
    val railColor = MiuixTheme.colorScheme.primaryContainer
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, start = 6.dp)
            .drawBehind {
                drawLine(
                    color = railColor,
                    start = Offset(0f, 0f),
                    end = Offset(0f, size.height),
                    strokeWidth = 2.dp.toPx(),
                )
            }
            .padding(start = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        replies.forEach { reply ->
            Column(
                modifier = Modifier.clickable { onReply(reply) },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = reply.username?.takeIf(String::isNotBlank) ?: "匿名用户",
                        style = MiuixTheme.textStyles.footnote1,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.primary,
                    )
                    if ((reply.likes ?: 0) > 0) {
                        Text(
                            text = "${reply.likes} 赞",
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
                Text(
                    text = reply.content?.decodeHtml()?.trim().orEmpty().ifBlank { "回复内容为空" },
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                reply.createdAt?.trim()?.takeIf(String::isNotBlank)?.let { createdAt ->
                    Text(
                        text = createdAt,
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailLoading() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun DetailError(message: String, onRetry: (() -> Unit)?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(text = "重试", onClick = onRetry)
        }
    }
}

internal class AlbumDetailRepository(
    private val core: JmxCore,
    private val accountRepository: AccountRepository,
) {
    private val chapterPageCounts = ConcurrentHashMap<String, Int>()

    fun bookshelfAlbum(album: HomeAlbum): HomeAlbum {
        val host = core.imageHostRegistry.current()
        return album.copy(
            imageHost = host,
            coverUrl = io.kazusa.jmcomic.core.image.ImageUrl.albumCover(host, album.id),
        )
    }

    suspend fun likeAlbum(albumId: String): JmxResult<ActionResult> =
        accountRepository.withSessionRecovery { core.interactionApi.likeAlbum(albumId) }

    suspend fun favoriteAlbum(albumId: String): JmxResult<ActionResult> =
        core.interactionApi.favoriteAlbum(albumId)

    suspend fun commentAlbum(
        albumId: String,
        comment: String,
        commentId: String? = null,
    ): JmxResult<ActionResult> =
        accountRepository.withSessionRecovery {
            core.interactionApi.commentAlbum(
                albumId = albumId,
                content = comment,
                status = "1",
                commentId = commentId,
            )
        }

    /** 详情映射（章节表、标签、`toRawMap`）也不轻，整段留在 IO 线程上，别在主线程上组装。 */
    suspend fun load(albumId: String): AlbumDetailUiState = withContext(Dispatchers.IO) {
        try {
            val detailResult = withTimeoutOrNull(DETAIL_REQUEST_TIMEOUT_MILLIS) {
                core.albumApi.detailFull(albumId)
            } ?: return@withContext AlbumDetailUiState.Error("详情请求超时，请检查网络或切换线路后重试。")
            when (detailResult) {
                is JmxResult.Success -> AlbumDetailUiState.Content(
                    detail = detailResult.value,
                    comments = DetailCommentsState(isLoading = true),
                )
                is JmxResult.Failure -> AlbumDetailUiState.Error(detailResult.error.toUiMessage())
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AlbumDetailUiState.Error(error.message ?: "详情加载出现未知异常。")
        }
    }

    suspend fun loadInitialComments(albumId: String): DetailCommentsState {
        return try {
            val result = withTimeoutOrNull(COMMENT_REQUEST_TIMEOUT_MILLIS) {
                core.interactionApi.albumComments(albumId, page = 1)
            } ?: return DetailCommentsState(error = "评论请求超时，可稍后重试。")
            when (result) {
                is JmxResult.Success -> result.value.toDetailCommentsState()
                is JmxResult.Failure -> DetailCommentsState(error = result.error.toUiMessage())
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            DetailCommentsState(error = error.message ?: "评论加载出现未知异常。")
        }
    }

    suspend fun loadMoreComments(
        albumId: String,
        current: DetailCommentsState,
    ): DetailCommentsState {
        if (current.endReached) return current.copy(isLoading = false)
        return try {
            when (val result = core.interactionApi.albumComments(albumId, page = current.nextPage)) {
                is JmxResult.Success -> {
                    val merged = mergeCommentPages(current.items, result.value.comments)
                    val total = result.value.total ?: current.total
                    current.copy(
                        items = merged,
                        total = total,
                        nextPage = current.nextPage + 1,
                        isLoading = false,
                        endReached = result.value.comments.isEmpty() ||
                            (total != null && merged.size >= total),
                        error = null,
                    )
                }
                is JmxResult.Failure -> current.copy(
                    isLoading = false,
                    error = result.error.toUiMessage(),
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            current.copy(isLoading = false, error = error.message ?: "评论加载出现未知异常。")
        }
    }

    suspend fun loadPageSummary(
        detail: AlbumDetail,
        onProgress: (AlbumPageSummary.Ready) -> Unit = {},
    ): AlbumPageSummary = coroutineScope {
        val chapters = detail.readingChapters()
        val albumImageCount = detail.imageCount
        if (albumImageCount != null && albumImageCount > 0) {
            if (chapters.size == 1) chapterPageCounts[chapters.first().id] = albumImageCount
            return@coroutineScope AlbumPageSummary.Ready(
                totalPages = albumImageCount,
                resolvedChapters = chapters.size,
                totalChapters = chapters.size,
            )
        }

        val semaphore = Semaphore(PAGE_COUNT_CONCURRENCY)
        val progressMutex = Mutex()
        var resolvedChapters = 0
        var resolvedPages = 0
        val counts = chapters.map { chapter ->
            async {
                val count = semaphore.withPermit {
                    chapterPageCounts[chapter.id] ?: loadChapterPageCount(chapter.id)?.also { count ->
                        chapterPageCounts[chapter.id] = count
                    }
                }
                if (count != null) {
                    progressMutex.withLock {
                        resolvedChapters++
                        resolvedPages += count
                        onProgress(
                            AlbumPageSummary.Ready(
                                totalPages = resolvedPages,
                                resolvedChapters = resolvedChapters,
                                totalChapters = chapters.size,
                            ),
                        )
                    }
                }
                count
            }
        }.map { it.await() }
        val resolved = counts.filterNotNull()
        if (resolved.isEmpty()) {
            AlbumPageSummary.Unavailable(totalChapters = chapters.size)
        } else {
            AlbumPageSummary.Ready(
                totalPages = resolved.sum(),
                resolvedChapters = resolved.size,
                totalChapters = chapters.size,
            )
        }
    }

    private suspend fun loadChapterPageCount(chapterId: String): Int? {
        when (val detailResult = core.chapterApi.detail(chapterId)) {
            is JmxResult.Success -> {
                val count = detailResult.value.imageCount ?: detailResult.value.pageArr.size
                if (count > 0) return count
            }
            is JmxResult.Failure -> Unit
        }
        return when (val templateResult = core.chapterApi.template(chapterId, shunt = DEFAULT_IMAGE_SHUNT)) {
            is JmxResult.Success -> templateResult.value.imageFileNames.size.takeIf { it > 0 }
            is JmxResult.Failure -> null
        }
    }
}

internal sealed interface AlbumDetailUiState {
    data object Loading : AlbumDetailUiState
    data class Content(
        val detail: AlbumDetail,
        val comments: DetailCommentsState,
    ) : AlbumDetailUiState
    data class Error(val message: String) : AlbumDetailUiState
}

private enum class DetailAccountAction { LIKE, FAVORITE, COMMENT }

internal fun ActionResult.rejectionMessageOrNull(): String? {
    val normalizedStatus = status?.trim()?.lowercase()
    val normalizedMessage = message?.trim()?.takeIf { it.isNotBlank() }
    if (normalizedStatus in DETAIL_SUCCESS_STATUSES) return null
    if (normalizedStatus.isNullOrEmpty()) {
        return normalizedMessage?.takeIf(::looksLikeActionRejection)
    }
    return normalizedMessage ?: "服务未接受本次操作（$status）"
}

private fun looksLikeActionRejection(message: String): Boolean {
    return listOf("重复留言", "失败", "拒绝", "禁止", "无权限").any(message::contains)
}

internal data class DetailCommentsState(
    val items: List<CommentItem> = emptyList(),
    val total: Int? = null,
    val nextPage: Int = 1,
    val isLoading: Boolean = false,
    val endReached: Boolean = false,
    val error: String? = null,
)

internal sealed interface AlbumPageSummary {
    data object Loading : AlbumPageSummary

    data class Ready(
        val totalPages: Int,
        val resolvedChapters: Int,
        val totalChapters: Int,
    ) : AlbumPageSummary

    data class Unavailable(val totalChapters: Int) : AlbumPageSummary
}

internal fun AlbumDetail.readingChapters(): List<AlbumChapter> {
    return series
        .filter { it.id.isNotBlank() }
        .distinctBy { it.id }
        .ifEmpty {
            listOf(AlbumChapter(id = id, name = name, sort = null))
        }
}

internal fun AlbumChapter.displayName(index: Int): String {
    return name?.decodeHtml()?.trim()?.takeIf { it.isNotBlank() }
        ?: sort?.trim()?.takeIf { it.isNotBlank() }?.let { "第 $it 话" }
        ?: "第 ${index + 1} 话"
}

private fun CommentPage.toDetailCommentsState(): DetailCommentsState {
    val commentTotal = total
    return DetailCommentsState(
        items = comments,
        total = commentTotal,
        nextPage = 2,
        endReached = comments.isEmpty() || (commentTotal != null && comments.size >= commentTotal),
    )
}

private fun mergeCommentPages(
    existing: List<CommentItem>,
    incoming: List<CommentItem>,
): List<CommentItem> {
    val knownIds = existing.mapNotNullTo(mutableSetOf()) { it.id?.takeIf(String::isNotBlank) }
    return buildList(existing.size + incoming.size) {
        addAll(existing)
        incoming.forEach { comment ->
            val id = comment.id?.takeIf(String::isNotBlank)
            if (id == null || knownIds.add(id)) add(comment)
        }
    }
}

private fun AlbumPageSummary.summaryText(detail: AlbumDetail?): String {
    return when (this) {
        AlbumPageSummary.Loading -> detail?.readingChapters()?.size?.let { "$it 话 · 正在统计" } ?: "正在统计"
        is AlbumPageSummary.Ready -> if (resolvedChapters == totalChapters) {
            "$totalPages 页"
        } else {
            "$totalPages+ 页"
        }
        is AlbumPageSummary.Unavailable -> "$totalChapters 话"
    }
}

private fun AlbumPageSummary.sectionText(detail: AlbumDetail): String {
    val chapterCount = detail.readingChapters().size
    return when (this) {
        AlbumPageSummary.Loading -> "共 $chapterCount 话，正在汇总各话页数"
        is AlbumPageSummary.Ready -> if (resolvedChapters == totalChapters) {
            "全 $totalPages 页 · 共 $totalChapters 话"
        } else {
            "已读取 $resolvedChapters/$totalChapters 话，共 $totalPages 页"
        }
        is AlbumPageSummary.Unavailable -> "共 $totalChapters 话"
    }
}

internal fun curvedCoverBounds(
    start: Rect,
    end: Rect,
    progress: Float,
    maxBend: Float,
): Rect {
    val p = progress.coerceIn(0f, 1f)
    val startPoint = start.topLeft
    val endPoint = end.topLeft
    val dx = endPoint.x - startPoint.x
    val dy = endPoint.y - startPoint.y
    val distance = hypot(dx, dy).coerceAtLeast(1f)
    val normal = Offset(-dy / distance, dx / distance)
    val bend = min(distance * 0.24f, maxBend)
    val control1 = startPoint + Offset(dx * 0.24f, dy * 0.24f) + normal * bend
    val control2 = startPoint + Offset(dx * 0.72f, dy * 0.72f) + normal * (bend * 0.72f)
    val topLeft = cubicBezier(startPoint, control1, control2, endPoint, p)
    val width = start.width + (end.width - start.width) * p
    val height = start.height + (end.height - start.height) * p
    return Rect(topLeft, androidx.compose.ui.geometry.Size(width, height))
}

private fun cubicBezier(
    start: Offset,
    control1: Offset,
    control2: Offset,
    end: Offset,
    progress: Float,
): Offset {
    val inverse = 1f - progress
    val startWeight = inverse * inverse * inverse
    val control1Weight = 3f * inverse * inverse * progress
    val control2Weight = 3f * inverse * progress * progress
    val endWeight = progress * progress * progress
    return Offset(
        x = start.x * startWeight + control1.x * control1Weight +
            control2.x * control2Weight + end.x * endWeight,
        y = start.y * startWeight + control1.y * control1Weight +
            control2.y * control2Weight + end.y * endWeight,
    )
}

private fun compactCount(value: Int?): String {
    val number = value ?: 0
    return when {
        number >= 10_000 -> "${(number / 1_000) / 10f}万"
        number >= 1_000 -> "${(number / 100) / 10f}千"
        else -> number.toString()
    }
}

private val DetailSpringSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = 280f,
    visibilityThreshold = 0.0001f,
)
private val DetailTransitionEasing = CubicBezierEasing(0.18f, 0.82f, 0.16f, 1f)
private val DETAIL_HORIZONTAL_PADDING = 16.dp
private val DETAIL_COVER_WIDTH = 105.dp
private val DETAIL_COVER_HEIGHT = 140.dp
private const val DETAIL_INFO_TAB = 0
private const val DETAIL_CATALOG_TAB = 1
private const val DETAIL_COMMENTS_TAB = 2
private const val PAGE_COUNT_CONCURRENCY = 4
internal const val DEFAULT_IMAGE_SHUNT = "1"
private const val DETAIL_ENTER_DURATION_MILLIS = 680
private const val DETAIL_EXIT_DURATION_MILLIS = 560
private const val DETAIL_REQUEST_TIMEOUT_MILLIS = 20_000L
private const val COMMENT_REQUEST_TIMEOUT_MILLIS = 10_000L
private val DETAIL_SUCCESS_STATUSES = setOf("ok", "success", "true", "1")
