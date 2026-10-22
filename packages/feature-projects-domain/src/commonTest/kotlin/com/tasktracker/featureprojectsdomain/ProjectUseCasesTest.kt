package com.tasktracker.featureprojectsdomain

import com.tasktracker.core.AppError
import com.tasktracker.core.TestDateProvider
import com.tasktracker.core.UserID
import com.tasktracker.core.randomCommentID
import com.tasktracker.core.randomProjectID
import com.tasktracker.core.randomTagID
import com.tasktracker.core.randomWorkItemID
import com.tasktracker.domain.MentionRules
import com.tasktracker.domain.Priority
import com.tasktracker.domain.WorkItemStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The use cases' decisions, tested against a fake port rather than a real store.
 *
 * What is worth testing here is *when a write is refused*, because that is the part the
 * data layer cannot check for you: the store will happily accept a blank name or a
 * duplicate key, and the rule exists precisely to stop it reaching that far.
 */
class ProjectUseCasesTest {

    private val now = Instant.fromEpochSeconds(1_700_000_000)

    // MARK: MentionRules

    @Test
    fun `extractMentions finds username mentions`() = runTest {
        val mentions = MentionRules.extractMentions("Hello @alice, how are you?")
        assertEquals(1, mentions.size)
        val mention = mentions.first() as MentionRules.Mention.ByUsername
        assertEquals("@alice", mention.raw)
        assertEquals("alice", mention.username)
        assertEquals(6, mention.start)
        assertEquals(12, mention.end)
    }

    @Test
    fun `extractMentions finds UserID mentions`() = runTest {
        val mentions = MentionRules.extractMentions("Hey @<12345678-1234-1234-1234-123456789012>, check this")
        assertEquals(1, mentions.size)
        val mention = mentions.first() as MentionRules.Mention.ByUserID
        assertEquals("@<12345678-1234-1234-1234-123456789012>", mention.raw)
        assertEquals(UserID("12345678-1234-1234-1234-123456789012"), mention.userID)
        assertEquals(4, mention.start)
        assertEquals(43, mention.end)
    }

    @Test
    fun `extractMentions finds multiple mentions of both types`() = runTest {
        val mentions = MentionRules.extractMentions("@alice and @<87654321-4321-4321-4321-210987654321> please review")
        assertEquals(2, mentions.size)
        val first = mentions[0] as MentionRules.Mention.ByUsername
        assertEquals("alice", first.username)
        val second = mentions[1] as MentionRules.Mention.ByUserID
        assertEquals(UserID("87654321-4321-4321-4321-210987654321"), second.userID)
    }

    @Test
    fun `extractMentions returns empty for text without mentions`() = runTest {
        assertEquals(emptyList(), MentionRules.extractMentions("No mentions here"))
    }

    @Test
    fun `extractMentions handles trailing punctuation`() = runTest {
        val mentions = MentionRules.extractMentions("Thanks @alice! See @<11111111-2222-3333-4444-555555555555>.")
        assertEquals(2, mentions.size)
        assertEquals("alice", (mentions[0] as MentionRules.Mention.ByUsername).username)
        assertEquals(UserID("11111111-2222-3333-4444-555555555555"), (mentions[1] as MentionRules.Mention.ByUserID).userID)
    }

    @Test
    fun `extractMentions handles mid-word at symbol`() = runTest {
        // email-like strings should not be treated as mentions
        val mentions = MentionRules.extractMentions("Contact alice@example.com for help")
        assertEquals(emptyList(), mentions)
    }

    @Test
    fun `extractMentions handles username with dots and hyphens`() = runTest {
        val mentions = MentionRules.extractMentions("Hey @alice.smith and @bob-jones")
        assertEquals(2, mentions.size)
        assertEquals("alice.smith", (mentions[0] as MentionRules.Mention.ByUsername).username)
        assertEquals("bob-jones", (mentions[1] as MentionRules.Mention.ByUsername).username)
    }

    @Test
    fun `extractMentions handles mention at start of text`() = runTest {
        val mentions = MentionRules.extractMentions("@alice this is a test")
        assertEquals(1, mentions.size)
        assertEquals(0, mentions[0].start)
    }

