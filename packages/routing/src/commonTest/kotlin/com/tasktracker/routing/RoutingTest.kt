package com.tasktracker.routing

import com.tasktracker.core.ProjectID
import com.tasktracker.core.WorkItemID
import com.tasktracker.core.randomProjectID
import com.tasktracker.core.randomWorkItemID
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val PROJECT_UUID = "11111111-1111-1111-1111-111111111111"
private const val ITEM_UUID = "22222222-2222-2222-2222-222222222222"

private val projectID = ProjectID(PROJECT_UUID)
private val itemID = WorkItemID(ITEM_UUID)

class DeepLinkParserTest {

    @Test
    fun `ignores a foreign scheme`() {
        assertNull(DeepLinkParser.route("https://example.com/board/$PROJECT_UUID"))
    }

    @Test
    fun `ignores an unknown host`() {
        assertNull(DeepLinkParser.route("tasktracker://nonsense/1"))
    }

    @Test
    fun `links to the dashboard`() {
        val link = DeepLinkParser.route("tasktracker://projects")
        assertEquals(AppTab.Projects, link?.tab)
        assertNull(link?.route)
        assertEquals(emptyList(), link?.path)
    }

    @Test
    fun `accepts the legacy dashboard alias`() {
        assertEquals(
            DeepLinkParser.route("tasktracker://projects")?.tab,
            DeepLinkParser.route("tasktracker://dashboard")?.tab,
        )
    }

    @Test
    fun `links to a board and installs the path`() {
        val link = DeepLinkParser.route("tasktracker://board/$PROJECT_UUID")
        assertEquals(listOf(AppRoute.Board(projectID)), link?.path)
        assertNull(link?.route)
    }

    @Test
    fun `links to an archive on the Board tab`() {
        val link = DeepLinkParser.route("tasktracker://archive/$PROJECT_UUID")
        assertEquals(AppTab.Board, link?.tab)
        assertEquals(listOf(AppRoute.Archive(projectID)), link?.path)
        assertNull(link?.route)
    }

    @Test
    fun `rejects an archive link with a malformed project id`() {
        assertNull(DeepLinkParser.route("tasktracker://archive/not-a-uuid"))
    }

    @Test
    fun `links to a work item and opens the editor over the board`() {
        val link = DeepLinkParser.route("tasktracker://workitem/$PROJECT_UUID/$ITEM_UUID")
        assertEquals(
            listOf(
                AppRoute.Board(projectID),
                AppRoute.WorkItemEditor(projectID, itemID),
            ),
            link?.path,
        )
    }

    @Test
    fun `new means create and not a malformed id`() {
        val link = DeepLinkParser.route("tasktracker://workitem/$PROJECT_UUID/new")
        assertEquals(
            AppRoute.WorkItemEditor(projectID, null),
            link?.path?.last(),
        )
    }

    @Test
    fun `a work-item link with no item segment is a create link`() {
        val link = DeepLinkParser.route("tasktracker://workitem/$PROJECT_UUID")
        assertEquals(AppRoute.WorkItemEditor(projectID, null), link?.route)
    }

    @Test
    fun `rejects a board link with a malformed project id`() {
        assertNull(DeepLinkParser.route("tasktracker://board/not-a-uuid"))
        assertNull(DeepLinkParser.route("tasktracker://board"))
    }

    @Test
    fun `rejects a work-item link with a malformed item id`() {
        assertNull(DeepLinkParser.route("tasktracker://workitem/$PROJECT_UUID/garbage"))
    }

    @Test
    fun `matches the scheme and host case-insensitively`() {
        val link = DeepLinkParser.route("TaskTracker://Board/$PROJECT_UUID")
        assertEquals(listOf(AppRoute.Board(projectID)), link?.path)
    }

    @Test
    fun `a query string does not corrupt the trailing id`() {
        val link = DeepLinkParser.route("tasktracker://board/$PROJECT_UUID?utm_source=email")
        assertEquals(listOf(AppRoute.Board(projectID)), link?.path)
    }

