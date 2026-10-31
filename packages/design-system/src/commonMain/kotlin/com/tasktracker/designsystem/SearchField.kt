package com.tasktracker.designsystem

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import com.tasktracker.resources.Res
import com.tasktracker.resources.work_item_search_clear
import org.jetbrains.compose.resources.stringResource

/**
 * The app's one search field: a glass pill with a magnifier, a hint, and an inline clear cross.
 *
 * ## Why this is a primitive rather than a screen's own pill
 *
 * The board and the backlog both grew the same control, and a third copy is how the two ends up
 * styled differently — the reasoning [ttTextField]'s class docs already record for the *other*
 * field. So the surface, the glyphs, the spacing and the clear affordance live here, and a
 * screen passes a value and a change callback.
 *
 * ## What this composable does not do
 *
 * It does not filter, debounce, or remember the query. It is a controlled field: every keystroke
 * calls [onValueChange] immediately and the hint follows [value], so the caller owns the query
 * and the delay between typing and applying it. That split is deliberate — a field that held its
 * own text would have to be re-seeded whenever the caller cleared it, and a field that filtered
 * would have to know what it was filtering.
 *
 * ## The geometry it shares with [ttTextField]
 *
 * Same order — surface, then padding — so the visible pill is the control's real 48dp rather
 * than a surface drawn inside an already-inset box. Same tokens: [ttRadius.control],
 * [ttSpacing.fieldMinHeight], [ttSpacing.xl] horizontally, [ttSpacing.md] vertically, and the
 * same focus treatment ([FocusWashAlpha], [FocusRingWidth]) so a focused search field and a
 * focused text field look like the same control in the same state. What differs is the radius
 * (a search pill is a control, not a row) and the two glyphs.
 *
 * ## The clear control is ink, not a second glass button
 *
 * The field is already a translucent surface. A 44dp glass circle inside a 48dp pill is all rim
 * and reads as two controls stacked; the inline 12dp cross is the pattern [MemberPicker]'s pills
 * already use, and it keeps its tap target by padding ([ttSpacing.xs]) rather than by elevation.
 *
 * ## The tag goes on the field, not the pill
 *
 * [fieldModifier] reaches the [BasicTextField] and [modifier] reaches the pill, matching
 * [ttTextField]. A tag moved from one to the other silently retargets `performTextInput` at a
 * node that cannot accept text, which fails as "nothing was typed" rather than as a compile
 * error.
 *
 * ## RTL
 *
 * Every offset here is symmetric (`padding(horizontal = ...)`, [Arrangement.spacedBy]), and the
 * row's children are laid out start to end, so the field mirrors with the layout direction and
 * needs no separate arrangement. The magnifier's own drawing does not mirror, which is correct:
 * a mirrored magnifier is still a magnifier, and flipping it would put the handle on the wrong
 * side of the lens.
 *
 * @param value the current text. The caller owns it.
 * @param onValueChange called on every edit, including the clear control's `""`.
 * @param hint placeholder drawn inside the field while [value] is empty.
 * @param keyboardActions the caller's, so a screen can dismiss focus on submit. The IME action
 *   itself is fixed at [ImeAction.Search] — this is a search field, and a caller that had to
 *   restate that would eventually forget to.
 * @param onFocusChanged reports focus transitions, for a screen that hides something on blur.
 */
@Composable
fun ttSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
    enabled: Boolean = true,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    onFocusChanged: ((Boolean) -> Unit)? = null,
) {
    val accent = ttAccent()
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(ttRadius.control)

    // A value rather than a branch around the modifier call: `ttGlassSurface` is a composable,
    // so calling it conditionally would change the composition structure and reset the surface
    // on every focus change. Same reason, same shape, as `ttTextField`.
    val wash = if (focused) accent.copy(alpha = FocusWashAlpha) else null
    val ring = if (focused) accent else null

    val surface = modifier
        .fillMaxWidth()
        .heightIn(min = ttSpacing.fieldMinHeight)
        .alpha(if (enabled) 1f else ttGlass.DISABLED_ALPHA)
        .ttGlassSurface(shape = shape, tint = wash)
    val ringed = if (ring == null) surface else surface.border(FocusRingWidth, ring, shape)

    val clearLabel = stringResource(Res.string.work_item_search_clear)

    Row(
        modifier = ringed.padding(horizontal = ttSpacing.xl, vertical = ttSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Decorative: the field's own semantics say what it is, and a described glyph beside
        // it would be announced as a second thing to read.
        ttIcon(
            image = ttActionIcons.search,
            contentDescription = null,
            tint = ttLabelSecondary(),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            // `weight` rather than `fillMaxWidth`: this is the only field in the app with
            // siblings in its row, and a filled field takes the pill's whole width — which
            // lays the clear cross out past the right rim, where it is clipped and untappable
            // while still being present in the tree.
            modifier = fieldModifier
                .weight(1f)
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
            visualTransformation = VisualTransformation.None,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = keyboardActions,
            decorationBox = { inner ->
                // The hint is a sibling of the input rather than a placeholder API, so it
                // inherits the field's own text style and the caller keeps one string.
                Box {
                    if (value.isEmpty()) {
                        ttText(
                            text = hint,
                            style = ttTypography.body,
                            color = ttLabelSecondary(),
                            modifier = Modifier.testTag(TTSearchFieldHintTag),
                        )
                    }
                    inner()
                }
            },
        )
        if (value.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(ttRadius.control))
                    .clickable(
                        role = Role.Button,
                        onClickLabel = clearLabel,
                        onClick = { onValueChange("") },
                    )
                    .padding(ttSpacing.xs)
                    .testTag(TTSearchFieldClearTag),
            ) {
                ttIcon(
                    image = ttActionIcons.close,
                    contentDescription = null,
                    tint = ttLabelSecondary(),
                    modifier = Modifier.size(ttIconSize.close),
                )
            }
        }
    }
}

/** The hint node, for a test that has to say whether the field is showing one. */
const val TTSearchFieldHintTag = "design.searchField.hint"

/** The clear affordance. Absent while the field is empty, which is the contract. */
const val TTSearchFieldClearTag = "design.searchField.clear"

/** Two states, because the clear cross only exists in one of them. */
@Preview(name = "Search field", showBackground = true, widthDp = 360)
@Composable
private fun TTSearchFieldPreview() {
    ttPreviewSurface {
        Column(
            modifier = Modifier.padding(ttSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.lg),
        ) {
            ttSearchField(value = "", onValueChange = {}, hint = "Search tasks")
            ttSearchField(value = "backlog", onValueChange = {}, hint = "Search tasks")
            ttSearchField(value = "sprint", onValueChange = {}, hint = "Search tasks", enabled = false)
        }
    }
}