    @Test
    fun `extractMentions handles mention at end of text`() = runTest {
        val mentions = MentionRules.extractMentions("Hello @alice")
        assertEquals(1, mentions.size)
        assertEquals(6, mentions[0].start)
        assertEquals(12, mentions[0].end)
    }

    // MARK: CreateProject

    @Test
    fun `a valid project is created and stored`() = runTest {
        val repository = FakeProjectRepository()
        val create = CreateProject(repository, TestDateProvider(now))

        val project = create(name = "  Apollo  ", keyPrefix = " apollo ", details = "Moon shots")

        assertEquals("Apollo", project.name)
        assertEquals("APOLLO", project.keyPrefix)
        assertEquals(1, repository.projectCount())
    }

    @Test
    fun `create project keeps selected member identifiers`() = runTest {
        val repository = FakeProjectRepository()
        val member = UserID("10000000-0000-4000-8000-000000000001")
        val project = CreateProject(repository, TestDateProvider(now))(
            name = "Apollo",
            keyPrefix = "AP",
            memberIDs = listOf(member),
        )

        assertEquals(listOf(member), project.memberIDs)
        assertEquals(listOf(member), repository.project(project.id)?.memberIDs)
    }

    @Test
    fun `a blank name is refused before anything is stored`() = runTest {
        val repository = FakeProjectRepository()
        val create = CreateProject(repository, TestDateProvider(now))

        assertFailsWith<AppError> { create(name = "   ", keyPrefix = "APOLLO") }

        assertEquals(
            0,
            repository.projectCount(),
            "a rejected create must not leave a row behind",
        )
    }

    @Test
    fun `a key prefix that is not a usable identifier is refused`() = runTest {
        val repository = FakeProjectRepository()
        val create = CreateProject(repository, TestDateProvider(now))

        assertFailsWith<AppError> { create(name = "Apollo", keyPrefix = "!") }

        assertEquals(0, repository.projectCount())
    }

    @Test
    fun `a store failure reaches the caller instead of being swallowed`() = runTest {
        val repository = FakeProjectRepository()
        repository.failure = AppError.Storage("disk is on fire")
        val create = CreateProject(repository, TestDateProvider(now))

        assertFailsWith<AppError> { create(name = "Apollo", keyPrefix = "APOLLO") }
    }

    // MARK: UpdateProject

    @Test
    fun `a rename is normalised and stored`() = runTest {
        val repository = FakeProjectRepository()
        val project = CreateProject(repository, TestDateProvider(now))(
            name = "Apollo",
            keyPrefix = "APOLLO",
            details = "Moon shots",
        )
        val update = UpdateProject(repository, TestDateProvider(now + 60.seconds))

        val updated = update(id = project.id, name = "  Artemis  ")

        assertEquals("Artemis", updated.name)
        assertEquals(
            "Moon shots",
            updated.details,
            "an edit that does not mention the notes must not touch them",
        )
        val stored = assertNotNull(repository.project(project.id))
        assertEquals("Artemis", stored.name)
    }

    @Test
    fun `rewriting the notes leaves the name alone`() = runTest {
        val repository = FakeProjectRepository()
        val project = CreateProject(repository, TestDateProvider(now))(
            name = "Apollo",
            keyPrefix = "APOLLO",
        )
        val update = UpdateProject(repository, TestDateProvider(now + 60.seconds))

        val updated = update(id = project.id, details = "Lunar landings")

        assertEquals("Apollo", updated.name)
        assertEquals("Lunar landings", updated.details)
    }

    @Test
    fun `a blank rename is refused before the store is touched`() = runTest {
        val repository = FakeProjectRepository()
        val project = CreateProject(repository, TestDateProvider(now))(
            name = "Apollo",
            keyPrefix = "APOLLO",
        )
        val update = UpdateProject(repository, TestDateProvider(now + 60.seconds))

        assertFailsWith<AppError> { update(id = project.id, name = "   ") }

        val stored = assertNotNull(repository.project(project.id))
        assertEquals(
            "Apollo",
            stored.name,
            "a rejected edit must not leave a half-renamed row behind",
        )
    }

