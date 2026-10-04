#!/usr/bin/env python3
"""Mutation ritual for Phase 23.6 - the Sprints tab's wiring.

Applies one deliberate defect at a time to the routing module (the tab, its routes, the
deep links, the stack a sprint is opened on), to the design system's tab glyph, or to the
shell that renders the tab bar, runs the JVM host of whichever module owns it, records which
assertions caught it, and restores the file.

Three outcomes are kept apart, and the separation is the point:

  * **caught** - an assertion went red, and the message named the defect.
  * **rejected at compile time** - the type system refused the defect before any test ran.
    This is NOT evidence: 23.2 N3, 23.3 M4, 23.4 D14 and 23.5 P6/P9 all hit the case where a
    mutation that does not compile was recorded as "caught".
  * **nothing ran** - something else went wrong, and the result is not evidence either.

The results directory is cleared before each run so the three stay separable.

**Why the first three matter more than the rest.** The shell renders a tab root and a pushed
root identically (`NavigationHost` draws `TabRoot` only when the stack is empty, and
`SprintDetailScreen` is opaque), so the shape of the sprints stack cannot be seen in a
screenshot at all. S1, S2 and S3 are the three ways the stack could be wrong; only the third
was caught before this runner existed, and it was caught by a Compose test rather than by the
module that owns the stack.
"""
import glob
import re
import shutil
import subprocess
import sys

ROOT = ""
ROUTING_DIR = (
    "packages/routing/src/commonMain/kotlin/com/tasktracker/routing/"
)
DESIGN = "packages/design-system/src/commonMain/kotlin/com/tasktracker/designsystem/"
SHARED_UI = "shared-ui/src/commonMain/kotlin/com/tasktracker/sharedui/"

ROUTER = ROUTING_DIR + "Router.kt"
PARSER = ROUTING_DIR + "DeepLinkParser.kt"
ROUTES = ROUTING_DIR + "Routes.kt"
TAB_ICONS = DESIGN + "TabIcons.kt"
NAV_HOST = SHARED_UI + "NavigationHost.kt"

# One entry per module: (task, results directory).
ROUTING = (
    ":packages:routing:jvmTest",
    "packages/routing/build/test-results/jvmTest",
)
DESIGN_SYSTEM = (
    ":packages:design-system:jvmTest",
    "packages/design-system/build/test-results/jvmTest",
)
SHARED = (
    ":shared-ui:jvmTest",
    "shared-ui/build/test-results/jvmTest",
)

# Red before this phase and red on purpose, or documented as pre-existing. Never evidence of
# anything, and counting them would make every mutation look caught (PLAN.md).
PRE_EXISTING = (
    "a caption on the rendered glass fill still clears the large-text bar",
    "the hairline reads as a lit edge in light",
    "aChosenThemeOutlivesTheProcess",
    "theBoardReturnsToTheList",
)

