#!/usr/bin/env python3
"""Mutation ritual for the date field's popup: its size, its placement, and its way out.

One reported symptom — "the calendar opens as a full screen page and there is no way to close
it" — and three defects behind it:

  * **The popup was window-width.** Every row of `TTDayGrid` is `fillMaxWidth()` and every cell
    is `weight(1f)`, so with nothing bounding them they resolve to the popup's *maximum* width,
    which is the window's. Measured before the fix: 1000px inside a 1024px window. P1 restores
    that.
  * **And that is why there was no way out.** A popup that fills the window has no outside left
    to tap, and `onDismissRequest` is reached by an outside tap or by back. Anchoring the popup
    to the field instead of centring it is P2.
  * **Nothing on screen to press.** A Done button in the popup's footer; P4 makes it inert, and
    P5 gives it a second, invisible way to answer the question.

* **P6 is a unit error that no host can see on its own.** `onSizeChanged` reports pixels, and the
  height was applied as `px.dp` — the same number at the tests' density of 1 and three times too
  large on a density-3 phone. It is caught by a density-invariance assertion (the card's height
  in dp must not change with the density), not by any measurement of the card itself.

Two outcomes are kept apart, and the separation is the point:

  * **caught** - an assertion went red, and the message named the defect.
  * **rejected at compile time** - the type system refused the defect. NOT evidence, and reported
    separately for that reason (23.2 N3, 23.3 M4, 23.4 D14 x2, 23.5 P6/P9, 23.6 S6, 23.10 Y1/Y2).

**The baseline is run first, and every verdict is void if it is not green.** A red baseline
turns every mutation into a false "caught" — four of which this project produced once already,
when a dropped guard collapsed the popup and seven pre-existing failures were mistaken for
evidence. See `tools/mutate_datefield_chevrons.py` for that account.
"""
import glob
import re
import shutil
import subprocess
import sys

ROOT = ""
DESIGN = "packages/design-system/src/commonMain/kotlin/com/tasktracker/designsystem/"
DATE_FIELD = DESIGN + "DateField.kt"

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
    # Nothing bounds the popup's width, so `fillMaxWidth()` resolves to the window's and the
    # calendar goes edge to edge.
    ("P1", DATE_FIELD,
     "                            .width(dateFieldPopupWidth)\n",
     ""),

    # Anchored to the field's left edge rather than centred — the 23.4 placement.
    ("P2", DATE_FIELD,
     "    x = ((window.width - content.width) / 2).coerceAtLeast(0),",
     "    x = 0,"),

    # A popup taller than its window gets a negative offset and its top-left corner, which is
    # where the month arrows are, is pushed off-screen.
    ("P3", DATE_FIELD,
     "    y = ((window.height - content.height) / 2).coerceAtLeast(0),",
     "    y = (window.height - content.height) / 2,"),

    # The Done button is on screen and does nothing.
    ("P4", DATE_FIELD,
     "                                onClick = dismissPicker,",
     "                                onClick = {},"),

    # Done also assigns, which is a second and invisible way to answer the question: choosing a
    # day already closes the picker, so the field would change with nothing to explain it.
    ("P5", DATE_FIELD,
     "                                onClick = dismissPicker,",
     "                                onClick = { onValueChange(anchorToday); dismissPicker() },"),

    # The defect this file's second half exists for: a pixel count read as a dp count. Invisible
    # at the tests' density of 1, three times too large on a density-3 phone — a card taller than
    # the window, so `coerceAtLeast(0)` pins `y` to 0 and it sits at the top of the page under a
    # background running the window's full height.
    ("P6", DATE_FIELD,
     "                                    popupContentHeight = with(density) { size.height.toDp() }",
     "                                    popupContentHeight = size.height.dp"),

    # No cap, so a window shorter than the calendar (a landscape phone, about 360dp) clips it at
    # the top again — the same symptom as P6 from the other direction.
    ("P7", DATE_FIELD,
     "                            .heightIn(max = maxHeight * TT_DATE_FIELD_MAX_HEIGHT_FRACTION)\n",
     ""),

    # The cap without the budget: the grid keeps its natural height inside a capped column, so it
    # is clipped rather than scrolled and the rows below the fold are unreachable.
    ("P8", DATE_FIELD,
     "                                .weight(1f, fill = false)\n",
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
        for block in re.findall(r'<testcase name="([^"]+)"[^>]*>\s*<(?:failure|error)', text):
            names.append(re.sub(r"\[jvm\]$", "", block))
    return [name for name in names if name not in PRE_EXISTING]


def main():
    original = read(DATE_FIELD)
    task, results = DESIGN_SYSTEM

    # The baseline, before any mutation. Without this the runner cannot tell a caught mutation
    # from a failure that was already there.
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
        if old not in original:
            print(f"{name}: PATTERN NOT FOUND in {path}")
            uncaught.append(name)
            continue
        shutil.rmtree(ROOT + results, ignore_errors=True)
        write(path, original.replace(old, new, 1))
        completed = subprocess.run(["./gradlew", task, "--continue"], capture_output=True, text=True)
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
