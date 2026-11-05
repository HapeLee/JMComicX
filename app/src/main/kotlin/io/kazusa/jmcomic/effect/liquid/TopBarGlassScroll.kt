package io.kazusa.jmcomic.effect.liquid

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Stable
internal class TopBarGlassScrollState {
    private var owner: Any? = null
    var targetAlpha by mutableFloatStateOf(0f)
        private set

    fun attach(source: Any) { owner = source; targetAlpha = 0f }
    fun update(source: Any, alpha: Float) {
        if (owner === source) targetAlpha = alpha.coerceIn(0f, 1f)
    }
    fun detach(source: Any) {
        if (owner === source) { owner = null; targetAlpha = 0f }
    }
}

internal val LocalTopBarGlassScroll = staticCompositionLocalOf<TopBarGlassScrollState?> { null }

/** 每个独立页面一份状态，预加载的相邻分页不能覆盖当前页的玻璃强度。 */
@Composable
internal fun GlassTopBarHost(content: @Composable () -> Unit) {
    val scroll = remember { TopBarGlassScrollState() }
    CompositionLocalProvider(LocalTopBarGlassScroll provides scroll, content = content)
}

internal fun topBarGlassFraction(firstVisibleItem: Int, offset: Float, distance: Float): Float =
    if (firstVisibleItem > 0) 1f else (offset / distance.coerceAtLeast(1f)).coerceIn(0f, 1f)

@Composable
internal fun rememberTopBarGlassProgress(): State<Float> = animateTopBarGlassProgress(
    LocalTopBarGlassScroll.current?.targetAlpha ?: 0f,
)

@Composable
internal fun animateTopBarGlassProgress(target: Float): State<Float> = animateFloatAsState(
    targetValue = target.coerceIn(0f, 1f), animationSpec = tween(180), label = "TopBarGlassProgress",
)

@Composable
internal fun BindTopBarGlassScroll(scroll: LazyGridState, active: Boolean = true) {
    val distance = with(LocalDensity.current) { 96.dp.toPx() }
    BindTopBarGlassScrollSource(scroll, active, remember(scroll, distance) {
        { topBarGlassFraction(scroll.firstVisibleItemIndex, scroll.firstVisibleItemScrollOffset.toFloat(), distance) }
    })
}

@Composable
internal fun BindTopBarGlassScroll(scroll: LazyListState, active: Boolean = true) {
    val distance = with(LocalDensity.current) { 96.dp.toPx() }
    BindTopBarGlassScrollSource(scroll, active, remember(scroll, distance) {
        { topBarGlassFraction(scroll.firstVisibleItemIndex, scroll.firstVisibleItemScrollOffset.toFloat(), distance) }
    })
}

@Composable
internal fun BindTopBarGlassScroll(scroll: ScrollState, active: Boolean = true) {
    val distance = with(LocalDensity.current) { 96.dp.toPx() }
    BindTopBarGlassScrollSource(scroll, active, remember(scroll, distance) {
        { topBarGlassFraction(0, scroll.value.toFloat(), distance) }
    })
}

@Composable
private fun BindTopBarGlassScrollSource(source: Any, active: Boolean, readAlpha: () -> Float) {
    val state = LocalTopBarGlassScroll.current ?: return
    val owner = remember(source) { Any() }
    DisposableEffect(state, owner, active) {
        if (active) state.attach(owner)
        onDispose { state.detach(owner) }
    }
    LaunchedEffect(state, owner, active, readAlpha) {
        if (active) snapshotFlow(readAlpha).collect { state.update(owner, it) }
    }
}
