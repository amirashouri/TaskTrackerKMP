package com.tasktracker.routing

import com.tasktracker.core.parseProjectID
import com.tasktracker.core.parseSprintID
import com.tasktracker.core.parseWorkItemID

/**
 * Parses `tasktracker://` URLs into routes.
 *
 * Kept as a pure function with no UI or container dependency so the whole link
 * grammar is unit-testable in isolation — and so it can be exercised from a plain
 * JVM test rather than needing a URL that only a platform type can represent.
 */
object DeepLinkParser {
    /**
     * Host-and-path grammar:
     * ```
     * tasktracker://projects                      -> the dashboard
     * tasktracker://settings                      -> settings
     * tasktracker://board/<projectID>             -> a project's board, as the Board tab's root
     * tasktracker://backlog/<projectID>           -> a project's backlog, as the Backlog tab's root
     * tasktracker://archive/<projectID>           -> a project's archive
     * tasktracker://sprints/<projectID>           -> a project's sprints, as the tab's root
     * tasktracker://sprint/<projectID>/<sprintID> -> one sprint's own screen
     * tasktracker://workitem/<projectID>/<itemID> -> edit a specific work item
     * tasktracker://workitem/<projectID>/new      -> create a work item
     * ```
     *
     * Takes a [String] rather than a platform `URL` because common Kotlin has no URL
     * type: the iOS shell passes `URL.absoluteString` and the Android shell
     * `Intent.data.toString()`, and both are plain text by the time anything shared
     * can read them.
     */
    fun route(url: String): ParsedLink? {
        val components = split(url) ?: return null
        val root = components.firstOrNull() ?: return null

        return when (root) {
            "projects", "dashboard" -> ParsedLink(AppTab.Projects, route = null, path = emptyList())
            "settings" -> ParsedLink(AppTab.Settings, route = null, path = emptyList())

            "board" -> components.projectIDAt(1)?.let {
                ParsedLink(AppTab.Board, route = null, path = listOf(AppRoute.Board(it)))
            }

            "backlog" -> components.projectIDAt(1)?.let {
                ParsedLink(AppTab.Backlog, route = null, path = listOf(AppRoute.Backlog(it)))
            }

            // The archive is a project-scoped screen, but it is reached from the Board
            // tab rather than being one, so the link lands on Board with the archive
            // pushed on top of its root.
            "archive" -> components.projectIDAt(1)?.let {
                ParsedLink(AppTab.Board, route = null, path = listOf(AppRoute.Archive(it)))
            }

            // The sprints tab *is* a tab, so this is its root: the same shape as `board` and
            // `backlog`, both of which also name their root as a route rather than leaving
            // the stack empty. `sprintListStack` is `Router.openProject`'s definition too, so
            // arriving by link and choosing a project describe the same state.
            "sprints" -> components.projectIDAt(1)?.let {
                ParsedLink(AppTab.Sprints, route = null, path = sprintListStack(it))
            }

            // One sprint, with the list underneath it. The detail is presented as the
            // `route` so the shell can decide whether to animate it over the list, and the
            // stack comes from `sprintStack` — the same function `Router.openSprint` uses for
            // a tapped row, which is what stops a link and a tap describing different
            // histories for the same screen.
            "sprint" -> {
                val projectID = components.projectIDAt(1) ?: return null
                val sprintID = components.getOrNull(2)?.let { parseSprintID(it) } ?: return null
                ParsedLink(
                    tab = AppTab.Sprints,
                    route = AppRoute.SprintDetail(projectID, sprintID),
                    path = sprintStack(projectID, sprintID),
                )
            }

            "workitem" -> {
                val projectID = components.projectIDAt(1) ?: return null
                val workItemID = when {
                    components.size < 3 -> null
                    // "new" is a keyword, not a malformed id — a reader must not be told
                    // the link is broken because it is a create link.
                    components[2] == "new" -> null
                    else -> parseWorkItemID(components[2]) ?: return null
                }
                val editor = AppRoute.WorkItemEditor(projectID, workItemID)
                ParsedLink(AppTab.Board, route = editor, path = listOf(AppRoute.Board(projectID), editor))
            }

            else -> null
        }
    }

    private fun List<String>.projectIDAt(index: Int): com.tasktracker.core.ProjectID? =
        getOrNull(index)?.let { parseProjectID(it) }

    /**
     * Splits into lowercased host + path segments, or returns null if this is not one of
     * our links.
     *
     * Stands in for Foundation's `url.host` and `url.pathComponents`, which between them
     * gave the original scheme, host and path for free. The parts that are easy to lose:
     *
     *  - an empty authority is dropped rather than treated as an empty first component, so
     *    `tasktracker:///board/x` parses like `tasktracker://board/x` instead of failing on
     *    a blank root;
     *  - the query and fragment are stripped, because Foundation keeps them out of
     *    `pathComponents` — without this a `?utm=` would corrupt the trailing id;
     *  - percent escapes are decoded, also because Foundation did it before the original
     *    ever saw the component.
     */
    private fun split(url: String): List<String>? {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd <= 0) return null
        if (!url.substring(0, schemeEnd).equals(DeepLinkScheme.TASK_TRACKER, ignoreCase = true)) return null

        val afterScheme = url.substring(schemeEnd + 3)
        val withoutTail = afterScheme.takeWhile { it != '?' && it != '#' }

        return withoutTail.split('/')
            .map { it.percentDecoded() }
            .filter { it.isNotEmpty() }
            .map { it.lowercase() }
    }

    /** Decodes `%XX` escapes. Malformed escapes are left as written rather than thrown on. */
    private fun String.percentDecoded(): String {
        if (!contains('%')) return this
        val out = StringBuilder(length)
        var i = 0
        while (i < length) {
            val char = this[i]
            if (char == '%' && i + 2 < length) {
                val hex = substring(i + 1, i + 3)
                val decoded = hex.toIntOrNull(16)
                if (decoded != null) {
                    out.append(decoded.toChar())
                    i += 3
                    continue
                }
            }
            out.append(char)
            i++
        }
        return out.toString()
    }
}
