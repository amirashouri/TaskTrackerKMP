#!/usr/bin/env python3
"""Fail when a scenario has no entry point on a host that should run it.

The contract this enforces is [docs/TESTING.md]'s: a scenario body lives in a `commonTest`
`…Scenarios` object, and every host that runs Compose UI tests reaches it either directly
(`FooScenarios.bar`), by inheriting a `commonTest` base class that does, or through an
indexed alias list (`ShellScenarios.projectBacklogScenarios[12]`) whose members it names.

Before this existed, the three host entry-point files were copies of each other and nothing
connected them: a scenario could be written, wired into one host, and never run on the other
two. The only symptom was a test count nobody read.

Usage:
    scripts/check-test-matrix.py [--root <TaskTrackerKMP>]
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

HOST_DIRS = ("jvmTest", "iosTest", "androidHostTest")

SCENARIO_OBJECT = re.compile(r"^object\s+(\w*Scenarios)\b", re.MULTILINE)
SCENARIO_VAL = re.compile(
    r"^    val\s+(\w+)\s*:\s*suspend\s+ComposeUiTest", re.MULTILINE
)
BASE_CLASS = re.compile(r"^abstract\s+class\s+(\w+)\b", re.MULTILINE)
CLASS_EXTENDS = re.compile(r"class\s+\w+\s*:\s*(\w+)\s*\(")
REFERENCE = re.compile(r"(\w*Scenarios)\s*(?:\.(\w+)|\s*\[)")
ALIAS_START = re.compile(r"^    val\s+(\w+)\s*(?::[^=]+)?=\s*listOf\(\s*$")
ALIAS_ENTRY = re.compile(r"(\w*Scenarios)\.(\w+)")


def scenario_objects(common_test: Path) -> dict[str, set[str]]:
    """The scenario names each `…Scenarios` object declares."""
    found: dict[str, set[str]] = {}
    for path in sorted(common_test.rglob("*.kt")):
        text = path.read_text()
        match = SCENARIO_OBJECT.search(text)
        if not match:
            continue
        names = set(SCENARIO_VAL.findall(text))
        if names:
            found[match.group(1)] = names
    return found


def base_classes(common_test: Path) -> dict[str, set[str]]:
    """Which `Object.name` pairs each abstract base class in commonTest wires to a `@Test`.

    Only references that follow an `@Test` annotation count. A scenario merely *mentioned* in a
    KDoc comment does not make it run, and the first version of this check missed exactly that:
    stripping `@Test` from one method still left its reference inside the previous method's
    slice, so the check stayed green while the test stopped existing.
    """
    bases: dict[str, set[str]] = {}
    for path in sorted(common_test.rglob("*.kt")):
        text = path.read_text()
        match = BASE_CLASS.search(text)
        if not match:
            continue
        refs: set[str] = set()
        lines = text.splitlines()
        for index, line in enumerate(lines):
            if line.strip() != "@Test":
                continue
            # The `runComposeUiTest(block = …)` always follows within a few lines; the window
            # is bounded so it cannot leak into the next test's annotation.
            for obj, name in REFERENCE.findall("\n".join(lines[index : index + 8])):
                if name:
                    refs.add(f"{obj}.{name}")
                elif obj:
                    refs.add(obj)
        bases[match.group(1)] = refs
    return bases


def scenario_aliases(common_test: Path) -> dict[str, set[str]]:
    """Which `Object.name` pairs each `val x = listOf(…)` alias in commonTest wires up.

    The shell's backlog section is declared once as named scenarios and reached from the hosts
    through an indexed list (`ShellScenarios.projectBacklogScenarios[12]`). The names behind
    those indices would otherwise be invisible to this check, so an object is also considered
    covered when every one of its scenarios appears in an alias list this host references.
    """
    aliases: dict[str, set[str]] = {}
    for path in sorted(common_test.rglob("*.kt")):
        lines = path.read_text().splitlines()
        index = 0
        while index < len(lines):
            match = ALIAS_START.match(lines[index])
            if not match:
                index += 1
                continue
            entries: set[str] = set()
            index += 1
            while index < len(lines) and not lines[index].strip().startswith(")"):
                entries.update(
                    f"{obj}.{name}" for obj, name in ALIAS_ENTRY.findall(lines[index])
                )
                index += 1
            if entries:
                aliases[match.group(1)] = entries
            index += 1
    return aliases


def host_coverage(module: Path, objects: dict[str, set[str]]) -> list[str]:
    common_test = module / "src" / "commonTest"
    bases = base_classes(common_test)
    aliases = scenario_aliases(common_test)
    problems: list[str] = []

    for host in HOST_DIRS:
        host_dir = module / "src" / host
        if not host_dir.is_dir():
            continue
        text = "".join(path.read_text() for path in sorted(host_dir.rglob("*.kt")))
        inherited: set[str] = set()
        for base in CLASS_EXTENDS.findall(text):
            inherited |= bases.get(base, set())

        for obj, names in objects.items():
            for name in sorted(names):
                reference = f"{obj}.{name}"
                if reference in text or reference in inherited:
                    continue
                if any(
                    reference in entries
                    and (
                        re.search(rf"\b{alias}\b", text)
                        or any(alias in ref for ref in inherited)
                    )
                    for alias, entries in aliases.items()
                ):
                    continue
                problems.append(f"{host_dir}: no entry point for {reference}")
    return problems


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--root",
        default=str(ROOT),
        help="the KMP project root (defaults to the parent of scripts/)",
    )
    args = parser.parse_args()

    root = Path(args.root)
    problems: list[str] = []
    checked = 0

    modules = sorted(root.glob("*/src")) + sorted(root.glob("packages/*/src"))
    for module in modules:
        common_test = module / "commonTest"
        if not common_test.is_dir():
            continue
        objects = scenario_objects(common_test)
        if not objects:
            continue
        if not any((module / host).is_dir() for host in HOST_DIRS):
            continue
        checked += len(objects)
        problems.extend(host_coverage(module.parent, objects))

    if problems:
        print("Test matrix check failed:", file=sys.stderr)
        for problem in problems:
            print(f"  - {problem}", file=sys.stderr)
        print(
            "\nEvery scenario needs an entry point on every host that runs it, either "
            "directly, through a commonTest abstract base class, or through an alias list "
            "the host references.",
            file=sys.stderr,
        )
        return 1

    print(f"test matrix: every scenario has an entry point on every host ({checked} objects)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
