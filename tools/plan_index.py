#!/usr/bin/env python3
"""Keep PLAN.md's index honest, and make the long phases collapsible.

Two jobs, because they failed together:

  1. **`--check`** reports index drift: every `PLAN.md:NNN` in the index table is a *hardcoded
     line number*, and any edit above it moves every row below. This file had drifted on 25 of
     26 rows, by −4 to −323 lines, plus one anchor that pointed at nothing at all. A line number
     in a link is a claim about the file that the file itself invalidates.
  2. **`--fix`** rewrites those numbers from the real headings, and wraps each top-level section
     in `<details>` so a finished phase can be closed.

## Why `--fix` writes explicit anchors

Collapsing a section hides its `##` heading inside a `<details>` block, and an anchor link into
a hidden heading is a link that may land on nothing. So every top-level section gets an explicit
`<a id="...">` **outside** the `<details>`, and every heading this document actually links to gets
one too. The index then points at ids this file owns rather than at ids the renderer happens to
derive. Auto-generated heading ids stop being load-bearing.

## The slug function is validated, not assumed

`slug()` reproduces GitHub's rule (lowercase, drop everything that is not a word character, a
hyphen or a space, spaces to hyphens). It was checked against the 40 anchors the index already
claimed before being trusted: it reproduced 39 and correctly identified the 40th as wrong. The
Phase 21 row claimed `...adopted-and--shared-ui-back` with two hyphens where the heading
`Phase 21 -- \`ttBodyLabel\` adopted, and \`:shared-ui\` back` yields one, because the colon sits
between the two spaces and removing it leaves a single one. That row had never resolved.

## Which sections stay open

Open: the index itself, "Where things stand", and every phase that is **not** done -- 14, 15, 22
and 23. Collapsed: everything else. That is the point of the feature rather than a side effect:
the open sections are exactly where work resumes, which is the first thing a new session needs to
find, and the finished phases stop competing for the scroll.

Run with `--check` in CI or before a session. Exit code is 1 when anything has drifted, so it can
gate a commit.
"""
import re
import sys

PLAN = "PLAN.md"

# Sections that stay expanded. Everything not listed here is collapsed, so the open set is the
# short list of what is still open -- deliberately the inverse of "everything I finished".
ALWAYS_OPEN = {
    "index",
    "where-things-stand",
}

# Top-level sections that are not finished. Keys are slugs, so they are checked by --check if a
# heading is ever renamed; a stale key here shows up as a section that silently stays collapsed.
STILL_OPEN = {
    "phase-14--ios-shell",
    "phase-15--verification-and-documentation",
    "phase-22--the-error-red-meets-its-bar-and-the-glass-hairline-is-measured",
    "phase-23--sprints-a-time-boxed-goal-a-set-of-tasks-and-a-verdict",
    "phase-24--board-identity-and-the-backlogs-third-segment",
}

# State shown in a collapsed section's summary, keyed by slug. Purely cosmetic -- it is the one
# thing you want without expanding the section.
STATE = {
    "phase-0--build-foundation": "done",
    "phase-1--packagescore--packagesdomain": "done",
    "phase-2--packagesrouting": "done",
    "phase-3--keychain--userdefaults": "done",
    "phase-4--packagesdatabase-room-kmp": "done",
    "phase-5--packagesnetwork-ktor-360": "done",
    "phase-6--packagessync": "done",
    "phase-7--packagesfeature-projects-domain": "done",
    "phase-8--packagesfeature-projects-data": "done",
    "phase-9--packagesfeature-auth-domain--packagesfeature-auth-data": "done",
    "phase-10--design-system-completion": "done",
    "phase-11--packagesfeature-auth-presentation": "done",
    "phase-13-part-1-pulled-forward--making-the-sign-in-screen-reachable": "done",
    "phase-13-part-1-follow-up--the-sign-in-button-was-invisible-and-it-was-three-bugs": "done",
    "phase-13-part-1-follow-up-2--the-projects-list-became-reachable-and-the-new-project-button-did-not": "done",
    "phase-12--packagesfeature-projects-presentation": "done",
    "phase-13--settings--composition-root--shared-ui-shell": "done",
    "phase-16--the-glass-slider-ttglasssurface-learns-an-intensity": "done",
    "phase-17--the-project-picker-was-not-using-the-scaffold": "done",
    "phase-18--the-app-had-no-icon": "done",
    "phase-19--the-palette-audit-the-error-red-was-never-held-to-the-accents-bar": "done",
    "phase-20--the-design-system-review-and-four-things-it-was-missing": "done",
    "phase-21--ttbodylabel-adopted-and-shared-ui-back": "done",
    "phase-22--the-error-red-meets-its-bar-and-the-glass-hairline-is-measured": "2 findings open",
    "phase-23--sprints-a-time-boxed-goal-a-set-of-tasks-and-a-verdict":
        "23.5.5-23.5.7, 23.6 partial",
    "phase-24--board-identity-and-the-backlogs-third-segment": "open",
}

