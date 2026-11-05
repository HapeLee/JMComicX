package io.kazusa.jmcomic.effect

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * 漫画详情卡片形变（Morph）计算。
 *
 * 借鉴 legado-with-MD3 的形变架构：
 * 1. 视觉面（Panel）：从封面卡片矩形线性过渡到全屏，圆角自适应屏幕物理硬件圆角。
 * 2. 内容层（Content）：无几何缩放（避免文字/图标非等比拉伸糊化），仅受边界裁剪与透明度遮罩控制。
 *    内容遮罩在 [MORPH_VEIL_START]..[MORPH_VEIL_END] 平滑淡入，并在动画稳定前通过输入闸门消费触点。
 * 3. 飞行封面（Cover）：双缓动分量：
 *    - 位置曲线 e1 = 1 - (1 - t)^2（先快后慢）
 *    - 尺寸曲线 e2 = t^2（先慢后快）
 *    营造出封面从卡片深处自然生长破壳而出的纵深感与弧形轨迹。
 */

/** 遮罩 / 内容开始淡入的下限点。 */
const val MORPH_VEIL_START = 0.20f

/** 遮罩 / 内容完全不透明的上限点：收起时在此点之前完全保持可见，之后在中后期渐变消失。 */
const val MORPH_VEIL_END = 0.65f

/** 视觉面形变圆角配置。 */
@Immutable
data class AlbumPanelCornerRadii(
    val topLeft: Float,
    val topRight: Float,
    val bottomRight: Float,
    val bottomLeft: Float,
) {
    companion object {
        val Zero = all(0f)
        fun all(radius: Float) = AlbumPanelCornerRadii(radius, radius, radius, radius)
    }
}

/** 视觉面板形变帧。 */
@Immutable
data class AlbumPanelMorphFrame(
    val bounds: Rect,
    val cornerRadiusPx: Float,
    val shadowAlpha: Float,
    val cornerRadii: AlbumPanelCornerRadii = AlbumPanelCornerRadii.all(cornerRadiusPx),
)

/** 封面形变帧。 */
@Immutable
data class AlbumCoverMorphFrame(
    val bounds: Rect,
    val cornerRadiusPx: Float,
    val shadowAlpha: Float,
)

internal fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/** 两端导数为零的平滑插值（Hermite 曲线）。 */
fun smoothstep(x: Float): Float {
    val c = x.coerceIn(0f, 1f)
    return c * c * (3f - 2f * c)
}

/** 位置曲线：先快后慢。 */
fun morphPositionEase(t: Float): Float {
    val clamped = t.coerceIn(0f, 1f)
    return 1f - (1f - clamped) * (1f - clamped)
}

/** 尺寸曲线：先慢后快。 */
fun morphSizeEase(t: Float): Float {
    val clamped = t.coerceIn(0f, 1f)
    return clamped * clamped
}

/**
 * 视觉面形变：从 [start]（卡片矩形）长到 [end]（全屏矩形）。
 */
fun computePanelMorphFrame(
    progress: Float,
    start: Rect,
    end: Rect,
    startCornerRadiusPx: Float,
    endCornerRadiusPx: Float,
    endCornerRadii: AlbumPanelCornerRadii = AlbumPanelCornerRadii.all(endCornerRadiusPx),
): AlbumPanelMorphFrame? {
    if (start.isEmpty || end.isEmpty) return null
    val t = progress.coerceIn(0f, 1f)

    val w = lerp(start.width, end.width, t)
    val h = lerp(start.height, end.height, t)
    val cornerRadiusPx = lerp(
        startCornerRadiusPx,
        endCornerRadiusPx.coerceAtMost(minOf(w, h) * 0.5f),
        t,
    )
    val maxRadius = minOf(w, h) * 0.5f
    return AlbumPanelMorphFrame(
        bounds = Rect(
            left = lerp(start.left, end.left, t),
            top = lerp(start.top, end.top, t),
            right = lerp(start.right, end.right, t),
            bottom = lerp(start.bottom, end.bottom, t),
        ),
        cornerRadiusPx = cornerRadiusPx,
        shadowAlpha = lerp(1f, 0f, morphSizeEase(t)),
        cornerRadii = AlbumPanelCornerRadii(
            topLeft = lerp(startCornerRadiusPx, endCornerRadii.topLeft, t).coerceIn(0f, maxRadius),
            topRight = lerp(startCornerRadiusPx, endCornerRadii.topRight, t).coerceIn(0f, maxRadius),
            bottomRight = lerp(startCornerRadiusPx, endCornerRadii.bottomRight, t).coerceIn(0f, maxRadius),
            bottomLeft = lerp(startCornerRadiusPx, endCornerRadii.bottomLeft, t).coerceIn(0f, maxRadius),
        ),
    )
}