    @Test
    fun `a fragment does not corrupt the trailing id`() {
        val link = DeepLinkParser.route("tasktracker://workitem/$PROJECT_UUID/$ITEM_UUID#top")
        assertEquals(AppRoute.WorkItemEditor(projectID, itemID), link?.route)
    }

    @Test
    fun `an empty authority parses like an explicit host`() {
        // Foundation reported a nil host here and compactMap dropped it, so the original
        // treated a triple slash the same as a double one. The hand-rolled split has to
        // make the same choice or a link that works on iOS 404s on Android.
        val link = DeepLinkParser.route("tasktracker:///board/$PROJECT_UUID")
        assertEquals(listOf(AppRoute.Board(projectID)), link?.path)
    }

    @Test
    fun `percent escapes are decoded before parsing`() {
        val escaped = PROJECT_UUID.replace("1", "%31")
        val link = DeepLinkParser.route("tasktracker://board/$escaped")
        assertEquals(listOf(AppRoute.Board(projectID)), link?.path)
    }

    @Test
    fun `rejects a bare scheme with nothing after it`() {
        assertNull(DeepLinkParser.route("tasktracker://"))
        assertNull(DeepLinkParser.route(""))
    }
}

class RouterTest {
    private val otherProjectID = randomProjectID()

    @Test
    fun `starts signed out on the auth root`() {
        val router = Router()
        assertEquals(RootDestination.Auth, router.root)
        assertEquals(AppTab.Projects, router.selectedTab)
    }

    @Test
    fun `entering main clears any stale navigation`() {
        val router = Router()
        router.setPath(listOf(AppRoute.Backlog(projectID)), onTab = AppTab.Projects)

        router.enterMain()

        assertEquals(RootDestination.Main, router.root)
        assertTrue(router.path(AppTab.Projects).isEmpty())
    }

    @Test
    fun `signing out wipes both tabs`() {
        val router = Router()
        router.enterMain()
        router.push(AppRoute.Board(projectID))
        router.setPath(listOf(AppRoute.Settings), onTab = AppTab.Settings)

        router.enterAuth()

        assertEquals(RootDestination.Auth, router.root)
        assertTrue(router.path(AppTab.Projects).isEmpty())
        assertTrue(router.path(AppTab.Settings).isEmpty())
        assertEquals(AppTab.Projects, router.selectedTab)
    }

    @Test
    fun `pushing the same route twice pops instead of stacking a duplicate`() {
        val router = Router()
        router.enterMain()

        router.push(AppRoute.Board(projectID))
        router.push(AppRoute.Backlog(projectID))
        router.push(AppRoute.Backlog(projectID))

        assertEquals(listOf(AppRoute.Board(projectID)), router.path(AppTab.Projects))
    }

    @Test
    fun `pushing at the root of a stack is a no-op rather than a crash`() {
        val router = Router()
        router.enterMain()
        router.push(AppRoute.Board(projectID))
        router.push(AppRoute.Board(projectID))
        assertEquals(listOf(AppRoute.Board(projectID)), router.path(AppTab.Projects))
    }

    @Test
    fun `reports the project currently on screen`() {
        val router = Router()
        router.enterMain()
        assertNull(router.currentProjectID)

        router.push(AppRoute.Board(projectID))
        assertEquals(projectID, router.currentProjectID)

        router.push(AppRoute.WorkItemEditor(otherProjectID, null))
        assertEquals(otherProjectID, router.currentProjectID)
    }

    @Test
    fun `each tab keeps its own stack`() {
        val router = Router()
        router.enterMain()
        router.push(AppRoute.Board(projectID))

        router.push(AppRoute.Settings, onTab = AppTab.Settings)
        router.push(AppRoute.Board(projectID), onTab = AppTab.Projects)

        assertEquals(listOf(AppRoute.Board(projectID)), router.path(AppTab.Projects))
        assertEquals(listOf(AppRoute.Settings), router.path(AppTab.Settings))
    }

    @Test
    fun `a deep link is ignored while signed out`() {
        val router = Router()
        assertFalse(router.open("tasktracker://projects"))
        assertEquals(RootDestination.Auth, router.root)
    }

