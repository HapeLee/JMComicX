package dev.jmx.client.effect.liquid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import com.kyant.backdrop.Backdrop
import top.yukonga.miuix.kmp.blur.LayerBackdrop

/**
 * 复用页面已录制的背景，交给与 Matter 相同的 Backdrop 渲染器处理光学效果。
 * 保留 MIUIX 顶栏渐进模糊和关于页的采样接口，无需额外录制一份全屏内容。
 * Kyant 的采样空间为原始像素，转发时不做 MIUIX 的降采样。
 */
private class MatterBackdrop(private val source: LayerBackdrop) : Backdrop {
    override val isCoordinatesDependent: Boolean get() = source.isCoordinatesDependent

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        with(source) { drawBackdrop(density, coordinates, layerBlock, downscaleFactor = 1) }
    }
}

@Composable
internal fun rememberMatterBackdrop(source: LayerBackdrop?): Backdrop? =
    remember(source) { source?.let(::MatterBackdrop) }
