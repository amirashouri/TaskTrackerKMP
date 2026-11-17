package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tasktracker.designsystem.TTBottomSheetScaffold
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttActionIcons
import com.tasktracker.designsystem.ttGlassButton
import com.tasktracker.designsystem.ttGlassCard
import com.tasktracker.designsystem.ttGlassSurface
import com.tasktracker.designsystem.ttIcon
import com.tasktracker.designsystem.ttIconSize
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttOnAccent
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.domain.BacklogSortBasis
import com.tasktracker.domain.BacklogSortKey
import com.tasktracker.domain.BacklogSortOrder
import com.tasktracker.resources.Res
import com.tasktracker.resources.backlog_sort_highest_first
import com.tasktracker.resources.backlog_sort_key_created
import com.tasktracker.resources.backlog_sort_key_priority
import com.tasktracker.resources.backlog_sort_lowest_first
import com.tasktracker.resources.backlog_sort_make_first
import com.tasktracker.resources.backlog_sort_make_second
import com.tasktracker.resources.backlog_sort_newest_first
import com.tasktracker.resources.backlog_sort_off
import com.tasktracker.resources.backlog_sort_oldest_first
import com.tasktracker.resources.backlog_sort_position_first
import com.tasktracker.resources.backlog_sort_position_then
import com.tasktracker.resources.backlog_sort_sheet_title
import com.tasktracker.resources.common_done
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Where the backlog's order is decided: one row per sort key.
 *
 * ## Why a sheet and not the row's two chips
 *
 * A sort key carries four facts — which field, whether it is in the order, which way it reads,
 * and whether it leads — and the chips could show them only as fill and glyph, so two keys and
 * two directions came out as controls a user had to decode. Here each fact is its own target:
 * the card is the key's on/off, the two direction chips name both directions in words, and the
 * position chip says "First" or "Then" and swaps the two keys. Nothing is inferred from a colour.
 *
 * The key's on/off also has a *shape*, not just a tint: the leading glyph sits in a filled tile
 * while the key is active and loses the tile when it is off. Two greys a theme can render close
 * together must not be the only difference between "this key applies" and "it does not".
 * Likewise the position chip is a full-height control with a swap glyph, not a coloured word —
 * its tap changes the order, and the affordance has to say so without a screen reader.
 *
 * ## The rules this enforces, and the ones it drops
 *
 * - Tapping a card turns its key on (as the second key, refining the order already on screen) or
 *   off. The last key cannot be turned off, because a list has to be ordered by something.
 * - Choosing a direction also turns the key on: a direction with no key is a control that does
 *   nothing.
 * - The two keys keep their own directions, so "highest priority, then oldest added" is a state
 *   the sheet can build and the summary can say.
 *
 * The one behaviour the old tap cycle had that this drops is "a tap both turns a key on and makes
 * it lead". That is now two visible controls instead of one implicit rule.
 */
@Composable
fun BacklogSortSheet(
    order: BacklogSortOrder,
    onOrderChange: (BacklogSortOrder) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
) {
    TTBottomSheetScaffold(
        visible = visible,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        // Two rows and a footer fit without scrolling; the scaffold's own scroll would compete
        // with the direction chips' taps on a short window.
        scrollable = false,
    ) {
        BacklogSortContent(
            order = order,
            onOrderChange = onOrderChange,
            onDismissRequest = onDismissRequest,
        )
    }
}

