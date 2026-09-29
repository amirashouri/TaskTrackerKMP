/**
 * Convention for the persistence module (:packages:database).
 *
 * Room's KMP code generator has to run for every target, not just Android, so
 * the compiler is wired into both the common metadata configuration and each
 * concrete target.
 */
plugins {
    id("tasktracker.kmp.library")
    id("androidx.room")
    id("com.google.devtools.ksp")
}

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

// Exported schemas live in the module, not the build directory, so a schema
// change shows up in review the same way a SwiftData migration diff would.
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    add("commonMainImplementation", libs.room.runtime)
    add("commonMainImplementation", libs.sqlite.bundled)

    add("kspCommonMainMetadata", libs.room.compiler)
    add("kspAndroid", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
    // The base convention adds a jvm() host target so commonTest runs on the JVM in
    // seconds, and Room's generated code lives in commonMain metadata. Without a
    // jvm KSP configuration the jvm compilation has no implementation of the
    // @Database class to link against, so commonMain fails to compile for that target
    // even though nothing in the module mentions the JVM explicitly.
    add("kspJvm", libs.room.compiler)
}
