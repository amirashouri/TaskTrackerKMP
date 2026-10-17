package com.tasktracker.sharedui

import com.tasktracker.core.ProjectID
import com.tasktracker.core.UserID
import com.tasktracker.core.randomUserID
import com.tasktracker.domain.Project
import com.tasktracker.featureprojectsdomain.AdminProject
import com.tasktracker.featureprojectsdomain.AdminProjectsPort
import com.tasktracker.featureprojectsdomain.ProjectAdminStatus
import com.tasktracker.featureteamsdomain.MemberAdminPort
import com.tasktracker.featureteamsdomain.MemberRole
import com.tasktracker.featureteamsdomain.User
import com.tasktracker.featureteamsdomain.UserStatus

/**
 * The two admin ports, for the shell scenarios that have to see past the placeholder.
 *
 * ## Why binding them is the point of these scenarios
 *
 * `NavigationHost` renders a page of its own only when the app bound that section's port, and
 * no shell scenario bound either one — so every admin assertion here ran against
 * `AdminMembersPlaceholder`/`AdminProjectsPlaceholder` and the real destinations were never
 * composed by a test. That is how a destination which ignored the `projectID` it was handed
 * survived a green suite: nothing ever exercised the branch that reads it.
 *
 * ## Why they are not recording doubles
 *
 * What these scenarios assert is *where* a screen lands — which pane, on which host width — and
 * that is a claim about the composition. The traffic claims (what a save carries, what a create
 * sends) belong to the feature modules' own model tests, which have a recording double each;
 * a second one here would be a second copy of those tests at the wrong level.
 */
class FakeMemberAdminPort(
    active: List<User> = emptyList(),
    deleted: List<User> = emptyList(),
) : MemberAdminPort {

    private val seeded = mapOf(UserStatus.Active to active, UserStatus.Deleted to deleted)
    private val created = mutableListOf<User>()

    override suspend fun members(status: UserStatus): List<User> =
        seeded[status].orEmpty() + created.filter { it.status == status }

    override suspend fun createMember(
        email: String,
        password: String,
        role: MemberRole,
        position: String?,
    ): User = User(
        id = randomUserID(),
        email = email,
        position = position,
        status = UserStatus.Active,
        role = role,
    ).also { created += it }

    /**
     * Records nothing and removes nothing.
     *
     * The delete path is a two-tap confirmation inside the members screen and its behaviour is
     * asserted there; a shell scenario that also observed it would be asserting the same thing
     * twice from further away.
     */
    override suspend fun softDeleteMember(id: UserID) = Unit

    override suspend fun reactivateMember(id: UserID): User =
        seeded[UserStatus.Deleted]?.firstOrNull { it.id == id }?.copy(status = UserStatus.Active)
            ?: User(id = id, email = "restored@example.com", status = UserStatus.Active)

    // The read-only half of `TeamPort`. The members screen resolves names through the rows it
    // was handed rather than through these, so an empty answer is honest here.

    override suspend fun user(id: UserID): User? = null

    override suspend fun users(ids: List<UserID>): List<User> = emptyList()

    override suspend fun searchMembers(query: String): List<User> = emptyList()
}

/**
 * The admin project API, from a list rather than a server.
 *
 * [project] answers `null` for every id it was not seeded with, which is the "the server no
 * longer has this project" answer the editor's missing state exists to tell apart from a
 * failure.
 *
 * `createProject` really does add a row, and the list reports it afterwards. That is the point of
 * the fake here: the section's create form lands on the server and the list is re-read rather
 * than appended to, so a scenario can only assert the new project appeared if the double holds
 * it the way the server would — the traffic claims still belong to the feature module's own
 * recording fake.
 */
class FakeAdminProjectsPort(
    private val seeded: List<AdminProject> = emptyList(),
) : AdminProjectsPort {

    private val created = mutableListOf<AdminProject>()

    private val byID = seeded.associateBy { it.project.id }.toMutableMap()

    override suspend fun projects(status: ProjectAdminStatus): List<AdminProject> =
        (seeded + created).filter { it.project.isArchived == (status == ProjectAdminStatus.Archived) }

    override suspend fun project(id: ProjectID): AdminProject? = byID[id]

    override suspend fun createProject(
        name: String,
        keyPrefix: String,
        details: String,
        memberIDs: List<UserID>,
    ): AdminProject {
        val project = shellTestProject(name = name, keyPrefix = keyPrefix)
            .copy(details = details, memberIDs = memberIDs)
        val stored = AdminProject(project, version = "1")
        created += stored
        byID[project.id] = stored
        return stored
    }

    /**
     * Returns what it was handed, with the version it was sent.
     *
     * Enough for the shell, which is not asking what the server did with the body: the save's
     * compare-and-swap is asserted in `AdminProjectEditorModelTest`, against a port that
     * records both halves of the pair.
     */
    override suspend fun updateProject(project: Project, version: String): AdminProject =
        AdminProject(project, version)
}

/** One project as the admin API hands it back, on the shell's fixed clock. */
fun shellTestAdminProject(
    project: Project = shellTestProject(),
    version: String = "1",
): AdminProject = AdminProject(project = project, version = version)

/** One directory entry, so the members list has a row rather than only its empty state. */
fun shellTestMember(email: String = "ada@example.com"): User = User(
    id = randomUserID(),
    email = email,
    status = UserStatus.Active,
)
