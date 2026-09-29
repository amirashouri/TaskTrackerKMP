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
 * Marks and labels drawn *on top of* the accent fill.
 *
 * Named rather than inlined because the pairing is a constraint, not a preference: the
 * accent is the only fill that carries text, so an accent bright enough to break this
 * pairing also becomes invisible against the dark canvas. See `AGENTS.md`.
 */
val ttOnAccent: Color = Color.White
