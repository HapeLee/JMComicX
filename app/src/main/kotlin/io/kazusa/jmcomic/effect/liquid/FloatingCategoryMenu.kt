package io.kazusa.jmcomic.effect.liquid

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Sort

/** 必须作为页面的全屏兄弟层挂载，展开后的列表不能放进只有底栏高的触摸区域。 */
@Composable
internal fun FloatingCategoryMenu(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    backdrop: LayerBackdrop?,
    description: String,
    modifier: Modifier = Modifier,
    bottomPadding: Dp? = null,
    visible: Boolean = true,
    motion: MatterBarMotion? = null,
    closeKey: Any? = null,
    /** 折叠态图标；默认排序图标（分类/分组语义），期刊入口可换成别的。 */
    iconOverride: (@Composable () -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    LaunchedEffect(visible, closeKey) { expanded = false }
    val reveal = motion?.buttonReveal ?: rememberMatterButtonReveal(visible && entered)
    val progress = animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = spring(dampingRatio = 1f, stiffness = 280f, visibilityThreshold = 0.0001f),
        label = "CategoryMenuMorph",
    )
    BackHandler(enabled = visible && expanded) { expanded = false }
    val navigationInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = bottomPadding ?: motion?.bottomPadding ?: navigationInset + 12.dp
    BoxWithConstraints(
        modifier = modifier.fillMaxSize().zIndex(
            if (visible && (expanded || progress.value > 0.01f)) 2f else 0f,
        ),
    ) {
        val panelHeight = morphMenuPanelHeight(items.size).coerceAtMost(
            (maxHeight - topInset - bottom - morphMenuPanelLift - 16.dp).coerceAtLeast(56.dp),
        )
        val menuHeight = panelHeight + morphMenuPanelLift
        val origin = remember(menuHeight) {
            TransformOrigin(
                pivotFractionX = (morphMenuPanelWidth - 28.dp) / morphMenuPanelWidth,
                pivotFractionY = (menuHeight - 28.dp) / menuHeight,
            )
        }
        if (visible && (expanded || progress.value > 0.001f)) {
            Box(
                Modifier.fillMaxSize().zIndex(1f).clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = "关闭$description",
                    onClick = { expanded = false },
                ),
            )
        }
        // 等弹簧退出到阈值后再移出组合；提前移除会直接截断消失动画。
        if (reveal.value > 0.01f) LiquidGlassMorphMenu(
            items = items,
            selectedIndex = selectedIndex,
            progress = progress,
            expanded = expanded,
            onToggle = { if (visible) expanded = !expanded },
            onItemClick = { index ->
                expanded = false
                if (visible) onSelect(index)
            },
            backdrop = backdrop,
            collapsedSize = 56.dp,
            maxPanelHeight = panelHeight,
            enabled = visible,
            modifier = Modifier.align(Alignment.BottomEnd)
                .offset(x = -(motion?.buttonEndInset ?: 16.dp), y = -bottom)
                .zIndex(2f)
                .graphicsLayer {
                    transformOrigin = origin
                    val frame = matterButtonFrame(reveal.value)
                    alpha = frame.alpha
                    scaleX = frame.scale
                    scaleY = frame.scale
                    translationX = frame.offsetX.dp.toPx()
                    translationY = frame.offsetY.dp.toPx()
                },
            icon = {
                if (iconOverride != null) {
                    iconOverride()
                } else {
                    LiquidGlassIcon(
                        imageVector = MiuixIcons.Sort,
                        contentDescription = "$description：${items.getOrNull(selectedIndex).orEmpty()}",
                    )
                }
            },
        )
    }
}
