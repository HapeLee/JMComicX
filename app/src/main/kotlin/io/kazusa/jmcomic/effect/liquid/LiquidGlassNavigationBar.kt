// Optical rendering ported from Matter's LegadoLiquidFloatingBottomBar.
// Drag interaction derived from Kyant0/AndroidLiquidGlass (Apache-2.0).
package io.kazusa.jmcomic.effect.liquid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import io.kazusa.jmcomic.effect.liquid.animation.DampedDragAnimation
import io.kazusa.jmcomic.NavigationTab
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BadgedBox
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import com.kyant.backdrop.shadow.InnerShadow as BackdropInnerShadow
import com.kyant.backdrop.shadow.Shadow as BackdropShadow

@Composable
internal fun MatterLiquidGlassNavigationBar(
    items: List<NavigationTab>,
    selectedIndex: Int,
    onItemClick: (Int) -> Unit,
    backdrop: LayerBackdrop?,
    isBlurActive: Boolean,
    modifier: Modifier = Modifier,
    positionFraction: (() -> Float)? = null,
    motion: MatterBarMotion,
    badge: (Int) -> (@Composable () -> Unit)? = { null },
) {
    val isDark = MiuixTheme.colorScheme.surface.luminance() < 0.5f
    val pillShape = rememberLiquidCapsuleShape()
    val glassBackdrop = rememberMatterBackdrop(backdrop)
    val blurActive = isBlurActive && glassBackdrop != null
    val highlight = if (isDark) buttonSpecularDark else buttonSpecularLight
    val accentColor = MiuixTheme.colorScheme.onSurface
    val tabContentColor = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val surfaceContainer = MiuixTheme.colorScheme.surface
    val containerColor = if (blurActive) surfaceContainer.copy(alpha = 0.4f) else surfaceContainer

    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()
    val tabsCount = items.size

    var tabWidthPx by remember { mutableFloatStateOf(0f) }
    var totalWidthPx by remember { mutableFloatStateOf(0f) }

    val offsetAnimation = remember { Animatable(0f) }
    val rubberBandPx = with(density) { 4.dp.toPx() }
    val panelOffset by remember(rubberBandPx) {
        derivedStateOf {
            if (totalWidthPx == 0f) {
                0f
            } else {
                val fraction = (offsetAnimation.value / totalWidthPx).coerceIn(-1f, 1f)
                rubberBandPx * fraction.sign * EaseOut.transform(abs(fraction))
            }
        }
    }

    var currentIndex by remember { mutableIntStateOf(selectedIndex) }
    val onItemClickUpdated by rememberUpdatedState(onItemClick)

    fun indexAt(positionX: Float): Int {
        if (tabWidthPx == 0f) return currentIndex
        val horizontalPaddingPx = with(density) { 18.dp.toPx() }
        val logicalX = if (isLtr) positionX else totalWidthPx - positionX
        return ((logicalX - horizontalPaddingPx) / tabWidthPx)
            .toInt()
            .coerceIn(0, tabsCount - 1)
    }

    val dampedDrag = remember(animationScope, tabsCount, density, isLtr) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = selectedIndex.toFloat(),
            valueRange = 0f..(tabsCount - 1).toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 78f / 56f,
            canDrag = { position ->
                position.x in 0f..totalWidthPx
            },
            onDragStarted = { position ->
                updateValue(indexAt(position.x).toFloat())
            },
            onDragStopped = {
                val targetIndex = targetValue.roundToInt().coerceIn(0, tabsCount - 1)
                if (currentIndex != targetIndex) {
                    currentIndex = targetIndex
                    onItemClickUpdated(targetIndex)
                }
                updateValue(targetIndex.toFloat())
                animationScope.launch {
                    offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                }
            },
            onDragCancelled = {
                updateValue(currentIndex.toFloat())
                animationScope.launch {
                    offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                }
            },
            onDrag = { _, dragAmount ->
                if (tabWidthPx > 0f && dragAmount.x != 0f) {
                    updateValue(
                        (targetValue + dragAmount.x / tabWidthPx * if (isLtr) 1f else -1f)
                            .coerceIn(0f, (tabsCount - 1).toFloat()),
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            },
        )
    }

    LaunchedEffect(selectedIndex) {
        if (currentIndex != selectedIndex) {
            currentIndex = selectedIndex
            dampedDrag.animateToValue(selectedIndex.toFloat())
        }
    }

    fun activateTab(index: Int) {
        if (currentIndex != index) {
            currentIndex = index
            onItemClickUpdated(index)
        }
        dampedDrag.animateToValue(index.toFloat())
    }

    val animatedPosition by remember(dampedDrag, positionFraction) {
        derivedStateOf {
            matterTabPosition(
                pagerPosition = positionFraction?.invoke(),
                dragPosition = dampedDrag.value,
                pressProgress = dampedDrag.pressProgress,
            )
        }
    }

    val tabsContent: @Composable RowScope.() -> Unit = {
        items.forEachIndexed { index, item ->
            Column(
                modifier = Modifier
                    .semantics(mergeDescendants = true) {
                        selected = index == currentIndex
                        role = Role.Tab
                        onClick {
                            activateTab(index)
                            true
                        }
                    }
                    .onKeyEvent { event ->
                        val isActivationKey = event.key == Key.Enter ||
                            event.key == Key.NumPadEnter ||
                            event.key == Key.Spacebar
                        if (isActivationKey) {
                            if (event.type == KeyEventType.KeyUp) activateTab(index)
                            true
                        } else {
                            false
                        }
                    }
                    .focusable()
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                horizontalAlignment = CenterHorizontally,
            ) {
                BadgedBox(badge = { badge(index)?.invoke() }) {
                    val selection = matterTabSelectionFraction(animatedPosition, index)
                    val tint = androidx.compose.ui.graphics.lerp(tabContentColor, accentColor, selection)
                    LiquidGlassTransitionIcon(
                        modifier = Modifier.size(24.dp),
                        outlinedIcon = item.icon,
                        filledIcon = item.selectedIcon,
                        selectionProgress = { matterTabSelectionFraction(animatedPosition, index) },
                        contentDescription = null,
                        tint = tint,
                    )
                }
                LiquidGlassText(
                    text = item.label,
                    style = MiuixTheme.textStyles.footnote2,
                    color = androidx.compose.ui.graphics.lerp(
                        tabContentColor, accentColor, matterTabSelectionFraction(animatedPosition, index),
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    val bottomPadding = motion.bottomPadding
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val panelWidth = motion.capsuleWidth
        Row(
            modifier = Modifier
                .padding(bottom = bottomPadding)
                .width(panelWidth)
                .selectableGroup()
                .onSizeChanged {
                    totalWidthPx = it.width.toFloat()
                    tabWidthPx = ((totalWidthPx - with(density) { 36.dp.toPx() }) / tabsCount).coerceAtLeast(0f)
                }
                .graphicsLayer { translationX = panelOffset }
                .then(
                    if (blurActive) {
                        Modifier.drawBackdrop(
                            backdrop = glassBackdrop,
                            shape = { pillShape },
                            effects = {
                                vibrancy()
                                blur(8.dp.toPx())
                                lens(
                                    refractionHeight = 10.dp.toPx(),
                                    refractionAmount = 20.dp.toPx(),
                                    depthEffect = true,
                                    chromaticAberration = true,
                                )
                            },
                            highlight = { highlight },
                            shadow = {
                                BackdropShadow(radius = 8.dp, color = Color.Black, alpha = if (isDark) 0.10f else 0.05f)
                            },
                            innerShadow = {
                                BackdropInnerShadow(radius = 1.dp, color = Color.White.copy(alpha = if (isDark) 0.04f else 0.08f))
                            },
                            layerBlock = {
                                val scale = androidx.compose.ui.util.lerp(
                                    1f, 1f + 16.dp.toPx() / size.width.coerceAtLeast(1f), dampedDrag.pressProgress,
                                )
                                scaleX = scale
                                scaleY = scale
                            },
                            onDrawSurface = { drawRect(containerColor) },
                        )
                    } else {
                        Modifier.dropShadow(
                            shape = pillShape,
                            shadow = Shadow(radius = 8.dp, color = Color.Black, alpha = if (isDark) 0.16f else 0.08f),
                        ).background(containerColor, pillShape)
                    },
                )
                .then(dampedDrag.modifier)
                .height(56.dp)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = tabsContent,
        )
    }
}

internal fun matterTabSelectionFraction(position: Float, index: Int): Float =
    (1f - abs(position - index)).coerceIn(0f, 1f)

/** Keep the pager/drag handoff continuous, including press/release and cancelled drags. */
internal fun matterTabPosition(pagerPosition: Float?, dragPosition: Float, pressProgress: Float): Float =
    androidx.compose.ui.util.lerp(
        pagerPosition ?: dragPosition,
        dragPosition,
        pressProgress.coerceIn(0f, 1f),
    )
