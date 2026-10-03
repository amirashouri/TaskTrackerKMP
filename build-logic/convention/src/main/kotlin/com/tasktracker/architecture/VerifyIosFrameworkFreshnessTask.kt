package com.tasktracker.architecture

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Fails when the linked iOS framework is older than the Kotlin sources it is supposed to contain.
 *
 * ## Why this exists
 *
 * `iosApp/project.yml` once skipped the framework link when Android Studio launched the build,
 * because a comment asserted that the IDE run configuration linked the framework first. It did
 * not: `.idea/runConfigurations/TaskTracker.xml` declared no Gradle before-run task. The two
 * build systems each believed the other was doing the work, and the app shipped against a
 * framework binary that predated its own sources.
 *
 * That failure is invisible by construction. A stale link is a *successful* link — Xcode finds
 * a framework at the path it expects, `import ComposeApp` resolves, and the app launches. The
 * only symptom is behaviour that contradicts the source you are reading, which reads as a bug
 * in the code rather than as a build that did not happen. Deleting the app and regenerating the
 * Xcode project cannot detect it, because both act downstream of the artifact that is stale.
 *
 * ## Why mtime and not a content check
 *
 * The obvious alternative — grep the binary for a symbol that only exists in current sources —
 * was the check that *found* the original defect, and it is not usable as a permanent guard.
 * Kotlin/Native stores string literals in a table that `strings` does not surface: three
 * `ShellPreferences` keys, two of them years old, all read as absent. Any guard anchored to a
 * literal is anchored to a storage detail of one compiler version.
 *
 * Timestamps are compiler-agnostic and need no knowledge of what the code looks like. The check
 * is deliberately coarse — "the binary is not older than the newest input" — because a coarse
 * check that never fires is worth more here than a precise one that false-alarms on every
 * incremental build. It cannot detect an edit made inside the same second as the last link,
 * which is a real limitation and not a hypothetical one; the mitigation is `linkDebugFramework`,
 * which Gradle re-runs on any input it considers changed.
 *
 * ## What it deliberately does not do
 *
 * It does not build the framework. A check that has to run the thing it is checking cannot fail
 * usefully, and this task is wired to run *after* the link, so by the time it executes a stale
 * binary means the link was skipped — which is exactly the case worth failing on.
 */
abstract class VerifyIosFrameworkFreshnessTask : DefaultTask() {

    /**
     * The linked `ComposeApp` binary, or more precisely the framework's main file.
     *
     * `@Internal` rather than `@InputFile`, because `@InputFile` makes Gradle validate presence
     * before the action runs, and it rejected a *missing* framework with a message that names a
     * path and no way forward — "Input file does not exist ... which doesn't exist" — where the
     * useful half of the answer is which task produces it. `@Optional` does not help: on a
     * `RegularFileProperty` the presence check still applies. Deferring the check to the action
     * lets one message cover both the missing case and the stale case, and both name
     * `:shared-ui:linkDebugFrameworkIosSimulatorArm64`.
     *
     * The cost of `@Internal` is that a relink no longer re-triggers this check on its own, so
     * it is never up-to-date-skipped on a changed binary. That is the right trade for a task
     * whose whole job is to compare two timestamps: it takes milliseconds, and an
     * up-to-date-skip is exactly the failure mode this exists to prevent.
     */
    @get:Internal
    abstract val frameworkBinary: RegularFileProperty

    /**
     * The Kotlin sources the framework is built from.
     *
     * Passed in as a collection rather than derived from a module so this stays a plain task
     * that can be pointed at any binary; the module and target that own it are set at
     * registration.
     */
    /**
     * The Kotlin source root to compare against.
     *
     * A `DirectoryProperty` rather than a `String` path precisely so the task never has to reach
     * for `project` at execution time: the configuration cache rejects that, and the sibling
     * architecture checks pass their directories in as values for the same reason.
     */
    @get:Internal
    abstract val sourceRoot: DirectoryProperty

    /** The linked target's name, for the failure message. */
    @get:Input
    abstract val targetName: Property<String>

    @TaskAction
    fun verify() {
        val binary = frameworkBinary.get().asFile
        val name = targetName.get()

        if (!binary.isFile) {
            throw GradleException(
                "No linked $name framework at ${binary.absolutePath}. Run " +
                    ":shared-ui:linkDebugFramework${name.replaceFirstChar { it.uppercase() }} " +
                    "before verifying, or launch through Xcode so iosApp/project.yml's " +
                    "\"Build ComposeApp\" phase runs it."
            )
        }

        val sourceRoot = sourceRoot.get().asFile
        if (!sourceRoot.isDirectory) {
            throw GradleException("No source tree at ${sourceRoot.absolutePath} to compare against.")
        }

        val newest = newestSource(sourceRoot)
        val built = binary.lastModified()

        if (built < newest.lastModified()) {
            val ageMinutes = (newest.lastModified() - built) / 60_000
            throw GradleException(
                buildString {
                    appendLine(
                        "The linked $name framework is older than the Kotlin it should contain."
                    )
                    appendLine("  framework: ${binary.absolutePath} (built ${built.asClock()})")
                    appendLine("  newest source: ${newest.relativeTo(sourceRoot)} " +
                        "(${newest.lastModified().asClock()}, $ageMinutes min newer)")
                    appendLine(
                        "  The link did not run. This is what a stale framework looks like: " +
                            "the build succeeds, the app launches, and behaviour contradicts " +
                            "the source you are reading."
                    )
                    append(
                        "  Fix: run :shared-ui:linkDebugFramework${name.replaceFirstChar { it.uppercase() }}, " +
                            "or launch from Xcode/IntelliJ so iosApp/project.yml's " +
                            "\"Build ComposeApp\" phase runs it."
                    )
                }
            )
        }

        logger.lifecycle("Linked $name framework (${built.asClock()}) is not older than its sources.")
    }

    private fun newestSource(root: File): File =
        root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .maxByOrNull { it.lastModified() }
            ?: error("No .kt file under ${root.absolutePath}; cannot verify framework freshness.")

    /** Seconds-resolution clock, because a sub-second mtime in a failure message is noise. */
    private fun Long.asClock(): String =
        java.time.Instant.ofEpochMilli(this).atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
}