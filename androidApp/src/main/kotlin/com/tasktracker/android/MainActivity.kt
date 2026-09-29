package com.tasktracker.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.tasktracker.sharedui.TaskTrackerApp

/**
 * The Android entry point.
 *
 * It does three things and delegates everything else: enables edge-to-edge so the shared
 * content can draw under the system bars the way the SwiftUI original does, obtains the
 * graph the [TaskTrackerApplication] built, and hands both to [TaskTrackerApp].
 *
 * There is deliberately no Android-specific navigation or view state here. A second
 * screen type appearing in this file would be the first sign that the platform shell is
 * growing responsibilities the shared module owns.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val resolver = (application as TaskTrackerApplication).resolver

        setContent {
            TaskTrackerApp(resolver = resolver)
        }
    }
}