/**
 * 封面形变：从列表封面卡片飞往详情页大封面。
 * 中心 X 先快就位（e1），中心 Y 随尺寸缓慢上升（e2），形成自然向左上方飞跃的弧线，并伴随尺寸破壳生长。
 */
fun computeCoverMorphFrame(
    progress: Float,
    start: Rect,
    end: Rect,
    startCornerRadiusPx: Float,
    endCornerRadiusPx: Float,
): AlbumCoverMorphFrame? {
    if (start.isEmpty || end.isEmpty) return null
    val t = progress.coerceIn(0f, 1f)
    val e1 = morphPositionEase(t)
    val e2 = morphSizeEase(t)

    val cx = lerp(start.center.x, end.center.x, e1)
    val cy = lerp(start.center.y, end.center.y, e2)
    val w = lerp(start.width, end.width, e2)
    val h = lerp(start.height, end.height, e2)
    val maxRadius = minOf(w, h) * 0.5f
    return AlbumCoverMorphFrame(
        bounds = Rect(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f),
        cornerRadiusPx = lerp(startCornerRadiusPx, endCornerRadiusPx, e2).coerceAtMost(maxRadius),
        shadowAlpha = lerp(1f, 0f, e2),
    )
}

/**
 * 内容可见度：前 [veilStart] 保持全透明，在 [veilStart]..[veilEnd] 平滑淡入。
 */
fun computeMorphVeil(
    progress: Float,
    veilStart: Float = MORPH_VEIL_START,
    veilEnd: Float = MORPH_VEIL_END,
): Float {
    val t = progress.coerceIn(0f, 1f)
    if (t <= veilStart) return 0f
    if (t >= veilEnd) return 1f
    return smoothstep((t - veilStart) / (veilEnd - veilStart))
}

/** 面板底色在起始段迅速建立，避免刚点击时穿透背景。 */
fun computeMorphPanelAlpha(progress: Float): Float = smoothstep(progress / 0.15f)

/** 临界阻尼弹簧临近终点的速度限制，避免手势甩动速度过冲。 */
fun computeMorphSettleVelocity(current: Float, target: Float, velocity: Float): Float {
    val distance = kotlin.math.abs(target - current)
    val limit = minOf(3f, kotlin.math.sqrt(280f) * distance)
    return velocity.coerceIn(-limit, limit)
}

/** 预测性返回跟随手指缩回进度。 */
fun computePredictiveMorphProgress(startProgress: Float, backProgress: Float): Float =
    startProgress.coerceIn(0f, 1f) * (1f - 0.45f * backProgress.coerceIn(0f, 1f))

/** 内容层何时放行触点与读屏交互。 */
fun morphContentSettled(veil: Float, progress: Float): Boolean =
    veil >= 1f || progress <= 0.001f

/** 判定坐标矩形是否在屏幕内有效可见。 */
fun isBoundsVisibleInScreen(bounds: Rect, screen: Rect): Boolean {
    if (bounds.isEmpty || screen.isEmpty) return false
    val left = maxOf(bounds.left, screen.left)
    val top = maxOf(bounds.top, screen.top)
    val right = minOf(bounds.right, screen.right)
    val bottom = minOf(bounds.bottom, screen.bottom)
    if (right <= left || bottom <= top) return false
    val visibleArea = (right - left) * (bottom - top)
    val totalArea = bounds.width * bounds.height
    return totalArea > 0f && (visibleArea / totalArea) >= 0.20f
}

/** 动态裁剪面板形状。 */
data class MorphPanelClipShape(
    val bounds: Rect,
    val radii: AlbumPanelCornerRadii,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = Outline.Rounded(
        RoundRect(
            rect = bounds,
            topLeft = CornerRadius(radii.topLeft, radii.topLeft),
            topRight = CornerRadius(radii.topRight, radii.topRight),
            bottomRight = CornerRadius(radii.bottomRight, radii.bottomRight),
            bottomLeft = CornerRadius(radii.bottomLeft, radii.bottomLeft),
        ),
    )
}
