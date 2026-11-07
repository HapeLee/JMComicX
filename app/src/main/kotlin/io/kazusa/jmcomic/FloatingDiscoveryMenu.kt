package io.kazusa.jmcomic

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.kazusa.jmcomic.effect.liquid.LiquidGlassIcon
import io.kazusa.jmcomic.effect.liquid.LiquidGlassMorphMenu
import io.kazusa.jmcomic.effect.liquid.matterButtonFrame
import io.kazusa.jmcomic.effect.liquid.morphMenuPanelHeight
import io.kazusa.jmcomic.effect.liquid.morphMenuPanelLift
import io.kazusa.jmcomic.effect.liquid.morphMenuPanelWidth
import io.kazusa.jmcomic.effect.liquid.rememberMatterButtonReveal
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.MoreCircle

/**
 * 左下角「发现」悬浮按钮：与分类菜单共用同一套液态玻璃形变，只是锚点改到左下、
 * 面板向右上方展开。点击项是动作而不是选择，所以不显示选中勾。
 *
 * 显隐与右侧分类按钮一致（弹簧 reveal）：只有首页才出现，书架/我的不留痕迹。
 */
@Composable
internal fun FloatingDiscoveryMenu(
    onWeekly: () -> Unit,
    onRanking: () -> Unit,
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
) {
    val items = remember { listOf("每周必看", "排行榜") }
    var expanded by remember { mutableStateOf(false) }
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    // 离开首页（切到书架/我的、进详情或阅读器）时收起并隐藏。
    LaunchedEffect(visible) { if (!visible) expanded = false }
    val reveal = rememberMatterButtonReveal(visible && entered)
    val progress = animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = spring(dampingRatio = 1f, stiffness = 280f, visibilityThreshold = 0.0001f),
        label = "DiscoveryMenuMorph",
    )
    BackHandler(enabled = visible && expanded) { expanded = false }
    // 缩放轴心必须落在按钮自己身上（左下角 28dp 处）。用默认的容器中心会让按钮
    // 朝容器中心（右上）缩没，观感与右侧分类按钮相反。
    val menuHeight = morphMenuPanelHeight(items.size) + morphMenuPanelLift
    val origin = remember(menuHeight) {
        TransformOrigin(
            pivotFractionX = 28.dp / morphMenuPanelWidth,
            pivotFractionY = (menuHeight - 28.dp) / menuHeight,
        )
    }
    // 等弹簧退出到阈值后再移出组合，避免截断消失动画。
    if (reveal.value > 0.01f) {
        // 展开时提到与分类菜单同一层，避免面板底部被悬浮底栏压住。
        Box(
            modifier
                .zIndex(if (expanded || progress.value > 0.01f) 2f else 0f)
                .graphicsLayer {
                    transformOrigin = origin
                    val frame = matterButtonFrame(reveal.value)
                    alpha = frame.alpha
                    scaleX = frame.scale
                    scaleY = frame.scale
                    // 左锚点：滑出方向与右侧分类按钮镜像，朝左下角收缩。
                    translationX = -frame.offsetX.dp.toPx()
                    translationY = frame.offsetY.dp.toPx()
                }
        ) {
            LiquidGlassMorphMenu(
                items = items,
                // 动作菜单没有"当前项"，-1 让所有行都不显示选中勾。
                selectedIndex = -1,
                progress = progress,
                expanded = expanded,
                onToggle = { if (visible) expanded = !expanded },
                onItemClick = { index ->
                    expanded = false
                    if (visible) when (index) {
                        0 -> onWeekly()
                        1 -> onRanking()
                    }
                },
                backdrop = backdrop,
                icon = { LiquidGlassIcon(imageVector = MiuixIcons.MoreCircle, contentDescription = "发现") },
                collapsedSize = 56.dp,
                maxPanelHeight = morphMenuPanelHeight(items.size),
                enabled = visible,
                alignStart = true,
            )
        }
    }
}