    @Test
    fun `a no-op edit reads the project rather than writing it`() = runTest {
        val repository = FakeProjectRepository()
        val project = CreateProject(repository, TestDateProvider(now))(
            name = "Apollo",
            keyPrefix = "APOLLO",
        )
        val update = UpdateProject(repository, TestDateProvider(now + 60.seconds))
        val changesBefore = repository.publishedChanges().size

        val unchanged = update(id = project.id)

        assertEquals(
            project.updatedAt,
            unchanged.updatedAt,
            "nothing changed so nothing was written",
        )
        assertEquals("Apollo", unchanged.name)
        assertEquals(
            changesBefore,
            repository.publishedChanges().size,
            "a no-op must not announce a change that did not happen",
        )
    }

    @Test
    fun `renaming a project that has been deleted is refused`() = runTest {
        val repository = FakeProjectRepository()
        val project = CreateProject(repository, TestDateProvider(now))(
            name = "Apollo",
            keyPrefix = "APOLLO",
        )
        val update = UpdateProject(repository, TestDateProvider(now + 60.seconds))
        // Deleting through the shared fake, on the same public path the UI would take.
        repository.deleteProject(project.id)

        assertFailsWith<AppError> { update(id = project.id, name = "Artemis") }
    }

    // MARK: CreateWorkItem

    @Test
    fun `a work item is created in its project`() = runTest {
        val repository = FakeProjectRepository()
        val project = repository.seedProject()
        val create = CreateWorkItem(repository, TestDateProvider(now))

        val item = create(projectID = project.id, title = "  Land it  ")

        assertEquals("Land it", item.title)
        assertEquals(project.id, item.projectID)
        assertEquals(1, repository.itemCount())
    }

    @Test
    fun `a blank title is refused and nothing is stored`() = runTest {
        val repository = FakeProjectRepository()
        val project = repository.seedProject()
        val create = CreateWorkItem(repository, TestDateProvider(now))

        assertFailsWith<AppError> { create(projectID = project.id, title = "   ") }

        assertEquals(0, repository.itemCount())
    }

    @Test
    fun `an item in a project that does not exist is refused`() = runTest {
        val repository = FakeProjectRepository()
        val create = CreateWorkItem(repository, TestDateProvider(now))

        assertFailsWith<AppError> {
            create(projectID = randomProjectID(), title = "Orphan")
        }

        assertEquals(0, repository.itemCount())
    }

    // MARK: MoveWorkItem

    @Test
    fun `a move reaches the store with the new status`() = runTest {
        val repository = FakeProjectRepository()
        val project = repository.seedProject()
        val item = repository.seedWorkItem(projectID = project.id, status = WorkItemStatus.Todo)
        val move = MoveWorkItem(repository, TestDateProvider(now))

        val moved = move(id = item.id, to = WorkItemStatus.InProgress)

        assertEquals(WorkItemStatus.InProgress, moved.status)
        assertEquals(1, repository.moveCallCount())
    }

    @Test
    fun `moving an item that no longer exists is refused and no write is attempted`() = runTest {
        val repository = FakeProjectRepository()
        val move = MoveWorkItem(repository, TestDateProvider(now))

        assertFailsWith<AppError> { move(id = randomWorkItemID(), to = WorkItemStatus.Done) }

        assertEquals(
            0,
            repository.moveCallCount(),
            "the existence check has to happen before the write not after",
        )
    }

    @Test
    fun `the current status is read before moving so a transition rule can use it`() = runTest {
        val repository = FakeProjectRepository()
        val project = repository.seedProject()
        val item = repository.seedWorkItem(projectID = project.id, status = WorkItemStatus.InReview)
        val move = MoveWorkItem(repository, TestDateProvider(now))

        // Moves backwards. Permissive today, because no transition policy has been written
        // down — but the test pins the behaviour so that adding one is a deliberate change
        // to a failing test, rather than a silent difference nobody notices.
        val moved = move(id = item.id, to = WorkItemStatus.Todo)

        assertEquals(WorkItemStatus.Todo, moved.status)
    }

