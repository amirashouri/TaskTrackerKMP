plugins {
    id("tasktracker.kmp.compose")
}

dependencies {
    commonMainImplementation(project(":packages:core"))

    // For `TTDateField`, and specifically for `LocalDate` rather than for anything else in
    // this file. A day grid is a calendar, and a calendar needs calendar arithmetic: which
    // weekday a month starts on, how long the month is, what the previous month is. Written
    // by hand over `kotlin.time.Instant` that means a UTC-midnight conversion, a leap-year
    // rule and a month-length table — and an `Instant` that lands on the *previous* day in
    // any negative-offset zone, which is how a date field ends up a day out.
    //
    // This is a library dependency rather than a module edge, so `allowedLibraryTable` and
    // `packageOwnersTable` are unchanged and neither architecture check reads it. It is
    // declared here rather than inherited for the same reason `:packages:domain` declares its
    // own `kotlinx.serialization`: a KMP `implementation` dependency is not `api`.
    commonMainImplementation(libs.kotlinx.datetime)

    // :packages:design-system is the single owner of Compose resources for the
    // whole build. The convention plugin deliberately does not add
    // compose.components.resources, because each module that configures
    // `compose.resources` emits its own *ResourceCollectors class and two of
    // them fail Android's dex merge. Feature and app modules consume the
    // generated Res API transitively from here, which is also the right
    // dependency direction: shared visual assets live in the design system
    // rather than being duplicated into every presentation module.
    commonMainImplementation(libs.compose.resources)
    androidMainImplementation(libs.androidx.core.ktx)
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.tasktracker.resources"
    generateResClass = always
}
