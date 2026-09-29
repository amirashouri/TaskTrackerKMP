package com.tasktracker.architecture

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/**
 * Fails the build when the declared module graph is not the architecture.
 *
 * A real task class rather than a `doLast` closure: a closure in a precompiled script
 * plugin captures the script instance, which the configuration cache refuses to serialize,
 * so the build would lose its configuration cache the moment this check was added. The
 * inputs are plain immutable data, which the cache handles without complaint.
 */
abstract class VerifyArchitectureGraphTask : DefaultTask() {

    /** Module path to the project paths it declares a dependency on, read from the model. */
    @get:Input
    abstract val declaredGraph: MapProperty<String, Set<String>>

    /** Module path to the project paths it is allowed to depend on. */
    @get:Input
    abstract val allowed: MapProperty<String, Set<String>>

    @TaskAction
    fun verify() {
        val declared = declaredGraph.get()
        val allowedGraph = allowed.get()
        val violations = mutableListOf<String>()

        for ((module, allowedFor) in allowedGraph) {
            val declaredFor = declared[module].orEmpty()
            for (extra in declaredFor - allowedFor) {
                violations += "$module must not depend on $extra"
            }
            for (missing in allowedFor - declaredFor) {
                violations += "$module is missing its declared dependency on $missing"
            }
        }

        // A module missing from the table would be entirely unchecked, which is the
        // failure mode this task exists to prevent, so it is itself a violation.
        for (module in declared.keys - allowedGraph.keys) {
            violations += "$module is not in the architecture table, so its dependencies " +
                "are unchecked: ${declared.getValue(module).sorted()}"
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Module graph violates the architecture:")
                    violations.sorted().forEach { appendLine("  - $it") }
                    appendLine()
                    append("See build-logic/convention/src/main/kotlin/")
                    append("tasktracker.architecture.gradle.kts for the rules and why each exists.")
                }
            )
        }

        logger.lifecycle("Module graph OK: ${allowedGraph.size} modules, all edges allowed.")
    }
}