    // MARK: SaveWorkItem

    @Test
    fun `saving without an id creates and does not attempt a move`() = runTest {
        val repository = FakeProjectRepository()
        val project = repository.seedProject()
        val save = SaveWorkItem(repository, TestDateProvider(now))

        val outcome = save(projectID = project.id, title = "  New thing  ", status = WorkItemStatus.Todo)

        val created = assertIs<WorkItemSaveOutcome.Created>(outcome)
        assertEquals(1, repository.itemCount())
        assertEquals("New thing", repository.workItem(created.workItemID)?.title)
        assertEquals(
            0,
            repository.moveCallCount(),
            "a new item has no previous column to leave",
        )
    }

    @Test
    fun `a create from a sprint board joins that sprint and carries its estimate`() = runTest {
        // The board shows only the active sprint's items, so the sprint id handed at creation
        // is the whole difference between a task that appears when the sheet closes and one
        // that saves into the store and never shows up. Deliberately asserting both fields off
        // the one write: the regression to watch is the editor routing `initialSprintID` as
        // `null` while the user types points into the estimate field.
        val repository = FakeProjectRepository()
        val project = repository.seedProject()
        val sprint = repository.createSprint(
            projectID = project.id,
            name = "Sprint 1",
            goalStoryPoints = 20,
            startDate = now,
            endDate = now + 7.days,
            ownerID = null,
            now = now,
        )
        val save = SaveWorkItem(repository, TestDateProvider(now))

        save(
            projectID = project.id,
            title = "Wired through the sprint filter",
            status = WorkItemStatus.Todo,
            storyPoints = 5,
            initialSprintID = sprint.id,
        )

        val stored = repository.workItems(project.id).single()
        assertEquals(sprint.id, stored.sprintID, "the task must land in the sprint the add came from")
        assertEquals(5, stored.storyPoints, "the estimate must not be dropped on the floor")
    }

    @Test
    fun `an unestimated create still joins the sprint and stays unestimated`() = runTest {
        val repository = FakeProjectRepository()
        val project = repository.seedProject()
        val sprint = repository.createSprint(
            projectID = project.id,
            name = "Sprint 1",
            goalStoryPoints = 20,
            startDate = now,
            endDate = now + 7.days,
            ownerID = null,
            now = now,
        )
        val save = SaveWorkItem(repository, TestDateProvider(now))

        save(
            projectID = project.id,
            title = "No estimate yet",
            status = WorkItemStatus.Todo,
            initialSprintID = sprint.id,
        )

        val stored = repository.workItems(project.id).single()
        assertEquals(sprint.id, stored.sprintID)
        assertNull(stored.storyPoints, "absent input must not become zero")
    }

    @Test
    fun `editing fields without touching the status does not move the item`() = runTest {
        val repository = FakeProjectRepository()
        val project = repository.seedProject()
        val item = repository.seedWorkItem(projectID = project.id, status = WorkItemStatus.Todo)
        val save = SaveWorkItem(repository, TestDateProvider(now))

        val outcome = save(
            id = item.id,
            projectID = project.id,
            title = "Renamed",
            status = WorkItemStatus.Todo,
        )

        assertEquals(WorkItemSaveOutcome.Updated(item.id, moved = false), outcome)
        assertEquals(
            0,
            repository.moveCallCount(),
            "an unchanged status must not re-rank anything",
        )
    }

    @Test
    fun `tags are capped at five unique ids on create`() = runTest {
        // The picker refuses a sixth tap, but the payload must not depend on the UI having
        // done it: a paste, a sync, or a second surface can all hand in more.
        val repository = FakeProjectRepository()
        val project = repository.seedProject()
        val save = SaveWorkItem(repository, TestDateProvider(now))
        val ids = List(7) { randomTagID() }

        val outcome = save(
            projectID = project.id,
            title = "Tagged",
            status = WorkItemStatus.Todo,
            tagIDs = ids + ids.first(),
        )

        val created = assertIs<WorkItemSaveOutcome.Created>(outcome)
        val stored = assertNotNull(repository.workItem(created.workItemID))
        assertEquals(ids.take(5), stored.tagIDs, "first five, in order, with the repeat dropped")
    }

