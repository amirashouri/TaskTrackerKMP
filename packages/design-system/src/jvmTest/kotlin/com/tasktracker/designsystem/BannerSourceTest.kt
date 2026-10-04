package com.tasktracker.designsystem

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The banner's message is ink, and it is asserted on the *argument* rather than on a ratio.
 *
 * ## Why a contrast test was not enough, and what this replaces
 *
 * `BannerContrastTest` sits beside this one and it is worth saying plainly that it is the
 * weaker of the pair. It computes `ttLabel()` against a computed fill and asks whether the
 * ratio clears 4.5:1 — and that question has an almost-unfalsifiable answer, because
 * `ttLabel()` is black on light and white on dark and a banner's fill is always a pale tint
 * near the canvas. **Black on pale pink passes.** So the ratio stayed green through both of
 * these mutations:
 *
 * - the message colour changed back to `base.copy(alpha = RIM_ALPHA)`, which is the original
 *   defect that put it at 1.37:1;
 * - the fill alpha was raised from 0.10 to 0.22.
 *
 * Neither is a rounding difference in the test. Both are the test measuring a fill it
 * computed rather than the one that was drawn, and holding a constant about a component it
 * does not read.
 *
 * So the assertion that matters is not a ratio at all. It is: **the message is `ttLabel()`**.
 * That is a fact about one argument, it is the fact that was wrong, and it is checkable.
 *
 * ## Why a source scan and not a rendering test
 *
 * Reading the resolved colour back out of a composed `ttText` means rasterising the node,
 * which costs a `thisHostRasterisesText()` skip on the Android host — so the assertion that
 * matters most would be the one silently not running on a third of the platforms. The defect
 * was an argument that was passed; the argument is what has to stay true.
 *
 * JVM-only for the same reason as `ProjectPaletteTest`: this needs a filesystem.
 */
class BannerSourceTest {

    @Test
    fun `the banner's message is ink rather than a tint of its own tone`() {
        val text = bannerSource().readText()

        // Scoped to the `ttText(` calls, not the whole file. A file-wide `color =` sweep also
        // catches the border's `color = rim` and the glyph's `color = base`, which are correct,
        // so the first version of this test was red against the code it was written to
        // protect - a false red that would have trained everyone to ignore it.
        val offenders = mutableListOf<String>()
        val calls = ttTextCalls(text)
        assertTrue(calls.isNotEmpty(), "no ttText call found in Banner.kt - the scan is broken")

        for (call in calls) {
            for (match in Regex("""color\s*=\s*([A-Za-z0-9_.]+)""").findAll(call)) {
                if (match.groupValues[1] != "ttLabel") {
                    offenders += "${match.groupValues[1]}()"
                }
            }
        }

        assertTrue(
            offenders.isEmpty(),
            "ttBanner passes something other than ttLabel() to a text node: " +
                offenders.joinToString(", ") + ".\n\n" +
                "The banner's tone belongs to the fill, the rim and the glyph. The message is " +
                "ink: ttLabel() measures 18.37:1 on the 10% fill in light and 19.53:1 in dark, " +
                "where the rim token measures 1.37:1 and 1.39:1. A tint strong enough to be " +
                "visible as a colour stops being a message.\n" +
                "Note that BannerContrastTest will NOT catch this - it computes its own fill " +
                "rather than reading the drawn one, so it stayed green through this exact " +
                "mutation. See its KDoc.",
        )
    }

    /**
     * The two tones differ, because a notice and a failure are not the same thing.
     *
     * The projects screens were already using their error banner for "3 items archived
     * instead", which is an outcome. Repointing it at the accent tone is the whole reason
     * [ttBannerTone] exists, so it is pinned here: a future edit that collapses the enum's
     * arms would make every notice red again, and nothing else in the package would see it.
     */
    @Test
    fun `the accent tone is not the error tone`() {
        assertTrue(
            AccentPalette.Classic.light != ttFixedColours.errorLight,
            "the banner's accent tone is the same colour as its error tone in light, so a " +
                "notice and a failure are painted identically",
        )
        assertTrue(
            AccentPalette.Classic.dark != ttFixedColours.errorDark,
            "the banner's accent tone is the same colour as its error tone in dark",
        )
    }

    /**
     * The argument list of every `ttText(…)` call, and nothing else.
     *
     * Balanced-paren rather than "up to the next `ttText(`" or "up to the end of the file".
     * Both of those run past the call and into `WarningGlyph`, whose `drawRect(color =
     * knockout)` and `drawArc(color = …)` are *correct* — so both versions of this test were
     * red against the code they were written to protect. A false red is worse than no test:
     * it teaches everyone that this assertion is noise.
     */
    private fun ttTextCalls(text: String): List<String> {
        val calls = mutableListOf<String>()
        var i = 0
        while (true) {
            val at = text.indexOf("ttText(", i)
            if (at < 0) break
            var depth = 0
            var j = at + "ttText".length
            val start = j + 1
            while (j < text.length) {
                when (text[j]) {
                    '(' -> depth++
                    ')' -> {
                        depth--
                        if (depth == 0) {
                            calls += text.substring(start, j)
                            break
                        }
                    }
                }
                j++
            }
            i = if (j > at) j else at + 1
        }
        return calls
    }

    /**
     * `Banner.kt`, located the same way `ProjectPaletteTest` locates its module.
     *
     * The design system is the module being scanned, so this walks up from the test class's own
     * code source looking for `src/commonMain` rather than reaching for a sibling module.
     */
    private fun bannerSource(): File {
        val root = generateSequence(
            javaClass.protectionDomain.codeSource.location.toURI().let(::File),
        ) { it.parentFile }
            .firstOrNull { File(it, "src/commonMain").isDirectory }
            ?: error(
                "Could not find src/commonMain from " +
                    javaClass.protectionDomain.codeSource.location +
                    " - walked up to the filesystem root.",
            )

        val banner = File(
            root,
            "src/commonMain/kotlin/com/tasktracker/designsystem/Banner.kt",
        )
        assertTrue(banner.isFile, "expected the banner at ${banner.path}, which does not exist")
        return banner
    }
}