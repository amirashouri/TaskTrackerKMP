#!/usr/bin/env python3
"""Mutation ritual for Phase 23.4.

Applies one deliberate defect at a time to the design system's date field or story points,
runs the JVM host, records which assertions caught it, and restores the file.

The three outcomes are kept distinct, and the distinction matters: "caught" means an assertion
went red, "rejected at compile time" means the type system refused the defect before any test
ran, and "nothing ran" means something else went wrong and the result is not evidence of
anything.

`GlassSurfacePixelTest` has two tests that are red *by design* on every run (PLAN.md, Phase
22). They are excluded from the caught/not-caught comparison by name, because counting them
would make every mutation look caught and the table meaningless -- the same failure mode 23.1
and 23.3 hit twice.
"""
import glob
import re
import shutil
import subprocess
import sys

DESIGN = "packages/design-system/src/commonMain/kotlin/com/tasktracker/designsystem/"
DATE_FIELD = DESIGN + "DateField.kt"
STORY_POINTS = DESIGN + "StoryPoints.kt"
RESULTS = "packages/design-system/build/test-results/jvmTest"

# Red before this phase and red on purpose. Never evidence of anything.
PRE_EXISTING = (
    "a caption on the rendered glass fill still clears the large-text bar",
    "the hairline reads as a lit edge in light",
)

MUTATIONS = [
    # A grid that resizes is a calendar that moves the control under the user's finger.
    ("D1", DATE_FIELD,
     "internal const val TT_MONTH_GRID_CELLS: Int = TT_MONTH_GRID_ROWS * TT_MONTH_GRID_COLUMNS",
     "internal const val TT_MONTH_GRID_CELLS: Int = TT_MONTH_GRID_ROWS * TT_MONTH_GRID_COLUMNS - 7"),
    # A month shifted by one cell puts the 1st under the wrong column.
    ("D2", DATE_FIELD,
     "val leadingBlanks = (firstOfMonth.dayOfWeek.isoDayNumber - firstDayOfWeek.isoDayNumber + 7) % 7",
     "val leadingBlanks = (firstOfMonth.dayOfWeek.isoDayNumber - firstDayOfWeek.isoDayNumber + 6) % 7"),
    # February 29th falls off the end of the grid -- the only month where this matters.
    ("D3", DATE_FIELD,
     "        .plus(1, DateTimeUnit.MONTH)\n        .plus(-1, DateTimeUnit.DAY)\n        .day",
     "        .plus(1, DateTimeUnit.MONTH)\n        .plus(-2, DateTimeUnit.DAY)\n        .day"),
    # Stepping by days rather than months: the popup lands mid-week.
    ("D4", DATE_FIELD,
     "LocalDate(month.year, month.month, 1).plus(months, DateTimeUnit.MONTH)",
     "LocalDate(month.year, month.month, 1).plus(months * 30, DateTimeUnit.DAY)"),
    # A header that ignores the grid's week start, so Monday and Tuesday swap places.
    ("D5", DATE_FIELD,
     "DayOfWeek(((firstDayOfWeek.isoDayNumber - 1 + column) % 7) + 1)",
     "DayOfWeek((((column) % 7) + 1))"),
    # "W" for Wednesday and for Thursday: a header the user has to count columns to read.
    ("D6", DATE_FIELD,
     "internal const val TT_WEEKDAY_INITIALS = 3",
     "internal const val TT_WEEKDAY_INITIALS = 1"),
    # A cell sized to its numeral, which is about 20dp and unusable.
    ("D7", DATE_FIELD,
     "            .size(ttSpacing.iconButtonSize)\n            .testTag(TTDayCellTag(day))",
     "            .size(ttSpacing.xxl)\n            .testTag(TTDayCellTag(day))"),
    # Ink that is the day-number role rather than the app's primary ink, on a glass popup.
    ("D8", DATE_FIELD,
     "color = if (isSelected) ttOnAccent else ttLabel(),",
     "color = if (isSelected) ttOnAccent else ttLabelSecondary(),"),
    # A selected day painted in the app's primary ink on the accent disc.
    ("D9", DATE_FIELD,
     "color = if (isSelected) ttOnAccent else ttLabel(),",
     "color = ttLabel(),"),
    # The header back on the secondary ink, which is 3.30:1 on this popup's base in light.
    ("D10", DATE_FIELD, "                    color = ttBodyLabel(),", "                    color = ttLabelSecondary(),"),
    # An unestimated card drawn at the same size as a real estimate.
    ("D11", STORY_POINTS,
     "if (points == null) ttTypography.captionSmall\n    else ttTypography.caption.ttMonospacedDigits()",
     "ttTypography.caption"),
    # Proportional digits, so a count changing 9 -> 10 reflows the metadata row.
    ("D12", STORY_POINTS,
     "else ttTypography.caption.ttMonospacedDigits()",
     "else ttTypography.caption"),
    # An estimate drawn in the card's own accent -- the defect 23.4.2 exists to prevent.
    ("D13", STORY_POINTS,
     "            color = ttLabel(),\n            maxLines = 1,\n            modifier = modifier.testTag(TTStoryPointsTag),",
     "            color = ttAccent(),\n            maxLines = 1,\n            modifier = modifier.testTag(TTStoryPointsTag),"),
    # The pill 23.4.2 rejected: fifty capsules across a board column.
    #
    # Two earlier forms of this did not compile and therefore proved nothing. The first
    # wrapped the numeral in a `Box` and never closed the brace; the second passed a second
    # `modifier =` to `ttText`, which already takes one. This one edits the modifier chain
    # that is already there, which is also how the pill would actually be added.
    ("D14", STORY_POINTS,
     "            color = ttLabel(),\n            maxLines = 1,\n            modifier = modifier.testTag(TTStoryPointsTag),",
     "            color = ttLabel(),\n            maxLines = 1,\n            modifier = modifier\n                .background(ttAccent(), RoundedCornerShape(ttRadius.row))\n                .padding(ttSpacing.xs)\n                .testTag(TTStoryPointsTag),"),
]


def read(path):
    with open(path) as handle:
        return handle.read()


def write(path, text):
    with open(path, "w") as handle:
        handle.write(text)


def read_failures():
    xmls = glob.glob(RESULTS + "/*.xml")
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
    paths = {DATE_FIELD, STORY_POINTS}
    originals = {path: read(path) for path in paths}
    uncaught = []
    for name, path, old, new in MUTATIONS:
        text = originals[path]
        if old not in text:
            print(f"{name}: PATTERN NOT FOUND in {path}")
            uncaught.append(name)
            continue
        shutil.rmtree(RESULTS, ignore_errors=True)
        write(path, text.replace(old, new, 1))
        subprocess.run(
            ["./gradlew", ":packages:design-system:jvmTest", "--continue"],
            capture_output=True, text=True,
        )
        caught = read_failures()
        write(path, text)

        if caught is None:
            print(f"{name}: NOTHING RAN -> the build failed in a way that produced no results")
            uncaught.append(name)
            continue
        status = "caught" if caught else "NOT CAUGHT"
        print(f"{name}: {status} -> {', '.join(caught) if caught else '(nothing went red)'}")
        if not caught:
            uncaught.append(name)

    print()
    if uncaught:
        print("MUTATIONS WITH NO EVIDENCE:", ", ".join(uncaught))
        return 1
    print(f"all {len(MUTATIONS)} mutations caught")
    return 0


if __name__ == "__main__":
    sys.exit(main())