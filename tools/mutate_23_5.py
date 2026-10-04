#!/usr/bin/env python3
"""Mutation ritual for Phase 23.5.

Applies one deliberate defect at a time to the sprint use cases, the sprint models, the
summary's verdict, or the date labels 23.5 made public, runs the JVM host of whichever
module owns it, records which assertions caught it, and restores the file.

Three outcomes are kept apart, and the separation is the point:

  * **caught** - an assertion went red, and the message named the defect.
  * **rejected at compile time** - the type system refused the defect before any test ran.
    This is NOT evidence: the earlier phases each hit the case where a mutation that does
    not compile was recorded as "caught", which inflates the count to the point where the
    table means nothing. 23.2 N3, 23.3 M4 and 23.4 D14 all did this.
  * **nothing ran** - something else went wrong, and the result is not evidence either.

The results directory is cleared before each run so the three stay separable. 23.1 and
23.3 each forgot this and produced a "caught" that was really a leftover failure from the
previous run.
"""
import glob
import re
import shutil
import subprocess
import sys

ROOT = ""
FEATURE_PRESENTATION = (
    "packages/feature-projects-presentation/src/commonMain/kotlin/"
    "com/tasktracker/featureprojectspresentation/"
)
FEATURE_DOMAIN = (
    "packages/feature-projects-domain/src/commonMain/kotlin/"
    "com/tasktracker/featureprojectsdomain/"
)
DESIGN = "packages/design-system/src/commonMain/kotlin/com/tasktracker/designsystem/"

MODELS = FEATURE_PRESENTATION + "SprintModels.kt"
EDITOR = FEATURE_PRESENTATION + "SprintEditorModel.kt"
SCREENS = FEATURE_PRESENTATION + "SprintScreens.kt"
USE_CASES = FEATURE_DOMAIN + "SprintUseCases.kt"
DATE_FIELD = DESIGN + "DateField.kt"

# One entry per module: (task, results directory, set of files that module's tests read).
PRESENTATION = (
    ":packages:feature-projects-presentation:jvmTest",
    "packages/feature-projects-presentation/build/test-results/jvmTest",
)
DOMAIN = (
    ":packages:feature-projects-domain:jvmTest",
    "packages/feature-projects-domain/build/test-results/jvmTest",
)
DESIGN_SYSTEM = (
    ":packages:design-system:jvmTest",
    "packages/design-system/build/test-results/jvmTest",
)

# Red before this phase and red on purpose (PLAN.md, Phase 22). Never evidence of
# anything, and counting them would make every mutation look caught.
PRE_EXISTING = (
    "a caption on the rendered glass fill still clears the large-text bar",
    "the hairline reads as a lit edge in light",
)

