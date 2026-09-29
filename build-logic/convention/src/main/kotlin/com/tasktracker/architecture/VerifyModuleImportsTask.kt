package com.tasktracker.architecture

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.TaskAction

/**
 * Fails when a module's sources import a package owned by a module it may not depend on.
 *
 * This is the second of the two architecture checks, and it covers the route the graph
 * check cannot see: a package that becomes reachable through an *intermediate* module
 * rather than through a direct edge. The graph stays legal, the leak is not.
 */
abstract class VerifyModuleImportsTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    /** Module path to the set of modules this one is permitted to see. */
    @get:Input
    abstract val allowed: SetProperty<String>

    /** Kotlin package prefix to the module that owns it. */
    @get:Input
    abstract val packageOwners: MapProperty<String, String>

    /**
     * The module's own path, and its directory, passed in as values.
     *
     * Not read from `project` at execution time: the configuration cache rejects that, and
     * a check that only works on the first run and then poisons the cache is a check nobody
     * keeps.
     */
    @get:Input
    abstract val modulePath: Property<String>

    @get:Internal
    abstract val moduleDirectory: DirectoryProperty

    @TaskAction
    fun verify() {
        val allowedModules = allowed.get()
        val owners = packageOwners.get()
        // Longest prefix first, so com.tasktracker.featureprojectsdata is matched before
        // a shorter prefix that is a prefix of it.
        val prefixes = owners.entries.sortedByDescending { it.key.length }
        val importPattern = Regex("""^\s*import\s+([A-Za-z0-9_.]+)""", RegexOption.MULTILINE)

        val violations = mutableListOf<String>()

        sources.asFileTree.matching { include("**/*.kt") }.files.sorted().forEach { file ->
            val imported = importPattern.findAll(file.readText()).map { it.groupValues[1] }
            val modulesTouched = imported
                .mapNotNull { fqcn -> prefixes.firstOrNull { (pkg, _) -> fqcn.startsWith("$pkg.") } }
                .map { it.value }
                .toSet()

            for (module in modulesTouched - allowedModules) {
                violations += "${file.relativeTo(moduleDirectory.get().asFile)} imports a type " +
                    "owned by $module, which ${modulePath.get()} may not depend on"
            }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("${modulePath.get()} imports across a module boundary:")
                    violations.sorted().forEach { appendLine("  - $it") }
                }
            )
        }
    }
}
