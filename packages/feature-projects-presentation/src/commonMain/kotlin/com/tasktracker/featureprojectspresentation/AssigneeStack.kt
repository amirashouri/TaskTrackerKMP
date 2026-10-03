package com.tasktracker.featureprojectspresentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttAvatarDescription
import com.tasktracker.designsystem.ttInitialsFor
import com.tasktracker.designsystem.ttInitialsStack
import com.tasktracker.domain.WorkItem
import com.tasktracker.resources.Res
import com.tasktracker.resources.board_assignees_description
import org.jetbrains.compose.resources.pluralStringResource

/**
 * Who is on this board, as overlapping initials at the header's trailing end.
 *
 * ## Why the header and not the cards
 *
 * Every card already names its own assignee. A stack *on each card* would repeat the same
 * project-level fact 200 times to answer a question that is not about the card: "who is on this
 * board" is asked once, and it is answered by a header that is on screen for every lane at once.
 * On a 300dp card the stack would also cost 88dp of the width the card's own title is read in.
 *
 * ## Why it is not a filter control
 *
 * Because it does not filter anything yet. It is a report of who appears on the board, and making
 * it look pressable would promise a tap that does nothing. That is a decision rather than an
 * omission: the interaction belongs with the behaviour, and the behaviour does not exist yet.
 *
 * ## What it says out loud
 *
 * [boardAssigneeInitials] deduplicates free text, so the discs are distinct *strings*, not
 * distinct people: "Amir" and "amir" are two discs and two people called Amir are one. The
 * description reports the same names rather than a count of humans, because a count of humans is
 * a claim this data cannot support.
 */
@Composable
internal fun AssigneeStack(
    names: List<String>,
    modifier: Modifier = Modifier,
) {
    if (names.isEmpty()) return

    ttInitialsStack(
        initials = names.map { ttInitialsFor(it) },
        modifier = modifier.ttAvatarDescription(
            pluralStringResource(
                Res.plurals.board_assignees_description,
                names.size,
                names.size,
                names.joinToString(),
            ),
        ),
    )
}

/**
 * The people a board's stack is drawn from, in the order the discs are laid out.
 *
 * ## Why sorted and not in column order
 *
 * Column order puts "the first Todo card's assignee" in the leftmost, fully readable disc, which
 * is an accident of drag history rather than a fact about the project. Sorting by name makes the
 * leftmost disc the alphabetically first person every time, so the same board always draws its
 * people the same way round.
 *
 * ## Why case-insensitively, and what that leaves alone
 *
 * `lowercase()` is the key so that "amir" and "Amir" land next to each other rather than at
 * opposite ends of the stack. Two names that differ only in case then compare *equal*, and a
 * stable sort leaves them in board order - which is deterministic for a given board and is not a
 * claim about the alphabet.
 *
 * ## Why it returns names and not initials
 *
 * Because the names are also what the stack *says*: the description under the discs is the same
 * list, joined. A function that returned initials would force the caller to recover the names for
 * the description, and the two copies of the rule - trim, drop blanks, distinct, sort - would
 * then be free to disagree.
 *
 * ## Why it is a named function
 *
 * Because three separate decisions live in it - drop blanks, deduplicate, sort - and all three are
 * invisible in a composition: a stack that quietly showed "amir" twice, or drew a blank disc for
 * an empty assignee, would look correct and be wrong. `WorkItemCardRulesTest` holds this one
 * without rendering anything.
 */
internal fun boardAssigneeNames(items: List<WorkItem>): List<String> = items
    .mapNotNull { it.assignee }
    .filter { it.isNotBlank() }
    .map { it.trim() }
    .distinct()
    .sortedBy { it.lowercase() }

const val ProjectBoardAssigneesTag = "board.assignees"

@Preview
@Composable
private fun AssigneeStackPreview() = ProjectsPreview(ThemeScheme.Dark) {
    AssigneeStack(names = boardAssigneeNames(ViewPreviewData.workItems))
}