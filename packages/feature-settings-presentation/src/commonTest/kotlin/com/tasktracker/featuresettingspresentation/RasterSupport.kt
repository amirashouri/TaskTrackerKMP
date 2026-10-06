package com.tasktracker.featuresettingspresentation

/**
 * Whether this host can rasterise a Compose frame and have the result mean what the JVM's
 * would.
 *
 * True on the JVM and on the iOS simulator, false on the Android host — the same three-way
 * split the design system's own pixel gates use (`TextRasterSupport.kt` there). The Android
 * host's condition is not a broken environment: `@GraphicsMode(NATIVE)` under Robolectric does
 * draw. It is that Robolectric's software rasteriser is a rasteriser this repo has measured to
 * disagree with Skia about strokes and coverage, which is exactly what the design system's
 * documented `testAndroidHostTest` failures are. A test that reads a ring off a frame asks this
 * first and skips on that host; the skip records as a pass, and the JVM and iOS simulator both
 * run the assertion.
 */
internal expect fun thisHostRasterisesText(): Boolean