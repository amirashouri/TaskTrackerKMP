# Portable KMP skills — discover skills across agent harnesses

- **status:** done
- **priority:** P2
- **plan:** none — standalone documentation and agent-customization maintenance.

## Scope

Make KMP's reusable agent skills discoverable independently of OpenCode while
keeping harness-specific agent configuration in its existing location.

## Files

- `.agents/skills/*/SKILL.md` — portable location for KMP skills.
- `AGENTS.md` and `../AGENTS.md` — document the shared skill location and link
  directly to the KMP skills.
- `docs/TESTING.md` and `docs/TEST-HARNESS-PLAN.md` — refresh skill references.
- `specs/agent-flow-roadmap.md` — correct its now-stale skill-location gotcha.
- This spec — record validation and handoff.

## Contract

- [x] All KMP skills remain available under `.agents/skills/` with their names,
  frontmatter, and contents preserved.
- [x] KMP and workspace agent guidance links directly to the portable skill
  files or directories.
- [x] All active KMP skill references use `.agents/skills/`.
- [x] OpenCode-specific agent configuration remains under `.opencode/`.

## Validation

- Docs-only change: nothing to build. `scripts/check.sh docs` is the release sweep.
- Run `git diff --check`.
- Validate local Markdown links in updated guidance and docs.
- Confirm KMP skill names and frontmatter are preserved after relocation.

## Handoff

- **Done:** Moved all five skills and updated KMP and workspace guidance plus
  the affected test documentation. Documentation checks, Markdown-link
  validation, and byte-for-byte skill preservation checks passed; the final
  diff was reviewed.
- **Pending:** Commit the branch.
- **Next step:** Commit the reviewed documentation and skill moves.
- **Gotchas:** Keep `.opencode/agent/test-reviewer.md` in place; it is a
  harness-specific agent, not a portable skill.
