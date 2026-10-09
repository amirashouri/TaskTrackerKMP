#!/usr/bin/env python3
"""Mutation ritual for the calendar's month arrows and the year screen's placement.

Three defects were reported together: the month arrows pointed backwards, they were too big, and
the year screen opened somewhere other than where the calendar was. They have three separate
causes, and two of them are invisible to the tests that already existed.

  * The arrow *glyphs* were on each other's names. `chevronLeft`'s body was the exact quarter
    turn of `chevronDown` with its apex at x=17 — a right-pointing chevron — so the left arrow
    stepped forward. C1 restores that.
  * The glyph-to-slot *binding* could be wrong independently, and nothing caught it: the tags
    are built from the `step` argument, not from the glyph, so swapping the two `glyph =` lines
    at the call site left both tags, both handlers and all eight tests in
    `DateFieldInteractionTest` passing while the calendar's arrows read backwards. C2 does
    exactly that.
  * `MonthStepGlyph` sized the `Image` to `ttSpacing.iconButtonSize`, so a chevron spanning 18
    of its vector's 24 units rendered 33dp across inside a 44dp target. Measured ink span went
    from 11px of the 44px box to 22px of it — 0.25 to 0.50. C3 restores that.
  * The year grid is three rows against the day grid's six, and a popup's y offset is a function
    of its height (below the field if it fits, above it if it does not), so on a field low in a
    sheet the two grids landed on opposite sides of the field. C4 unpins the height.

**C5 was cut after measuring.** It unpinned the popup's *width*, on the theory that the
provider's `x` clamp (`windowWidth - contentWidth`) meant a narrower year grid would shift
sideways. It went green: every row of both grids is `fillMaxWidth()`, so both resolve to the
popup's own maximum width and the popup is always window-width (1000px on the JVM host, both
states). The constraint was dead code, so it was deleted and the mutation with it — rather than
kept, along with a comment defending a case that cannot occur.

**C3 also went green the first time, and that one was the test's fault.** `the arrows are drawn
well inside their 44dp targets` bounded the ink span at 0.55 of the box, from a geometric
estimate of 0.36 and 0.75. The estimate was wrong in the one direction that mattered: the ink
threshold stops short of the antialiased edge, so the measured fractions are 0.25 and 0.50, and
the oversized arrow was *under* the bound. The bound is now 0.35, between the two measured
numbers.

Three outcomes are kept apart, and the separation is the point:

  * **caught** - an assertion went red, and the message named the defect.
  * **rejected at compile time** - the type system refused the defect. NOT evidence, and reported
    separately for that reason (23.2 N3, 23.3 M4, 23.4 D14 x2, 23.5 P6/P9, 23.6 S6, 23.10 Y1/Y2).
  * **nothing ran** - something else went wrong, and the result is not evidence either.

The results directory is cleared before each run so the three stay separable.

**The baseline is run first, and every verdict is void if it is not green.** This is not
bookkeeping: it was added after a run in which the unmutated tree was *itself* red — a guard had
been dropped from `TTDateField`'s popup sizing, the popup collapsed to `height(0.dp)`, and seven
of the nine `DateFieldInteractionTest` cases failed with "could not find any node". Every one of
the four mutations then reported itself "caught", because the seven were always in the failure
list. Four green ticks, zero evidence. A mutation runner that cannot tell a caught mutation from
a pre-existing failure is worse than no runner, and the fix is to measure the baseline rather than
assume it.

**C2 is the mutation that matters most.** C1 is the shipped defect and is easy to reason about
from the path. C2 is a defect nothing in the suite could see, and the only reason it is now
visible is that `MonthArrowRenderTest` reads ink off the screen.
"""
import glob
import re
import shutil
import subprocess
import sys

ROOT = ""
DESIGN = "packages/design-system/src/commonMain/kotlin/com/tasktracker/designsystem/"
DATE_FIELD = DESIGN + "DateField.kt"
TAB_ICONS = DESIGN + "TabIcons.kt"

DESIGN_SYSTEM = (
    ":packages:design-system:jvmTest",
    "packages/design-system/build/test-results/jvmTest",
)

# Red before this work and red on purpose (docs/PORT-LOG.md, Phase 22). Never evidence of anything.
PRE_EXISTING = (
    "a caption on the rendered glass fill still clears the large-text bar",
    "the hairline reads as a lit edge in light",
)

