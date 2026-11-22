package com.tasktracker.sharedui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tasktracker.designsystem.LocalReducedMotion
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttCountBadge
import com.tasktracker.designsystem.ttGlassIconButton
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.routing.AppTab

/** The compact bar's synthetic fifth cell. Also the test tag for its speed-dial anchor. */
internal const val MoreTabTag = "shell.tab.more"

/**
 * The two destinations that used to sit on the compact bar, now reached from More.
 *
 * The old menu was a glass rectangle with a row per destination: icon, caption and, for Inbox,
 * a count. It read as a small settings panel rather than as an extension of the bar, and the
 * captions were what made it bulky. This is the same two actions as circular glass buttons in a
 * vertical speed dial, with the bottom button centred on the More cell it grows out of.
 *
 * The captions move into each button's `contentDescription`, so a screen reader still hears
 * "Settings" and "Inbox" while the rendered menu is icon-only.
 */
@Composable
internal fun MoreActionMenu(
    visible: Boolean,
    selected: AppTab,
    badgeCount: Int,
    cellCount: Int,
    anchorCentreX: Float?,
    onSelect: (AppTab) -> Unit,
) {
    val reduceMotion = LocalReducedMotion.current
    val density = LocalDensity.current
    var menuRightX by remember { mutableStateOf<Float?>(null) }
    val enter: EnterTransition = if (reduceMotion) {
        EnterTransition.None
    } else {
        fadeIn(animationSpec = tween(120)) +
            scaleIn(
                animationSpec = spring(dampingRatio = 0.78f, stiffness = 450f),
                transformOrigin = TransformOrigin(0.5f, 1f),
            ) +
            slideInVertically(
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 420f),
                initialOffsetY = { it / 3 },
            )
    }
    val exit: ExitTransition = if (reduceMotion) {
        ExitTransition.None
    } else {
        fadeOut(animationSpec = tween(100)) +
            scaleOut(
                animationSpec = tween(100),
                targetScale = 0.8f,
                transformOrigin = TransformOrigin(0.5f, 1f),
            ) +
            slideOutVertically(
                animationSpec = tween(100),
                targetOffsetY = { it / 3 },
            )
    }

    BoxWithConstraints(
        modifier = Modifier.onGloballyPositioned {
            menuRightX = it.boundsInRoot().right
        },
    ) {
        // Prefer the measured centre of the actual More cell. A formula from the bar's width is
        // only a first frame: TTGlassTabBar insets its row by a hairline and the host rounds
        // cell widths, which put a computed dial 2-3dp to the trailing side on the JVM host.
        // The cell reports its own root-space centre; this box reports its own right edge.
        val anchor = anchorCentreX
        val right = menuRightX
        val measuredInset = if (anchor != null && right != null) {
            with(density) {
                (right - anchor - ttSpacing.iconButtonSize.toPx() / 2).toDp()
            }
        } else {
            // The first frame before either position callback has landed. The bar is inset from
            // the screen by ttSpacing.tabBarEdgeInset, so this box spans exactly the bar's width.
            val segmentWidth = (maxWidth - ttSpacing.hairlineInset * 2) /
                cellCount.coerceAtLeast(1)
            segmentWidth / 2 - ttSpacing.iconButtonSize / 2
        }
        val trailingInset: Dp = measuredInset.coerceAtLeast(0.dp)

        AnimatedVisibility(
            visible = visible,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = trailingInset),
            enter = enter,
            exit = exit,
        ) {
            Column(
                modifier = Modifier.padding(bottom = 64.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
            ) {
                MoreActionButton(
                    tab = AppTab.Settings,
                    selected = selected == AppTab.Settings,
                    badgeCount = 0,
                    onSelect = onSelect,
                )
                MoreActionButton(
                    tab = AppTab.Notifications,
                    selected = selected == AppTab.Notifications,
                    badgeCount = badgeCount,
                    onSelect = onSelect,
                )
            }
        }
    }
}

@Composable
private fun MoreActionButton(
    tab: AppTab,
    selected: Boolean,
    badgeCount: Int,
    onSelect: (AppTab) -> Unit,
) {
    Box {
        // Tag and clickable stay on one node: a parent tag would report a hit area that is not
        // the button's own, which is exactly the measurement mistake the shell's other controls
        // already guard against.
        ttGlassIconButton(
            image = tabIcon(tab),
            contentDescription = tabTitle(tab),
            tint = if (selected) ttAccent() else ttLabel(),
            onClick = { onSelect(tab) },
            modifier = Modifier.testTag(TabButtonTag(tab)),
        )
        if (badgeCount > 0) {
            ttCountBadge(
                count = badgeCount,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
    }
}
