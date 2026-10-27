package com.tasktracker.designsystem

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * The one single-line text field in the app.
 *
 * ## Why it exists
 *
 * Every sheet that needed a field used to spell the same five-modifier pill itself — the
 * settings module alone had five copies across four files, and `ChangePasswordSheet`'s own
 * comment predicted the outcome: *"a third inline copy of the pill geometry is how the pill
 * ends up styled four ways."* It did. Three defects came with the copies, and all three are
 * geometry rather than taste:
 *
 *  1. **The pill floated.** The copies applied `padding(vertical = sm)` *before*
 *     `ttGlassSurface`, so the glass was drawn inside an already-inset box: a 48dp control
 *     whose visible surface was ~36dp, with a phantom 6dp band above and below it. The
 *     surface is applied first here, then padded, which is the order `ttGlassButton` uses.
 *  2. **The text hugged the rim.** The copies padded the inner text by `sm`/`xs` (6dp/4dp)
 *     while [ttSpacing.xl] documents itself as *"the workhorse. Card padding, field
 *     padding"*. The field contradicted the token's own doc; it now spends [ttSpacing.xl]
 *     horizontally.
 *  3. **Focus was invisible.** A focused field differed from an unfocused one only by the
 *     caret, so a form gave no feedback about which field was being typed into. Focus now
 *     draws an accent ring and a faint accent wash; [isError] draws the same treatment in
 *     [ttError], which is what the validation messages under these fields were already
 *     reporting with no corresponding change in the field itself.
 *
 * ## Reading the value in draw phase, not composition
 *
 * [roll]/[pitch] do not apply here, but the same discipline as [ttGlassSurface] does: this
 * composable holds one boolean of state ([focused]) and nothing else changes per frame.
 *
 * ## The tag goes on the field, not the pill
 *
 * [fieldModifier] is applied to the [BasicTextField] and [modifier] to the pill around it.
 * Tests find a field by its own semantics node (`onNodeWithText`), and the centring
 * assertion in the settings suite compares the field's centre against the pill's, so
 * moving a tag from one to the other silently retargets a test rather than breaking it.
 *
 * @param value the current text.
 * @param onValueChange called on every edit. Length caps belong to the caller: the limit
 *   is the server's and differs per field, so clamping here would have to guess.
 * @param hint placeholder drawn inside the field while [value] is empty.
 * @param masked renders the value as dots and sets a password keyboard unless
 *   [keyboardOptions] overrides it.
 * @param isError draws the error ring and wash. The message itself is the caller's.
 * @param onFocusChanged reports focus transitions, for callers that commit on blur.
 */
@Composable
fun ttTextField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
    masked: Boolean = false,
    isError: Boolean = false,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    onFocusChanged: ((Boolean) -> Unit)? = null,
) {
    val accent = ttAccent()
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(ttRadius.row)

    // The wash is a value rather than a branch around the modifier call: `ttGlassSurface` is
    // a composable, so calling it conditionally would change the composition structure and
    // reset the surface on every focus change.
    val wash = when {
        isError -> ttErrorFill()
        focused -> accent.copy(alpha = FocusWashAlpha)
        else -> null
    }
    // Resting fields keep the glass's own hairline (and the dark-mode dispersion that is
    // gated on it). Focus and error draw a ring on top, wide enough to read at a glance.
    val ring = when {
        isError -> ttError()
        focused -> accent
        else -> null
    }

    val surface = modifier
        .fillMaxWidth()
        .heightIn(min = ttSpacing.fieldMinHeight)
        .alpha(if (enabled) 1f else ttGlass.DISABLED_ALPHA)
        .ttGlassSurface(shape = shape, tint = wash)
    val ringed = if (ring == null) surface else surface.border(FocusRingWidth, ring, shape)

    Row(
        modifier = ringed.padding(horizontal = ttSpacing.xl, vertical = ttSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = fieldModifier
                .fillMaxWidth()
                .onFocusChanged { state ->
                    if (focused != state.isFocused) {
                        focused = state.isFocused
                        onFocusChanged?.invoke(state.isFocused)
                    }
                },
            enabled = enabled,
            textStyle = ttTypography.body.copy(color = ttLabel()),
            cursorBrush = SolidColor(accent),
            singleLine = true,
            maxLines = 1,
            visualTransformation = if (masked) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            decorationBox = { inner ->
                // The hint is drawn as a sibling of the input rather than through
                // `KeyboardOptions`/placeholder APIs, so it inherits the field's own text
                // style and the caller keeps one string to translate.
                Box {
                    if (value.isEmpty()) {
                        ttText(
                            text = hint,
                            style = ttTypography.body,
                            color = ttLabelSecondary(),
                        )
                    }
                    inner()
                }
            },
        )
    }
}

/** The accent wash behind a focused field: visible as a tint, not as a colour. */
private const val FocusWashAlpha = 0.06f

/**
 * 1.5dp — the focus and error ring.
 *
 * Wider than [ttGlass.hairlineWidth] (0.5dp) because it has to be legible against the
 * glass's own rim, which it is drawn over; at hairline width the two read as one stroke and
 * the field looks unfocused.
 */
private val FocusRingWidth = 1.5.dp
