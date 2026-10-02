package com.tasktracker.compositionroot

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The rule that makes the graph a graph: **only [AppGraph] builds a container.**
 *
 * ## Why a source scan rather than a test that resolves something
 *
 * The rule is about a *name appearing in a file*, so there is nothing to resolve. A test that
 * resolved every port would prove the registrations are reachable — which [AppGraphTest] and
 * [AuthFlowThroughGraphTest] already do — and would be silent about the actual failure mode:
 * a feature or a shell quietly calling `KoinResolver.build { }` for its own private graph.
 *
 * That failure is invisible otherwise. It compiles, every existing test stays green, and the
 * app ships with two containers: the sign-out path resets the scope of one while the other
 * keeps serving a session nobody has ended. So the check is textual, and it is here rather
 * than in `build-logic` because the rule is about *intent* — a module gaining a second graph
 * is an architectural decision, not a compile error, and the thing to stop it is a failing
 * test someone reads the message of.
 *
 * ## Why `KoinResolver` and `ResolverBuilder` and not `:packages:di` as a dependency
 *
 * `:shared-ui` and `:androidApp` are both *allowed* to depend on `:packages:di` — the shell
 * takes a `Resolver` as a parameter, and that is the whole point of the port. What they are
 * not allowed to do is name the implementation. `Resolver` is the contract; `KoinResolver`
 * is one way of satisfying it, and a caller holding the second has stopped being able to
 * change the first. `ResolverBuilder` is banned for the same reason and one more: it is the
 * only way to *describe* a graph, so a `build { }` call outside [AppGraph] is the assembly
 * this file exists to prevent.
 *
 * ## The scan is whole-repo and build-directory-blind
 *
 * `src` directories only, so generated sources and Gradle's own output are not scanned, and
 * every module is included regardless of what the architecture table happens to allow today —
 * this rule is not in that table and is not derivable from it.
 */
class AppGraphAccessTest {

    /**
     * Modules allowed to name the container implementation.
     *
     * Built through [moduleOf] rather than written out, because a hand-written path here and
     * a derived one in [moduleOf] are two spellings of the same string and only one of them
     * would be correct.
     */
    private val allowedModules = setOf(
        moduleOfRoot("composition-root"),
        moduleOfRoot("packages", "di"),
    )

    /** Directories whose Kotlin sources are scanned, relative to the repository root. */
    private val scannedDirectories = listOf("packages", "composition-root", "shared-ui", "androidApp")

    /** The names that mean "this module is building its own object graph". */
    private val forbiddenNames = listOf("KoinResolver", "ResolverBuilder")

    @Test
    fun `only the composition root and the di module name the container`() {
        val all = scan()
        val violations = all.filterNot { (module, _) -> module in allowedModules }

        assertTrue(
            violations.isEmpty(),
            buildString {
                appendLine("Only :composition-root may describe an object graph, and only :packages:di may define one.")
                appendLine("These files name a container implementation directly:")
                violations.forEach { (module, file) ->
                    appendLine("  - ${file.relativeTo(repositoryRoot).path} ($module) names ${containerNamedIn(file.readText())}")
                }
                appendLine("Ask AppGraph for the type instead, so there is one graph in the process.")
            },
        )
    }

    /**
     * The positive half, so the scan cannot be satisfied by deleting everything.
     *
     * Without this, renaming the container or emptying [AppGraph] would make the rule above
     * pass by having nothing left to police — a test that goes green because the thing it
     * watches disappeared. [AppGraph] must keep building the graph, and the check is that it
     * still names both the implementation and the builder.
     */
    @Test
    fun `the composition root is still the thing that builds the graph`() {
        val graph = File(repositoryRoot, "composition-root/src/commonMain/kotlin/com/tasktracker/compositionroot/AppGraph.kt")
        assertTrue(graph.isFile, "expected ${graph.path} to exist")

        val text = graph.readText()
        forbiddenNames.forEach { name ->
            assertTrue(
                text.contains(name),
                "expected AppGraph.kt to name $name; if the container moved, this scan's " +
                    "allow-list needs updating in the same change",
            )
        }
    }

    /**
     * Every Kotlin file that *mentions* a container implementation, paired with its module.
     *
     * Scanned by content rather than by file name: the name appears in an import and in a
     * call, not in a path, so a name-based scan would find nothing and pass vacuously.
     * Comments count, which is deliberate — a file documenting the ban while naming the type
     * it is describing is a file a reader would take as an example, and the rule is cheaper to
     * keep if the scan does not need a comment carved out of it.
     */
    private fun scan(): List<Pair<String, File>> =
        scannedDirectories
            .map { File(repositoryRoot, it) }
            .filter { it.isDirectory }
            .flatMap { root ->
                root.walkTopDown()
                    .filter { file ->
                        file.isFile &&
                            file.name.endsWith(".kt") &&
                            !file.path.contains("${File.separator}build${File.separator}") &&
                            mentionsAContainer(file)
                    }
                    .map { it to moduleOf(root, it) }
                    .toList()
            }
            .map { (file, module) -> module to file }
            .sortedBy { (_, file) -> file.path }

    /**
     * `:packages/foo` for a file under `packages/foo`, `:shared-ui` for one under it.
     *
     * Derived from the first path segment under the scanned root rather than from a list of
     * module names, so a module added later is policed without anyone remembering to add it
     * here — which is the difference between a rule and an inventory.
     */
    private fun moduleOf(root: File, file: File): String =
        moduleOfRoot(root.name, file.relativeTo(root).invariantSeparatorsPath.substringBefore('/'))

    /**
     * `:packages/foo` for a file under `packages/foo`, `:shared-ui` for one under it.
     *
     * [top] is only used when [directory] is `packages`; otherwise a module *is* its own
     * top-level directory. Both forms are produced here rather than at the call sites so the
     * separator cannot differ between the scan and its allow-list.
     */
    private fun moduleOfRoot(directory: String, top: String = directory): String =
        if (directory == "packages") ":packages/$top" else ":$directory"

    private fun mentionsAContainer(file: File): Boolean {
        val text = file.readText()
        return forbiddenNames.any { name -> Regex("""\b${Regex.escape(name)}\b""").containsMatchIn(text) }
    }

    /** Only used in the failure message, so the reader knows which name to look for. */
    private fun containerNamedIn(text: String): String =
        forbiddenNames.firstOrNull { name -> Regex("""\b${Regex.escape(name)}\b""").containsMatchIn(text) }
            ?: forbiddenNames.joinToString(" or ")

    /**
     * The repository root, from the working directory Gradle gives a JVM test task.
     *
     * `composition-root` is a direct child of the root and a test task runs with its own
     * project directory as the working directory, so the parent *is* the root. Walking up
     * until a `settings.gradle.kts` appears is what makes the lookup independent of where the
     * test file lives, rather than assuming two levels up.
     */
    private val repositoryRoot: File
        get() = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("could not find the repository root above ${System.getProperty("user.dir")}")
}