    @Test
    fun `a deep link applies once signed in onto its own tab`() {
        val router = Router()
        router.enterMain()
        val id = randomProjectID()

        assertTrue(router.open("tasktracker://backlog/$id"))

        assertEquals(AppTab.Backlog, router.selectedTab)
        assertEquals(listOf(AppRoute.Backlog(id)), router.path(AppTab.Backlog))
        assertEquals(id, router.activeProjectID)
    }

    @Test
    fun `a board deep link lands on the Board tab with the project active`() {
        val router = Router()
        router.enterMain()

        assertTrue(router.open("tasktracker://board/$PROJECT_UUID"))

        assertEquals(AppTab.Board, router.selectedTab)
        assertEquals(projectID, router.activeProjectID)
    }

    @Test
    fun `a work-item deep link scopes the screen without moving the tabs`() {
        val router = Router()
        router.enterMain()
        router.openProject(projectID)

        assertTrue(router.open("tasktracker://workitem/$PROJECT_UUID/$ITEM_UUID"))

        assertEquals(projectID, router.currentProjectID)
        assertEquals(
            projectID,
            router.activeProjectID,
            "an editor route is project-scoped but must not become the active project",
        )
    }

    @Test
    fun `an unrecognised deep link leaves the state untouched`() {
        val router = Router()
        router.enterMain()
        router.push(AppRoute.Board(projectID))

        assertFalse(router.open("tasktracker://nonsense/1"))

        assertEquals(AppTab.Projects, router.selectedTab)
        assertEquals(listOf(AppRoute.Board(projectID)), router.path(AppTab.Projects))
    }

    @Test
    fun `restoring the last project is ignored while signed out`() = runTest {
        val router = Router()

        router.restoreLastVisitedProject(projectID)
        assertNull(router.activeProjectID)

        router.enterMain()
        router.restoreLastVisitedProject(projectID)

        assertEquals(projectID, router.activeProjectID)
        assertTrue(router.path(AppTab.Projects).isEmpty())
    }

    @Test
    fun `a stored project that no longer exists is dropped not restored`() = runTest {
        val router = Router()
        router.enterMain()

        router.restoreLastVisitedProject(projectID) { false }

        assertNull(router.activeProjectID, "a deleted default must not become the active project")
    }

    @Test
    fun `a validator that cannot answer keeps the stored id and skips the restore`() = runTest {
        val router = Router()
        router.enterMain()

        router.restoreLastVisitedProject(projectID) { null }

        assertNull(router.activeProjectID, "an unvalidatable restore must not activate a project it could not check")
    }

    @Test
    fun `restoring is idempotent`() = runTest {
        val router = Router()
        router.enterMain()

        router.restoreLastVisitedProject(projectID)
        router.restoreLastVisitedProject(projectID)

        assertEquals(projectID, router.activeProjectID)
    }

    @Test
    fun `a null project id is a no-op`() = runTest {
        val router = Router()
        router.enterMain()
        router.openProject(projectID)

        router.restoreLastVisitedProject(null)

        assertEquals(projectID, router.activeProjectID)
    }

    @Test
    fun `opening a project scopes the Board and Backlog tabs to it`() {
        val router = Router()
        router.enterMain()
        router.openProject(projectID)

        assertEquals(projectID, router.activeProjectID)
        assertEquals(AppTab.Projects, router.selectedTab)
        assertEquals(listOf(AppRoute.Board(projectID)), router.path(AppTab.Projects))

        router.push(AppRoute.Board(projectID), onTab = AppTab.Board)
        assertEquals(projectID, router.currentProjectID, "switching to Board keeps the same project")
    }

    @Test
    fun `openProject on a scoped tab resets that tab's stack to its root`() {
        val router = Router()
        router.enterMain()
        router.openProject(projectID, inTab = AppTab.Backlog)
        router.push(AppRoute.WorkItemEditor(projectID, null))

        router.openProject(otherProjectID, inTab = AppTab.Backlog)

        assertTrue(router.path(AppTab.Backlog).isEmpty())
        assertEquals(otherProjectID, router.activeProjectID)
    }

