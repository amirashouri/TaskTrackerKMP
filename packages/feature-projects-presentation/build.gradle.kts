plugins {
    id("tasktracker.kmp.compose")
}

dependencies {
    commonMainImplementation(project(":packages:core"))
    commonMainImplementation(project(":packages:domain"))
    commonMainImplementation(project(":packages:design-system"))
    commonMainImplementation(project(":packages:routing"))
    commonMainImplementation(project(":packages:di"))
    commonMainImplementation(project(":packages:feature-projects-domain"))

    // For `observe(scope)` on ProjectModel, and for the month headings on the archive -
    // which are cut in a zone rather than in UTC, or a task filed at 23:30 on the last
    // day of a month lands in the wrong section for anyone east of Greenwich.
    commonMainImplementation(libs.kotlinx.coroutines.core)
    commonMainImplementation(libs.kotlinx.datetime)

    commonTestImplementation(libs.kotlinx.coroutines.test)
}
