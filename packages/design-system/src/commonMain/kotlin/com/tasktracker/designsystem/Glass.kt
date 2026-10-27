package com.tasktracker.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.compose.ui.util.lerp as lerpFloat

/** Core Liquid Glass design system primitives, surfaces, lenses, and pickers. */
object ttGlass {
    const val TINT_ALPHA: Float = 0.22f
    const val PROMINENT_TINT_ALPHA: Float = 0.85f

    @Deprecated("Use GlassThickness.STANDARD.baseAlpha", ReplaceWith("ttGlass.GlassThickness.STANDARD.baseAlpha"))
    val FILL_ALPHA: Float = GlassThickness.STANDARD.baseAlpha

    const val FILL_FADE: Float = 0.4f
    const val LENS_ALPHA: Float = 0.08f
    const val HAIRLINE_ALPHA: Float = 0.14f
    const val LIGHT_HAIRLINE_ALPHA: Float = 0.22f
    val hairlineWidth: Dp = 0.5.dp
    const val PRESSED_SCALE: Float = 0.95f

    const val DISABLED_ALPHA: Float = 0.4f
    val pressSpring: SpringSpec<Float> = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)
    val selectionSpring: SpringSpec<Float> = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)
    const val TAB_LENS_SETTLE_MILLIS: Int = 260
    const val TAB_LENS_ALPHA: Float = 0.14f
    const val TAB_LENS_FADE: Float = 0.75f
    const val SELECTION_WASH_ALPHA: Float = 0.25f
    const val RIM_BOTTOM_ALPHA: Float = 0.6f
    /** Base alpha for the dark-mode chromatic dispersion fringe drawn under the rim. */
    const val DISPERSION_ALPHA: Float = 0.4f
    /** Multiplier for the green channel of the dispersion fringe (green leads slightly). */
    const val DISPERSION_GREEN_MULTIPLIER: Float = 1.5f
    const val TAB_LENS_RIM_ALPHA: Float = 0.45f
    const val SOLID_LENS_ALPHA: Float = 0.45f
    const val SOLID_LENS_RIM_ALPHA: Float = 0.6f
    const val TAB_LENS_WIDEN: Float = 0.10f
    const val TAB_LENS_SQUASH: Float = 0.06f

    /**
     * The lens speed, in dp/s, at which [ttGlassLens] starts drawing its motion trail, and the
     * widest that trail's blur ever gets.
     *
     * Dp per second because that is what the lens reports — the placement's velocity is the
     * spring's own `Animatable.velocity`, in the dp the lens animates in. So these two numbers are
     * the whole of when a fast tab switch starts smearing and how far it smears: the trail is
     * invisible below the threshold, and a speed that would take it past [LENS_MAX_BLUR_DP] stops
     * there.
     */
    val LENS_VELOCITY_THRESHOLD_DP: Dp = 500.dp
    val LENS_MAX_BLUR_DP: Dp = 8.dp
    const val SPECULAR_ALPHA: Float = 0.12f
    val SPECULAR_HEIGHT_DP: Dp = 3.dp
    const val TAB_LENS_ICON_SCALE: Float = 1.08f
    val tabBarShadowElevation: Dp = 10.dp
    const val TAB_BAR_SHADOW_ALPHA: Float = 0.22f

    /** Glass thickness levels controlling base fill alpha before intensity scaling. */
    sealed class GlassThickness(val baseAlpha: Float) {
        object THIN : GlassThickness(0.45f)
        object STANDARD : GlassThickness(0.55f)
        object THICK : GlassThickness(0.65f)
    }

    /** Computes dual-stop vertical color gradient for unbased glass surfaces. */
    fun fillRamp(
        tint: Color?,
        canvas: Color,
        accent: Color,
        thickness: GlassThickness = GlassThickness.STANDARD,
        intensity: Float = 1f,
        reducedSurface: Color,
    ): List<Color> {
        val top = tint ?: accent
        val shipped = (tint?.alpha ?: thickness.baseAlpha).coerceIn(0f, 1f)
        val i = intensity.coerceIn(0f, 1f)
        val alpha = lerpFloat(1f, shipped, i)
        val crossed = lerp(reducedSurface, top, i)
        return listOf(
            crossed.copy(alpha = alpha),
            lerp(crossed, canvas, FILL_FADE * i).copy(alpha = alpha),
        )
    }
}

