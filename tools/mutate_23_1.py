#!/usr/bin/env python3
"""Mutation ritual for Phase 23.1.

Applies one deliberate defect at a time, runs the affected tests, records which
assertions caught it, and restores the file. Exits non-zero if any mutation left
the suite green - a mutation nobody caught is an assertion that cannot fail.
"""
import re
import subprocess
import sys

JAVA_HOME = "/Applications/Android Studio.app/Contents/jbr/Contents/Home"

CORE = "packages/core/src/commonMain/kotlin/com/tasktracker/core/Identifiers.kt"
ENTITIES = "packages/domain/src/commonMain/kotlin/com/tasktracker/domain/Entities.kt"
SUMMARY = "packages/domain/src/commonMain/kotlin/com/tasktracker/domain/SprintSummary.kt"

MUTATIONS = [
    ("M1", ENTITIES, "if (goalStoryPoints < 0) goalStoryPoints = 0",
     "if (false) goalStoryPoints = 0",
     ":packages:domain:jvmTest"),
    ("M2", ENTITIES, "if (points != null && points < 0) storyPoints = 0",
     "if (points == null) storyPoints = 0",
     ":packages:domain:jvmTest"),
    ("M3", ENTITIES, "finishedAt = if (isFinished) (finishedAt ?: now) else null",
     "finishedAt = if (isFinished) (finishedAt ?: now) else finishedAt",
     ":packages:domain:jvmTest"),
    ("M4", ENTITIES, "finishedAt = if (isFinished) (finishedAt ?: now) else null",
     "finishedAt = if (isFinished) now else null",
     ":packages:domain:jvmTest"),
    ("M5", ENTITIES, "now >= startDate && now <= endDate",
     "now >= startDate && now < endDate",
     ":packages:domain:jvmTest"),
    ("M6", ENTITIES, "var sprintID: SprintID? = null,",
     "var sprintID: SprintID? = randomSprintID(),",
     ":packages:domain:jvmTest"),
    ("M7", SUMMARY, "val own = items.filter { it.sprintID == sprint.id }",
     "val own = items",
     ":packages:domain:jvmTest"),
    ("M8", SUMMARY, "items.filter { it.status.isComplete }",
     "items.filter { it.status.isTerminal }",
     ":packages:domain:jvmTest"),
    ("M9", SUMMARY, "if (goal <= 0) null else achieved >= goal",
     "if (goal <= 0) null else achieved > goal",
     ":packages:domain:jvmTest"),
    ("M10", SUMMARY, "if (goal <= 0) null else achieved >= goal",
     "if (goal <= 0) false else achieved >= goal",
     ":packages:domain:jvmTest"),
    ("M11", SUMMARY,
     "incomplete = own.filterNot { it.status.isTerminal }.sortedBy { it.orderingKey },",
     "incomplete = own.filterNot { it.status.isTerminal },",
     ":packages:domain:jvmTest"),
    ("M12", CORE, "fun randomSprintID(): SprintID = SprintID(Uuid.random())",
     "fun randomSprintID(): SprintID = SprintID(Uuid.fromLongs(0L, 0L))",
     ":packages:core:jvmTest"),
    ("M13", CORE, "fun parseSprintID(raw: String): SprintID? = runCatching { SprintID(Uuid.parse(raw)) }.getOrNull()",
     "fun parseSprintID(raw: String): SprintID? = null",
     ":packages:core:jvmTest"),
]

TEST = "packages/domain/build/test-results/jvmTest"
CORE_TEST = "packages/core/build/test-results/jvmTest"


def read(path):
    with open(path) as handle:
        return handle.read()


def write(path, text):
    with open(path, "w") as handle:
        handle.write(text)


def failed_tests(result_dir):
    out = subprocess.run(
        ["grep", "-rl", "<failure", result_dir], capture_output=True, text=True
    ).stdout
    names = []
    for xml in out.strip().splitlines():
        text = read(xml)
        for block in re.findall(r"<testcase name=\"([^\"]+)\"[^>]*>\s*<failure", text):
            names.append(block)
    return names


def main():
    originals = {path: read(path) for path in {CORE, ENTITIES, SUMMARY}}
    uncaught = []
    for name, path, old, new, task in MUTATIONS:
        text = originals[path]
        if old not in text:
            print(f"{name}: PATTERN NOT FOUND in {path}")
            uncaught.append(name)
            continue
        write(path, text.replace(old, new, 1))
        subprocess.run(
            ["./gradlew", task, "--continue"],
            capture_output=True, text=True,
        )
        caught = failed_tests(CORE_TEST if task.endswith("core:jvmTest") else TEST)
        write(path, text)
        status = "caught" if caught else "NOT CAUGHT"
        print(f"{name}: {status} -> {', '.join(caught) if caught else '(nothing went red)'}")
        if not caught:
            uncaught.append(name)

    print()
    if uncaught:
        print("MUTATIONS THAT NO TEST CAUGHT:", ", ".join(uncaught))
        return 1
    print(f"all {len(MUTATIONS)} mutations caught")
    return 0


if __name__ == "__main__":
    sys.exit(main())