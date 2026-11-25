package com.tasktracker.sharedui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.tasktracker.core.ProjectID
import com.tasktracker.core.isDesktopApp
import com.tasktracker.compositionroot.AppGraph
import com.tasktracker.compositionroot.platformApiBaseUrl
import com.tasktracker.designsystem.TTGlassButtonStyle
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttGlassButton
import com.tasktracker.designsystem.ttGlassCard
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTheme
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.di.Resolver
import com.tasktracker.featureauthdomain.UserRole
import com.tasktracker.featuresettingsdomain.AppVersion
import com.tasktracker.resources.Res
import com.tasktracker.resources.app_name
import com.tasktracker.resources.common_close
import com.tasktracker.resources.desktop_about_body
import com.tasktracker.resources.desktop_about_item
import com.tasktracker.resources.desktop_menu_add_tag
import com.tasktracker.resources.desktop_menu_admin
import com.tasktracker.resources.desktop_menu_file
import com.tasktracker.resources.desktop_menu_new
import com.tasktracker.resources.desktop_menu_new_epic
import com.tasktracker.resources.desktop_menu_new_project
import com.tasktracker.resources.desktop_menu_new_task
import com.tasktracker.resources.desktop_menu_search
import com.tasktracker.resources.desktop_quit_item
import com.tasktracker.resources.settings_agent_setup
import com.tasktracker.resources.settings_manage_members
import com.tasktracker.resources.settings_manage_projects
import com.tasktracker.resources.settings_version
import com.tasktracker.resources.tab_settings
import com.tasktracker.routing.AppRoute
import com.tasktracker.routing.AppTab
import com.tasktracker.routing.RootDestination
import com.tasktracker.routing.Router
import com.tasktracker.userdefaults.UserDefaultsStore
import com.tasktracker.userdefaults.WindowGeometry
import com.tasktracker.userdefaults.WindowGeometryDefaults
import java.awt.Desktop
import java.awt.Image
import java.awt.Taskbar
import java.awt.Toolkit
import java.awt.desktop.PreferencesHandler
import javax.imageio.ImageIO
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import androidx.compose.ui.res.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The name Desktop shows in the macOS menu bar and the window title bar.
 *
 * The window title reads the same value through `Res.string.app_name`; this constant exists
 * only because [main] has to set the AWT property before any composition can resolve a
 * resource. Keep the two in step — `ResourceStringsScenarios` pins the resource, and
 * `runDesktop`/`nativeDistributions.macOS.dockName` name the same string for the packaged
 * launch.
 */
private const val DesktopDisplayName = "Task Tracker"

/** The settings root, expressed as the link grammar the router already owns. */
private const val DESKTOP_SETTINGS_LINK = "tasktracker://settings"

/** macOS is where the primary shortcut modifier is Command rather than Control. */
private fun isMacDesktop(): Boolean =
    System.getProperty("os.name").orEmpty().startsWith("Mac", ignoreCase = true)

/** The platform's primary shortcut modifier: Command on macOS, Control elsewhere. */
private fun desktopShortcut(key: Key, isMac: Boolean): KeyShortcut =
    if (isMac) KeyShortcut(key, meta = true) else KeyShortcut(key, ctrl = true)

/** The create-task route, built through the same grammar every other entry point uses. */
private fun newTaskLink(projectID: ProjectID): String =
    "tasktracker://workitem/${projectID.stringValue}/new"

/**
 * Puts Settings in macOS's own leftmost application menu.
 *
 * The OS owns that menu and already places About and Quit there; a Settings row drawn in the
 * File menu would be a second copy of the same destination. AWT's preferences action is the
 * native seam, and it is installed only while signed in because the settings route refuses to
 * bypass the auth gate.
 */
@Composable
private fun MacSettingsMenuItem(router: Router, signedIn: Boolean) {
    DisposableEffect(signedIn) {
        setMacPreferencesHandler(
            if (signedIn) {
                { router.open(DESKTOP_SETTINGS_LINK) }
            } else {
                null
            },
        )
        onDispose { setMacPreferencesHandler(null) }
    }
}

/**
 * Registers the macOS Settings action, or removes it for a null handler.
 *
 * Guarded twice because AWT's desktop integration is optional: a headless or minimal JVM can
 * report no desktop support, and a platform without the preferences action would throw on
 * `setPreferencesHandler`. Either way the app opens without the native row rather than failing.
 */
private fun setMacPreferencesHandler(onSettings: (() -> Unit)?) {
    try {
        if (!Desktop.isDesktopSupported()) return
        val desktop = Desktop.getDesktop()
        if (!desktop.isSupported(Desktop.Action.APP_PREFERENCES)) return
        desktop.setPreferencesHandler(
            onSettings?.let { open -> PreferencesHandler { open() } },
        )
    } catch (_: Exception) {
        // No desktop integration: Settings remains reachable through the shell.
    }
}