/** Renders base glass surface with optional tint, thickness, and specular highlights. */
@Composable
fun Modifier.ttGlassSurface(
    shape: Shape,
    tint: Color? = null,
    thickness: ttGlass.GlassThickness = ttGlass.GlassThickness.STANDARD,
    borderWidth: Dp = ttGlass.hairlineWidth,
    base: Color? = null,
): Modifier {
    val accent = ttAccent()
    val canvas = ttCanvas()
    val intensity = LocalGlassIntensity.current
    val rim = ttGlassHairline()
    val isDark = LocalThemeScheme.current == ThemeScheme.Dark
    val reducedMotion = LocalReducedMotion.current
    // Composition takes the controller object only; the roll State itself is read inside
    // the drawBehind below. The sensor updates at frame rate, so a composition-phase read
    // would recompose every glass surface per motion event where the draw-phase read
    // invalidates redraw only. See [DeviceMotionController].
    val deviceMotion = rememberDeviceMotion()

    val wash = if (base == null) {
        ttGlass.fillRamp(
            tint = tint,
            canvas = canvas,
            accent = accent,
            thickness = thickness,
            intensity = intensity,
            reducedSurface = ttSurface(),
        )
    } else {
        listOf(
            Color.White.copy(alpha = ttGlass.LENS_ALPHA),
            Color.Transparent,
        )
    }

    return this
        .clip(shape)
        .background(color = base ?: Color.Transparent, shape = shape)
        .drawBehind {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = wash,
                    startY = 0f,
                    endY = size.height,
                ),
            )

            if (base == null && size.width > 2f * size.height) {
                val radius = size.width * 0.6f
                val centerX = size.width / 2f
                val centerY = 0f
                val scatterAlpha = 0.025f
                drawRect(
                    brush = Brush.radialGradient(
                        center = Offset(centerX, centerY),
                        radius = radius,
                        colors = listOf(
                            Color.White.copy(alpha = scatterAlpha),
                            Color.Transparent,
                        ),
                    ),
                )
            }

            val specularOffset = if (reducedMotion) 0f else deviceMotion.roll.value * size.width * 0.15f
            val specularHeight = with(density) { ttGlass.SPECULAR_HEIGHT_DP.toPx() }
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = ttGlass.SPECULAR_ALPHA),
                        Color.White.copy(alpha = 0f),
                    ),
                    start = Offset(specularOffset, 0f),
                    end = Offset(specularOffset, specularHeight),
                ),
                topLeft = Offset(0f, 0f),
                size = Size(size.width, specularHeight),
            )

            // Chromatic dispersion fringe (dark mode only, no base). Simulates subtle
            // spectral separation at the raised top edge by drawing pure R/G/B hairlines
            // under the rim; green leads slightly. Drawn before the rim border.
            if (isDark && borderWidth > 0.dp && base == null) {
                val strokePx = with(density) { borderWidth.toPx() }
                val y = strokePx
                val alpha = rim.alpha * ttGlass.DISPERSION_ALPHA
                drawLine(
                    color = Color.Red.copy(alpha = alpha),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = strokePx,
                )
                drawLine(
                    color = Color.Green.copy(alpha = alpha * ttGlass.DISPERSION_GREEN_MULTIPLIER),
                    start = Offset(0f, y + strokePx * 0.5f),
                    end = Offset(size.width, y + strokePx * 0.5f),
                    strokeWidth = strokePx,
                )
                drawLine(
                    color = Color.Blue.copy(alpha = alpha),
                    start = Offset(0f, y + strokePx),
                    end = Offset(size.width, y + strokePx),
                    strokeWidth = strokePx,
                )
            }
        }
        .drawBehind {
            if (borderWidth > 0.dp) {
                val strokePx = with(density) { borderWidth.toPx() }
                val halfStroke = strokePx / 2f
                val rimBrush = Brush.verticalGradient(
                    colors = listOf(
                        rim,
                        rim.copy(alpha = rim.alpha * ttGlass.RIM_BOTTOM_ALPHA),
                    ),
                    startY = 0f,
                    endY = size.height,
                )
                drawRoundRect(
                    brush = rimBrush,
                    topLeft = Offset(halfStroke, halfStroke),
                    size = Size(size.width - strokePx, size.height - strokePx),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokePx),
                )
            }
        }
}

