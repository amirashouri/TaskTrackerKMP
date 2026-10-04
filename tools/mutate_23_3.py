#!/usr/bin/env python3
"""Mutation ritual for Phase 23.3.

Applies one deliberate defect at a time to the port's implementation layer, runs the JVM
host, records which assertions caught it, and restores the file.

The three outcomes are kept distinct, and the distinction matters: "caught" means an
assertion went red, "rejected at compile time" means the type system or Room refused the
defect before any test ran, and "nothing ran" means something else went wrong and the
result is not evidence of anything.
"""
import glob
import re
import shutil
import subprocess
import sys

REPO = "packages/feature-projects-data/src/commonMain/kotlin/com/tasktracker/featureprojectsdata/"
MAPPING = REPO + "RecordMapping.kt"
REPOSITORY = REPO + "RoomProjectRepository.kt"
BUS = REPO + "ProjectChangeBus.kt"
SINK = REPO + "LocalDataSyncSink.kt"
RESULTS = "packages/feature-projects-data/build/test-results/jvmTest"

MUTATIONS = [
    # The two new columns are exactly what 23.2 made nullable so this cannot happen.
    ("M1", MAPPING, "storyPoints = reconciled.storyPoints,",
     "storyPoints = reconciled.storyPoints ?: 0,"),
    ("M2", MAPPING, "sprintID = reconciled.sprintID?.stringValue,\n    )\n}",
     "sprintID = null,\n    )\n}"),
    # A sprint the payload says is finished stops being able to arrive from another device,
    # which is the asymmetry `SprintRecord.apply` exists for.
    ("M3", MAPPING, "isFinished = reconciled.isFinished,\n        finishedAt = reconciled.finishedAt,\n        updatedAt = reconciled.updatedAt,",
     "isFinished = isFinished,\n        finishedAt = finishedAt,\n        updatedAt = reconciled.updatedAt,"),
    # The one-open rule, defeated three different ways. Each of these has to compile: a
    # mutation the type system rejects is not evidence about the tests. So each defect is a
    # wrong-but-well-typed query or condition rather than a null literal or a dead branch.
    # M4 asks the dao for a sprint that can never be open, so the guard is always false.
    ("M4", REPOSITORY, "val open = database.sprintDao().openByProject(projectID.stringValue)",
     "val open = database.sprintDao().byProject(projectID.stringValue).firstOrNull { it.isFinished }"),
    # M5 inverts reopen's guard, so reopening beside a live sprint goes through.
    ("M5", REPOSITORY, "if (open != null && open.id != sprint.id.stringValue) {",
     "if (open != null && open.id == sprint.id.stringValue) {"),
    # Finishing twice silently moves the date the summary is read against. The pattern stops
    # before the refusal's message so it does not depend on how the quotes are escaped here.
    ("M6", REPOSITORY, "if (sprint.isFinished) {\n                throw AppError.Validation(",
     "if (sprint.isFinished && false) {\n                throw AppError.Validation("),
    # Reassigning quietly re-ranks, which is how a board reorders itself.
    ("M7", REPOSITORY, "item.sprintID = target\n                item.updatedAt = now",
     "item.sprintID = target\n                item.rank = 0\n                item.updatedAt = now"),
    # A bulk write with no payload: saved locally, never pushed.
    ("M8", REPOSITORY, "database.workItemDao().update(item.toRecord())\n                enqueueWorkItem(item, stamp, now)",
     "database.workItemDao().update(item.toRecord())"),
    # Deleting a project leaves the server holding its sprints.
    ("M9", REPOSITORY, "for (sprint in database.sprintDao().byProject(id.stringValue)) {\n                database.sprintDao().deleteByID(sprint.id)\n                enqueueDelete(OutboxEntity.Sprint, sprint.id, stamp, sprint.updatedAt)\n            }",
     "for (sprint in emptyList<com.tasktracker.database.SprintRecord>()) {\n                database.sprintDao().deleteByID(sprint.id)\n                enqueueDelete(OutboxEntity.Sprint, sprint.id, stamp, sprint.updatedAt)\n            }"),
    # Deleting a sprint orphans its items.
    ("M10", REPOSITORY, "value.sprintID = null\n                value.updatedAt = now",
     "value.updatedAt = now"),
    # The bus announces only one of the two kinds a sprint write moves.
    ("M11", BUS, "send(ProjectChange(kind = ProjectChange.Kind.Sprints, projectID = projectID))\n    send(ProjectChange(kind = ProjectChange.Kind.WorkItems, projectID = projectID))\n}",
     "send(ProjectChange(kind = ProjectChange.Kind.Sprints, projectID = projectID))\n}"),
    # A pulled sprint delete leaves the cards pointing at it.
    ("M12", SINK, "database.workItemDao().releaseFromSprint(id)\n", ""),
    # A pulled sprint upsert refuses the closed state.
    ("M13", SINK, "database.sprintDao().update(existing.apply(sprint))",
     "database.sprintDao().update(\n                    existing.apply(sprint.copy(isFinished = false, finishedAt = null)),\n                )"),
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
        for block in re.findall(r"<testcase name=\"([^\"]+)\"[^>]*>\s*<failure", text):
            names.append(re.sub(r"\[jvm\]$", "", block))
    return names


def main():
    paths = {MAPPING, REPOSITORY, BUS, SINK}
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
            ["./gradlew", ":packages:feature-projects-data:jvmTest", "--continue"],
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