# Anchors the index claims that no heading can produce, mapped to the one that can. Kept as data
# rather than as a similarity guess: the Phase 21 row claimed `...adopted-and--shared-ui-back`
# with two hyphens, where the heading `Phase 21 -- `ttBodyLabel` adopted, and `:shared-ui` back`
# yields one, because the colon sits between the two spaces and deleting it leaves a single one.
# That link had never resolved, and a tool that silently re-points unknown anchors would hide the
# next typo the same way.
ANCHOR_FIXES = {
    "phase-21--ttbodylabel-adopted-and--shared-ui-back":
        "phase-21--ttbodylabel-adopted-and-shared-ui-back",
}

HEADING = re.compile(r"^(#{2,3}) (.*)$")
INDEX_ROW = re.compile(
    r"^(\| \[[^\]]+\]\(#([^)]+)\) \| [^|]+ \| )\[PLAN\.md:(\d+)\]\((#[^)]+)\)(.*)$"
)


def slug(text):
    """GitHub's heading-slug rule. Validated against the 40 anchors the index already claimed."""
    stripped = re.sub(r"[^\w\- ]", "", text.strip().lower(), flags=re.UNICODE)
    return stripped.replace(" ", "-")


def summary_label(text):
    """`text` as HTML for a <summary>, which renders no markdown of its own."""
    label = re.sub(r"`([^`]*)`", r"<code>\1</code>", text)
    return label.replace("&", "&amp;").replace("<code>", "\x00").replace("</code>", "\x01") \
        .replace("<", "&lt;").replace(">", "&gt;").replace("\x00", "<code>").replace("\x01", "</code>")


def read():
    with open(PLAN) as handle:
        return handle.read().splitlines()


def sections(lines):
    """(index, level, text, slug) for every heading, in file order."""
    return [
        (i, len(m.group(1)), m.group(2), slug(m.group(2)))
        for i, line in enumerate(lines)
        if (m := HEADING.match(line))
    ]


def collapsible(lines):
    """True when the file has not been transformed yet."""
    return not any(line.startswith("<details>") for line in lines)


def wrap(lines):
    """Insert explicit anchors and wrap each top-level section in <details>.

    Every `##` gets an anchor *outside* its `<details>`, because that is the only position that
    is guaranteed to be rendered whether or not the section is open. The `##` heading itself
    stays inside, so the renderer's own outline keeps working.
    """
    heads = sections(lines)
    tops = [i for i, level, _, _ in heads if level == 2]
    linked = set(re.findall(r"\]\(#([^)]+)\)", "\n".join(lines)))

    out = []
    opened = False
    for pos, line in enumerate(lines):
        match = HEADING.match(line)
        is_top = pos in tops

        if is_top:
            # Close the previous section before the next one starts. Guarded by `opened` so the
            # first heading does not emit a closing tag for a section that never began.
            if opened:
                out.append("</details>")
                out.append("")

        if match and is_top:
            level, text = len(match.group(1)), match.group(2)
            anchor = slug(text)
            open_attr = " open" if anchor in ALWAYS_OPEN or anchor in STILL_OPEN else ""
            state = STATE.get(anchor, "")
            tail = f" &mdash; <i>{state}</i>" if state else ""
            out.append(f'<a id="{anchor}"></a>')
            out.append(f"<details{open_attr}>")
            out.append(f"<summary><b>{summary_label(text)}</b>{tail}</summary>")
            out.append("")
            opened = True
        elif match and slug(match.group(2)) in linked:
            # A `###` somebody links to, which would otherwise be reachable only by luck.
            out.append(f'<a id="{slug(match.group(2))}"></a>')

        out.append(line)

    if opened:
        out.append("</details>")
    return out


def top_level_lines(lines):
    """Line number of every heading, 1-indexed, on the *transformed* file.

    Both `##` and `###`: one index row legitimately points at a `###` (the Phase 13 part-1
    follow-up is a sub-heading of the pulled-forward phase), so a map of only the top level would
    report a working link as dead.
    """
    found = {}
    for i, line in enumerate(lines, 1):
        match = HEADING.match(line)
        if match:
            found.setdefault(slug(match.group(2)), i)
    return found