/** Renders translucent selection lens wash and border rim over component surface. */
@Composable
fun Modifier.ttGlassLens(
    shape: Shape,
    washAlpha: Float = ttGlass.TAB_LENS_ALPHA,
    rimAlpha: Float = ttGlass.TAB_LENS_RIM_ALPHA,
    velocityX: Dp = 0.dp,
): Modifier {
    val intensity = LocalGlassIntensity.current
    val density = LocalDensity.current
    val velocityThreshold = ttGlass.LENS_VELOCITY_THRESHOLD_DP
    val maxBlurRadius = ttGlass.LENS_MAX_BLUR_DP
    val speed = kotlin.math.abs(velocityX.value)
    val blurRadius: Float = if (speed > velocityThreshold.value) {
        ((speed - velocityThreshold.value) * 0.01f).coerceIn(0f, maxBlurRadius.value)
    } else 0f
    val blurPx = with(density) { Dp(blurRadius).toPx() }
    return this
        .clip(shape)
        .drawBehind {
            val lensAlpha = washAlpha * intensity.coerceIn(0f, 1f)
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = lensAlpha),
                        Color.White.copy(alpha = lensAlpha * (1f - ttGlass.TAB_LENS_FADE)),
                    ),
                    startY = 0f,
                    endY = size.height,
                ),
            )
            if (blurRadius > 0f) {
                val trailAlpha = (blurRadius / maxBlurRadius.value * 0.15f).coerceIn(0f, 0.15f)
                val direction = if (velocityX.value > 0) 1f else -1f
                val trailWidth = (blurPx * 2f).coerceIn(0f, size.width * 0.5f)
                val trailX = if (velocityX.value > 0) -trailWidth else 0f
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = trailAlpha),
                            Color.White.copy(alpha = 0f),
                        ),
                        startX = 0f,
                        endX = trailWidth,
                    ),
                    topLeft = Offset(trailX, 0f),
                    size = Size(trailWidth, size.height),
                )
            }
        }
        .border(
            width = ttGlass.hairlineWidth,
            color = Color.White.copy(alpha = rimAlpha),
            shape = shape,
        )
}

/** Renders animated accent selection wash background over option rows. */
@Composable
fun Modifier.ttSelectionWash(
    selected: Boolean,
    alpha: Float = ttGlass.SELECTION_WASH_ALPHA,
): Modifier {
    val reducedMotion = LocalReducedMotion.current
    val target = if (selected) alpha else 0f
    val washAlpha by animateFloatAsState(
        targetValue = target,
        animationSpec = if (reducedMotion) snap() else ttGlass.selectionSpring,
        label = "selectionWash",
    )
    val accent = ttAccent()
    return this.background(
        color = accent.copy(alpha = washAlpha),
        shape = RoundedCornerShape(ttRadius.control),
    )
}

