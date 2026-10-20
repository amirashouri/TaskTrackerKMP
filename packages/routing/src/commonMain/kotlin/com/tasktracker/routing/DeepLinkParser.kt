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
     * tasktracker://connect?url=<encoded URL>     -> settings, with the address prefilled
     * ```
     *
     * The last shape is the CLI's pairing link. It is the only one whose payload is in the
     * query rather than the path, so [connectLink] reads the raw string; [split] deliberately
     * drops the query so a `?utm=` cannot corrupt a trailing id.
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

            // The pairing link. A path after the host would be a second shape for one idea,
            // so it is refused rather than ignored; every other root uses the path.
            "connect" -> if (components.size == 1) connectLink(url) else null

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
     * `tasktracker://connect?url=<percent-encoded server URL>`.
     *
     * The value is percent-decoded here and **exactly once**. The CLI encodes the URL whole, so
     * a URL that itself carries a `%` sequence arrives with its `%` written as `%25` and
     * survives the one decode intact; decoding twice would turn a legitimate `%2F` in a path
     * into `/` and change the address. Nothing else in the link is decoded.
     *
     * A malformed escape is refused rather than passed through, because the alternative is
     * prefilling a different address from the one the CLI printed. This is a check on the
     * *link*, not on the server: scheme policy, reachability and normalisation belong to
     * `CheckServerAddress`, which runs when the prefilled sheet is saved, so this function
     * never decides whether the URL is usable.
     */
    private fun connectLink(url: String): ParsedLink? {
        // The fragment is stripped before looking for `?`: a question mark after `#` is part
        // of the fragment, not a query.
        val withoutFragment = url.substringAfter("://", "").substringBefore('#')
        val query = withoutFragment.substringAfter('?', "")
        if (query.isEmpty()) return null

        val encoded = query.split('&')
            .firstOrNull { it.substringBefore('=') == "url" }
            ?.substringAfter('=', "")
            .orEmpty()
        if (encoded.isEmpty()) return null

        val address = encoded.percentDecodedExactlyOnce() ?: return null
        if (address.isEmpty()) return null

        return ParsedLink(
            tab = AppTab.Settings,
            route = null,
            path = emptyList(),
            serverAddress = address,
        )
    }

    /**
     * Decodes `%XX` escapes, refusing a malformed one.
     *
     * Unlike [percentDecoded], which keeps the rest of the grammar forgiving, the pairing value
     * has one correct answer: a truncated escape or a non-hex pair means the link is broken and
     * the honest response is to reject it.
     */
    private fun String.percentDecodedExactlyOnce(): String? {
        if (!contains('%')) return this
        val out = StringBuilder(length)
        var i = 0
        while (i < length) {
            val char = this[i]
            if (char != '%') {
                out.append(char)
                i++
                continue
            }
            if (i + 2 >= length) return null
            val hex = substring(i + 1, i + 3)
            if (!hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
            out.append(hex.toInt(16).toChar())
            i += 3
        }
        return out.toString()
    }

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