def rewrite_index(lines):
    """Recompute every `PLAN.md:NNN` in the index, and the 'first unticked box' line."""
    # The index must be read against the final file, so index rows are matched by anchor, not
    # by position -- the anchors survive the rewrite because they are headings, not line numbers.
    numbers = top_level_lines(lines)

    def fix_row(line):
        match = INDEX_ROW.match(line)
        if not match:
            return line
        head, phase_anchor, _, _, tail = match.groups()
        # The phase-name link is the authority for *which* section the row means. The "13 pt 1
        # follow-ups" row carried two different anchors -- its name pointed at follow-up #2 and
        # its `file:line` at follow-up #1 -- so the row silently described two places and the
        # number could never satisfy both. Deriving the number from the name link makes the two
        # agree instead of leaving the contradiction in place.
        anchor = ANCHOR_FIXES.get(phase_anchor, phase_anchor)
        if anchor not in numbers:
            return line
        return f"{head}[PLAN.md:{numbers[anchor]}](#{anchor}){tail}"

    lines = [fix_row(line) for line in lines]

    # Then the anchors themselves, file-wide. An index row carries the same anchor twice -- once
    # on the phase name, once on the `file:line` -- and only the second one is a `PLAN.md:NNN`, so
    # repairing the numbers alone leaves the row's first link still pointing at nothing.
    for stale, repaired in ANCHOR_FIXES.items():
        lines = [line.replace(f"(#{stale})", f"(#{repaired})") for line in lines]

    # "First unticked box: [14.1](#...), at [PLAN.md:1915](#...)."
    for i, line in enumerate(lines):
        if line.startswith("**First unticked box:"):
            lines[i] = re.sub(
                r"\[PLAN\.md:\d+\]\((#[^)]+)\)",
                lambda m: f"[PLAN.md:{numbers.get(m.group(1)[1:], '?')}]({m.group(1)})",
                line,
            )
    return lines


def unwrap(lines):
    """Remove the tags `wrap` added, so `fix` can re-wrap from scratch.

    `--fix` has to be idempotent: if it skipped already-wrapped files, then correcting a `STATE`
    key would silently not reach the summaries, which is exactly the miss this just had. So the
    file is always returned to its original shape first and wrapped again, and the only lines
    that survive are the ones `wrap` is responsible for adding.
    """
    tags = ("<details", "</details>", "<summary>", '<a id="')
    out = []
    skip_blank = False
    for line in lines:
        if line.startswith(tags):
            skip_blank = line.startswith("<summary>") or line.startswith("</details>")
            continue
        if skip_blank and line == "":
            skip_blank = False
            continue
        skip_blank = False
        out.append(line)
    return out


def fix(lines):
    lines = wrap(unwrap(lines))
    return rewrite_index(lines)


def check(lines):
    problems = []
    if collapsible(lines):
        problems.append("file is not wrapped in <details> yet -- run with --fix")
        return problems

    lines = rewrite_index(lines)
    numbers = top_level_lines(lines)
    anchors = set(re.findall(r'<a id="([^"]+)"></a>', "\n".join(lines)))

    # A STATE key that matches no heading is a label that silently never renders -- the Phase 22
    # row said "2 findings open" in the index and showed nothing on its collapsed bar, because
    # the key was written with one hyphen where the em dash produces two.
    for key in sorted(STATE):
        if key not in numbers:
            problems.append(f"STATE key #{key} matches no heading, so its label never renders")

    # An ANCHOR_FIXES entry that no heading produces would silently re-point a link at nothing,
    # which is the failure this whole tool exists to prevent -- so the repair is checked too.
    for stale, repaired in ANCHOR_FIXES.items():
        if repaired not in numbers:
            problems.append(
                f"ANCHOR_FIXES maps #{stale} to #{repaired}, which no heading produces"
            )

    for i, line in enumerate(lines, 1):
        match = INDEX_ROW.match(line)
        if not match:
            continue
        claimed = int(match.group(3))
        phase_anchor, target = match.group(2), match.group(4)[1:]
        if phase_anchor != target:
            problems.append(
                f"line {i}: row's phase link #{phase_anchor} and its file:line link #{target} "
                f"are different sections"
            )
        anchor = ANCHOR_FIXES.get(phase_anchor, phase_anchor)
        if anchor not in numbers:
            problems.append(f"line {i}: index anchor #{anchor} matches no heading")
        elif claimed != numbers[anchor]:
            problems.append(
                f"line {i}: index says PLAN.md:{claimed} for #{anchor}, heading is at "
                f"{numbers[anchor]} (off by {claimed - numbers[anchor]:+d})"
            )

    for anchor in sorted(set(re.findall(r"\]\(#([^)]+)\)", "\n".join(lines))) - anchors):
        problems.append(f"link #{anchor} has no explicit <a id> and may not resolve when collapsed")

    return problems


def main():
    mode = sys.argv[1] if len(sys.argv) > 1 else "--check"
    lines = read()

    if mode == "--check":
        problems = check(lines)
        for problem in problems:
            print(problem)
        print(f"\n{len(problems)} problem(s)")
        return 1 if problems else 0

    if mode == "--fix":
        fixed = fix(lines)
        with open(PLAN, "w") as handle:
            handle.write("\n".join(fixed) + "\n")
        problems = check(fixed)
        for problem in problems:
            print(problem)
        print(f"rewrote {PLAN}: {len(fixed)} lines, {len(problems)} remaining problem(s)")
        return 1 if problems else 0

    print(__doc__)
    return 2


if __name__ == "__main__":
    sys.exit(main())