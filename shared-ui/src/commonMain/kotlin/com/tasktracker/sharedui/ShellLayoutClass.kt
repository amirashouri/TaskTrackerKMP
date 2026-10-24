package com.tasktracker.sharedui

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How much room the shell has, reduced to the one question its chrome asks.
 *
 * ## The answer is a width, never a platform
 *
 * The tempting source for this is [com.tasktracker.core.isDesktopApp], which already exists and
 * already answers "am I the Desktop app". It is the wrong question, and the failure is not
 * hypothetical: a Desktop window dragged to 700dp would be handed the two-pane layout that
 * `Expanded` unlocks and would not have the room for it, and a tablet at 1024dp would be handed
 * the phone layout for the same reason in reverse. `isDesktopApp` says which *process* this is;
 * what the chrome needs to know is how many *points* it was offered.
 *
 * So this is derived from the width, and every host produces the same answer for the same
 * window. Nothing anywhere branches on the platform to decide a layout.
 *
 * ## Three classes, and the third is not Material's medium class
 *
 * Material's split puts a medium class at 600dp, and 600dp is where a side rail starts to fit.
 * It is still left out deliberately. Between 600 and [ShellExpandedMinimumWidth] a rail plus a
 * single pane costs horizontal room and gains nothing, because the second pane that would spend
 * that room is the thing this class is gating. A third class at 600 would also mean "which
 * chrome" and "how many panes" became two answers to one question — the exact shape
 * [RootScreen] rejects for the session root, where two answers is how a sign-out is applied to
 * the tree and not to the navigation.
 *
 * [Sidebar] is the *other* end of the ladder, and it is a different argument. At
 * [ShellSidebarMinimumWidth] there is room for a 280dp panel beside a second pane that is still
 * as wide as the narrowest genuine list-detail pair, so the panel can carry labels and a project
 * switcher instead of five glyphs. It is not a "medium" step between phone and tablet; it is the
 * same wide layout with more room to spend, which is why it changes only the *leading chrome* and
 * nothing about how many panes exist. See [isWide].
 *
 * ## Why the first threshold is 840dp
 *
 * It is the width at which the narrowest genuine list-detail pair in this app fits side by side
 * without either pane going below a readable measure: a rail, a list at its natural width, and a
 * detail pane that still has room for a board's columns. It is pinned by an exact-value test in
 * `ShellLayoutClassTest`, so moving it is a deliberate edit rather than a silent drift.
 *
 * This class does not know *what* each case means. Which chrome is drawn, and whether a second
 * pane exists, are the shell's decisions and live in `NavigationHost`; keeping the vocabulary
 * here free of them is what lets a test assert the boundary without standing up a shell.
 */
enum class ShellLayoutClass {
    /** A phone, or a desktop window narrowed to one. Today's shell, unchanged. */
    Compact,

    /** Room for a side rail and a second pane. */
    Expanded,

    /** Room for a labelled sidebar and a second pane. See [ShellSidebarMinimumWidth]. */
    Sidebar,
    ;

    /**
     * Whether this class draws leading chrome — a rail or a sidebar — rather than a bottom bar.
     *
     * ## A property because the alternative is nine comparisons that can each be forgotten
     *
     * Before this existed, every consumer asked `layoutClass == ShellLayoutClass.Expanded`.
     * Adding a class made each of those a silent bug **only at widths no scenario used**: the
     * editor's exit, the sheet's presentation, the Sprints two-pane arrangement and the leading
     * inset all key off this question. A comparison answers "is this exactly the class I was
     * written against"; the consumers were always asking "is this a wide shell". Writing the
     * question down once is what lets a future class be added in one place.
     *
     * `!= Compact` rather than `== Expanded || == Sidebar`, so a new class is assumed wide until
     * somebody deliberately classifies it. Every class at or above [ShellExpandedMinimumWidth] is
     * wide by construction, and [Compact] is the only narrow one.
     */
    val isWide: Boolean get() = this != Compact

