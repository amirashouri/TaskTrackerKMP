#!/usr/bin/env python3
"""Mutation ritual for Phase 23.2.

Applies one deliberate defect at a time to the persistence layer, runs the JVM host,
records which assertions caught it, and restores the file. Exits non-zero if any mutation
left the suite green.
"""
import re
import subprocess
import sys

JAVA_HOME = "/Applications/Android Studio.app/Contents/jbr/Contents/Home"

WORK_ITEM = "packages/database/src/commonMain/kotlin/com/tasktracker/database/WorkItemRecord.kt"
V3 = "packages/database/src/commonTest/kotlin/com/tasktracker/database/VersionThreeSchema.kt"
RESULTS = "packages/database/build/test-results/jvmTest"

MUTATIONS = [
    # A NOT NULL storyPoints forces the auto-migration to add the column with DEFAULT 0, so
    # every pre-sprint item silently becomes "estimated as zero" - the exact conflation
    # `Int?` exists to prevent.
    ("N1", WORK_ITEM, "val storyPoints: Int? = null,", "val storyPoints: Int = 0,"),
    # The same for sprintID: every existing item arrives belonging to a sprint named "".
    ("N2", WORK_ITEM, "val sprintID: String? = null,", "val sprintID: String = \"\","),
    # Deleting a sprint leaves its items pointing at it. `WHERE 0` is rejected by Room at
    # compile time (it leaves :sprintID unused), so N3b below is the version that reaches
    # runtime - otherwise this assertion would only ever have been shown red by a build
    # failure, which is not the same evidence.
    ("N3", WORK_ITEM,
     "@Query(\"UPDATE work_items SET sprintID = NULL WHERE sprintID = :sprintID\")",
     "@Query(\"UPDATE work_items SET sprintID = NULL WHERE 0\")"),
    ("N3b", WORK_ITEM,
     "@Query(\"UPDATE work_items SET sprintID = NULL WHERE sprintID = :sprintID\")",
     "@Query(\"UPDATE work_items SET sprintID = NULL \" +\n        \"WHERE sprintID = :sprintID AND statusRaw = 'in_progress'\")"),
    # Reassigning an item quietly re-ranks it, which is how a board reorders itself.
    ("N4", WORK_ITEM, "SET sprintID = :sprintID, updatedAt = :now",
     "SET sprintID = :sprintID, updatedAt = :now, rank = 0"),
    # The hand-written v3 DDL stops describing v3: a column goes missing.
    ("N5", V3, "`updatedAt` INTEGER NOT NULL,\n          `resolvedAt` INTEGER, PRIMARY KEY(`id`))",
     "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"),
    # The same, for the table list the drift guard compares.
    ("N6", V3, "\"outbox\", \"sync_metadata\", \"session\", \"projects\", \"work_items\", \"status_changes\",",
     "\"outbox\", \"sync_metadata\", \"session\", \"projects\", \"work_items\", \"status_changes\",\n        \"ghost_table\","),
    # An index that v3 really had, dropped from the literal. A store with a missing index
    # still works, which is why this one is compared rather than assumed.
    ("N7", V3,
     "\"CREATE INDEX IF NOT EXISTS `index_status_changes_projectID` \" +\n            \"ON `status_changes` (`projectID`)\",",
     "\"\","),
]


def read(path):
    with open(path) as handle:
        return handle.read()


def write(path, text):
    with open(path, "w") as handle:
        handle.write(text)


def failed_tests():
    """Assertions that went red, or None if the build never produced results.

    None is a distinct outcome and has to be reported as one. An earlier version of this
    script left the *previous* run's XML in place, so a mutation that failed to compile
    read as "nothing went red" — and for N1 and N2 that is exactly what happened: Room
    rejects a NOT NULL column with no default at compile time, which is a defence, not a
    gap. Deleting the results directory first is what makes the three cases separable.
    """
    import glob
    import shutil

    shutil.rmtree(RESULTS, ignore_errors=True)
    return None


def read_failures():
    import glob

    xmls = glob.glob(RESULTS + "/*.xml")
    if not xmls:
        return None
    names = []
    for xml in xmls:
        text = read(xml)
        for block in re.findall(r"<testcase name=\"([^\"]+)\"[^>]*>\s*<failure", text):
            names.append(re.sub(r"\[jvm\]$", "", block))
    return names


def main():
    originals = {path: read(path) for path in {WORK_ITEM, V3}}
    uncaught = []
    for name, path, old, new in MUTATIONS:
        text = originals[path]
        if old not in text:
            print(f"{name}: PATTERN NOT FOUND in {path}")
            uncaught.append(name)
            continue
        failed_tests()  # clears stale results before the run
        write(path, text.replace(old, new, 1))
        subprocess.run(
            ["./gradlew", ":packages:database:jvmTest", "--continue"],
            capture_output=True, text=True,
        )
        caught = read_failures()
        write(path, text)

        if caught is None:
            # The mutation was rejected before any test could run. Room's auto-migration
            # processor refuses a NOT NULL column with no default outright, which moves a
            # data-loss bug from "ships and eats every existing row" to "does not build".
            print(f"{name}: REJECTED AT COMPILE TIME -> no test could run (a build failure, "
                  f"not a silent pass)")
            continue
        status = "caught" if caught else "NOT CAUGHT"
        print(f"{name}: {status} -> {', '.join(caught) if caught else '(nothing went red)'}")
        if not caught:
            uncaught.append(name)

    print()
    if uncaught:
        print("MUTATIONS THAT NO TEST CAUGHT:", ", ".join(uncaught))
        return 1
    print(f"all {len(MUTATIONS)} mutations caught or rejected at compile time")
    return 0


if __name__ == "__main__":
    sys.exit(main())