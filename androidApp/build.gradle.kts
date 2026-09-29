plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.tasktracker.android"

    defaultConfig {
        applicationId = "com.tasktracker.android"
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(25)
    }
}

dependencies {
    // The shell is above the architecture, not inside it. It holds the graph and renders
    // the shared tree, so it names both modules directly rather than reaching them through
    // a transitively-leaking implementation dependency.
    implementation(project(":composition-root"))
    implementation(project(":shared-ui"))
    implementation(project(":packages:di"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    // No Koin and no Ktor here. The container is reached through the Resolver interface
    // in :packages:di and the HTTP client is built in :packages:network; a second copy of
    // either in the app module would be a graph that behaves differently from the one the
    // composition root describes.
}
