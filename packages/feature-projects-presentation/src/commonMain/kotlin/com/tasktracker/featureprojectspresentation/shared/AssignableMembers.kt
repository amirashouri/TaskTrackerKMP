package com.tasktracker.featureprojectspresentation

import com.tasktracker.domain.Project
import com.tasktracker.featureteamsdomain.User

/**
 * The people a task on [project] may be assigned to: that project's members, as the directory
 * can name them.
 *
 * ## Why this is a presentation rule and not a domain one
 *
 * The *rule* — who belongs to a project — is already persisted as `Project.memberIDs` and owned
 * by `domain`. What this adds is the join from those ids to the profiles a picker can show,
 * which needs the directory, and `feature-projects-presentation` is the only layer that holds
 * both. Putting it in `feature-projects-domain` would make the domain depend on the directory
 * to answer a question it already has the data for; putting it in `shared-ui` would put a
 * business rule in the module whose whole job is to bind others' rules together.
 *
 * Public rather than `internal`, because the wiring lives in `shared-ui`, a different Gradle
 * module that cannot reach an `internal` declaration. Its callers are the `searchMembers`
 * lambdas feeding a `MemberPicker`; each applies it to the directory result it is about to
 * hand over.
 *
 * ## Membership order, not directory order
 *
 * The result follows `project.memberIDs`, which is the order the user arranged their members
 * in. The directory's own order is alphabetical by name and belongs to the *unfiltered*
 * picker; re-sorting here would silently reorder the project editor's own list the moment the
 * user had put someone at the top on purpose. An intersection that preserves the order of
 * whichever list the user curated is the one that cannot surprise them.
 *
 * ## Ids the directory cannot name are dropped, not rendered raw
 *
 * A member added on another device, or one this device's directory has not caught up with,
 * resolves to no [User]. The same reasoning as `WorkItemCard`'s assignee row applies: this
 * surface does not invent a label for a person it cannot name, and a raw `0195…` in a list of
 * names is noise. Dropping it costs the picker an entry and keeps the count honest.
 *
 * ## An empty membership falls back to the whole directory — a deliberate, documented leak
 *
 * A project with no members recorded is a project nobody has configured yet, not a project
 * with no members, and the two are indistinguishable in the data. Offering nobody would make
 * every freshly created project unassignable, which is a far worse failure than offering
 * someone who should not be there: the first blocks all work, the second shows one extra name
 * in a list the user is choosing from rather than auto-assigning anyone.
 *
 * So the fallback is deliberate, and it is the one place 1.6 does not hold. It is stated here
 * rather than left to be discovered, because the next reader needs to know this is a choice:
 * the filter is exactly as strict as the membership it is given, and a project with no
 * membership has none to be strict about.
 */
fun assignableMembersFor(
    project: Project?,
    directory: List<User>,
): List<User> {
    // No project — the project editor's own member picker, or a board before its project has
    // loaded. Scoping to nothing here would empty a picker the user is mid-way through using.
    if (project == null) return directory

    val memberIDs = project.memberIDs
    if (memberIDs.isEmpty()) return directory

    val byID = directory.associateBy { it.id }
    return memberIDs.mapNotNull { byID[it] }
}