package com.tasktracker.featureprojectspresentation

import java.io.File
import kotlin.test.Test
import kotlin.test.fail

/**
 * A rim or fill token is decoration, and cannot be used as a text colour.
 *
 * ## What this catches
 *
 * `ProjectsErrorBanner` drew its message in `ttErrorRim()` — the *rim* token, error at 25%
 * alpha — composited over its own `ttErrorFill()` at 10%, on the canvas. Measured, that put
 * the message at **1.37:1** on the light appearance's fill and **1.39:1** on the dark one:
 * pale pink on pale pink, in both, on the one component whose entire job is to be read.
 *
 * ## Why this is a source scan and not a rendering test
 *
 * The defect is not a rendering difference, it is an *argument that was passed*. Read the
 * rendered pixels back and the message is legible now; read the source and it says
 * `color = ttErrorRim()` still, which is the thing that has to stay true. Every route to
 * reading a Compose text node's resolved colour goes through rasterising the node, and
 * that costs a `thisHostRasterisesText()` skip on the Android host — so the assertion that
 * matters most would be the one silently not running on a third of the platforms.
 *
 * So this checks the call instead. It is deliberately narrow: `color =` only, which is the
 * argument `ttText` takes its ink from. `ttErrorRim()` used as a `.border()` colour is the
 * *correct* use and is not flagged — `ProjectsErrorBanner`'s own rim is right there in the
 * same function.
 *
 * ## Why it lives in `jvmTest` and not `commonTest`
 *
 * It needs a filesystem, and there is no portable way to locate the module's own sources
 * from `commonTest` across three targets. That is a real limitation and it is stated here
 * rather than papered over: this guard runs on the JVM host only. The cross-platform half
 * of the same rule lives in `AccentContrastTest`, which holds the *tokens* to their bars on
 * every host.
 *
 * ## How the root is found
 *
 * From the test class's own code source — `build/classes/kotlin/jvm/test` — walking up
 * until a directory containing `src/commonMain` appears. Not `System.getProperty("user.dir")`,
 * which is the Gradle project directory only by convention, and not a hardcoded relative
 * path, which breaks the moment this runs from an IDE with a different working directory.
 */
class ProjectPaletteTest {

    @Test
    fun `no error rim or fill token is used as a text colour`() {
        val offences = mutableListOf<String>()

        for (source in moduleSources()) {
            val text = source.readText()
            text.lineSequence().forEachIndexed { index, line ->
                // The comment blocks in this module quote the offending call while
                // explaining why it was removed. Only a line that actually passes the
                // token as an argument counts, so the leading `//` is what separates the
                // two.
                if (!line.trimStart().startsWith("//")) {
                    for (token in DECORATION_ONLY_TOKENS) {
                        val needle = "color = $token("
                        if (line.contains(needle)) {
                            offences += "${source.name}:${index + 1}  ${line.trim()}"
                        }
                    }
                }
            }
        }

        if (offences.isNotEmpty()) {
            fail(
                "A decoration-only token is being used as a text colour:\n" +
                    offences.joinToString("\n") { "  $it" } +
                    "\n\n`ttErrorRim()` is error at 25% alpha and `ttErrorFill()` at 10%. " +
                    "Composited over the banner's own fill those measure 1.37:1 and 1.39:1 " +
                    "against it — see `ProjectsErrorBanner`. Both are correct as a " +
                    "`.border()` colour and as a `.background()`, which is why only " +
                    "`color =` is checked.\n" +
                    "For text use `ttLabel()` (18.37:1 on the fill in light, 19.53:1 in " +
                    "dark) and keep the error-ness in the fill, the rim and the glyph.",
            )
        }
    }

    /** Every `.kt` under the module's `commonMain`, which is where the screens live. */
    private fun moduleSources(): List<File> {
        val root = generateSequence(javaClass.protectionDomain.codeSource.location.toURI().let(::File)) {
            it.parentFile
        }.firstOrNull { File(it, "src/commonMain").isDirectory }
            ?: error(
                "Could not find this module's src/commonMain from " +
                    javaClass.protectionDomain.codeSource.location +
                    " - walked up to the filesystem root. The class-source walk assumes " +
                    "the tests run out of build/classes, which they do on the JVM host.",
            )

        return File(root, "src/commonMain/kotlin")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
    }

    private companion object {
        /** Tokens that are documented as decoration and measured far below the text bar. */
        val DECORATION_ONLY_TOKENS = listOf("ttErrorRim", "ttErrorFill")
    }
}