/**
 * About, as a small modal over the shell rather than a second window.
 *
 * The version is optional because a developer run has no packaged version to read; the row is
 * then absent rather than printing a blank, matching the Settings screen's About section.
 */
@Composable
private fun AboutDialog(version: String?, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        ttTheme(ThemeScheme.System) {
            Column(
                modifier = Modifier
                    .widthIn(max = 380.dp)
                    .ttGlassCard()
                    .padding(ttSpacing.xxl),
                verticalArrangement = Arrangement.spacedBy(ttSpacing.md),
            ) {
                ttText(
                    text = stringResource(Res.string.app_name),
                    style = ttTypography.title,
                    color = ttLabel(),
                )
                version?.let {
                    ttText(
                        text = "${stringResource(Res.string.settings_version)} $it",
                        style = ttTypography.footnote,
                        color = ttLabelSecondary(),
                    )
                }
                ttText(
                    text = stringResource(Res.string.desktop_about_body),
                    style = ttTypography.callout,
                    color = ttLabelSecondary(),
                )
                ttGlassButton(
                    label = stringResource(Res.string.common_close),
                    onClick = onDismiss,
                    style = TTGlassButtonStyle(prominent = true),
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }
}

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
fun main() {
    // Before anything initialises AWT: `setDockIcon()` below is the first call that does, and
    // macOS reads the menu-bar name at that point. `runDesktop` also passes this as a `-D`
    // argument; this assignment is what covers the IDE's `hotRunJvm` and a packaged launch.
    System.setProperty("apple.awt.application.name", DesktopDisplayName)
    startDesktopProcess {
        setDockIcon()
        val resolver = DesktopGraph.resolver
        val appVersion = resolver.getOrNull(AppVersion::class)?.display
        application {
            val router = resolver.resolve(Router::class)
            // Created here rather than inside the shell so the window menu can read the same
            // cached role the Settings screen gates on. RootScreen receives this instance
            // instead of making a second one that nothing would refresh.
            val preferences = remember(resolver) {
                ShellPreferences(
                    store = resolver.resolve(UserDefaultsStore::class),
                    router = router,
                )
            }
            val windowState = rememberWindowState(
                size = desktopWindowSize(WindowGeometryDefaults.load()),
            )
            Window(
                onCloseRequest = ::exitApplication,
                title = stringResource(Res.string.app_name),
                state = windowState,
                icon = painterResource("AppIcon.png"),
            ) {
                val navigation by router.state.collectAsState()
                val appName = stringResource(Res.string.app_name)
                val isMac = isMacDesktop()
                // macOS already shows the OS-owned app menu named Task Tracker, so the window
                // menu takes the conventional name instead of drawing the same title twice.
                val menuTitle = if (isMac) stringResource(Res.string.desktop_menu_file) else appName
                var isAboutOpen by remember { mutableStateOf(false) }
                val signedIn = navigation.root == RootDestination.Main
                val activeProject = navigation.activeProjectID
                val userRole by preferences.currentUserRole.collectAsState()
                val canAdmin = signedIn && userRole == UserRole.Admin
                if (isMac) MacSettingsMenuItem(router = router, signedIn = signedIn)
                MenuBar {
                    Menu(text = menuTitle) {
                        Menu(text = stringResource(Res.string.desktop_menu_new)) {
                            Item(
                                text = stringResource(Res.string.desktop_menu_new_task),
                                enabled = signedIn && activeProject != null,
                                shortcut = desktopShortcut(Key.N, isMac),
                                onClick = {
                                    activeProject?.let { router.open(newTaskLink(it)) }
                                },
                            )
                            // The same entry points the Projects tab offers: an epic editor
                            // pushed onto that tab, and the project form drawn by its host.
                            Item(
                                text = stringResource(Res.string.desktop_menu_new_epic),
                                enabled = signedIn,
                                onClick = {
                                    router.push(AppRoute.EpicEditor(null), AppTab.Projects)
                                },
                            )
                            Item(
                                text = stringResource(Res.string.desktop_menu_new_project),
                                enabled = signedIn,
                                onClick = router::requestNewProject,
                            )
                        }
                        Item(
                            text = stringResource(Res.string.desktop_menu_search),
                            enabled = signedIn && activeProject != null,
                            shortcut = desktopShortcut(Key.F, isMac),
                            onClick = { router.requestSearch() },
                        )
                        Separator()
                        // macOS already renders Settings in its own application menu.
                        if (!isMac) {
                            Item(
                                text = stringResource(Res.string.tab_settings),
                                enabled = signedIn,
                                shortcut = desktopShortcut(Key.Comma, isMac),
                                onClick = { router.open(DESKTOP_SETTINGS_LINK) },
                            )
                            Separator()
                        }
                        Item(
                            text = stringResource(Res.string.desktop_about_item, appName),
                            onClick = { isAboutOpen = true },
                        )
                        Item(
                            text = stringResource(Res.string.desktop_quit_item, appName),
                            shortcut = desktopShortcut(Key.Q, isMac),
                            onClick = ::exitApplication,
                        )
                    }
                    // Hidden rather than disabled for a member: these are the actions the
                    // Settings screen itself withholds from them, and a greyed row would
                    // still advertise a section that is not theirs.
                    if (canAdmin) {
                        Menu(text = stringResource(Res.string.desktop_menu_admin)) {
                            Item(
                                text = stringResource(Res.string.settings_manage_members),
                                onClick = router::openAdminMembers,
                            )
                            Item(
                                text = stringResource(Res.string.settings_manage_projects),
                                onClick = { router.openAdminProjects() },
                            )
                            Separator()
                            Item(
                                text = stringResource(Res.string.settings_agent_setup),
                                onClick = router::requestAgentSetup,
                            )
                            Item(
                                text = stringResource(Res.string.desktop_menu_add_tag),
                                enabled = activeProject != null,
                                onClick = {
                                    activeProject?.let { router.openAdminProjects(it, focusTags = true) }
                                },
                            )
                        }
                    }
                }
                if (isAboutOpen) {
                    AboutDialog(version = appVersion, onDismiss = { isAboutOpen = false })
                }
                RememberWindowGeometry(windowState)
                DesktopContent(resolver = resolver, shellPreferences = preferences)
            }
        }
    }
}

/**
 * Replaces the default Java dock icon with the TaskTracker mark on macOS/Linux/Windows.
 *
 * `runDesktop` launches a raw `java` process with no `.app` bundle, so macOS/OS defaults fall
 * back to the Java icon and label the process "Java". Standard JDK 9+ `Taskbar.getTaskbar().iconImage`
 * overrides it; for older/custom JDK environments it falls back to `com.apple.eawt.Application`.
 */
private fun setDockIcon() {
    try {
        val image: Image? = ImageIO.read(
            DesktopGraph::class.java.getResource("/AppIcon.png") ?: return
        ) ?: return

        if (Taskbar.isTaskbarSupported()) {
            val taskbar = Taskbar.getTaskbar()
            if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                taskbar.iconImage = image
                return
            }
        }

        val applicationClass = Class.forName("com.apple.eawt.Application")
        val application = applicationClass.getMethod("getApplication").invoke(null)
        applicationClass.getMethod("setDockIconImage", Image::class.java).invoke(application, image)
        Toolkit.getDefaultToolkit().sync()
    } catch (_: Exception) {
        // Not supported or Apple AWT unavailable: keep default icon.
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

@Composable
private fun DesktopContent(resolver: Resolver, shellPreferences: ShellPreferences) {
    TaskTrackerApp(resolver = resolver, shellPreferences = shellPreferences)
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
 * ## Why it names a base URL
 *
 * Desktop runs beside the Go server, so it asks for the real HTTP remote and transport rather
 * than the in-process demo pair. The value comes from [platformApiBaseUrl], which reads each
 * platform's own configuration surface — the same function the iOS and Android shells now call,
 * so all three name a backend the same way.
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
 * and `HttpAuthRemote` comes back — and `PlatformApiBaseUrlJvmTest` pins [platformApiBaseUrl]'s
 * value. The holder's single
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
 * Builds the one graph this process runs on, against the platform's API root.
 *
 * [platformApiBaseUrl] is the default rather than the argument spelled at the call site, so the
 * URL decision lives in exactly one place for all three shells. A caller passes a root, or turns
 * [startServices] off, only to describe the graph without running it; [DesktopGraph.resolver]
 * passes nothing.
 *
 * `startServices = false` is not a second production shape: it builds every registration
 * exactly as production does and merely declines to start the three loops, which is what lets
 * an assertion look at the auth remote without opening the developer's store on the way.
 *
 * The default is an expression this file alone holds and no test can see: the root reaches
 * `KtorHttpClient` privately, so `= "http://localhost:9999"` here would leave every test green
 * while Desktop dialled the wrong port. Naming [platformApiBaseUrl] is what keeps the value in
 * the one place its tests do pin.
 */
internal fun desktopGraph(
    apiBaseUrl: String = platformApiBaseUrl(),
    startServices: Boolean = true,
): Resolver = AppGraph.start(apiBaseUrl = apiBaseUrl, startServices = startServices)