@Composable
fun <Option> ttLensPlacement(
    positions: Map<Option, IntRect>,
    selection: Option,
    density: Density,
): LensPlacement {
    val selectedBounds = positions[selection]
    val reducedMotion = LocalReducedMotion.current
    val springSpec = if (reducedMotion) snap<Float>() else spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)

    val targetX = with(density) { (selectedBounds?.left ?: 0).toDp() }
    val targetY = with(density) { (selectedBounds?.top ?: 0).toDp() }
    val targetWidth = with(density) { (selectedBounds?.width ?: 0).toDp() }
    val targetHeight = with(density) { (selectedBounds?.height ?: 0).toDp() }

    // The four animatables are born at the target they are handed rather than at a `0f`
    // sentinel, and the `remember` is keyed on whether the selected segment has a rectangle
    // yet. That key is what makes appearing *not* travelling: segments are measured after the
    // first layout, so on the frame the lens first has bounds it is given the place it belongs
    // in and starts there — where an animatable left over from an earlier zero would have
    // travelled the whole distance, and now that the velocity is the spring's own, drawn a
    // full-strength trail out of nowhere on its first frame. A selection change leaves the key
    // alone, so the same animatables carry on and the spring's velocity survives the switch.
    val measured = selectedBounds != null
    val animX = remember(measured) { Animatable(targetX.value) }
    val animY = remember(measured) { Animatable(targetY.value) }
    val animWidth = remember(measured) { Animatable(targetWidth.value) }
    val animHeight = remember(measured) { Animatable(targetHeight.value) }

    LaunchedEffect(targetX.value, reducedMotion) {
        if (reducedMotion) {
            animX.snapTo(targetX.value)
        } else {
            animX.animateTo(targetX.value, springSpec)
        }
    }
    LaunchedEffect(targetY.value, reducedMotion) {
        if (reducedMotion) {
            animY.snapTo(targetY.value)
        } else {
            animY.animateTo(targetY.value, springSpec)
        }
    }
    LaunchedEffect(targetWidth.value, reducedMotion) {
        if (reducedMotion) {
            animWidth.snapTo(targetWidth.value)
        } else {
            animWidth.animateTo(targetWidth.value, springSpec)
        }
    }
    LaunchedEffect(targetHeight.value, reducedMotion) {
        if (reducedMotion) {
            animHeight.snapTo(targetHeight.value)
        } else {
            animHeight.animateTo(targetHeight.value, springSpec)
        }
    }

    val lensX = animX.value.dp
    val lensY = animY.value.dp
    val lensWidth = animWidth.value.dp
    val lensHeight = animHeight.value.dp

    // The spring's own velocity, in the dp-per-second unit `LensPlacement.velocityX` exposes
    // and `ttGlassLens` thresholds against (500.dp). It is not divided by the density: the
    // animatable holds dp, so its velocity is already dp/s, and dividing it scaled the trail's
    // threshold with the display — a 3x screen needed three times the speed to draw one.
    val velocityX = if (reducedMotion) 0.dp else animX.velocity.dp

    return LensPlacement(lensX, lensY, lensWidth, lensHeight, velocityX)
}

data class LensPlacement(
    val x: Dp,
    val y: Dp,
    val width: Dp,
    val height: Dp,
    val velocityX: Dp = 0.dp,
)

/** Glass card surface modifier for floating content. */
@Composable
fun Modifier.ttGlassCard(
    tint: Color? = null,
    cornerRadius: Dp = ttRadius.surface,
    thickness: ttGlass.GlassThickness = ttGlass.GlassThickness.STANDARD,
): Modifier = ttGlassSurface(shape = RoundedCornerShape(cornerRadius), tint = tint, thickness = thickness)

/** Single slab segmented control sharing a unified glass selection lens. */
@Composable
fun <Option> TTGlassSegmentedPicker(
    options: List<Option>,
    selection: Option,
    onSelect: (Option) -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    testTag: @Composable (Option) -> String? = { null },
    title: @Composable (Option) -> String,
) {
    val density = LocalDensity.current
    val positions = remember { mutableStateMapOf<Option, IntRect>() }
    var containerCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val selectedBounds = positions[selection]

    val (lensX, lensY, lensWidth, lensHeight, velocityX) = ttLensPlacement(positions, selection, density)
    val lensShape = RoundedCornerShape(ttRadius.control)

    Box(
        modifier = modifier
            .ttGlassSurface(
                shape = lensShape,
                tint = tint,
                thickness = ttGlass.GlassThickness.STANDARD,
                borderWidth = ttGlass.hairlineWidth,
            )
            .padding(ttSpacing.hairlineInset)
            .onGloballyPositioned { containerCoordinates = it },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (option in options) {
                val optionTestTag = testTag(option)
                Segment(
                    label = title(option),
                    isSelected = option == selection,
                    onClick = { onSelect(option) },
                    onPositioned = { coordinates ->
                        val parent = containerCoordinates
                        if (parent != null) {
                            val position = parent.localPositionOf(coordinates, Offset.Zero)
                            positions[option] = IntRect(
                                left = position.x.roundToInt(),
                                top = position.y.roundToInt(),
                                right = position.x.roundToInt() + coordinates.size.width,
                                bottom = position.y.roundToInt() + coordinates.size.height,
                            )
                        }
                    },
                    modifier = Modifier.weight(1f).let { if (optionTestTag != null) it.testTag(optionTestTag) else it },
                )
            }
        }

        if (selectedBounds != null) {
            Box(
                modifier = Modifier
                    .size(lensWidth, lensHeight)
                    .offset(x = lensX, y = lensY)
                    .ttGlassLens(lensShape, velocityX = velocityX),
            )
        }
    }
}

