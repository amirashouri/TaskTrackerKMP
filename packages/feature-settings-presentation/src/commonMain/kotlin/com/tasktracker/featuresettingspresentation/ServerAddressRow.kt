package com.tasktracker.featuresettingspresentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSettingsIcons
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTheme
import com.tasktracker.designsystem.ttTypography

/**
 * The Server row: the address the running app is using, tappable to edit the one the next
 * launch will use.
 *
 * ## A row of its own rather than [SettingsIconRow]
 *
 * [SettingsIconRow] clips a value that is too long and has no ellipsis, which is right for
 * "Online" and wrong for a URL: a truncated address with no ellipsis reads as the whole
 * address, and the one thing a user checking their server needs is to see that something was
 * cut off. Here the value is the flexible half of the row and ellipsizes, so the label stays
 * legible and a long address ends in `…` rather than mid-character.
 *
 * ## The whole row is the button
 *
 * Same tap target and role as [SettingsActionRow], so a row that opens something is reachable
 * and announced the same way whether it is an action or a setting. The row is drawn as a
 * setting rather than as an accent-coloured action because the address it shows is a reading
 * first and an edit second.
 */
@Composable
fun ServerAddressRow(
    label: String,
    address: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(vertical = ttSpacing.xs)
            .clip(RoundedCornerShape(ttRadius.row))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                role = Role.Button,
                onClick = onClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsIconWell(icon = icon, tint = ttAccent())
            ttText(text = label, style = ttTypography.body, color = ttLabel())
        }
        ttText(
            text = address,
            style = ttTypography.subheadline,
            color = ttLabelSecondary(),
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = ttSpacing.md),
        )
    }
}

@Preview
@Composable
private fun ServerAddressRowPreview() {
    ttTheme(ThemeScheme.Dark) {
        SettingsSection {
            ServerAddressRow(
                label = "Server",
                address = "https://tasks.example.com/api",
                icon = ttSettingsIcons.connectivity,
                onClick = {},
            )
        }
    }
}
