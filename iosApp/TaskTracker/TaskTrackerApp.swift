import SwiftUI
import ComposeApp

/// The iOS entry point. Deliberately as thin as the Android one.
///
/// The screen tree, its state and its navigation all live in Kotlin. `mainViewController()`
/// returns a `UIViewController` hosting the Compose tree, and it owns the object graph on
/// the Kotlin side, so this file contributes only the scene and the window.
///
/// It does three things and delegates everything else: declares a single window group,
/// asks Kotlin for the view controller, and lets the shared content draw under the
/// system bars the way the SwiftUI original does. There is deliberately no iOS-specific
/// navigation or view state here. A second screen type appearing in this file would be
/// the first sign that the platform shell is growing responsibilities the shared module
/// owns.
@main
struct TaskTrackerApp: App {

    var body: some Scene {
        WindowGroup {
            MainViewControllerContainer()
                .ignoresSafeArea(.all)
        }
    }
}

/// Wraps Kotlin's view controller so SwiftUI owns its lifetime.
///
/// `UIViewControllerRepresentable` rather than an inline `UIViewController`, so the
/// controller is created once and its identity is stable across parent recompositions.
struct MainViewControllerContainer: UIViewControllerRepresentable {

    func makeUIViewController(context: Context) -> UIViewController {
        // Kotlin owns the graph and hands back a hosted Compose tree. Nothing is passed
        // in: see MainViewController.kt for why the entry point takes no arguments.
        MainViewControllerKt.mainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        // Nothing to do. The graph is process-lifetime and the Compose tree owns its own
        // state, so a parent recomposition carries no new information to push down. The
        // case that would need handling here - a different graph - is a different
        // controller, not an update to this one.
    }
}
