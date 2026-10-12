package com.tasktracker.featureprojectspresentation

import com.tasktracker.domain.Project
import com.tasktracker.featureteamsdomain.User
import com.tasktracker.core.UserID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Who a task on a given project may be assigned to.
 *
 * Plain [Test] methods rather than scenarios, because the rule is a pure function of two lists
 * and asserting on values is both cheaper and sharper than asserting on a rendered picker. A
 * `commonTest` class runs on every target, so these cost one JVM run and two near-free native
 * ones.
 */
class AssignableMembersForTest {

    @Test
    fun `only the project's members are assignable`() {
        val stranger = carol
        val project = project(members = listOf(alice.id, bob.id))

        assertEquals(
            listOf(alice.id, bob.id),
            assignableMembersFor(project, listOf(alice, bob, stranger)).map { it.id },
            "someone outside the project must not be assignable, however well their name " +
                "matches the search",
        )
    }

    /**
     * The result follows membership order, not the directory's.
     *
     * The directory is alphabetical by name; the membership list is whatever order the user
     * arranged. Pinning the distinction matters because the two orders agree by accident
     * whenever a fixture happens to be alphabetical — a test written against such a fixture
     * would pass under either implementation, and a user who put someone at the top would watch
     * them move.
     */
    @Test
    fun `members keep the order the project lists them in`() {
        // Directory order is alphabetical: alice, bob, carol.
        val project = project(members = listOf(carol.id, alice.id, bob.id))

        assertEquals(
            listOf(carol.id, alice.id, bob.id),
            assignableMembersFor(project, listOf(alice, bob, carol)).map { it.id },
            "membership order is the user's own arrangement and must survive the intersection",
        )
    }

    /**
     * A member the directory cannot name is dropped, not rendered as a raw id.
     *
     * A member added on another device has an id this device has no profile for. Printing it
     * would put a `0195…` in a list of names; dropping it costs the picker an entry and keeps
     * the list honest about who it can actually offer.
     */
    @Test
    fun `a member with no directory profile is dropped rather than shown as an id`() {
        // A member whose profile this device has never received. Deliberately not `testUserID`,
        // which is the module's shared signed-in fixture.
        val unknown = UserID("ffffffff-ffff-4fff-8fff-ffffffffffff")
        val project = project(members = listOf(unknown, alice.id))

        assertEquals(
            listOf(alice.id),
            assignableMembersFor(project, listOf(alice)).map { it.id },
            "an unnameable member must not appear as a raw id",
        )
    }

    /**
     * A project with no members recorded offers the whole directory.
     *
     * An unconfigured project is not a project with no members — the two are the same empty
     * list — and offering nobody would make every freshly created project unassignable. That
     * is a far worse failure than showing one extra name in a list the user is choosing from,
     * so the filter is exactly as strict as the membership it is given.
     */
    @Test
    fun `a project with no members offers the whole directory`() {
        val project = project(members = emptyList())

        assertEquals(
            listOf(alice.id, bob.id),
            assignableMembersFor(project, listOf(alice, bob)).map { it.id },
            "an unconfigured project must stay usable rather than offering nobody",
        )
    }

    /**
     * No project — the project editor's own picker, or a board before its project has loaded.
     *
     * Scoping to nothing here would empty a picker the user may be part-way through using.
     */
    @Test
    fun `no project leaves the directory unscoped`() {
        assertEquals(
            listOf(alice.id, bob.id),
            assignableMembersFor(null, listOf(alice, bob)).map { it.id },
            "with no project there is nothing to scope to, and the picker must stay populated",
        )
    }

    /** The intersection of two sets cannot gain a member, which is the property 1.6 rests on. */
    @Test
    fun `a non-member never appears however the directory is ordered`() {
        val project = project(members = listOf(alice.id))

        for (directory in listOf(
            listOf(alice, bob, carol),
            listOf(carol, bob, alice),
            listOf(bob, carol),
        )) {
            val names = assignableMembersFor(project, directory).map { it.id }
            assertTrue(
                bob.id !in names && carol.id !in names,
                "a non-member leaked into $names from directory order ${directory.map { u -> u.id }}",
            )
        }
    }

    /**
 * A directory person.
 *
 * Ids are spelled out rather than generated because [UserID] only accepts a real UUID, and a
 * generator that derives one from a readable key has to emit hex — which is a second thing to
 * get wrong before the test can run. Fixed rather than random, so a failure names the same
 * people on every run, and the email carries the readable part the pickers search on.
 */
private fun user(id: UserID, key: String, name: String) =
    User(id = id, email = "$key@example.com", displayName = name)

private val aliceId = UserID("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
private val bobId = UserID("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb")
private val carolId = UserID("cccccccc-cccc-4ccc-8ccc-cccccccccccc")

private val alice = user(aliceId, "alice", "Alice Ahmed")
private val bob = user(bobId, "bob", "Bob Barnes")
private val carol = user(carolId, "carol", "Carol Costa")

    private fun project(members: List<UserID>) = Project(
        keyPrefix = "TT",
        name = "Tracker",
        memberIDs = members,
        createdAt = ViewPreviewData.instant,
        updatedAt = ViewPreviewData.instant,
    )
}