    @Test
    fun `clearing every tag reaches the store as a clear`() = runTest {
        // An edit the user made must not read as silence: `Patch.Unmentioned` would leave the
        // removed tags on the row, which looks like the editor ignoring the tap.
        val repository = FakeProjectRepository()
        val project = repository.seedProject()
        val item = repository.seedWorkItem(projectID = project.id, status = WorkItemStatus.Todo)
        item.tagIDs = listOf(randomTagID(), randomTagID())
        val save = SaveWorkItem(repository, TestDateProvider(now))

        save(
            id = item.id,
            projectID = project.id,
            title = item.title,
            status = item.status,
            tagIDs = emptyList(),
        )

        assertEquals(emptyList(), repository.workItem(item.id)?.tagIDs)
    }

    @Test
    fun `changing the status also re-ranks which a field update alone would not do`() = runTest {
        val repository = FakeProjectRepository()
        val project = repository.seedProject()
        val item = repository.seedWorkItem(projectID = project.id, status = WorkItemStatus.Todo)
        val save = SaveWorkItem(repository, TestDateProvider(now))

        val outcome = save(
            id = item.id,
            projectID = project.id,
            title = "Land the capsule",
            status = WorkItemStatus.Done,
        )

        assertEquals(WorkItemSaveOutcome.Updated(item.id, moved = true), outcome)
        assertEquals(1, repository.moveCallCount())
        assertEquals(WorkItemStatus.Done, repository.workItem(item.id)?.status)
    }

    @Test
    fun `a blank title is refused on save and nothing is written`() = runTest {
        val repository = FakeProjectRepository()
        val project = repository.seedProject()
        val item = repository.seedWorkItem(projectID = project.id)
        val save = SaveWorkItem(repository, TestDateProvider(now))

        assertFailsWith<AppError> {
            save(id = item.id, projectID = project.id, title = "  ", status = WorkItemStatus.Todo)
        }

        assertEquals(0, repository.moveCallCount())
    }

    @Test
    fun `saving with a cleared assignee clears it rather than leaving it alone`() = runTest {
        // The case a plain nullable cannot express on its own. `SaveWorkItem` always sends
        // a value — a `null` assigneeID *is* an instruction to unassign — so a port that read
        // "not mentioned" from it would keep the old assignee and report the edit as saved.
        val repository = FakeProjectRepository()
        val project = repository.seedProject()
        val adaID = UserID("33333333-3333-4333-8333-333333333333")
        val item = repository.createWorkItem(
            projectID = project.id,
            title = "Land the capsule",
            details = "",
            status = WorkItemStatus.Todo,
            priority = Priority.Medium,
            assigneeID = adaID,
            dueDate = null,
            now = now,
        )
        val save = SaveWorkItem(repository, TestDateProvider(now + 60.seconds))

        save(
            id = item.id,
            projectID = project.id,
            title = "Land the capsule",
            status = WorkItemStatus.Todo,
            assigneeID = null,
        )

        assertEquals(null, repository.workItem(item.id)?.assigneeID)
    }

    @Test
    fun `a patch that was not mentioned leaves the field exactly as it was`() = runTest {
        val repository = FakeProjectRepository()
        val project = repository.seedProject()
        val due = now + 86_400.seconds
        val adaID = UserID("33333333-3333-4333-8333-333333333333")
        val item = repository.createWorkItem(
            projectID = project.id,
            title = "Land the capsule",
            details = "",
            status = WorkItemStatus.Todo,
            priority = Priority.Medium,
            assigneeID = adaID,
            dueDate = due,
            now = now,
        )

        repository.updateWorkItem(
            id = item.id,
            title = "Renamed",
            details = null,
            priority = null,
            assigneeID = Patch.Unmentioned,
            dueDate = Patch.Unmentioned,
            storyPoints = Patch.Unmentioned,
            now = now + 60.seconds,
        )

        val stored = assertNotNull(repository.workItem(item.id))
        assertEquals(adaID, stored.assigneeID)
        assertEquals(due, stored.dueDate)
    }