MUTATIONS = [
    # ------------------------------------------------------------------ domain: the rules
    # A sprint windowed backwards. The store tolerates one from a server, so nothing else
    # in the write path would notice.
    ("N1", USE_CASES, DOMAIN,
     "        requireWindow(startDate = startDate, endDate = endDate)\n"
     "        return repository.createSprint(",
     "        return repository.createSprint("),
    # The partial-edit check reading only its own arguments, which accepts exactly the edit
    # easiest to make by accident: moving the start past an end it was never told about.
    ("N2", USE_CASES, DOMAIN,
     "        requireWindow(\n"
     "            startDate = startDate ?: existing.startDate,\n"
     "            endDate = endDate ?: existing.endDate,\n"
     "        )",
     "        if (startDate != null && endDate != null) {\n"
     "            requireWindow(startDate = startDate, endDate = endDate)\n"
     "        }"),
    # Clearing the owner read as "leave it alone" - the invisible failure Patch exists for.
    ("N3", USE_CASES, DOMAIN,
     "            owner = if (owner is Patch.Of) {\n"
     "                Patch.Of(WorkItemRules.normaliseAssignee(owner.value))\n"
     "            } else {\n"
     "                Patch.Unmentioned\n"
     "            },",
     "            owner = Patch.Unmentioned,"),
    # A sprint's name shouted, which a list row and a detail header both render verbatim.
    #
    # The obvious mutation here — dropping the `normaliseName` call altogether — was tried
    # first and recorded NOT CAUGHT, and it is not evidence for anything: the fake's
    # `createSprint` normalises the name too, exactly as `RoomProjectRepository` does, so
    # removing the use case's copy is invisible through this port. That is the fake doing
    # its job (a fake that diverged from the store is how the two would drift apart), and
    # the lesson is that "delete the redundant call" is not a mutation when the layer below
    # already does the same thing.
    ("N4", USE_CASES, DOMAIN,
     "        val name = ProjectRules.normaliseName(name)\n"
     "        // `normaliseAssignee`",
     "        val name = ProjectRules.normaliseName(name).uppercase()\n"
     "        // `normaliseAssignee`"),
    # A whitespace owner stored, which fails an equality check against every real name -
    # so "am I the owner" is false on a sprint whose owner field looks empty.
    ("N5", USE_CASES, DOMAIN,
     "        val owner = WorkItemRules.normaliseAssignee(owner)\n"
     "        requireWindow(",
     "        val owner = owner\n"
     "        requireWindow("),

    # ------------------------------------------------------- presentation: the row's numbers
    # A row reporting what its sprint committed to as what it achieved.
    ("P1", MODELS, PRESENTATION,
     "                achievedStoryPoints = summary.achievedStoryPoints,",
     "                achievedStoryPoints = summary.committedStoryPoints,"),
    # Progress clamped, which throws away the overshoot of a sprint that beat its target.
    ("P2", MODELS, PRESENTATION,
     "            achievedStoryPoints.toFloat() / sprint.goalStoryPoints.toFloat()",
     "            (achievedStoryPoints.toFloat() / sprint.goalStoryPoints.toFloat())\n"
     "                .coerceAtMost(1f)"),
    # A sprint with no goal drawing a bar at zero - claiming it achieved nothing.
    ("P3", MODELS, PRESENTATION,
     "        get() = if (!hasGoal) {\n            null",
     "        get() = if (false) {\n            null"),
    # "Loading" collapsed into "empty", so a cold start flashes an empty state over a
    # populated list.
    ("P4", MODELS, PRESENTATION,
     "    val isEmpty: Boolean? get() = if (isLoading) null else rows.isEmpty()",
     "    val isEmpty: Boolean? get() = rows.isEmpty()"),
    # The open-sprint check inverted, so the create affordance appears exactly when it
    # must not.
    ("P5", MODELS, PRESENTATION,
     "    val hasOpenSprint: Boolean get() = rows.any { !it.sprint.isFinished }",
     "    val hasOpenSprint: Boolean get() = rows.any { it.sprint.isFinished }"),
    # The zone the model was handed ignored, and UTC used instead — which is exactly the
    # defect `sprintLocalDate`/`sprintInstant` are parameterised to prevent, and the one a
    # UTC-only test suite would never see.
    #
    # The first attempt at this mutation used `date.atTime(12, 0).toInstant(timeZone)` and
    # was REJECTED AT COMPILE TIME: those are `expect`-declared extensions, so the call needs
    # imports the file does not have. A mutation that does not compile proves nothing, and
    # is counted separately in the summary below for that reason.
    ("P6", MODELS, PRESENTATION,
     "internal fun sprintInstant(date: LocalDate, timeZone: TimeZone): Instant =\n"
     "    date.atStartOfDayIn(timeZone)",
     "internal fun sprintInstant(date: LocalDate, timeZone: TimeZone): Instant =\n"
     "    date.atStartOfDayIn(TimeZone.UTC)"),

    # ------------------------------------------------- presentation: the editor's decisions
    # A one-day sprint refused, because the window test became strict.
    ("P7", EDITOR, PRESENTATION,
     "            return end < start",
     "            return end <= start"),
    # A blank goal stored as a real zero: "nobody estimated this" and "this is worth
    # nothing" become the same value, which is exactly what story points are nullable to
    # prevent.
    ("P8", EDITOR, PRESENTATION,
     "            trimmed.isEmpty() -> null\n"
     "            else -> trimmed.toIntOrNull()?.takeIf { it >= 0 }",
     "            trimmed.isEmpty() -> 0\n"
     "            else -> trimmed.toIntOrNull() ?: 0"),
    # The form forgetting it has changes, so an edit silently saves nothing.
    #
    # Two earlier forms of this proved nothing, and both are worth recording. The first
    # appended `|| false` to the disjunction: `false || (x)` is `x`, so it was not a mutation
    # at all, and it recorded NOT CAUGHT. The second truncated the whole getter to
    # `get() = isCreating`, which was REJECTED AT COMPILE TIME — it orphaned the four
    # `original*` vals this build treats as errors. So the condition is short-circuited
    # instead, which compiles and is a real defect: every edit now reads as unchanged, and
    # every save is skipped.
    ("P9", EDITOR, PRESENTATION,
     "        get() = isCreating || (\n"
     "            name.trim() != originalName ||\n"
     "                goalStoryPoints != originalGoalStoryPoints ||\n"
     "                startDate != originalStartDate ||\n"
     "                endDate != originalEndDate ||\n"
     "                owner.trim() != originalOwner.orEmpty()\n"
     "            )",
     "        get() = isCreating || (false && (\n"
     "            name.trim() != originalName ||\n"
     "                goalStoryPoints != originalGoalStoryPoints ||\n"
     "                startDate != originalStartDate ||\n"
     "                endDate != originalEndDate ||\n"
     "                owner.trim() != originalOwner.orEmpty()\n"
     "            ))"),
    # Clearing the owner dropped on the way to the port - the same invisible failure as N3,
    # reached from the other end, so the two mutations pin both halves.
    ("P10", EDITOR, PRESENTATION,
     "                        Patch.Of(owner.trim().takeIf { it.isNotEmpty() })",
     "                        Patch.Unmentioned"),
    # The start date's comparison inverted, so an unchanged edit looks changed and a
    # changed one looks unchanged — which turns every edit into a no-op.
    #
    # The first attempt compared the dates as *instants* instead, on the theory that the
    # stored instant is not necessarily at midnight and so the LocalDate comparison might
    # hide something. It recorded NOT CAUGHT because `sprintInstant` round-trips exactly, so
    # the two comparisons agree for every input: an equivalent mutation, not a defect. The
    # LocalDate choice is still the right one and it is asserted directly in
    # `an unchanged edit is not a change`, which re-picks the same day and expects no change
    # — but it cannot be mutated into failing, because it makes no observable difference.
    ("P11", EDITOR, PRESENTATION,
     "                startDate != originalStartDate ||",
     "                startDate == originalStartDate ||"),

    # ------------------------------------------------- presentation: who may close a sprint
    # An unowned sprint locked to nobody, leaving a sprint nobody can ever close.
    ("P12", EDITOR, PRESENTATION,
     "        get() = !sprint.isFinished && (!hasOwner || isViewerTheOwner)",
     "        get() = !sprint.isFinished && isViewerTheOwner"),
    # Finishing offered on a sprint that is already finished, which is the write the store
    # refuses because the summary is computed from the items as they are read.
    ("P13", EDITOR, PRESENTATION,
     "        get() = !sprint.isFinished && (!hasOwner || isViewerTheOwner)",
     "        get() = (!hasOwner || isViewerTheOwner)"),
    # An unidentified viewer treated as the owner, which puts a Finish button on screen for
    # an identity the app has not established.
    ("P14", EDITOR, PRESENTATION,
     "            isViewerTheOwner = sprint.owner != null && sprint.owner == currentUserName,",
     "            isViewerTheOwner = sprint.owner == null || sprint.owner == currentUserName,"),

    # -------------------------------------------- presentation: the verdict, read not computed
    # The third state collapsed into the second, so a sprint nobody gave a target is
    # reported as having missed one.
    ("P15", SCREENS, PRESENTATION,
     "internal fun sprintVerdict(didHitGoal: Boolean?): SprintVerdict = when (didHitGoal) {\n"
     "    true -> SprintVerdict.Hit\n"
     "    false -> SprintVerdict.Miss\n"
     "    null -> SprintVerdict.NoGoal\n"
     "}",
     "internal fun sprintVerdict(didHitGoal: Boolean?): SprintVerdict =\n"
     "    if (didHitGoal == true) SprintVerdict.Hit else SprintVerdict.Miss"),
    # And the first state collapsed into the second, which is the `>` that gets "exactly at
    # the goal" wrong.
    ("P16", SCREENS, PRESENTATION,
     "internal fun sprintVerdict(didHitGoal: Boolean?): SprintVerdict = when (didHitGoal) {\n"
     "    true -> SprintVerdict.Hit",
     "internal fun sprintVerdict(didHitGoal: Boolean?): SprintVerdict = when (didHitGoal) {\n"
     "    true -> SprintVerdict.Miss"),

    # ------------------------------------------------- design system: the labels 23.5 reused
    # The year printed twice in a window, which makes a single-year sprint read as though
    # it spans a year boundary - the exact thing a reader of a sprint window checks for.
    ("D1", DATE_FIELD, DESIGN_SYSTEM,
     "        \"${ttDateLabelWithoutYear(start)} – ${ttDateLabel(end)}\"",
     "        \"${ttDateLabel(start)} – ${ttDateLabel(end)}\""),
    # The year dropped across a boundary, hiding the very thing it exists to show.
    ("D2", DATE_FIELD, DESIGN_SYSTEM,
     "    } else {\n"
     "        \"${ttDateLabel(start)} – ${ttDateLabel(end)}\"\n"
     "    }",
     "    } else {\n"
     "        \"${ttDateLabelWithoutYear(start)} – ${ttDateLabelWithoutYear(end)}\"\n"
     "    }"),
    # The year dropped from the label itself, so every date on every surface loses it.
    ("D3", DATE_FIELD, DESIGN_SYSTEM,
     "    \"${date.day} ${ttMonthName(date.month)} ${date.year}\"",
     "    \"${date.day} ${ttMonthName(date.month)}\""),
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
    paths = {MODELS, EDITOR, SCREENS, USE_CASES, DATE_FIELD}
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
