import XCTest

/// Shared setup for the iOS UI tests.
///
/// Booting a simulator costs far more than the unit tests, so this bundle is a deliberate
/// step rather than part of every run:
///
///     xcodebuild test -scheme TaskTracker -only-testing:TaskTrackerUITests
///
/// It is also the only place the composition root, the router and the session bus are
/// exercised together, which is exactly the wiring unit tests cannot reach - the
/// original records the same reasoning at `project.yml:152-158`.
///
/// ## Two traps this base class exists to survive
///
/// **The system "Save Password?" sheet.** It covers the app while leaving the underlying
/// screen visible *and* still present in the accessibility tree. Element assertions keep
/// passing while every tap is silently discarded, so a test that only queries elements
/// reports a green run against a UI that is not responding. `dismissSavePasswordPrompt()`
/// is called from `setUp` for that reason.
///
/// **The simulator keeps its container between launches.** Sign-out is a one-way door
/// here: it writes to the keychain, which outlives the app's data store. Uninstalling
/// would fix the symptom by discarding the seeded-data store along with it, so
/// `setUp` normalises back to the auth gate by driving the UI instead.
class AppUITestCase: XCTestCase {

    /// How long to wait for a transition that has no accessibility signal of its own.
    ///
    /// Generous on purpose: a simulator under load can spend several seconds in the
    /// graph's `start()` plus the first database open, and a short timeout produces a
    /// failure that looks like an app bug and is not one.
    private static let transitionTimeout: TimeInterval = 10

    override func setUpWithError() throws {
        try super.setUpWithError()

        // `continueAfterFailure` stays at its default. The original leaves it on: these
        // tests assert a flow, and a half-finished flow that keeps going produces a
        // cascade of failures pointing at symptoms rather than the cause.
        dismissSavePasswordPrompt()
        returnToAuthGate()
    }

    /// Dismisses the system save-password sheet if it is covering the app.
    ///
    /// The sheet has no stable identifier across iOS versions, so it is found by its
    /// known buttons rather than by an identifier that would break on an OS update.
    private func dismissSavePasswordPrompt() {
        let save = app.buttons["Save Password"]
        guard save.waitForExistence(timeout: 1) else { return }
        save.tap()
    }

    /// Drives the app back to the auth gate so each test starts from the same state.
    ///
    /// Not `uninstall`. Uninstalling discards the seeded-data store, so the next launch
    /// reseeds and the test that follows is no longer testing the state the previous one
    /// left behind. The sign-out path is driven through the UI, which is also the only
    /// version of it that exercises the teardown.
    private func returnToAuthGate() {
        let signOut = app.buttons["Sign Out"]
        guard signOut.waitForExistence(timeout: Self.transitionTimeout) else {
            // Already at the gate, which is the normal case. The first launch after a
            // fresh install lands here too, so this is not a failure.
            return
        }
        signOut.tap()
    }

    /// The application under test.
    ///
    /// `firstMatch` everywhere it is used: SwiftUI surfaces an accessibility identifier
    /// on both a `Menu` and the button drawn inside it, so a bare query returns two
    /// elements and the test fails on the ambiguity rather than on the behaviour.
    var app: XCUIApplication {
        let application = XCUIApplication()
        application.launch()
        return application
    }

    /// Taps the centre of `element` rather than the element itself.
    ///
    /// `XCUIElement.tap()` fails quietly when something overlaps the element's frame -
    /// a transparent overlay, a still-animating sheet - and reports a successful tap for
    /// a touch that went nowhere. Tapping a resolved coordinate inside the frame does not
    /// have that failure mode.
    func tapCentre(of element: XCUIElement) {
        element.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
    }
}