MUTATIONS = [
    # ------------------------------------------------- the sprint stack: invisible in pixels
    # The defect this phase actually shipped and then fixed: a tapped row pushed the detail
    # onto whatever the tab had, which after a plain tab switch is nothing.
    ("S1", ROUTER, ROUTING,
     ").withPath(AppTab.Sprints, sprintStack(projectID, sprintID))",
     ").withPath(AppTab.Sprints, listOf(AppRoute.SprintDetail(projectID, sprintID)))"),
    # The same drift from the other end - the link stops installing the list. S1 and S2 are
    # the two halves of the pair the drift test compares, and only one can be wrong at a time.
    ("S2", PARSER, ROUTING,
     "path = sprintStack(projectID, sprintID),",
     "path = listOf(AppRoute.SprintDetail(projectID, sprintID)),"),
    # And `openProject` regressing to the empty stack it had before the root was named.
    ("S3", ROUTER, ROUTING,
     ".withPath(AppTab.Sprints, sprintListStack(projectID))",
     ".withPath(AppTab.Sprints, emptyList())"),

    # ----------------------------------------------------------------- the tab and its links
    # The tab forgetting it is project-scoped, which is what makes `openProject` on it a tab
    # switch rather than a push.
    ("S4", ROUTES, ROUTING,
     "            Board, Backlog, Sprints -> true\n"
     "            Projects, Settings -> false",
     "            Board, Backlog -> true\n"
     "            Projects, Sprints, Settings -> false"),
    # The wire value the tab is looked up and persisted by.
    ("S5", ROUTES, ROUTING,
     "    Sprints(\"sprints\"),",
     "    Sprints(\"sprintz\"),"),
    # A sprint segment that is missing or malformed accepted anyway, by reading the
    # *project* id out of the wrong slot. `parseSprintID` takes a UUID string and the project
    # slot holds one, so the link is accepted rather than refused - which is the whole point of
    # the parser's `return null`. The trailing `?: return null` is kept so the expression is
    # still a non-null `SprintID`; without it the mutation is rejected at compile time and
    # proves nothing.
    ("S6", PARSER, ROUTING,
     "                val sprintID = components.getOrNull(2)?.let { parseSprintID(it) } ?: return null",
     "                val sprintID = parseSprintID(components.getOrNull(1) ?: return null) ?: return null"),
    # The list link landing somewhere other than the Sprints tab.
    ("S7", PARSER, ROUTING,
     "                ParsedLink(AppTab.Sprints, route = null, path = sprintListStack(it))",
     "                ParsedLink(AppTab.Projects, route = null, path = sprintListStack(it))"),

    # --------------------------------------------------------------------- the glyph itself
    # Three equal bars: still three bars, still bottom-aligned, but no longer a sequence. The
    # KDoc on `sprints` says the rising is the whole reading, so this is the mutation that
    # checks the geometry test is reading geometry rather than counting rectangles.
    ("D1", TAB_ICONS, DESIGN_SYSTEM,
     "private val HEIGHTS = floatArrayOf(5.5f, 9.5f, 15.0f)",
     "private val HEIGHTS = floatArrayOf(10.5f, 10.5f, 10.5f)"),
    # Descending instead - a chart of decline rather than of iterations.
    ("D2", TAB_ICONS, DESIGN_SYSTEM,
     "private val HEIGHTS = floatArrayOf(5.5f, 9.5f, 15.0f)",
     "private val HEIGHTS = floatArrayOf(15.0f, 9.5f, 5.5f)"),
    # The glyph dropped from the walked set, which is what `all` exists to catch.
    ("D3", TAB_ICONS, DESIGN_SYSTEM,
     "    val all: List<ImageVector> = listOf(projects, board, backlog, sprints, settings)",
     "    val all: List<ImageVector> = listOf(projects, board, backlog, settings)"),

    # ------------------------------------------------------------------------- the tab bar
    # The shell rendering the Sprints tab with the Projects glyph.
    ("U1", NAV_HOST, SHARED,
     "    AppTab.Sprints -> ttTabIcons.sprints",
     "    AppTab.Sprints -> ttTabIcons.projects"),
    # And with the Projects label, which is the other half of "this tab is reachable".
    ("U2", NAV_HOST, SHARED,
     "    AppTab.Sprints -> stringResource(Res.string.tab_sprints)",
     "    AppTab.Sprints -> stringResource(Res.string.tab_projects)"),
    # The Sprints tab drawing a board. `SprintsDestination` and `BoardDestination` take the
    # same five arguments, which is what makes this a one-word mutation that compiles; the
    # switcher callback is left alone so the board's own navigation still works and the
    # failure is about *which screen* the tab shows rather than about a crash.
    ("U3", NAV_HOST, SHARED,
     "        ) { projectID ->\n"
     "            SprintsDestination(",
     "        ) { projectID ->\n"
     "            BoardDestination("),
]

def read(path):
    with open(ROOT + path) as handle:
        return handle.read()


def write(path, text):
    with open(ROOT + path, "w") as handle:
        handle.write(text)


def read_failures(results):
    xmls = glob.glob(ROOT + results + "/*.xml")
    if not xmls:
        return None
    names = []
    for xml in xmls:
        with open(xml) as handle:
            text = handle.read()
        for block in re.findall(r'<testcase name="([^"]+)"[^>]*>\s*<failure', text):
            names.append(re.sub(r"\[jvm\]$", "", block))
    return [name for name in names if name not in PRE_EXISTING]


def main():
    paths = {ROUTER, PARSER, ROUTES, TAB_ICONS, NAV_HOST}
    originals = {path: read(path) for path in paths}
    uncaught = []
    rejected = []
    for name, path, module, old, new in MUTATIONS:
        task, results = module
        text = originals[path]
        if old not in text:
            print(f"{name}: PATTERN NOT FOUND in {path}")
            uncaught.append(name)
            continue
        shutil.rmtree(ROOT + results, ignore_errors=True)
        write(path, text.replace(old, new, 1))
        completed = subprocess.run(
            ["./gradlew", task, "--continue"],
            capture_output=True, text=True,
        )
        caught = read_failures(results)
        write(path, originals[path])

        if caught is None:
            compiled_away = "Compilation error" in completed.stdout + completed.stderr
            if compiled_away:
                print(f"{name}: REJECTED AT COMPILE TIME -> no evidence")
                rejected.append(name)
            else:
                print(f"{name}: NOTHING RAN -> the build failed in a way that produced no results")
                uncaught.append(name)
            continue

        status = "caught" if caught else "NOT CAUGHT"
        print(f"{name}: {status} -> {', '.join(caught) if caught else '(nothing went red)'}")
        if not caught:
            uncaught.append(name)

    print()
    print(f"caught: {len(MUTATIONS) - len(uncaught) - len(rejected)}"
          f"  compile-rejected: {len(rejected)}  uncaught: {len(uncaught)}")
    if rejected:
        print("COMPILE-REJECTED (not evidence):", ", ".join(rejected))
    if uncaught:
        print("MUTATIONS WITH NO EVIDENCE:", ", ".join(uncaught))
        return 1
    if rejected:
        return 1
    print(f"all {len(MUTATIONS)} mutations caught")
    return 0


if __name__ == "__main__":
    sys.exit(main())