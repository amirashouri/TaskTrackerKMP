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
 * ## Two classes, not three, and the middle one is rejected rather than overlooked
 *
 * Material's split puts a medium class at 600dp, and 600dp is where a side rail starts to fit.
 * It is left out deliberately. Between 600 and [ShellExpandedMinimumWidth] a rail plus a single
 * pane costs horizontal room and gains nothing, because the second pane that would spend that
 * room is the thing this class is gating. A third class would also mean "which chrome" and "how
 * many panes" became two answers to one question — the exact shape [RootScreen] rejects for the
 * session root, where two answers is how a sign-out is applied to the tree and not to the
 * navigation.
 *
 * ## Why the threshold is 840dp
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
    ;

    companion object {
        /**
         * The class a width resolves to.
         *
         * `>=` rather than `>`, so [ShellExpandedMinimumWidth] is the *first* expanded width
         * rather than the last compact one. The boundary is asserted from both sides, including
         * a fractional width below it, because `>=` and `>` differ by exactly one value and a
         * test that only ever tries round numbers cannot tell them apart.
         *
         * `Dp.Infinity` answers [Expanded], and it is not a defensive branch. A
         * `BoxWithConstraints` inside a scrolling or otherwise unbounded parent genuinely
         * reports an infinite `maxWidth`, and `Infinity >= 840.dp` is already true — so the
         * branch is really "do not special-case infinity into the compact layout", which is
         * what an `isFinite` guard added later would do by accident.
         */
        fun forWidth(width: Dp): ShellLayoutClass =
            if (width >= ShellExpandedMinimumWidth) Expanded else Compact
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
