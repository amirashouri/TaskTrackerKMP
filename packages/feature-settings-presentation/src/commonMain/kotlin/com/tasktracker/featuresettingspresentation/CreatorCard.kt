package com.tasktracker.featuresettingspresentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttGlassSurface
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttOnAccent
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSettingsIconSize
import com.tasktracker.designsystem.ttSettingsIcons
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.resources.Res
import com.tasktracker.resources.settings_creator_body
import com.tasktracker.resources.settings_creator_email_description
import com.tasktracker.resources.settings_creator_email_label
import com.tasktracker.resources.settings_creator_email_value
import com.tasktracker.resources.settings_creator_linkedin_description
import com.tasktracker.resources.settings_creator_linkedin_label
import com.tasktracker.resources.settings_creator_linkedin_value
import com.tasktracker.resources.settings_creator_name
import com.tasktracker.resources.settings_creator_pill
import org.jetbrains.compose.resources.stringResource

/** The contact URIs come from the README badges; a tap opens a mail draft or a profile. */
private const val CreatorEmailUri = "mailto:amirashouri72@gmail.com"
private const val CreatorLinkedInUri = "https://www.linkedin.com/in/amirreza-ashouri-229019156"

const val CreatorCardTag = "settings.creator.card"
const val CreatorPillTag = "settings.creator.pill"
const val CreatorEmailRowTag = "settings.creator.email"
const val CreatorLinkedInRowTag = "settings.creator.linkedin"

/**
 * The card that closes Settings: who built the app and the two ways to reach them.
 *
 * [LocalUriHandler] is Compose's own seam for opening a URI — it has platform actuals for
 * Android, iOS and Desktop, so this stays common code, and tests can replace it with a
 * recording handler to prove what a tap would open without touching a browser or a mail app.
 */
@Composable
fun CreatorCard(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current

    Box(modifier = modifier.testTag(CreatorCardTag)) {
        SettingsSection {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SettingsIconWell(icon = ttSettingsIcons.account, tint = ttAccent())
                    ttText(
                        text = stringResource(Res.string.settings_creator_name),
                        style = ttTypography.headline,
                        color = ttLabel(),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // The capsule sits in the card's top-right corner and carries ink, so it takes
                // the opaque accent fill that ttOnAccent is contrast-tested against — accent
                // text on untinted glass disappears into the lens's own accent top stop.
                Box(
                    modifier = Modifier
                        .ttGlassSurface(
                            shape = RoundedCornerShape(percent = 50),
                            tint = ttAccent(),
                        )
                        .padding(horizontal = ttSpacing.md, vertical = ttSpacing.xxs)
                        .testTag(CreatorPillTag),
                    contentAlignment = Alignment.Center,
                ) {
                    ttText(
                        text = stringResource(Res.string.settings_creator_pill),
                        style = ttTypography.captionEmphasised,
                        color = ttOnAccent,
                        maxLines = 1,
                    )
                }
            }
            ttText(
                text = stringResource(Res.string.settings_creator_body),
                style = ttTypography.footnote,
                color = ttLabelSecondary(),
                // Line up with the identity block, past the icon well and its gap.
                modifier = Modifier.padding(
                    start = ttSettingsIconSize.tile + ttSpacing.md,
                    top = ttSpacing.md,
                ),
            )
            SettingsDivider()
            CreatorContactRow(
                label = stringResource(Res.string.settings_creator_email_label),
                value = stringResource(Res.string.settings_creator_email_value),
                description = stringResource(Res.string.settings_creator_email_description),
                icon = ttSettingsIcons.email,
                tint = ttAccent(),
                tag = CreatorEmailRowTag,
                onClick = { uriHandler.openUri(CreatorEmailUri) },
            )
            SettingsDivider()
            CreatorContactRow(
                label = stringResource(Res.string.settings_creator_linkedin_label),
                value = stringResource(Res.string.settings_creator_linkedin_value),
                description = stringResource(Res.string.settings_creator_linkedin_description),
                icon = ttSettingsIcons.linkedin,
                tint = ttAccent(),
                tag = CreatorLinkedInRowTag,
                onClick = { uriHandler.openUri(CreatorLinkedInUri) },
            )
        }
    }
}

@Composable
private fun CreatorContactRow(
    label: String,
    value: String,
    description: String,
    icon: ImageVector,
    tint: Color,
    tag: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(ttRadius.row))
            .clickable(
                enabled = true,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                role = Role.Button,
                onClick = onClick,
            )
            .testTag(tag)
            .semantics(mergeDescendants = true) { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIconWell(icon = icon, tint = tint)
        Column(modifier = Modifier.weight(1f)) {
            ttText(text = label, style = ttTypography.body, color = ttLabel())
            ttText(
                text = value,
                style = ttTypography.footnote,
                color = ttLabelSecondary(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
