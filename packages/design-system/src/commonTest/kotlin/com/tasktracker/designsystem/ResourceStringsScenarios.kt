package com.tasktracker.designsystem

import com.tasktracker.resources.Res
import com.tasktracker.resources.app_name
import com.tasktracker.resources.settings_title
import com.tasktracker.resources.status_in_review
import kotlin.test.assertEquals
import org.jetbrains.compose.resources.getString

/**
 * That the string table is actually packaged for each host, and decodes to what it says.
 *
 * This is the assertion `PLAN.md` 4.7 asks for, and it did not exist before: the shell
 * scenarios render `ttText(text = stringResource(...))` and pass, but they only fail on a
 * missing bundle *incidentally* — nothing in the suite ever named an expected string, so a host
 * that resolved every resource to a placeholder, or to the wrong one, stayed green.
 *
 * Asserted through `getString` rather than by rendering. The property is "the resource layer
 * resolves on this target", which is readable without a frame, and a rendered assertion would
 * additionally be at the mercy of the Android host's viewport for no gain. Rendering is
 * exercised by the shells; this is the layer underneath them.
 *
 * ## Why the body is here and not in a test class
 *
 * The Android host cannot run this as a plain JVM test. Its `android.jar` is a stub whose
 * `android.content.res.Resources.getSystem()` throws *"not mocked"*, so the test dies before it
 * asserts anything — measured, not assumed: it failed with exactly that `RuntimeException`
 * when this was a single `commonTest` class. Under `RobolectricTestRunner` the framework is
 * there and the resource packaging `tasktracker.kmp.compose.gradle.kts` performs is what makes
 * the bundle readable.
 *
 * Reaching a runner means a JVM-only annotation, which cannot sit in `commonTest` alongside the
 * iOS target. So the body lives here and each host contributes an entry point — the same split
 * `ShellScenarios` uses on `:shared-ui` for the same reason.
 *
 * Three values rather than one, and they are chosen to cover the three shapes the resource file
 * contains: a `translatable="false"` identity string, an ordinary label, and a status word that
 * is also a wire value's display name. A single assertion would still pass if the generated
 * accessors were wired to the wrong resources.
 */
object ResourceStringsScenarios {

    val theGeneratedAccessorsResolveToTheDeclaredText: suspend () -> Unit = {
        assertEquals(
            "TaskTracker",
            getString(Res.string.app_name),
            "app_name is declared translatable=false and must still resolve",
        )
        assertEquals(
            "Settings",
            getString(Res.string.settings_title),
            "the Settings label must come from composeResources, not a raw fallback",
        )
        assertEquals(
            "In Review",
            getString(Res.string.status_in_review),
            "a status label is read on the board and in the editor, so it has one resource value",
        )
    }
}