@Composable
fun <Option> TTGlassFlowPicker(
    options: List<Option>,
    selection: Option,
    onSelect: (Option) -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    title: @Composable (Option) -> String,
) {
    val accent = ttAccent()
    val canvas = ttCanvas()
    val density = LocalDensity.current
    val positions = remember { mutableStateMapOf<Option, IntRect>() }
    var containerCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val selectedBounds = positions[selection]
    val (lensX, lensY, lensWidth, lensHeight, velocityX) = ttLensPlacement(positions, selection, density)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .ttGlassSurface(
                shape = RoundedCornerShape(ttRadius.surface),
                tint = tint,
                thickness = ttGlass.GlassThickness.STANDARD,
                borderWidth = ttGlass.hairlineWidth,
            )
            .padding(ttSpacing.sm)
            .onGloballyPositioned { containerCoordinates = it },
    ) {
        if (selectedBounds != null) {
            Box(
                modifier = Modifier
                    .offset(x = lensX, y = lensY)
                    .size(lensWidth, lensHeight)
                    .ttGlassLens(CircleShape, velocityX = velocityX),
            )
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.xs),
        ) {
            options.forEach { option ->
                FlowPickerChip(
                    label = title(option),
                    isSelected = option == selection,
                    accent = accent,
                    onClick = { onSelect(option) },
                    onPositioned = { coordinates ->
                        val parent = containerCoordinates
                        if (parent != null) {
                            val position = parent.localPositionOf(coordinates, Offset.Zero)
                            val left = position.x.roundToInt()
                            val top = position.y.roundToInt()
                            val measured = IntRect(
                                left = left,
                                top = top,
                                right = left + coordinates.size.width,
                                bottom = top + coordinates.size.height,
                            )
                            if (positions[option] != measured) positions[option] = measured
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun FlowPickerChip(
    label: String,
    isSelected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    onPositioned: (LayoutCoordinates) -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .onGloballyPositioned(onPositioned)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(vertical = ttSpacing.xs, horizontal = ttSpacing.sm)
            .semantics {
                contentDescription = label
                selected = isSelected
            },
        contentAlignment = Alignment.Center,
    ) {
        ttText(
            text = label,
            style = if (isSelected) ttTypography.subheadlineSelected else ttTypography.subheadline,
            color = if (isSelected) ttLabel() else lerp(accent, Color.White, FLOW_PICKER_INACTIVE_LIGHTENING),
            textAlign = TextAlign.Center,
        )
    }
}

private const val FLOW_PICKER_INACTIVE_LIGHTENING = 0.18f

@Composable
private fun Segment(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onPositioned: (LayoutCoordinates) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .onGloballyPositioned(onPositioned)
            .padding(
                vertical = ttSpacing.controlPaddingV,
                horizontal = ttSpacing.controlPaddingH,
            )
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                role = Role.Tab,
                onClick = onClick,
            )
            .semantics { selected = isSelected },
        contentAlignment = Alignment.Center,
    ) {
        val content = ttLabel()
        Box(modifier = Modifier.semantics { contentDescription = label }) {
            ttText(
                text = label,
                style = if (isSelected) ttTypography.subheadlineSelected else ttTypography.subheadline,
                color = content,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Encapsulates button styles (resting vs prominent accent fills). */
data class TTGlassButtonStyle(
    val tint: Color? = null,
    val prominent: Boolean = false,
) {
    internal companion object {
        val resting = TTGlassButtonStyle()
        fun prominent(tint: Color? = null) = TTGlassButtonStyle(tint = tint, prominent = true)
    }
}

/** Interactive glass button supporting resting, prominent tints, and loading states. */
@Composable
fun ttGlassButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: TTGlassButtonStyle = TTGlassButtonStyle.resting,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val accent = ttAccent()
    val resolvedTint = if (style.prominent) (style.tint ?: accent) else style.tint
    val content = if (style.prominent) ttOnAccent else ttLabel()

    val interactive = enabled && !loading
    val reducedMotion = LocalReducedMotion.current

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && interactive) ttGlass.PRESSED_SCALE else 1f,
        animationSpec = if (reducedMotion) snap() else ttGlass.pressSpring,
        label = "ttGlassButton press",
    )

    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else ttGlass.DISABLED_ALPHA)
            .scale(scale)
            .ttGlassSurface(
                shape = CircleShape,
                tint = resolvedTint,
                thickness = if (style.prominent) ttGlass.GlassThickness.THICK else ttGlass.GlassThickness.STANDARD,
            )
            .padding(
                vertical = ttSpacing.buttonPaddingV,
                horizontal = ttSpacing.buttonPaddingH,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = interactive,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .alpha(if (loading) 0f else 1f)
                .semantics { contentDescription = label },
        ) {
            ttText(
                text = label,
                style = ttTypography.button,
                color = content,
                textAlign = TextAlign.Center,
            )
        }
        if (loading) {
            Box(modifier = Modifier.alpha(1f - if (loading) 0f else 1f)) {
                ttSpinner(color = content)
            }
        }
    }
}

/**
 * The skeleton both circular glass actions share: the size, the glass surface, the disabled dim,
 * the click, and the description. The two public buttons differ only in how big they are and what
 * they draw, which is what [size] and [content] carry in here.
 *
 * Three properties of this chain are load-bearing rather than incidental.
 *
 * The dim is applied *outside* the surface, so a disabled button fades its fill and its rim
 * together. The other order leaves a fully lit hairline around a faded face, which reads as a
 * control you can press. It is also what disables the click — the same [enabled] flag dims the
 * button and makes it inert, because a dimmed button that still fires is the defect the flag
 * exists to prevent.
 *
 * And the not-disabled branch applies `Modifier.alpha(1f)`, which looks like a no-op and is one:
 * `Modifier.alpha` returns its receiver untouched at exactly 1f, so an enabled button carries no
 * layer at all. That is the reason this branch is written as a value rather than as two chains —
 * both buttons' enabled pixels are held to being unchanged, and an empty `graphicsLayer` over this
 * bar was measured to darken its backdrop by 0.14 of relative luminance (see `TabLens` in
 * TabBar.kt), so a layer added silently here would be visible.
 */
@Composable
private fun GlassRoundAction(
    size: Dp,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            .alpha(if (enabled) 1f else ttGlass.DISABLED_ALPHA)
            .ttGlassSurface(shape = CircleShape, borderWidth = ttGlass.hairlineWidth)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * Floating circular glass action button.
 *
 * Draws a `+` unless [image] is given, which is how a caller that wants a glyph gets this larger
 * circle with the icon button's icon path. The text is the default, so a caller that passes nothing
 * keeps the `+` this control has always drawn — the glyph is added, not swapped in.
 *
 * [enabled] fades the whole control to [ttGlass.DISABLED_ALPHA] and stops it responding — both at
 * once, since a button that looks disabled and still fires is worse than either.
 */
@Composable
fun ttGlassAddButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    image: ImageVector? = null,
) {
    GlassRoundAction(
        size = ttSpacing.floatingActionSize,
        contentDescription = contentDescription,
        enabled = enabled,
        onClick = onClick,
        modifier = modifier,
    ) {
        if (image != null) {
            ttIcon(
                image = image,
                contentDescription = null,
                tint = ttLabel(),
                modifier = Modifier.size(ttIconSize.tab),
            )
        } else {
            ttText(
                text = "+",
                style = ttTypography.title,
                color = ttLabel(),
            )
        }
    }
}

/** Compact circular glass action button with icon glyph. */
@Composable
fun ttGlassIconButton(
    image: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = ttLabel(),
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    GlassRoundAction(
        size = ttSpacing.iconButtonSize,
        contentDescription = contentDescription,
        enabled = enabled,
        onClick = onClick,
        modifier = modifier,
    ) {
        ttIcon(
            image = image,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(ttIconSize.tab),
        )
    }
}