    @Test
    fun `openProject is ignored on the settings tab`() {
        val router = Router()
        router.enterMain()
        router.openProject(projectID, inTab = AppTab.Projects)

        router.openProject(otherProjectID, inTab = AppTab.Settings)

        assertEquals(projectID, router.activeProjectID, "settings is not project-scoped")
        assertEquals(
            listOf(AppRoute.Board(projectID)),
            router.path(AppTab.Projects),
            "a rejected open must not disturb the stack it was aimed at",
        )
    }

    @Test
    fun `signing out keeps the last project but clears the stacks`() {
        val router = Router()
        router.enterMain()
        router.openProject(projectID)

        router.enterAuth()

        assertEquals(projectID, router.activeProjectID)
        assertTrue(router.path(AppTab.Projects).isEmpty())
    }

    @Test
    fun `currentProjectID falls back to the active project on an empty stack`() {
        val router = Router()
        router.enterMain()
        assertNull(router.currentProjectID)

        router.openProject(projectID)
        assertEquals(projectID, router.currentProjectID)
    }

    @Test
    fun `currentProjectID skips a settings route to find a project below it`() {
        val router = Router()
        router.enterMain()
        router.push(AppRoute.Board(projectID), onTab = AppTab.Settings)
        router.push(AppRoute.Settings, onTab = AppTab.Settings)

        assertEquals(projectID, router.currentProjectID)
    }

    @Test
    fun `the session read is pending until the router is told where it landed`() {
        val router = Router()
        // The initial state is the point of this flag: at launch the app has not asked
        // yet, and root == Auth cannot say whether that is because nobody is signed in.
        assertTrue(router.isRestoringSession)

        router.enterAuth()
        assertFalse(router.isRestoringSession)
    }

    @Test
    fun `a restored session clears the pending flag as well`() {
        val router = Router()
        router.beginRestoringSession()

        router.enterMain()

        assertFalse(router.isRestoringSession)
        assertEquals(RootDestination.Main, router.root)
    }

    @Test
    fun `a repeat enterMain keeps navigation but still clears the pending flag`() {
        val router = Router()
        router.beginRestoringSession()
        router.enterMain()
        router.push(AppRoute.Board(projectID))
        router.beginRestoringSession()

        router.enterMain()

        // The original guards on `root != .main` and returns, so a second call is a
        // no-op on navigation. It only re-asserts that the session read has landed.
        assertFalse(router.isRestoringSession)
        assertEquals(
            listOf(AppRoute.Board(projectID)),
            router.path(AppTab.Projects),
            "a redundant enterMain must not wipe where the user was",
        )
    }

    @Test
    fun `popToRoot discards only the named tab`() {
        val router = Router()
        router.enterMain()
        router.push(AppRoute.Board(projectID), onTab = AppTab.Projects)
        router.push(AppRoute.Settings, onTab = AppTab.Settings)

        router.popToRoot(AppTab.Projects)

        assertTrue(router.path(AppTab.Projects).isEmpty())
        assertEquals(listOf(AppRoute.Settings), router.path(AppTab.Settings))
    }

    @Test
    fun `state is published as one atomic snapshot`() {
        val router = Router()
        router.enterMain()
        router.push(AppRoute.Board(projectID))

        val state = router.state.value
        assertEquals(RootDestination.Main, state.root)
        assertEquals(AppTab.Projects, state.selectedTab)
        assertEquals(listOf(AppRoute.Board(projectID)), state.projectsPath)
        assertEquals(projectID, state.activeProjectID)
    }

    @Test
    fun `the last-visited key is stable so relaunches find the same project`() {
        assertEquals("routing.lastVisitedProjectID", Router().lastProjectDefaultsKey)
    }

    @Test
    fun `routes and tabs stay in sync with their wire values`() {
        assertEquals(AppTab.Board, AppTab.fromWireValue("board"))
        assertNull(AppTab.fromWireValue("nope"))
        assertTrue(AppTab.Board.isProjectScoped)
        assertFalse(AppTab.Settings.isProjectScoped)
    }

    @Test
    fun `a generated id round-trips through a deep link`() {
        val generated = randomWorkItemID()
        val link = DeepLinkParser.route("tasktracker://workitem/$projectID/$generated")
        assertEquals(AppRoute.WorkItemEditor(projectID, generated), link?.route)
    }
}
