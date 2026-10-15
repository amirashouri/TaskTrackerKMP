package com.tasktracker.sharedui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.tasktracker.core.isDesktopApp
import com.tasktracker.compositionroot.AppGraph
import com.tasktracker.di.Resolver
import com.tasktracker.resources.Res
import com.tasktracker.resources.app_name
import com.tasktracker.userdefaults.WindowGeometry
import com.tasktracker.userdefaults.WindowGeometryDefaults
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

/**
 * The Desktop entry point.
 *
 * Modeled on `MainViewController.kt`, the same shape iOS already uses: the
 * graph is built once and held in process scope, and the platform shell
 * contributes only the window and the graph. Nothing in `commonMain` changes
 * for Desktop.
 *
 * Unlike the mobile shells, Desktop's entry is a Compose `application {}`
 * block rather than a bare `main()`. That is the canonical way to open a
 * top-level `Window` on Compose Desktop; the `Window` composable runs inside
 * the application's composable scope.
 *
 * ## Why this takes no arguments
 *
 * The alternative is a `main` that builds the graph itself by reaching into
 * `:packages:di` or `:composition-root` from the platform side — which is the
 * wrong dependency direction. The graph is owned in Kotlin, exactly as
 * `TaskTrackerApplication` owns it on Android and `IosGraph` owns it on iOS.
 */
fun main() = startDesktopProcess {
    val resolver = DesktopGraph.resolver
    application {
        val windowState = rememberWindowState(
            size = desktopWindowSize(WindowGeometryDefaults.load()),
        )
        Window(
            onCloseRequest = ::exitApplication,
            title = stringResource(Res.string.app_name),
            state = windowState,
        ) {
            RememberWindowGeometry(windowState)
            DesktopContent(resolver = resolver)
        }
    }
}

/**
 * The size the Desktop window opens at before anything has been remembered.
 *
 * Comfortably above [ShellExpandedMinimumWidth] so a first launch reaches the wide,
 * two-pane shell without the user having to drag the frame — which is the entire point of
 * giving the window a size at all. `1280 × 800` is a common laptop's usable area and leaves
 * the expanded layout headroom rather than sitting on the threshold; a value at the
 * threshold would satisfy the class but leave neither pane a readable measure, which is what
 * the threshold's own KDoc says it is for.
 */
internal val DefaultDesktopWindowSize: DpSize = DpSize(width = 1280.dp, height = 800.dp)

/**
 * The size the window should open at: the remembered one, or [DefaultDesktopWindowSize].
 *
 * A pure function rather than an expression inside `main` so a test can pin the choice.
 * `main` opens a real window and blocks until it closes, so nothing in the suite could
 * otherwise enter the branch that decides whether a stored size is honoured.
 */
internal fun desktopWindowSize(stored: WindowGeometry?): DpSize =
    stored?.let { DpSize(width = it.width.dp, height = it.height.dp) } ?: DefaultDesktopWindowSize

/** How long the window must hold a new size before it is written. See [RememberWindowGeometry]. */
private const val WINDOW_GEOMETRY_SETTLE_MS: Long = 500L

/**
 * Writes the window's size back once a resize has settled.
 *
 * ## Why the write waits rather than landing on every frame
 *
 * Dragging the window's edge fires a size change per frame, and a write on each would be a
 * `Properties` file rewrite per frame. Keying the effect on `windowState.size` makes each new
 * drag frame **cancel** the pending write and start a fresh delay, so one settled size is the
 * one that reaches disk. That is the same split `ShellPreferences` draws between a glass
 * slider's `preview` (every frame) and `commit` (on release).
 *
 * ## Why this is not a save on window close
 *
 * A write in `onCloseRequest` runs only when the window is closed through the chrome; a
 * process that is killed, or a machine that is shut down with the app open, would keep
 * whatever size the last *polite* close happened to leave. Observing the size while the app
 * runs means the stored value is already correct before any close arrives.
 *
 * The read is a frame late on the very first composition — the state is created from the
 * stored size, so the first observed value is already the remembered one — and the delay
 * then writes back the same number, which is harmless.
 */
@Composable
private fun RememberWindowGeometry(windowState: WindowState) {
    LaunchedEffect(windowState.size) {
        delay(WINDOW_GEOMETRY_SETTLE_MS)
        WindowGeometryDefaults.save(
            WindowGeometry(
                width = windowState.size.width.value.roundToInt(),
                height = windowState.size.height.value.roundToInt(),
            ),
        )
    }
}

/**
 * Marks this process as the Desktop app, and only then opens the shell.
 *
 * The order is the contract, not a style choice. The shell's graph builds every store when
 * it is started, and both JVM storage seams pick their backend from [isDesktopApp] at
 * construction time — `platformPreferences()` returns the file-backed backend only once the
 * flag is set, and the JVM `systemKeychain()` likewise hands out the in-memory stub while it
 * is clear. Dropping the assignment, or opening the shell first, leaves a Desktop app that
 * holds its session and its appearance in memory alone and loses both on every restart, with
 * nothing on screen to say so.
 *
 * Extracted from `main` because that decision has to be reachable from a test. `main` opens a
 * window and blocks until it closes, so nothing in the suite could enter the sequence that
 * decides whether Desktop persists anything; `DesktopEntryPointJvmTest` asserts the flag is
 * already set at the moment the shell is opened, which fails both when the assignment is
 * deleted and when the two steps are swapped.
 */