    companion object {
        /**
         * The class a width resolves to.
         *
         * `>=` rather than `>`, so each threshold is the *first* width in its class rather than
         * the last width of the previous one. The boundaries are asserted from both sides,
         * including a fractional width below each, because `>=` and `>` differ by exactly one
         * value and a test that only ever tries round numbers cannot tell them apart.
         *
         * `Dp.Infinity` answers [Sidebar], and it is not a defensive branch. A
         * `BoxWithConstraints` inside a scrolling or otherwise unbounded parent genuinely
         * reports an infinite `maxWidth`, and `Infinity >= …` is already true — so the branch is
         * really "do not special-case infinity into the compact layout", which is what an
         * `isFinite` guard added later would do by accident.
         */
        fun forWidth(width: Dp): ShellLayoutClass = when {
            width >= ShellSidebarMinimumWidth -> Sidebar
            width >= ShellExpandedMinimumWidth -> Expanded
            else -> Compact
        }
    }
}

/**
 * The narrowest width that gets the wide layout.
 *
 * A top-level `val` rather than a companion member or a design-system token. It is not a visual
 * token: nothing is painted from it and no screen reads it. It is one number that the shell and
 * its tests have to agree on, and the `TabBar` precedent is the same call — the tab bar's
 * geometry was moved back out of the design system once it became clear that the split bought
 * nothing, because there is only ever going to be one shell.
 */
val ShellExpandedMinimumWidth: Dp = 840.dp

/**
 * The width of the wide shell's sidebar.
 *
 * 280dp, because that is the narrowest panel that can show a project name beside its glyph
 * without truncating the seeded names at [com.tasktracker.designsystem.ttTypography]'s body
 * size. It is a *shell* number rather than a design-system token for the same reason
 * [ShellExpandedMinimumWidth] is one: nothing is painted from it, no screen reads it, and there
 * is only ever going to be one shell. The design system owns the panel's surface and spacing;
 * the caller owns how much room the panel takes, exactly as the rail's width is owned by its
 * captions rather than by a token.
 */
val ShellSidebarWidth: Dp = 280.dp

/**
 * The narrowest width that gets the sidebar instead of the rail.
 *
 * The expression is the rule: a 280dp sidebar must leave at least [ShellExpandedMinimumWidth] of
 * content, or the second pane it sits beside stops being the list-detail pair the 840dp
 * threshold was chosen for. Written as arithmetic rather than as `1120.dp` so the two decisions
 * cannot drift apart when either input moves; the boundary itself is pinned in
 * `ShellLayoutClassTest`, where a change shows up as a deliberate edit.
 */
val ShellSidebarMinimumWidth: Dp = ShellExpandedMinimumWidth + ShellSidebarWidth

/**
 * The class the shell is currently laid out at, for anything below it that needs to know.
 *
 * ## A local because the reader that needs it most cannot be handed a parameter
 *
 * The obvious alternative is a parameter — and it is what `NavigationHost` gets, since it is
 * called one level down and already takes `themeScheme` explicitly. That works for a reader in
 * `:shared-ui` and fails for the one that matters: `TTBottomSheetScaffold` lives in
 * `:packages:design-system`, which sits **below** `:shared-ui` in the architecture and therefore
 * cannot be handed a shell value at all — `verifyArchitectureGraph` pins that direction. A modal
 * that has to know whether to anchor at the bottom edge or centre itself is a design-system
 * component, so a local is not a convenience here; it is the only mechanism that reaches it.
 *
 * This is the second instance of the pattern, not a new one:
 * [com.tasktracker.designsystem.LocalTabBarScrollUnderInset] is a shell-provided,
 * defaulted local read by the same component for the same kind of reason.
 *
 * ## The default is the load-bearing half
 *
 * [ShellLayoutClass.Compact]. A screen rendered outside the signed-in shell — a preview,
 * `MissingProjectEnvironmentView`, the sign-in tree — reads `Compact` and lays out exactly as it
 * did before any of this existed, so every preview in the repo keeps working without naming a
 * layout class it has no opinion about. That is the same reasoning
 * [com.tasktracker.designsystem.LocalTabBarScrollUnderInset] gives for defaulting to `0.dp`.
 *
 * ## Provided in 5.1, read in 5.3 and 5.5
 *
 * Nothing consumes this yet, and that is the intended shape of the task rather than an
 * oversight: `ShellLayout` derives a class, and publishing it *is* what the derivation is for —
 * a composable that computes a value and discards it delivers nothing. The consumers are the
 * chrome branch and the modal presentation, and both land on top of this seam.
 */
val LocalShellLayoutClass: ProvidableCompositionLocal<ShellLayoutClass> =
    compositionLocalOf { ShellLayoutClass.Compact }
