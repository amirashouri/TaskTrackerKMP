package com.tasktracker.designsystem

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.input.pointer.pointerInput
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
    const val TAB_LENS_RIM_ALPHA: Float = 0.45f
    const val SOLID_LENS_ALPHA: Float = 0.45f
    const val SOLID_LENS_RIM_ALPHA: Float = 0.6f
    const val TAB_LENS_WIDEN: Float = 0.10f
    const val TAB_LENS_SQUASH: Float = 0.06f
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
    val deviceMotion = DeviceMotion()
    val roll = deviceMotion.roll()

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

            val specularOffset = roll * size.width * 0.15f
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

            if (isDark && borderWidth > 0.dp && base == null) {
                val strokePx = with(density) { borderWidth.toPx() }
                val y = strokePx
                val alpha = rim.alpha * 0.4f
                drawLine(
                    color = Color.Red.copy(alpha = alpha),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = strokePx,
                )
                drawLine(
                    color = Color.Green.copy(alpha = alpha * 1.5f),
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
        .border(
            width = borderWidth,
            brush = Brush.verticalGradient(
                colors = listOf(
                    rim,
                    rim.copy(alpha = rim.alpha * ttGlass.RIM_BOTTOM_ALPHA),
                ),
                startY = 0f,
                endY = Float.POSITIVE_INFINITY,
            ),
            shape = shape,
        )
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
    val velocityThreshold = 500.dp
    val maxBlurRadius = 8.dp
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
    val target = if (selected) alpha else 0f
    val washAlpha by animateFloatAsState(
        targetValue = target,
        animationSpec = ttGlass.selectionSpring,
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
    val lensSpring: AnimationSpec<Dp> = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)

    val targetX = with(density) { (selectedBounds?.left ?: 0).toDp() }
    val targetY = with(density) { (selectedBounds?.top ?: 0).toDp() }
    val targetWidth = with(density) { (selectedBounds?.width ?: 0).toDp() }
    val targetHeight = with(density) { (selectedBounds?.height ?: 0).toDp() }

    val lensX by animateDpAsState(targetValue = targetX, animationSpec = lensSpring, label = "lensX")
    val lensY by animateDpAsState(targetValue = targetY, animationSpec = lensSpring, label = "lensY")
    val lensWidth by animateDpAsState(targetValue = targetWidth, animationSpec = lensSpring, label = "lensWidth")
    val lensHeight by animateDpAsState(targetValue = targetHeight, animationSpec = lensSpring, label = "lensHeight")

    class VelocityTracker(var prevX: Dp, var prevTime: Long)
    val tracker = remember { VelocityTracker(lensX, 0L) }

    val currentTime = currentNanoTime()
    val dtSeconds = if (tracker.prevTime > 0) (currentTime - tracker.prevTime) / 1_000_000_000.0 else 0.0
    val deltaX = lensX.value - tracker.prevX.value
    val currentVelocityX = if (dtSeconds > 0) (deltaX / dtSeconds).dp else 0.dp

    tracker.prevX = lensX
    tracker.prevTime = currentTime

    return LensPlacement(lensX, lensY, lensWidth, lensHeight, currentVelocityX)
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
    compact: Boolean = false,
) {
    Box(
        modifier = modifier
            .onGloballyPositioned(onPositioned)
            .then(
                Modifier.padding(
                    vertical = if (compact) ttSpacing.xs else ttSpacing.controlPaddingV,
                    horizontal = if (compact) ttSpacing.sm else ttSpacing.controlPaddingH,
                ),
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

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && interactive) ttGlass.PRESSED_SCALE else 1f,
        animationSpec = ttGlass.pressSpring,
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

@Composable
fun ttGlassAddButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(ttSpacing.floatingActionSize)
            .ttGlassSurface(shape = CircleShape, borderWidth = ttGlass.hairlineWidth)
            .background(Color.Transparent)
            .pointerInput(Unit) { }
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        ttText(
            text = "+",
            style = ttTypography.title,
            color = ttLabel(),
        )
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
    Box(
        modifier = modifier
            .size(ttSpacing.iconButtonSize)
            .alpha(if (enabled) 1f else ttGlass.DISABLED_ALPHA)
            .ttGlassSurface(shape = CircleShape, borderWidth = ttGlass.hairlineWidth)
            .background(Color.Transparent)
            .pointerInput(Unit) { }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        ttIcon(
            image = image,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(ttIconSize.tab),
        )
    }
}