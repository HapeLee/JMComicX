package dev.jmx.client.effect.liquid

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.menu.WindowIconCascadingDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal class GlassTopBarScope(val backdrop: LayerBackdrop?, val progress: State<Float>)
internal val LocalGlassTopBarScope = staticCompositionLocalOf<GlassTopBarScope?> { null }
internal val LocalGlassForeground = staticCompositionLocalOf { false }
private val LocalGroupedTopBarActions = staticCompositionLocalOf { false }

@Composable
internal fun currentTopBarGlassAlpha(): Float = LocalGlassTopBarScope.current?.progress?.value ?: 0f

/** 高度、外边距与内部点击热区固定；一组操作只绘制一块玻璃。 */
@Composable
internal fun GlassTopBarActions(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val scope = LocalGlassTopBarScope.current
    if (scope == null) {
        Row(modifier, verticalAlignment = Alignment.CenterVertically, content = content)
    } else {
        LiquidGlassCapsule(
            modifier = modifier.height(44.dp), backdrop = scope.backdrop, glassAlpha = scope.progress.value,
        ) {
            CompositionLocalProvider(LocalGroupedTopBarActions provides true) { content() }
        }
    }
}

@Composable
internal fun GlassTopBarIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = MiuixTheme.colorScheme.onSurface,
) {
    if (LocalGlassForeground.current || LocalGlassTopBarScope.current != null) {
        LiquidGlassIcon(imageVector, contentDescription, modifier, tint)
    } else {
        Icon(imageVector, contentDescription, modifier, tint)
    }
}

/** 仅在顶栏作用域内替换表面；弹窗、输入框等普通按钮沿用 MIUIX。 */
@Composable
internal fun GlassTopBarIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    holdDownState: Boolean = false,
    minWidth: Dp = 44.dp,
    minHeight: Dp = 44.dp,
    content: @Composable () -> Unit,
) {
    val scope = LocalGlassTopBarScope.current
    if (scope == null || minWidth < 40.dp || minHeight < 40.dp || LocalGroupedTopBarActions.current) {
        val grouped = scope != null && LocalGroupedTopBarActions.current
        IconButton(
            onClick = onClick, modifier = modifier, enabled = enabled,
            holdDownState = holdDownState,
            minWidth = if (grouped) maxOf(44.dp, minWidth) else minWidth,
            minHeight = if (grouped) maxOf(44.dp, minHeight) else minHeight,
            content = content,
        )
    } else {
        LiquidGlassIconButton(
            onClick = onClick, modifier = modifier,
            backdrop = scope.backdrop, size = maxOf(44.dp, minOf(minWidth, minHeight)), enabled = enabled,
            glassAlpha = scope.progress.value,
            content = content,
        )
    }
}

/** 菜单继续使用原有锚点和键盘/无障碍行为，仅把触发器包在同配方的玻璃表面里。 */
@Composable
internal fun GlassTopBarDropdownMenu(
    entry: DropdownEntry,
    onExpandedChange: (Boolean) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val scope = LocalGlassTopBarScope.current
    if (scope == null || LocalGroupedTopBarActions.current) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            WindowIconCascadingDropdownMenu(entry = entry, onExpandedChange = onExpandedChange, content = content)
        }
    } else {
        LiquidGlassCapsule(backdrop = scope.backdrop, modifier = Modifier.size(44.dp), glassAlpha = scope.progress.value) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                WindowIconCascadingDropdownMenu(entry = entry, onExpandedChange = onExpandedChange, content = content)
            }
        }
    }
}
