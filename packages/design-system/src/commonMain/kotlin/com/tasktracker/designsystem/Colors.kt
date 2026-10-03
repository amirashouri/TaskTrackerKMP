package com.tasktracker.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The app's colour tokens.
 *
 * Semantic colours are the point of this file, and they are preserved from the original
 * `Palette.swift` rather than reimplemented. A literal `Color(0xFFF2F2F7)` looks harmless
 * and is a bug the moment the device is in the other appearance, so the surface colours
 * resolve through the *platform's own* theme on each target and adapt on their own:
 *
 * | token         | iOS                    | Android                                        |
 * |---------------|------------------------|------------------------------------------------|
 * | [ttCanvas]    | `UIColor.systemBackground`   | `?android:attr/colorBackground`           |
 * | [ttSurface]   | `UIColor.secondarySystemBackground` | `?android:attr/colorBackgroundFloating` |
 * | [ttSeparator] | `UIColor.separator`    | `?android:attr/textColorSecondary` at low alpha |
 * | [ttOnAccent]  | white                  | white                                         |
 *
 * There is deliberately no Material3 dependency here. Material3 would replace these
 * adaptive semantic colours with a fixed scheme, which is the exact divergence the port
 * is meant to avoid: the original is *not* a Material app.
 *
 * They are `@Composable` rather than plain vals because the answer depends on the current
 * configuration. A cached static would keep painting the light canvas through a dark-mode
 * switch, because a `val` is read once.
 */
@Composable
expect fun ttCanvas(): Color

/** A card surface behind a field or a row: a step above the page background. */
@Composable
expect fun ttSurface(): Color

/** The unfocused hairline around a field or a divider. */
@Composable
expect fun ttSeparator(): Color

/**
 * The colour a failure is drawn in.
 *
 * Added with the sign-in screen, which is the first thing in the port to need one: the
 * original's `ErrorBanner` reaches for `Color.red` directly, and a presentation module
 * doing the same would put a colour literal in a feature file — the exact bug every other
 * token in this file exists to prevent, and the one that surfaces as a light-mode-only bug
 * the moment somebody runs the app dark.
 *
 * The original's banner also uses the red twice more, at 10% for its fill and 25% for its
 * rim. Those are derived from this token rather than written down, so a banner cannot end
 * up with a fill that belongs to a different red than its icon. See `ttErrorFill` and
 * `ttErrorRim`.
 */
@Composable
expect fun ttError(): Color

/**
 * The fill behind a failure banner, at 10% of [ttError].
 *
 * The original's `Color.red.opacity(0.10)`. A token rather than a literal at the call site
 * because the alpha is the part that has to match the rim and the icon; three copies of it
 * in a banner is three chances for one of them to be a different number.
 */
@Composable
fun ttErrorFill(alpha: Float = 0.10f): Color = ttError().copy(alpha = alpha)

/** The rim around a failure banner, at 25% of [ttError]. The original's `opacity(0.25)`. */
@Composable
fun ttErrorRim(alpha: Float = 0.25f): Color = ttError().copy(alpha = alpha)

/**
 * The colour of a label — the app's "primary text".
 *
 * Added in the design-system phase rather than with the canvas, and the reason is worth
 * stating: the original never declared a text colour at all. It used SwiftUI's
 * `.foregroundStyle(.primary)` and `.secondary`, which resolve to `UIColor.label` and
 * `secondaryLabel`, and assumed every reader knew that. A port that has no label token
 * has three bad options — use `ttCanvas`, which paints white text on the white canvas;
 * use a literal, which is precisely the bug the rest of this file exists to prevent; or
 * import Material3 to get `onSurface`, which re-adds the dependency the file refuses.
 *
 * So the token is added. It is the fourth semantic role the app actually has, and leaving
 * it out would have pushed the mistake into eleven presentation files.
 */
@Composable
expect fun ttLabel(): Color

/**
 * A supporting label: a card's metadata, a screen's subtitle.
 *
 * `secondaryLabel` rather than "a dimmer [ttLabel]", because the platform's answer for what
 * "dimmer" means is derived from the two appearances' text colours and is not a fixed
 * fraction of either. Computing it as `ttLabel().copy(alpha = 0.6f)` would look close and
 * be wrong on both platforms, at every size where the platform's own choice differs.
 */
@Composable
expect fun ttLabelSecondary(): Color

/** Body-text ink over the opaque card surface, by appearance. */
@Composable
expect fun ttBodyLabel(): Color

/**
 * Marks and labels drawn *on top of* the accent fill.
 *
 * Named rather than inlined because the pairing is a constraint, not a preference: the
 * accent is the only fill that carries text, so an accent bright enough to break this
 * pairing also becomes invisible against the dark canvas. See `AGENTS.md`.
 */
val ttOnAccent: Color = Color.White
