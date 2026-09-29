import com.tasktracker.architecture.VerifyArchitectureGraphTask
import com.tasktracker.architecture.VerifyModuleImportsTask
import org.gradle.api.artifacts.ProjectDependency

/**
 * Makes the module graph a checked invariant rather than a convention.
 *
 * The original enforces its architecture with SwiftPM target boundaries: a target simply
 * has no dependency edge, so a presentation target *cannot* see a data target. Gradle has
 * no equivalent — any module can declare any other as a dependency, and a build file that
 * quietly adds the edge compiles happily. So the rule has to be checked somewhere, and
 * this plugin is that somewhere.
 *
 * Two checks, because they catch different mistakes:
 *
 *  - [verifyArchitectureGraph] compares every project's declared project dependencies
 *    against [ALLOWED_DEPENDENCIES]. It catches a new edge at the point it is added, in
 *    the file that added it.
 *  - [verifyModuleImports] scans every module's Kotlin sources for an `import` that resolves
 *    to a module the module may not depend on. It catches the leak that arrives by
 *    another route — a dependency added to a module *between* this one and the target,
 *    then re-exported.
 *
 * Both fail the build with the offending edge named. A checker that says "architecture
 * violated" is one nobody trusts after the third false positive.
 */
plugins {
    base
}

// Package prefix -> module. One line per module, so the mapping is auditable and adding a
// module is a deliberate act rather than a side effect of naming a package.
val packageOwnersTable: Map<String, String> = mapOf(
    "com.tasktracker.core" to ":packages:core",
    "com.tasktracker.domain" to ":packages:domain",
    "com.tasktracker.designsystem" to ":packages:design-system",
    "com.tasktracker.routing" to ":packages:routing",
    "com.tasktracker.database" to ":packages:database",
    "com.tasktracker.network" to ":packages:network",
    "com.tasktracker.sync" to ":packages:sync",
    "com.tasktracker.keychain" to ":packages:keychain",
    "com.tasktracker.userdefaults" to ":packages:user-defaults",
    "com.tasktracker.di" to ":packages:di",
    "com.tasktracker.featureprojectsdomain" to ":packages:feature-projects-domain",
    "com.tasktracker.featureprojectsdata" to ":packages:feature-projects-data",
    "com.tasktracker.featureprojectspresentation" to ":packages:feature-projects-presentation",
    "com.tasktracker.featureauthdomain" to ":packages:feature-auth-domain",
    "com.tasktracker.featureauthdata" to ":packages:feature-auth-data",
    "com.tasktracker.featureauthpresentation" to ":packages:feature-auth-presentation",
    "com.tasktracker.featuresettingsdomain" to ":packages:feature-settings-domain",
    "com.tasktracker.featuresettingsdata" to ":packages:feature-settings-data",
    "com.tasktracker.featuresettingspresentation" to ":packages:feature-settings-presentation",
    "com.tasktracker.compositionroot" to ":composition-root",
    "com.tasktracker.sharedui" to ":shared-ui",
)

/**
 * The architecture, in one table.
 *
 * The rules it encodes, and the reason each exists:
 *  - a domain module depends on `:packages:core` and `:packages:domain` only, never on
 *    Compose, Room, Ktor, or its own feature's data module, so the business rules stay
 *    testable with no infrastructure standing in;
 *  - a presentation module sees its own domain module and the cross-cutting modules, and
 *    never its own feature's data module — that is the edge that would let UI reach a
 *    database directly instead of through a use case;
 *  - a data module sees the infrastructure modules and its own domain module, and never a
 *    presentation module, which would be a cycle;
 *  - a module outside this table is not in the build, and the graph check will say so
 *    rather than leaving it unchecked.
 */