    // MARK: RemoveProject

    @Test
    fun `a project with unfinished work is archived rather than deleted`() = runTest {
        val repository = FakeProjectRepository()
        val project = repository.seedProject()
        repository.seedWorkItem(projectID = project.id, status = WorkItemStatus.Todo)
        val remove = RemoveProject(repository, TestDateProvider(now))

        val outcome = remove(project.id)

        assertEquals(ProjectRemovalOutcome.Archived, outcome)
        assertEquals(1, repository.projectCount(), "archiving destroys nothing")
        assertTrue(assertNotNull(repository.project(project.id)).isArchived)
    }

    @Test
    fun `a closed item is a decision and keeps its project out of the delete path`() = runTest {
        // `isComplete` and not `isTerminal`. A closed item is finished with, but it is also
        // a record of a decision that was taken, so a project holding one is archived.
        val repository = FakeProjectRepository()
        val project = repository.seedProject()
        repository.seedWorkItem(projectID = project.id, status = WorkItemStatus.Closed)
        val remove = RemoveProject(repository, TestDateProvider(now))

        val outcome = remove(project.id)

        assertEquals(ProjectRemovalOutcome.Archived, outcome)
        assertEquals(1, repository.projectCount())
    }

    @Test
    fun `a project whose work is all delivered is deleted with its items`() = runTest {
        val repository = FakeProjectRepository()
        val project = repository.seedProject()
        repository.seedWorkItem(projectID = project.id, status = WorkItemStatus.Done)
        val remove = RemoveProject(repository, TestDateProvider(now))

        val outcome = remove(project.id)

        assertEquals(ProjectRemovalOutcome.Deleted, outcome)
        assertEquals(0, repository.projectCount())
        assertEquals(0, repository.itemCount(), "the cascade takes the items with it")
    }

    @Test
    fun `removing an already-archived project does nothing at all`() = runTest {
        val repository = FakeProjectRepository()
        val project = repository.seedProject()
        repository.seedWorkItem(projectID = project.id, status = WorkItemStatus.Todo)
        val clock = TestDateProvider(now)
        RemoveProject(repository, clock)(project.id)
        val changesBefore = repository.publishedChanges().size

        val outcome = RemoveProject(repository, clock)(project.id)

        assertEquals(ProjectRemovalOutcome.AlreadyArchived, outcome)
        assertEquals(
            changesBefore,
            repository.publishedChanges().size,
            "a second removal must not publish a change that did not happen",
        )
    }

    @Test
    fun `removing a project that no longer exists is refused`() = runTest {
        val repository = FakeProjectRepository()
        val remove = RemoveProject(repository, TestDateProvider(now))

        assertFailsWith<AppError> { remove(randomProjectID()) }
    }

    // MARK: AddComment

    @Test
    fun `a comment is stored with its body trimmed and its author`() = runTest {
        val repository = FakeProjectRepository()
        val project = repository.seedProject()
        val item = repository.seedWorkItem(projectID = project.id)
        val author = UserID("77777777-7777-4777-8777-777777777777")
        val post = AddComment(repository, TestDateProvider(now))

        val result = post(workItemID = item.id, authorID = author, body = "  Looks right to me  ")

        val success = assertIs<AddCommentResult.Success>(result)
        val posted = success.comment
        assertEquals("Looks right to me", posted.body, "the use case owns the trim, not the store")
        assertEquals(author, posted.authorID)
        assertEquals(
            listOf(posted.id),
            repository.comments(item.id).map { it.id },
            "the thread must hold exactly what the use case returned",
        )
    }

