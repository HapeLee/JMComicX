package io.kazusa.jmcomic.effect.liquid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedCornerStyle
import com.kyant.shapes.RoundedRectangle

/** 与 Matter 的 smoothCapsule 相同，使用连续曲率而非圆弧拼接。 */
@Composable
internal fun rememberLiquidCapsuleShape(): Shape =
    remember { Capsule(RoundedCornerStyle.Continuous) }

internal fun liquidRoundedRectShape(radius: Dp): Shape = RoundedRectangle(radius, RoundedCornerStyle.Continuous)

@Composable
internal fun rememberLiquidRoundedRectShape(radius: Dp): Shape =
    remember(radius) { liquidRoundedRectShape(radius) }