val allowedLibraryTable: Map<String, Set<String>> = mapOf(
    ":packages:core" to emptySet(),

    // Cross-cutting, bottom-up. Design system, keychain, user defaults and DI sit just
    // above core with nothing below them but the shared primitives.
    ":packages:domain" to setOf(":packages:core"),
    ":packages:design-system" to setOf(":packages:core"),
    ":packages:keychain" to setOf(":packages:core"),
    ":packages:user-defaults" to setOf(":packages:core"),
    ":packages:di" to setOf(":packages:core"),
    ":packages:routing" to setOf(":packages:core", ":packages:domain"),
    ":packages:database" to setOf(":packages:core", ":packages:domain"),
    // core only, and this is the table's one deliberately narrow entry after
    // :packages:core itself. A transport is the bottom of the stack: it builds URLs and
    // decodes bytes, and the moment it names a domain type it starts deciding what a
    // request *means*, which is the repositories' job one layer up. The original's
    // NetworkService depended on TaskTrackerCore alone; the rule now says so, rather than
    // carrying an edge the module does not use and that a graph check would then enforce
    // forever.
    ":packages:network" to setOf(":packages:core"),
    // core, database and network — the same three the original's Package.swift declared.
    // :packages:domain is deliberately absent: the engine routes opaque payloads and never
    // learns what a WorkItem is, and the graph check enforces every declared edge, so a
    // dependency here would be one the module has to keep justifying.
    ":packages:sync" to setOf(
        ":packages:core",
        ":packages:database",
        ":packages:network",
    ),

    // Feature modules. Note the absence of any presentation -> data edge.
    ":packages:feature-projects-domain" to setOf(":packages:core", ":packages:domain"),
    ":packages:feature-projects-data" to setOf(
        ":packages:core",
        ":packages:domain",
        ":packages:database",
        ":packages:network",
        ":packages:sync",
        ":packages:keychain",
        ":packages:feature-projects-domain",
    ),
    ":packages:feature-projects-presentation" to setOf(
        ":packages:core",
        ":packages:domain",
        ":packages:design-system",
        ":packages:routing",
        ":packages:di",
        ":packages:feature-projects-domain",
    ),

    ":packages:feature-auth-domain" to setOf(":packages:core", ":packages:domain"),
    ":packages:feature-auth-data" to setOf(
        ":packages:core",
        ":packages:domain",
        ":packages:database",
        ":packages:sync",
        ":packages:network",
        ":packages:keychain",
        ":packages:feature-auth-domain",
    ),
    ":packages:feature-auth-presentation" to setOf(
        ":packages:core",
        ":packages:domain",
        ":packages:design-system",
        ":packages:routing",
        ":packages:di",
        ":packages:feature-auth-domain",
    ),

    ":packages:feature-settings-domain" to setOf(":packages:core", ":packages:domain"),
    ":packages:feature-settings-data" to setOf(
        ":packages:core",
        ":packages:domain",
        ":packages:database",
        ":packages:sync",
        ":packages:feature-settings-domain",
    ),
    ":packages:feature-settings-presentation" to setOf(
        ":packages:core",
        ":packages:domain",
        ":packages:design-system",
        ":packages:routing",
        ":packages:di",
        ":packages:feature-settings-domain",
    ),
)

/** Every library module. Derived, so a new module cannot be added and left unchecked. */
val allModulesTable: Set<String> = allowedLibraryTable.keys

val allowedDependenciesTable: Map<String, Set<String>> = allowedLibraryTable +
    mapOf(
        // The composition root is the only module that sees every library: it is where a
        // port is bound to an implementation, so it is the only place both sides are visible
        // at once. Derived rather than listed, so a new library is visible to it
        // automatically. Note it stops at the libraries — it cannot see :shared-ui or
        // :androidApp, which sit above it.
        ":composition-root" to allModulesTable,

        // The shared screen tree: the presentation modules and the graph, never a data
        // module, so it cannot assemble its own data.
        ":shared-ui" to setOf(
            ":packages:core",
            ":packages:domain",
            ":packages:design-system",
            ":packages:routing",
            ":packages:di",
            ":composition-root",
            ":packages:feature-projects-presentation",
            ":packages:feature-auth-presentation",
            ":packages:feature-settings-presentation",
        ),

        // The Android shell sits above the architecture and names what it needs directly.
        ":androidApp" to setOf(":composition-root", ":shared-ui", ":packages:di"),
    )



// --- registration -----------------------------------------------------------
//
// The tables above are the architecture. Everything from here on is plumbing, and the
// plumbing is deliberately thin: the only judgement call is when the graph is read, which
// has to be after every subproject's build script has been evaluated.

// Gradle synthesises a project for each container segment of a path, so `:packages`
// exists as a grouping node with no build file and no code. It is not a module.
val modules = subprojects.filter { it.buildFile.exists() }

gradle.projectsEvaluated {
    val modelGraph: Map<String, Set<String>> = modules.associate { module ->
        module.path to module.configurations
            .filterNot { it.name.contains("Test", ignoreCase = true) }
            .flatMap { configuration -> runCatching { configuration.dependencies }.getOrDefault(emptyList()) }
            .filterIsInstance<ProjectDependency>()
            .map { it.path }
            .toSet()
    }

    tasks.register<VerifyArchitectureGraphTask>("verifyArchitectureGraph") {
        group = "verification"
        description = "Fails if any module declares a dependency the architecture does not allow."
        declaredGraph.set(modelGraph)
        allowed.set(allowedDependenciesTable)
    }
}

modules.forEach { module ->
    module.tasks.register<VerifyModuleImportsTask>("verifyModuleImports") {
        group = "verification"
        description = "Fails if ${module.path} imports a package owned by a module it may not see."
        sources.from(module.layout.projectDirectory.dir("src"))
        allowed.set(allowedDependenciesTable[module.path].orEmpty())
        packageOwners.set(packageOwnersTable)
        modulePath.set(module.path)
        moduleDirectory.set(module.layout.projectDirectory)
    }
}

tasks.named("check") {
    dependsOn(tasks.named("verifyArchitectureGraph"))
    dependsOn(modules.map { it.tasks.named("verifyModuleImports") })
}
