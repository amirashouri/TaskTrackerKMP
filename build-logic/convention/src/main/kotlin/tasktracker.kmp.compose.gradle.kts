/**
 * Convention for every module that renders Compose UI: :packages:design-system,
 * the nine feature presentation modules, :composition-root and :shared-ui.
 *
 * Note what is *absent*: this plugin brings in Compose, not Room, Ktor or Koin.
 * A presentation module gets its data by depending on its own feature's domain
 * module, so an accidental presentation -> data edge fails at compile time
 * rather than being quietly satisfied by a convention plugin.
 */
plugins {
    id("tasktracker.kmp.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

dependencies {
    // Deliberately no Material3. The original's colours are UIKit *semantic*
    // colours (ttCanvas -> systemBackground, ttSurface -> secondarySystemBackground),
    // not hex values, and the port keeps that property by resolving them through
    // expect/actual per platform. Material3 would replace adaptive semantic
    // colours with a fixed scheme, which is the divergence the design decision
    // explicitly rejected. runtime/foundation/ui are all the design system needs;
    // Material3 gets added per-component later only if a component genuinely
    // requires it, and it would need its own pinned version track.
    add("commonMainImplementation", libs.compose.runtime)
    add("commonMainImplementation", libs.compose.foundation)
    add("commonMainImplementation", libs.compose.ui)
    add("commonMainImplementation", libs.androidx.lifecycle.viewmodel)
    add("commonMainImplementation", libs.androidx.lifecycle.viewmodel.compose)
    add("commonMainImplementation", libs.androidx.lifecycle.runtime)

    add("commonTestImplementation", libs.compose.uiTest)
    // No ui-test-manifest: that artifact is Android-only and was never published
    // multiplatform, so it cannot resolve for the iOS test compilations.
    // No compose.components.resources either: every module that configures
    // `compose.resources` generates its own com.tasktracker.resources.*ResourceCollectors,
    // and two of those collide at dex merge ("defined multiple times"). Exactly
    // one module owns resources -- see :packages:design-system.
}