@Composable
private fun BacklogSortContent(
    order: BacklogSortOrder,
    onOrderChange: (BacklogSortOrder) -> Unit,
    onDismissRequest: () -> Unit,
) {
    // No inset of its own: the scaffold puts 24dp between the card's edges and this content in
    // both presentations, so the title and the Done row are read against the glass rather than
    // against the card's rim.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(BacklogSortSheetTag),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.lg),
    ) {
        ttText(
            text = stringResource(Res.string.backlog_sort_sheet_title),
            style = ttTypography.title,
            color = ttLabel(),
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
        ) {
            BacklogSortBasis.entries.forEach { basis ->
                BacklogSortRow(
                    basis = basis,
                    order = order,
                    onToggle = { onOrderChange(order.withKeyToggled(basis)) },
                    onDirection = { descending -> onOrderChange(order.withDirection(basis, descending)) },
                    onSwapPosition = { onOrderChange(order.withKeyPromoted(basis)) },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            ttGlassButton(
                label = stringResource(Res.string.common_done),
                onClick = onDismissRequest,
                modifier = Modifier.testTag(BacklogSortDoneTag),
            )
        }
    }
}

/**
 * One key: its on/off card, both directions as named chips, and its place in the order.
 *
 * The card is the toggle rather than a checkbox drawn inside it: a checkbox would be a second
 * thing to aim at for an action the whole row already means, and the selected state is published
 * as `selected` for assistive technology either way.
 */
@Composable
private fun BacklogSortRow(
    basis: BacklogSortBasis,
    order: BacklogSortOrder,
    onToggle: () -> Unit,
    onDirection: (descending: Boolean) -> Unit,
    onSwapPosition: () -> Unit,
) {
    val key = order.keyFor(basis)
    val isActive = key != null
    val isLeading = order.isLeading(basis)
    val direction = sortDirectionLabel(basis, key)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .ttGlassCard()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                role = Role.Checkbox,
                onClick = onToggle,
            )
            .padding(horizontal = ttSpacing.xl, vertical = ttSpacing.lg)
            .testTag(BacklogSortRowTag(basis))
            .semantics {
                selected = isActive
                stateDescription = direction
            },
        verticalArrangement = Arrangement.spacedBy(ttSpacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
        ) {
            // A filled tile, not just an accent tint, marks an active key. The old row changed
            // only the glyph's ink from secondary to accent; on the dark Classic palette those
            // two greys are close enough that the on/off state was effectively colour-only.
            // The tile is also a shape cue, so it survives any accent a user picks.
            Box(
                modifier = Modifier
                    .size(BacklogKeyGlyphTileSize)
                    .then(
                        if (isActive) {
                            Modifier
                                .background(ttAccent(), RoundedCornerShape(ttRadius.row))
                                .testTag(BacklogSortKeyGlyphTileTag(basis))
                        } else {
                            Modifier
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                ttIcon(
                    image = sortBasisGlyph(basis),
                    contentDescription = null,
                    tint = if (isActive) ttOnAccent else ttLabelSecondary(),
                    modifier = Modifier.size(ttIconSize.close),
                )
            }
            ttText(
                text = stringResource(sortBasisName(basis)),
                style = ttTypography.callout,
                color = if (isActive) ttLabel() else ttLabelSecondary(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // Position only exists between two keys: with one key on, there is nothing to
            // swap it with and the chip would be a button that does nothing.
            if (isActive && order.keys.size > 1) {
                PositionChip(basis = basis, isLeading = isLeading, onClick = onSwapPosition)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
        ) {
            DirectionOption(
                label = stringResource(sortDirectionName(basis, descending = true)),
                basis = basis,
                descending = true,
                isSelected = key?.descending == true,
                onClick = { onDirection(true) },
                modifier = Modifier.weight(1f),
            )
            DirectionOption(
                label = stringResource(sortDirectionName(basis, descending = false)),
                basis = basis,
                descending = false,
                isSelected = key?.descending == false,
                onClick = { onDirection(false) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** One direction of one key, named in words rather than drawn as an arrow. */
@Composable
private fun DirectionOption(
    label: String,
    basis: BacklogSortBasis,
    descending: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .heightIn(min = ttSpacing.iconButtonSize)
            .ttGlassSurface(
                shape = RoundedCornerShape(ttRadius.control),
                tint = if (isSelected) ttAccent() else null,
            )
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = ttSpacing.sm)
            .testTag(BacklogSortDirectionTag(basis, descending))
            .semantics { selected = isSelected },
        contentAlignment = Alignment.Center,
    ) {
        ttText(
            text = label,
            style = ttTypography.caption,
            color = if (isSelected) ttOnAccent else ttLabelSecondary(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * "First" / "Then": where this key sits, and the control that swaps the pair.
 *
 * A full control, not a coloured word. It was a 20dp-high caption in `ttAccent()` on an
 * untinted glass lens — and an untinted lens takes the accent as its top fill stop, so the
 * ink and its own backdrop were the same hue at roughly half opacity. Under the muted dark
 * accent the word all but disappeared. It now takes the same pairing as a selected
 * direction chip: an opaque accent fill carrying `ttOnAccent` white ink, a pairing the design
 * system holds to 4.5:1 for every palette in both appearances. The swap glyph makes the tap
 * visible; "First"/"Then" alone named a state while the action lived only in the
 * content description.
 *
 * The height and minimum width matter as much as the ink: the two direction chips beside it
 * are 44dp, and a 20dp target in the same row read as a label rather than something to press.
 */
@Composable
private fun PositionChip(
    basis: BacklogSortBasis,
    isLeading: Boolean,
    onClick: () -> Unit,
) {
    val description = stringResource(
        if (isLeading) {
            Res.string.backlog_sort_make_second
        } else {
            Res.string.backlog_sort_make_first
        },
    )
    Box(
        modifier = Modifier
            .heightIn(min = ttSpacing.iconButtonSize)
            .defaultMinSize(minWidth = BacklogPositionChipMinWidth)
            .ttGlassSurface(
                shape = RoundedCornerShape(ttRadius.control),
                tint = ttAccent(),
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = ttSpacing.md)
            .testTag(BacklogSortPositionTag(basis))
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.xs),
        ) {
            ttText(
                text = stringResource(
                    if (isLeading) {
                        Res.string.backlog_sort_position_first
                    } else {
                        Res.string.backlog_sort_position_then
                    },
                ),
                style = ttTypography.captionEmphasised,
                color = ttOnAccent,
                maxLines = 1,
                modifier = Modifier.testTag(BacklogSortPositionLabelTag(basis)),
            )
            ttIcon(
                image = ttActionIcons.swap,
                contentDescription = null,
                tint = ttOnAccent,
                modifier = Modifier.size(ttIconSize.close),
            )
        }
    }
}

/** The words a screen reader hears for [basis]'s current direction, or that it is off. */
@Composable
private fun sortDirectionLabel(basis: BacklogSortBasis, key: BacklogSortKey?): String =
    if (key == null) {
        stringResource(Res.string.backlog_sort_off)
    } else {
        stringResource(sortDirectionName(basis, key.descending))
    }

private fun sortDirectionName(basis: BacklogSortBasis, descending: Boolean): StringResource = when {
    basis == BacklogSortBasis.Priority && descending -> Res.string.backlog_sort_highest_first
    basis == BacklogSortBasis.Priority -> Res.string.backlog_sort_lowest_first
    descending -> Res.string.backlog_sort_newest_first
    else -> Res.string.backlog_sort_oldest_first
}

private fun sortBasisName(basis: BacklogSortBasis): StringResource = when (basis) {
    BacklogSortBasis.Priority -> Res.string.backlog_sort_key_priority
    BacklogSortBasis.CreatedAt -> Res.string.backlog_sort_key_created
}

private fun sortBasisGlyph(basis: BacklogSortBasis): ImageVector = when (basis) {
    BacklogSortBasis.Priority -> ttActionIcons.priority
    BacklogSortBasis.CreatedAt -> ttActionIcons.createdAt
}

/**
 * Turn [basis] on as the second key, or off.
 *
 * Off is refused for the last key: the order always has one, so a checkbox that emptied the
 * selection would leave the list with no ordering at all.
 */
private fun BacklogSortOrder.withKeyToggled(basis: BacklogSortBasis): BacklogSortOrder = when {
    !isActive(basis) -> copy(keys = keys + BacklogSortKey(basis))
    keys.size == 1 -> this
    else -> copy(keys = keys.filterNot { it.basis == basis })
}

/**
 * Set the direction of [basis], turning it on if it was off.
 *
 * The second key keeps its own direction: "highest priority, then oldest added" is a real order,
 * so a direction belongs to a key rather than to the selection.
 */
private fun BacklogSortOrder.withDirection(
    basis: BacklogSortBasis,
    descending: Boolean,
): BacklogSortOrder {
    val existing = keyFor(basis) ?: return copy(keys = keys + BacklogSortKey(basis, descending))
    return copy(
        keys = keys.map { key ->
            if (key.basis == basis) key.copy(descending = descending) else key
        },
    )
}

/** Move an active [basis] to the front, handing the other key the tie-break. */
private fun BacklogSortOrder.withKeyPromoted(basis: BacklogSortBasis): BacklogSortOrder {
    val promoted = keyFor(basis) ?: return this
    return copy(keys = listOf(promoted) + keys.filterNot { it.basis == basis })
}

const val BacklogSortSheetTag = "backlog.sort.sheet"
const val BacklogSortDoneTag = "backlog.sort.done"
const val BacklogSortPriorityTag = "backlog.sort.priority"
const val BacklogSortCreatedTag = "backlog.sort.created"

/** The selectable card for one sort key; the same tags the header's chips used to carry. */
fun BacklogSortRowTag(basis: BacklogSortBasis): String = when (basis) {
    BacklogSortBasis.Priority -> BacklogSortPriorityTag
    BacklogSortBasis.CreatedAt -> BacklogSortCreatedTag
}

fun BacklogSortDirectionTag(basis: BacklogSortBasis, descending: Boolean): String =
    "backlog.sort.direction.${basis.name.lowercase()}.${if (descending) "desc" else "asc"}"

/** The "First"/"Then" chip of one key's row. */
fun BacklogSortPositionTag(basis: BacklogSortBasis): String =
    "backlog.sort.position.${basis.name.lowercase()}"

/** The chip's label, so a rendered-ink test can sample it without guessing where the glyphs are. */
fun BacklogSortPositionLabelTag(basis: BacklogSortBasis): String =
    "backlog.sort.position.label.${basis.name.lowercase()}"

/** Present only while the key is active: the shape that carries on/off besides the tint. */
fun BacklogSortKeyGlyphTileTag(basis: BacklogSortBasis): String =
    "backlog.sort.key.tile.${basis.name.lowercase()}"

/** The position chip shares the direction chips' 44dp height, and is wide enough for word + glyph. */
private val BacklogPositionChipMinWidth = 72.dp

/** The filled tile behind an active key's glyph. 24dp, so a 12dp glyph has a clear rim. */
private val BacklogKeyGlyphTileSize = 24.dp
