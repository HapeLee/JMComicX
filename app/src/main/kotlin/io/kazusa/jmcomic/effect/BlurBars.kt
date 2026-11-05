package io.kazusa.jmcomic.effect

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import io.kazusa.jmcomic.effect.liquid.GlassTopBarScope
import io.kazusa.jmcomic.effect.liquid.LocalGlassTopBarScope
import io.kazusa.jmcomic.effect.liquid.LocalTopBarGlassScroll
import io.kazusa.jmcomic.effect.liquid.rememberTopBarGlassProgress
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingToolbarDefaults
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 顶栏模糊样式：GAUSSIAN=高斯模糊，PROGRESSIVE=渐进模糊 */
enum class TopBarBlurStyle {
    GAUSSIAN,
    PROGRESSIVE;

    fun label(): String = when (this) {
        GAUSSIAN -> "高斯模糊"
        PROGRESSIVE -> "渐进模糊"
    }

    companion object {
        fun fromName(name: String?): TopBarBlurStyle =
            if (name == PROGRESSIVE.name) PROGRESSIVE else GAUSSIAN
    }
}

/** IOS_LIKE 保留原有存储值，视觉实现使用 Matter 的液态玻璃配方。 */
enum class FloatingNavBarStyle {
    DEFAULT,
    IOS_LIKE;

    fun label(): String = when (this) {
        DEFAULT -> "MIUIX"
        IOS_LIKE -> "Matter 液态玻璃"
    }

    companion object {
        fun fromName(name: String?): FloatingNavBarStyle =
            if (name == IOS_LIKE.name) IOS_LIKE else DEFAULT
    }
}

/**
 * 页面级共享的采样背景层：内容侧用 [Modifier.layerBackdrop] 挂载，
 * 顶栏/底栏在各自位置以 textureBlur / progressiveTextureBlur 采样。
 * 设备不支持 RuntimeShader 时返回 null，调用方自动退化为普通表面。
 */
@Composable
fun rememberBarBackdrop(): LayerBackdrop? {
    if (!isRuntimeShaderSupported()) return null
    val surfaceColor = MiuixTheme.colorScheme.surface
    return rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
}

/**
 * 顶栏模糊容器（对照 miuix example 的 BlurredBar 官方实现）：
 * - 高斯模糊：整条 textureBlur，半径 30f，表面色 0.82 透明度混合
 * - 渐进模糊：progressiveTextureBlur，顶部向下渐隐（曲线 2.2f），半径 26f，表面色 0.5；
 *   不额外叠加渐变颜色遮罩。
 * backdrop 为 null 时不施加任何效果。
 */
@Composable
fun BlurredBar(
    backdrop: LayerBackdrop?,
    style: TopBarBlurStyle,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val blurActive = backdrop != null
    val glassProgress = rememberTopBarGlassProgress()
    val scrollSource = LocalTopBarGlassScroll.current
    val glassScope = remember(backdrop, scrollSource) { GlassTopBarScope(backdrop, glassProgress) }
    Box(
        modifier = when {
            !blurActive -> Modifier
            style == TopBarBlurStyle.GAUSSIAN -> Modifier.textureBlur(
                backdrop = backdrop,
                shape = RectangleShape,
                blurRadius = 30f,
                colors = barBlurColors(progressive = false),
            )
            else -> Modifier
        }.then(modifier),
    ) {
        if (blurActive && style == TopBarBlurStyle.PROGRESSIVE) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .progressiveTextureBlur(
                        backdrop = backdrop,
                        shape = RectangleShape,
                        gradient = ProgressiveBlur.Top.copy(curve = 2.2f),
                        blurRadius = 26f,
                        colors = barBlurColors(progressive = true),
                    ),
            )
        }
        CompositionLocalProvider(LocalGlassTopBarScope provides glassScope) {
            content()
        }
    }
}

@Composable
private fun barBlurColors(progressive: Boolean): BlurColors = BlurDefaults.blurColors(
    blendColors = listOf(
        BlendColorEntry(color = MiuixTheme.colorScheme.surface.copy(if (progressive) 0.5f else 0.82f)),
    ),
)

/**
 * MIUIX 官方悬浮底栏 + 液态玻璃（磨砂）效果，对照 miuix example 的 AppContent.kt 简单版实现。
 * 直接使用官方 [FloatingNavigationBar]，尺寸/圆角/阴影由官方 Defaults 决定（不再本地手写常量）。
 * 开启模糊时把底栏底色设为透明，用 textureBlur 采样内容；backdrop 为 null（不支持 RuntimeShader）
 * 时退化为普通 surfaceContainer 悬浮栏。
 */
@Composable
fun BlurredFloatingNavigationBar(
    backdrop: LayerBackdrop?,
    content: @Composable () -> Unit,
) {
    val blurActive = backdrop != null
    val floatingShape = RoundedCornerShape(FloatingToolbarDefaults.CornerRadius)
    FloatingNavigationBar(
        modifier = if (blurActive) {
            Modifier.textureBlur(
                backdrop = backdrop,
                shape = floatingShape,
                blurRadius = 25f,
                colors = floatingBarBlurColors(),
            )
        } else {
            Modifier
        },
        color = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surfaceContainer,
        content = content,
    )
}

@Composable
private fun floatingBarBlurColors(): BlurColors = BlurDefaults.blurColors(
    blendColors = listOf(
        BlendColorEntry(color = MiuixTheme.colorScheme.surfaceContainer.copy(0.55f)),
    ),
)
