package dev.jmx.client

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import dev.jmx.client.core.api.AlbumChapter
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 阅读控件始终悬浮于漫画上方，模糊强度不随正文的滚动位置变化。 */
@Composable
internal fun ReaderTopBar(
    chapter: AlbumChapter,
    chapterIndex: Int,
    chapterCount: Int,
    source: ReaderResourceSource,
    onBack: () -> Unit,
    onShowSettings: () -> Unit,
    backdrop: LayerBackdrop?,
) {
    val minCapsuleHeight = 48.dp
    Row(
        modifier = Modifier
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReaderFloatingSurface(
            backdrop = backdrop,
            modifier = Modifier.fillMaxHeight(),
            cornerRadius = minCapsuleHeight / 2,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.width(minCapsuleHeight).fillMaxHeight()) {
                Icon(MiuixIcons.Back, contentDescription = "返回详情", modifier = Modifier.size(20.dp))
            }
        }
        ReaderFloatingSurface(
            backdrop = backdrop,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            cornerRadius = minCapsuleHeight / 2,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(min = minCapsuleHeight).fillMaxHeight()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
            ) {
                Text(
                    text = chapter.displayName(chapterIndex),
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${if (source is ReaderResourceSource.Offline) "离线 · " else ""}第 ${chapterIndex + 1} / $chapterCount 话",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        ReaderFloatingSurface(
            backdrop = backdrop,
            modifier = Modifier.fillMaxHeight(),
            cornerRadius = minCapsuleHeight / 2,
        ) {
            IconButton(onClick = onShowSettings, modifier = Modifier.width(minCapsuleHeight).fillMaxHeight()) {
                Icon(MiuixIcons.Settings, contentDescription = "阅读设置", modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
internal fun ReaderControlPanel(
    backdrop: LayerBackdrop?,
    currentPageIndex: Int,
    totalPages: Int,
    sliderDraft: Float?,
    onSliderChange: (Float) -> Unit,
    onSliderFinished: () -> Unit,
    canPreviousChapter: Boolean,
    canNextChapter: Boolean,
    failedPage: Int?,
    reverseDirection: Boolean,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onShowCatalog: () -> Unit,
    pageLabel: (Int) -> String = { "第 ${it + 1} 页" },
) {
    Box(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .widthIn(max = 380.dp)
            .fillMaxWidth(),
    ) {
        ReaderFloatingSurface(backdrop = backdrop, modifier = Modifier.fillMaxWidth(), cornerRadius = 28.dp) {
            Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ReaderToolbarAction(
                        icon = MiuixIcons.ChevronForward, label = "上一话", enabled = canPreviousChapter,
                        mirrorHorizontally = !reverseDirection, onClick = onPreviousChapter,
                    )
                    ReaderPageSlider(
                        value = sliderDraft ?: currentPageIndex.toFloat(),
                        totalPages = totalPages,
                        scrubbing = sliderDraft != null,
                        reverseDirection = reverseDirection,
                        onValueChange = onSliderChange,
                        onValueChangeFinished = onSliderFinished,
                        pageLabel = pageLabel,
                        modifier = Modifier.weight(1f),
                    )
                    ReaderToolbarAction(
                        icon = MiuixIcons.ChevronForward, label = "下一话", enabled = canNextChapter,
                        mirrorHorizontally = reverseDirection, onClick = onNextChapter,
                    )
                    ReaderToolbarAction(
                        icon = MiuixIcons.ListView, label = "章节目录", onClick = onShowCatalog,
                    )
                }
                if (failedPage != null) {
                    Text(
                        text = "第 ${failedPage + 1} 页加载失败，点击该页重试",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 6.dp),
                    )
                }
            }
        }
    }
}

/** 由 miuix 处理拖动、逐页吸附与触觉反馈，只替换滑轨绘制，保留 48dp 触控区域。 */
@Composable
private fun ReaderPageSlider(
    value: Float,
    totalPages: Int,
    scrubbing: Boolean,
    reverseDirection: Boolean,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    pageLabel: (Int) -> String = { "第 ${it + 1} 页" },
) {
    val enabled = totalPages > 1
    val end = (totalPages - 1).toFloat().coerceAtLeast(1f)
    val currentValue = value.coerceIn(0f, end)
    val fraction = if (totalPages > 1) currentValue / end else 0f
    val systemRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val rtl = reverseDirection
    val colors = MiuixTheme.colorScheme
    val trackColor = colors.onSurface.copy(alpha = 0.12f)
    val fillColor = if (enabled) colors.primary else colors.disabledOnSurface
    val thumbRadius by animateDpAsState(
        targetValue = if (scrubbing) 11.dp else 9.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 600f),
        label = "ReaderSliderThumb",
    )
    val trackHeight by animateDpAsState(
        targetValue = if (scrubbing) 6.dp else 4.dp,
        animationSpec = spring(stiffness = 600f),
        label = "ReaderSliderTrack",
    )
    Box(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = "阅读进度"
            stateDescription = if (totalPages > 0) {
                "第 ${readerPageFromSlider(currentValue, totalPages) + 1} 页，共 $totalPages 页"
            } else {
                "正在加载章节"
            }
            progressBarRangeInfo = ProgressBarRangeInfo(currentValue, 0f..end, (totalPages - 2).coerceAtLeast(0))
            if (enabled) {
                setProgress { target ->
                    onValueChange(readerPageFromSlider(target, totalPages).toFloat())
                    onValueChangeFinished()
                    true
                }
            } else {
                disabled()
            }
        },
    ) {
        Slider(
            value = currentValue,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            enabled = enabled,
            reverseDirection = reverseDirection xor systemRtl,
            valueRange = 0f..end,
            steps = (totalPages - 2).coerceAtLeast(0),
            height = 48.dp,
            modifier = Modifier.fillMaxWidth().height(48.dp).drawWithContent {
                // 替换原控件的粗滑轨及按压底色，布局、手势和触觉反馈仍由 miuix 提供。
                // 与 miuix 的触摸映射共用半个控件高度的端点内缩，首尾页也能准确拖到。
                val inset = size.height / 2f
                val width = (size.width - 2f * inset).coerceAtLeast(0f)
                val start = Offset(if (rtl) size.width - inset else inset, center.y)
                val finish = Offset(if (rtl) inset else size.width - inset, center.y)
                val thumb = Offset(inset + width * (if (rtl) 1f - fraction else fraction), center.y)
                drawLine(trackColor, start, finish, strokeWidth = trackHeight.toPx(), cap = StrokeCap.Round)
                if (fraction > 0f) {
                    drawLine(fillColor, start, thumb, strokeWidth = trackHeight.toPx(), cap = StrokeCap.Round)
                }
                val radius = thumbRadius.toPx()
                drawCircle(Color.Black.copy(alpha = 0.08f), radius + 1.dp.toPx(), thumb + Offset(0f, 1.dp.toPx()))
                drawCircle(Color.White, radius, thumb)
                drawCircle(Color.Black.copy(alpha = 0.06f), radius, thumb, style = Stroke(0.5.dp.toPx()))
            },
        )
        if (scrubbing && enabled) {
            val gap = with(LocalDensity.current) { 10.dp.roundToPx() }
            val positionProvider = remember(fraction, gap, reverseDirection) { ReaderPageIndicatorPositionProvider(fraction, gap, reverseDirection) }
            // 独立浮层避开悬浮卡片的裁切，也不改变滑轨尺寸或拦截正在进行的拖动。
            Popup(
                popupPositionProvider = positionProvider,
                properties = PopupProperties(
                    focusable = false,
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false,
                ),
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.surfaceContainerHigh,
                    shadowElevation = 6.dp,
                    modifier = Modifier.clearAndSetSemantics { },
                ) {
                    Text(
                        text = pageLabel(readerPageFromSlider(currentValue, totalPages)),
                        style = MiuixTheme.textStyles.footnote1.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** 气泡跟随旋钮；端点内缩与 miuix 的 48dp 触控区域一致，并在窗口边缘限位。 */
private class ReaderPageIndicatorPositionProvider(
    private val fraction: Float,
    private val gap: Int,
    private val reverseDirection: Boolean,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val inset = anchorBounds.height / 2f
        val travel = (anchorBounds.width - 2f * inset).coerceAtLeast(0f)
        val visualFraction = if (reverseDirection) 1f - fraction else fraction
        val thumbX = anchorBounds.left + inset + travel * visualFraction
        val maxLeft = (windowSize.width - popupContentSize.width - gap).coerceAtLeast(0)
        val minLeft = gap.coerceAtMost(maxLeft)
        val left = (thumbX - popupContentSize.width / 2f).roundToInt().coerceIn(minLeft, maxLeft)
        val top = (anchorBounds.top - popupContentSize.height - gap)
            .coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0))
        return IntOffset(left, top)
    }
}

@Composable
private fun ReaderToolbarAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    mirrorHorizontally: Boolean = false,
) {
    val color = if (enabled) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.disabledOnSurface
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(44.dp).semantics {
            contentDescription = label
            role = Role.Button
            if (!enabled) disabled()
        },
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp).graphicsLayer { scaleX = if (mirrorHorizontally) -1f else 1f },
        )
    }
}

/** 使用 miuix 原生悬浮容器与背景采样；无模糊能力时保留实色表面和清晰的前景。 */
@Composable
private fun ReaderFloatingSurface(
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 28.dp,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val colors = MiuixTheme.colorScheme
    val dark = colors.surface.luminance() < 0.5f
    FloatingToolbar(
        modifier = modifier
            .then(
                if (backdrop != null) Modifier.textureBlur(
                    backdrop = backdrop,
                    shape = shape,
                    blurRadius = 28f,
                    colors = BlurDefaults.blurColors(
                        blendColors = listOf(BlendColorEntry(colors.surfaceContainer.copy(alpha = 0.78f))),
                    ),
                ) else Modifier,
            )
            .border(0.5.dp, Color.White.copy(alpha = if (dark) 0.12f else 0.65f), shape),
        color = if (backdrop != null) Color.Transparent else colors.surfaceContainer,
        cornerRadius = cornerRadius,
        outSidePadding = PaddingValues(0.dp),
        content = content,
    )
}