internal fun startDesktopProcess(openShell: () -> Unit) {
    isDesktopApp = true
    openShell()
}

/**
 * The preferences file the Desktop app owns.
 *
 * Exists so the Desktop shell can open the same file the preferences backend writes
 * without reaching across the architecture boundary into `:packages:database`.
 * `user-defaults` already depends on `core` only and owns this path locally for exactly
 * that reason; the shell reads the path here so the run task / desktop launcher can point
 * at it without adding a new module edge.
 */
public val preferencesFilePath: String
    get() = com.tasktracker.userdefaults.preferencesFile().path

/**
 * The API root the Desktop app talks to.
 *
 * `http://localhost:8080` is the port `docker compose --profile postgres` publishes the Go
 * server on, so a launched Desktop app reaches the backend with nothing exported.
 * `TASKTRACKER_API_URL` overrides it for a server on another host or port.
 *
 * A pure function of the environment rather than a `System.getenv` call at the use site, so a
 * test can pin both halves without owning the process's environment — the same reason
 * [desktopWindowSize] takes the stored geometry instead of reading it.
 */
internal fun desktopApiBaseUrl(environment: Map<String, String> = System.getenv()): String =
    environment[API_BASE_URL_ENV_VAR]?.takeIf { it.isNotBlank() } ?: DEFAULT_API_BASE_URL

/** The environment variable that overrides [desktopApiBaseUrl]. */
internal const val API_BASE_URL_ENV_VAR: String = "TASKTRACKER_API_URL"

/** Where the Go server listens when nothing overrides it. See [desktopApiBaseUrl]. */
internal const val DEFAULT_API_BASE_URL: String = "http://localhost:8080"

@Composable
private fun DesktopContent(resolver: Resolver) {
    TaskTrackerApp(resolver = resolver)
}

/**
 * The process-lifetime graph on Desktop.
 *
 * Mirrors `IosGraph` on iOS, including the reason the graph is built once and
 * held rather than created per window or per recomposition: a second window or a
 * re-launch must not produce two graphs.
 *
 * ## Why there is no matching `stop`
 *
 * Same asymmetry as iOS. A Desktop process exits when the window closes or the
 * process is killed; at that point releasing the three looping services is
 * correct, and hooking a `stop` off a window-close callback would cancel them for
 * an app the user was about to resume. So there is no such symbol here. If Desktop
 * ever needs explicit teardown, this holder is where it belongs.
 *
 * ## Why it names a base URL and the mobile shells do not
 *
 * Desktop is the one target that runs beside the Go server, so it is the one target that
 * asks for the real HTTP remote and transport instead of the in-process demo pair. Naming
 * [desktopApiBaseUrl] here rather than in `commonMain` is what keeps the mobile shells on the
 * offline demo they were built against.
 *
 * ## Why the graph is built by a named function
 *
 * [DesktopGraph] is a `by lazy` holder behind a private name, so nothing could enter the call
 * that decides whether this process talks to the Go server or to the offline demo — the same
 * reason [desktopWindowSize] takes the stored geometry as an argument rather than reading it
 * inside `main`. A process that lost `apiBaseUrl` there would still start, still sign in against
 * the in-process mock, and simply never reach the server, which is the failure this file exists
 * to make loud.
 *
 * ## The residual, stated rather than implied
 *
 * `DesktopEntryPointJvmTest` pins [desktopGraph]'s own line — that a URL reaches `AppGraph.start`
 * and `HttpAuthRemote` comes back — and pins [desktopApiBaseUrl]'s value. The holder's single
 * line below, `desktopGraph()` with no argument, is **not** covered: reverting it to a bare
 * `AppGraph.start()` leaves the suite byte-identical. No behavioural test can reach it, because
 * entering the holder's lazy initialiser starts the three loops, and `startLoops` resolves the
 * store — the developer's real database — so asserting the composition of the two tested halves
 * would create it. Scanning this file's text for `desktopGraph(` would pin it and is this repo's
 * idiom for a rule with nothing to resolve (`AppGraphAccessTest` does exactly that for the
 * container), but a scan cannot tell a call from a mention in a comment, so the gap is written
 * down here instead of gated by something a comment could satisfy.
 */
private object DesktopGraph {
    val resolver: Resolver by lazy { desktopGraph() }
}

/**
 * Builds the one graph this process runs on, against a named API root.
 *
 * [desktopApiBaseUrl] is the default rather than the argument spelled at the call site, so the
 * URL decision lives in exactly one place. A caller passes a root, or turns
 * [startServices] off, only to describe the graph without running it; [DesktopGraph.resolver]
 * passes nothing.
 *
 * `startServices = false` is not a second production shape: it builds every registration
 * exactly as production does and merely declines to start the three loops, which is what lets
 * an assertion look at the auth remote without opening the developer's store on the way.
 *
 * The default is an expression this file alone holds and no test can see: the root reaches
 * `KtorHttpClient` privately, so `= "http://localhost:9999"` here would leave every test green
 * while Desktop dialled the wrong port. Naming [desktopApiBaseUrl] is what keeps the value in
 * the one place its tests do pin.
 */
internal fun desktopGraph(
    apiBaseUrl: String = desktopApiBaseUrl(),
    startServices: Boolean = true,
): Resolver = AppGraph.start(apiBaseUrl = apiBaseUrl, startServices = startServices)
