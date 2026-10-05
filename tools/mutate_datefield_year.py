#!/usr/bin/env python3
"""Mutation ritual for the date field's year picker.

A sprint's date fields opened on January 1970 with month arrows that moved nothing. The cause was
one nullable seed: `shownMonth` came from `value ?: today`, both null when creating a sprint, and
the arrow handler did `shownMonth?.let { shift(it) }`, which assigns null to null — so the arrows
were inert *permanently*, not only on the first press.

This applies one deliberate defect at a time to `DateField.kt`, runs the design system's JVM host,
records which assertions caught it, and restores the file.

Three outcomes are kept apart, and the separation is the point:

  * **caught** - an assertion went red, and the message named the defect.
  * **rejected at compile time** - the type system refused the defect before any test ran. This
    is NOT evidence, and is reported separately for that reason (23.2 N3, 23.3 M4, 23.4 D14 x2,
    23.5 P6/P9, 23.6 S6).
  * **nothing ran** - something else went wrong, and the result is not evidence either.

The results directory is cleared before each run so the three stay separable.

**The one mutation that matters most is Y1**, because it is the shipped defect itself and because
nothing about it is visible: the old and new grids are identical whenever `today` is non-null, so
every existing test stayed green while the calendar was unusable.
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

# Red before this fix and red on purpose (PLAN.md, Phase 22). Never evidence of anything.
PRE_EXISTING = (
    "a caption on the rendered glass fill still clears the large-text bar",
    "the hairline reads as a lit edge in light",
)

MUTATIONS = [
    # ------------------------------------------------------- the shipped defect, restored
    # **Y1 and Y2 were first written as the literal old expressions and were compile-rejected.**
    # `var shownMonth by remember(...)` infers its type from the seed, so changing the seed from
    # `value ?: today` (nullable) to `ttClampToYearWindow(value ?: anchorToday, anchorToday)`
    # also changed the property from `LocalDate?` to `LocalDate` — and the old
    # `shownMonth?.let { ttShiftMonth(it, step) }` then no longer assigns a `LocalDate?`.
    # That is a real result and the better outcome of the two, but it is *not* evidence that
    # anything is tested: a defect the compiler refuses is a defect that cannot ship, which is a
    # different claim from "a test caught this". So Y1 and Y2 below reproduce the same two
    # *observable* faults in a form that does compile.
    #
    # Y1: the arrows are inert. This is the symptom that was reported.
    ("Y1", DATE_FIELD,
     "                                onStepMonth = { step ->\n"
     "                                    shownMonth = ttStepMonthInWindow(shownMonth, step, anchorToday)\n"
     "                                },",
     "                                onStepMonth = { step ->\n"
     "                                    ttShiftMonth(shownMonth, step)\n"
     "                                },"),

    # Y2: the seed ignores the value, so every field opens on today. The other half of the
    # original line, and the reason `a field with a value opens on the value` exists.
    ("Y2", DATE_FIELD,
     "        mutableStateOf(ttClampToYearWindow(value ?: anchorToday, anchorToday))",
     "        mutableStateOf(ttClampToYearWindow(anchorToday, anchorToday))"),

    # The literal itself, for the case where the seed is fine but the last fallback is not.
    ("Y3", DATE_FIELD,
     "internal fun ttSystemToday(): LocalDate =\n"
     "    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date",
     "internal fun ttSystemToday(): LocalDate = LocalDate(1970, 1, 1)"),

    # ------------------------------------------------------------------------ the clamp
    # Stepping becomes unbounded again, which is how a window the year list does not contain
    # becomes reachable.
    ("Y4", DATE_FIELD,
     "): LocalDate = ttClampToYearWindow(ttShiftMonth(month, months), today, span)",
     "): LocalDate = ttShiftMonth(month, months)"),

    # Clamping to the wrong end of the window: "stepping forward" would move backwards.
    ("Y5", DATE_FIELD,
     "        month.year > window.last -> LocalDate(window.last, Month.DECEMBER, 1)\n"
     "        month.year < window.first -> LocalDate(window.first, Month.JANUARY, 1)",
     "        month.year > window.last -> LocalDate(window.first, Month.JANUARY, 1)\n"
     "        month.year < window.first -> LocalDate(window.last, Month.DECEMBER, 1)"),

    # A month inside the window gets touched, so the clamp is no longer invisible when idle.
    ("Y6", DATE_FIELD,
     "        else -> month\n    }\n}",
     "        else -> LocalDate(month.year, Month.JANUARY, 1)\n    }\n}"),

    # --------------------------------------------------------------- choosing a year at all
    # The label stops being a control: the calendar goes back to having no way to pick a year.
    ("Y7", DATE_FIELD,
     "                                onPickYear = { isChoosingYear = true },",
     "                                onPickYear = null,"),

    # Picking a year silently answers the month question too.
    ("Y8", DATE_FIELD,
     "                                    shownMonth = ttClampToYearWindow(\n"
     "                                        LocalDate(year, shownMonth.month, 1),\n"
     "                                        anchorToday,\n"
     "                                    )",
     "                                    shownMonth = ttClampToYearWindow(\n"
     "                                        LocalDate(year, Month.JANUARY, 1),\n"
     "                                        anchorToday,\n"
     "                                    )"),

    # The year grid offered the whole calendar rather than the window.
    ("Y9", DATE_FIELD,
     "fun ttYearWindow(today: LocalDate, span: Int = TT_YEAR_WINDOW_SPAN): IntRange =\n"
     "    (today.year - span)..(today.year + span)",
     "fun ttYearWindow(today: LocalDate, span: Int = TT_YEAR_WINDOW_SPAN): IntRange =\n"
     "    (today.year - span * 2)..(today.year + span * 2)"),

    # Picking a year does not return to the days, so the calendar is stuck on the year list.
    ("Y10", DATE_FIELD,
     "                                    isChoosingYear = false\n"
     "                                },",
     "                                },"),
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
    original = read(DATE_FIELD)
    task, results = DESIGN_SYSTEM
    uncaught = []
    rejected = []
    for name, path, old, new in MUTATIONS:
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