    @Test
    fun `a comment with mentions returns them in the result`() = runTest {
        val repository = FakeProjectRepository()
        val project = repository.seedProject()
        val item = repository.seedWorkItem(projectID = project.id)
        val author = UserID("77777777-7777-4777-8777-777777777777")
        val post = AddComment(repository, TestDateProvider(now))

        val result = post(workItemID = item.id, authorID = author, body = "Hey @alice and @<12345678-1234-1234-1234-123456789012>, please review")

        val success = assertIs<AddCommentResult.Success>(result)
        assertEquals(2, success.mentions.size)
        val mention1 = success.mentions[0] as MentionRules.Mention.ByUsername
        assertEquals("alice", mention1.username)
        val mention2 = success.mentions[1] as MentionRules.Mention.ByUserID
        assertEquals(UserID("12345678-1234-1234-1234-123456789012"), mention2.userID)
    }

    @Test
    fun `a comment without mentions returns empty mention list`() = runTest {
        val repository = FakeProjectRepository()
        val project = repository.seedProject()
        val item = repository.seedWorkItem(projectID = project.id)
        val author = UserID("77777777-7777-4777-8777-777777777777")
        val post = AddComment(repository, TestDateProvider(now))

        val result = post(workItemID = item.id, authorID = author, body = "No mentions here")

        val success = assertIs<AddCommentResult.Success>(result)
        assertEquals(emptyList(), success.mentions)
    }

    @Test
    fun `a blank comment is refused before the store is touched`() = runTest {
        // The refusal is on the *normalised* body: a check on the raw input would let
        // whitespace through as a comment that renders as nothing.
        val repository = FakeProjectRepository()
        val project = repository.seedProject()
        val item = repository.seedWorkItem(projectID = project.id)
        val author = UserID("77777777-7777-4777-8777-777777777777")
        val post = AddComment(repository, TestDateProvider(now))
        val changesBefore = repository.publishedChanges().size

        val result = post(workItemID = item.id, authorID = author, body = "   ")

        assertIs<AddCommentResult.EmptyBody>(result)
        assertEquals(emptyList(), repository.comments(item.id))
        assertEquals(
            changesBefore,
            repository.publishedChanges().size,
            "a refused comment must not announce one either",
        )
    }

    @Test
    fun `a reply to a comment that is not in the thread is refused`() = runTest {
        val repository = FakeProjectRepository()
        val project = repository.seedProject()
        val item = repository.seedWorkItem(projectID = project.id)
        val author = UserID("77777777-7777-4777-8777-777777777777")
        val post = AddComment(repository, TestDateProvider(now))

        val result = post(workItemID = item.id, authorID = author, body = "Replying to nothing", parentID = randomCommentID())

        assertIs<AddCommentResult.ParentNotFound>(result)

        assertEquals(
            emptyList(),
            repository.comments(item.id),
            "an orphaned reply addresses nobody, so it must not be stored",
        )
    }

    @Test
    fun `a reply to a comment in the thread is stored under it`() = runTest {
        val repository = FakeProjectRepository()
        val project = repository.seedProject()
        val item = repository.seedWorkItem(projectID = project.id)
        val author = UserID("77777777-7777-4777-8777-777777777777")
        val post = AddComment(repository, TestDateProvider(now))

        val rootResult = post(workItemID = item.id, authorID = author, body = "Root")
        val root = assertIs<AddCommentResult.Success>(rootResult).comment
        val replyResult = post(workItemID = item.id, authorID = author, body = "Answer", parentID = root.id)
        val reply = assertIs<AddCommentResult.Success>(replyResult).comment

        assertEquals(root.id, reply.parentID)
        assertEquals(
            listOf(root.id, reply.id),
            repository.comments(item.id).map { it.id },
            "the reply lands after its parent in the thread it belongs to",
        )
    }

    // MARK: LoadComments

    @Test
    fun `a load failure reaches the caller rather than an empty thread`() = runTest {
        // An empty list would be read as "no comments yet" by the editor, which is exactly
        // what a broken store must never claim to be.
        val repository = FakeProjectRepository()
        val project = repository.seedProject()
        val item = repository.seedWorkItem(projectID = project.id)
        repository.failure = AppError.Storage("disk is on fire")

        assertFailsWith<AppError> { LoadComments(repository)(item.id) }
    }
}
