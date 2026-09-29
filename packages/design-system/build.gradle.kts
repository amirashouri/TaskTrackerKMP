plugins {
    id("tasktracker.kmp.compose")
}

dependencies {
    commonMainImplementation(project(":packages:core"))

    // :packages:design-system is the single owner of Compose resources for the
    // whole build. The convention plugin deliberately does not add
    // compose.components.resources, because each module that configures
    // `compose.resources` emits its own *ResourceCollectors class and two of
    // them fail Android's dex merge. Feature and app modules consume the
    // generated Res API transitively from here, which is also the right
    // dependency direction: shared visual assets live in the design system
    // rather than being duplicated into every presentation module.
    commonMainImplementation(libs.compose.resources)
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.tasktracker.resources"
    generateResClass = always
}
