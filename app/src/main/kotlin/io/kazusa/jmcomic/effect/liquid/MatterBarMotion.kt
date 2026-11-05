package io.kazusa.jmcomic.effect.liquid

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Matter MainBottomBarLayer 的同一份宽度/按钮进度，两个组件不能各跑一条动画。 */
@Stable
internal class MatterBarMotion(
    val screenWidth: Dp,
    val fullCapsuleWidth: Dp,
    val bottomPadding: Dp,
    val capsuleScale: State<Float>,
    val buttonReveal: State<Float>,
) {
    val capsuleWidth: Dp get() = fullCapsuleWidth * capsuleScale.value
    // 必须允许过冲时的短暂负值；调用方用 offset，不能用 padding 夹紧位置。
    val buttonEndInset: Dp get() = screenWidth / 2 - capsuleWidth / 2 - 10.dp - 56.dp
}

internal fun matterCapsuleScaleTarget(screenWidth: Float, fullCapsuleWidth: Float): Float =
    ((screenWidth / 2f - 10f - 56f - 16f) / (fullCapsuleWidth / 2f)).coerceIn(0.7f, 1f)

@Composable
internal fun rememberMatterButtonReveal(visible: Boolean): State<Float> = animateFloatAsState(
    targetValue = if (visible) 1f else 0f,
    animationSpec = if (visible) spring(stiffness = 280f, dampingRatio = 0.62f)
    else spring(stiffness = 340f, dampingRatio = 0.68f),
    label = "MatterCategoryButtonReveal",
)

@Composable
internal fun rememberMatterBarMotion(showSideButton: Boolean, itemCount: Int): MatterBarMotion {
    val density = LocalDensity.current
    val screenWidth = with(density) { LocalWindowInfo.current.containerSize.width.toDp() }
    val fullWidth = (60 * itemCount + 36).dp
    val navigationInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val visible = showSideButton && entered
    val scale = animateFloatAsState(
        targetValue = if (visible) matterCapsuleScaleTarget(screenWidth.value, fullWidth.value) else 1f,
        animationSpec = spring(stiffness = 300f, dampingRatio = 0.66f),
        label = "MatterBottomBarCapsuleScale",
    )
    val reveal = rememberMatterButtonReveal(visible)
    return remember(screenWidth, fullWidth, navigationInset, scale, reveal) {
        MatterBarMotion(screenWidth, fullWidth, navigationInset + 12.dp, scale, reveal)
    }
}

internal data class MatterButtonFrame(val alpha: Float, val scale: Float, val offsetX: Float, val offsetY: Float)

/** Alpha 才夹到 0..1；缩放和位移保留弹簧过冲，与 Matter 一致。位移单位为 dp。 */
internal fun matterButtonFrame(reveal: Float): MatterButtonFrame = MatterButtonFrame(
    alpha = reveal.coerceIn(0f, 1f),
    scale = (0.15f + 0.85f * reveal).coerceAtLeast(0f),
    offsetX = (1f - reveal) * 14f,
    offsetY = (1f - reveal) * 18f,
)
