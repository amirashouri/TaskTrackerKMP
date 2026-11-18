package com.tasktracker.designsystem

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * `ttGlassButton`'s pressable region is the pill it draws, pinned as the modifier order that
 * makes it so.
 *
 * ## The defect
 *
 * The control draws a circle-shaped glass surface and then pads its label inside it. With
 * `padding` applied *before* `clickable`, that padding is inert to input: the glass reaches
 * `buttonPaddingH` past each end of the word and the strip either side of the word swallows a
 * mouse press — no press feedback, no action — so the button reads as if it were hit only on its
 * text. Reported twice from use, in the one shared control: the forgot-password sheet's cancel
 * and the work-item editor's floating action row.
 *
 * ## Why this is a source scan and not a click test
 *
 * A coordinate click cannot see this. `performTouchInput` in `runComposeUiTest` resolves the
 * point against **layout-node** bounds, so a press inside the padding still reaches the inner
 * `clickable` modifier and the action fires — measured here on the real `ForgotPasswordSheet` and
 * `WorkItemEditorSheet` compositions, at 1, 3, 6, 10 and 13dp inside the pill's left edge, with
 * touch and mouse input and with a settled press animation. Every one stayed green with the
 * padding back in front of the click, which makes that test worthless rather than merely weak.
 *
 * So the assertion is the fact that was wrong: the order of two modifiers in one chain. It is the
 * same trade `BannerSourceTest` makes for an argument no ratio could falsify.
 *
 * ## The second assertion
 *
 * `ttGlassSurface` must stay *before* the padding for the opposite reason: it is what makes the
 * drawn pill the whole control rather than a surface inside a padded box. Kitchen-sink "move the
 * padding out" edits break the first rule while keeping the second, and an edit that moves the
 * surface inward would keep the pressable region honest and still shrink the glass — both are
 * pinned, so neither can be fixed by breaking the other.
 *
 * JVM-only, because it needs a filesystem.
 */
class GlassButtonHitAreaSourceTest {

    @Test
    fun `the pill's click is applied outside its label padding`() {
        val chain = ttGlassButtonModifierChain()

        val click = chain.indexOf(".clickable(")
        val padding = chain.indexOf(".padding(")
        val surface = chain.indexOf(".ttGlassSurface(")

        assertTrue(
            click >= 0 && padding >= 0 && surface >= 0,
            "the scan found no clickable/padding/surface in ttGlassButton's modifier chain " +
                "(clickable=$click, padding=$padding, surface=$surface) — the scan is broken, " +
                "not the button",
        )
        assertTrue(
            surface < padding,
            "ttGlassButton's padding is applied before its glass surface, so the pill is drawn " +
                "inside a larger padded box instead of being the control. The surface has to " +
                "stay first for the drawn pill to be the thing that is pressed",
        )
        assertTrue(
            click < padding,
            "ttGlassButton applies `padding` before `clickable`, which makes that padding inert " +
                "to input: the glass reaches " +
                "${ttSpacing.buttonPaddingH.value.toInt()}dp past each end of the " +
                "label and a mouse click on that strip never reaches the button — no press " +
                "feedback and no action, so the target reads as the text alone. Put " +
                "`.clickable(...)` before `.padding(...)`; the control's size does not change, " +
                "because padding consumes the same space on either side of it. A coordinate " +
                "click cannot catch this — see this file's KDoc",
        )
    }

    /**
     * The `Box` modifier chain of `ttGlassButton`, from `Box(` to the box's alignment argument.
     *
     * Sliced rather than scanned file-wide: `GlassRoundAction` and the segmented picker in the
     * same file carry their own chains, and those are correct — so a file-wide sweep for
     * `.padding(` before `.clickable(` would be red against code it was not written about, and a
     * false red is how an assertion like this gets ignored and then deleted.
     */
    private fun ttGlassButtonModifierChain(): String {
        val text = glassSource().readText()
        val function = text.indexOf("fun ttGlassButton(")
        assertTrue(function >= 0, "no `fun ttGlassButton(` in Glass.kt - the scan is broken")

        val box = text.indexOf("Box(", function)
        val end = text.indexOf("contentAlignment", box)
        assertTrue(
            box >= 0 && end > box,
            "could not delimit ttGlassButton's Box modifier chain (box=$box, end=$end)",
        )
        return text.substring(box, end)
    }

    /**
     * `Glass.kt`, located the same way `BannerSourceTest` locates `Banner.kt`: the design system
     * is the module being scanned, so this walks up from the test class's own code source until
     * it finds `src/commonMain`.
     */
    private fun glassSource(): File {
        val root = generateSequence(
            javaClass.protectionDomain.codeSource.location.toURI().let(::File),
        ) { it.parentFile }
            .firstOrNull { File(it, "src/commonMain").isDirectory }
            ?: error(
                "Could not find src/commonMain from " +
                    javaClass.protectionDomain.codeSource.location +
                    " - walked up to the filesystem root.",
            )

        val glass = File(
            root,
            "src/commonMain/kotlin/com/tasktracker/designsystem/Glass.kt",
        )
        assertTrue(glass.isFile, "expected Glass.kt at ${glass.path}, which does not exist")
        return glass
    }
}
