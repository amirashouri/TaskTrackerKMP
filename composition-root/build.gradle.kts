plugins {
    id("tasktracker.kmp.compose")
}

/**
 * The version a release was cut as, stamped onto this module's JVM jar.
 *
 * The release workflow passes the same `-PtasktrackerVersion` to every `:shared-ui`
 * packaging task, and a Gradle property is build-wide, so this module sees it in the same
 * invocation. The tag (`0.1.6`) is what the user downloaded; it is deliberately not
 * `desktopPackageVersion`, which macOS bumps to a non-zero major (`1.1.6`) only because
 * jpackage refuses a zero one.
 *
 * Absent means a local build: the attribute is empty, `PlatformAppVersion.jvm` answers
 * blanks, and the About row hides itself instead of showing a version nobody shipped.
 */
val releaseVersion: String =
    (findProperty("tasktrackerVersion") as String? ?: "").removePrefix("v").trim()

tasks.withType<org.gradle.jvm.tasks.Jar>().configureEach {
    manifest.attributes["Implementation-Version"] = releaseVersion
}

dependencies {
    commonMainImplementation(project(":packages:core"))
    commonMainImplementation(project(":packages:domain"))
    commonMainImplementation(project(":packages:design-system"))
    commonMainImplementation(project(":packages:routing"))
    commonMainImplementation(project(":packages:di"))
    commonMainImplementation(project(":packages:database"))
    commonMainImplementation(project(":packages:network"))
    commonMainImplementation(project(":packages:sync"))
    commonMainImplementation(project(":packages:keychain"))
    commonMainImplementation(project(":packages:user-defaults"))
    commonMainImplementation(project(":packages:feature-projects-domain"))
    commonMainImplementation(project(":packages:feature-projects-data"))
    commonMainImplementation(project(":packages:feature-projects-presentation"))
    commonMainImplementation(project(":packages:feature-auth-domain"))
    commonMainImplementation(project(":packages:feature-auth-data"))
    commonMainImplementation(project(":packages:feature-auth-presentation"))
    commonMainImplementation(project(":packages:feature-settings-domain"))
    commonMainImplementation(project(":packages:feature-settings-data"))
    commonMainImplementation(project(":packages:feature-settings-presentation"))
    commonMainImplementation(project(":packages:feature-teams-domain"))
    commonMainImplementation(project(":packages:feature-teams-data"))
    commonMainImplementation(project(":packages:feature-teams-presentation"))
    commonMainImplementation(project(":packages:feature-notifications-domain"))
    commonMainImplementation(project(":packages:feature-notifications-data"))
    commonMainImplementation(project(":packages:feature-notifications-presentation"))
}