MUTATIONS = [
    # ------------------------------------------------------- the shipped defect, restored
    # Both contours are correct rotations of `chevronDown`; they were simply on each other's
    # names. `TabIconsGeometryTest` reads the apex off the path and `MonthArrowRenderTest`
    # reads it off the ink, and between them neither a swap of the bodies nor a swap of the
    # call-site arguments can survive.
    ("C1", TAB_ICONS,
     "    val chevronLeft: ImageVector = icon(\"ChevronLeft\") {\n"
     "        moveTo(16.0f, 3.0f)\n"
     "        lineTo(7.0f, 12.0f)\n"
     "        lineTo(16.0f, 21.0f)\n"
     "        lineTo(12.5f, 21.0f)\n"
     "        lineTo(3.5f, 12.0f)\n"
     "        lineTo(12.5f, 3.0f)\n"
     "        close()\n"
     "    }",
     "    val chevronLeft: ImageVector = icon(\"ChevronLeft\") {\n"
     "        moveTo(8.0f, 3.0f)\n"
     "        lineTo(17.0f, 12.0f)\n"
     "        lineTo(8.0f, 21.0f)\n"
     "        lineTo(11.5f, 21.0f)\n"
     "        lineTo(20.5f, 12.0f)\n"
     "        lineTo(11.5f, 3.0f)\n"
     "        close()\n"
     "    }"),

    # The gap. The left slot draws the right-pointing glyph. Every tag, handler and test in
    # DateFieldInteractionTest still agrees with itself, because the tag says `step` and
    # `step` still means "previous". Only ink can see this.
    ("C2", DATE_FIELD,
     "                glyph = ttActionIcons.chevronLeft,",
     "                glyph = ttActionIcons.chevronRight,"),

    # The arrow is the size of its own tap target again. Measured ink span 11px -> 22px of the
    # 44px box, which is what `the arrows are drawn well inside their 44dp targets` bounds.
    ("C3", DATE_FIELD,
     "            image = glyph,\n"
     "            contentDescription = contentDescription,\n"
     "            tint = ttLabel(),\n"
     "            modifier = Modifier.size(dateFieldChevronSize),",
     "            image = glyph,\n"
     "            contentDescription = contentDescription,\n"
     "            tint = ttLabel(),\n"
     "            modifier = Modifier.size(ttSpacing.iconButtonSize),"),

    # The popup's height is free again, so the year screen is free to open on the other side of
    # the field from the calendar. The zero guard is left in place, because removing *that*
    # collapses the popup rather than unpinning it - see C6.
    ("C4", DATE_FIELD,
     "                                        Modifier.height(popupContentHeight)\n",
     "                                        Modifier\n"),

    # The unmeasured guard goes, which is a real defect and not a hypothetical one: it was
    # dropped while removing a width pin, and the popup then collapsed to `height(0.dp)`,
    # reported zero back through `onSizeChanged`, and never opened at all. Seven of the nine
    # `DateFieldInteractionTest` cases caught that.
    ("C6", DATE_FIELD,
     "                                .then(\n"
     "                                    if (popupContentHeight == Dp.Unspecified) {\n"
     "                                        Modifier\n"
     "                                    } else {\n"
     "                                        Modifier.height(popupContentHeight)\n"
     "                                    },\n"
     "                                )\n",
     ""),
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
    originals = {path: read(path) for path in (DATE_FIELD, TAB_ICONS)}
    task, results = DESIGN_SYSTEM

    # The baseline, before any mutation. Without this the runner cannot tell a caught mutation
    # from a failure that was already there - see the module docstring.
    shutil.rmtree(ROOT + results, ignore_errors=True)
    subprocess.run(["./gradlew", task, "--continue"], capture_output=True, text=True)
    baseline = read_failures(results)
    if baseline is None:
        print("BASELINE DID NOT RUN -> no mutation evidence is valid")
        return 1
    if baseline:
        print("BASELINE IS RED -> every 'caught' below would be meaningless:")
        for name in baseline:
            print(f"  {name}")
        return 1
    print("baseline: green")

    uncaught = []
    rejected = []
    for name, path, old, new in MUTATIONS:
        original = originals[path]
        if old not in original:
            print(f"{name}: PATTERN NOT FOUND in {path}")
            uncaught.append(name)
            continue
        shutil.rmtree(ROOT + results, ignore_errors=True)
        write(path, original.replace(old, new, 1))
        completed = subprocess.run(
            ["./gradlew", task, "--continue"],
            capture_output=True, text=True,
        )
        caught = read_failures(results)
        write(path, original)

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
