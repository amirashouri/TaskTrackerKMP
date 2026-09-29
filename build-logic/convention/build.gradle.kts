plugins {
    `kotlin-dsl`
}

group = "com.tasktracker.buildlogic"

// Type-safe accessors are generated per-build, so the accessor class for this
// build's own version catalog is not automatically visible to the precompiled
// script plugins. Putting its code source on the compile classpath lets them
// resolve `the<LibrariesForLibs>()`, which is how the convention plugins read
// versions from gradle/libs.versions.toml instead of duplicating them there.
dependencies {
    implementation(files(libs.javaClass.protectionDomain.codeSource.location))

    implementation(libs.android.gradlePlugin)
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.kotlin.serialization.gradlePlugin)
    implementation(libs.kotlin.composeCompilerPlugin)
    implementation(libs.compose.gradlePlugin)
    implementation(libs.ksp.gradlePlugin)
    implementation(libs.room.gradlePlugin)
}
