# TaskTrackerKMP — active and completed roadmap items

This file is a lean index that mirrors `docs/PLAN.md` but holds only the real
items: the active feature backlog and the expanded-shell section still in flight.
All completed areas — 0, 4, 5, 6, and 7 — stay in the two documentation files
they already own, and all standalone completed specs already list `status: done`.

- **Active roadmap (with the two pending 8.x items):** `docs/PLAN.md`
- **Completed feature history (areas 0, 4, 5 and the admin CLI slice):** `docs/PLAN-ARCHIVE.md`
- **Completed §6 (server connection) and completed §7 (Liquid Glass) specs:** `specs/`
- **Completed standalone specs (agent-flow, desktop version, live-member-directory, portable skills, project-custom-tags, sync-debug-logging, license/readme):** `specs/`, each with `status: done`

## Items still in progress or pending

### 9.1 — Remove the deprecated WorkItemEditor expand/collapse parameters
- status: done
- file: `specs/9.1-remove-workitemeditor-expand-collapse-deprecation.md`

### 9.2 — Remove the deprecated Glass fill-alpha helper
- status: done
- file: `specs/9.2-remove-deprecated-glass-fill-alpha.md`


| Task / Spec | State | Where to read it |
| --- | --- | --- |
| 8.1 — wide shell leading chrome | pending | `specs/8.1-wide-shell-sidebar.md`, `docs/PLAN.md` |
| 8.2 — sidebar project switcher | pending | `specs/8.2-sidebar-project-switcher.md`, `docs/PLAN.md` |
| 8.3 — rail cell density (mid-width band) | deferred | `docs/PLAN.md` only, no spec yet |
| 1.7 — role-based project permissions | pending | `docs/PLAN.md`, `docs/PLAN-ARCHIVE.md` §1.7 |
| 1.8 — activity feed / project timeline | pending | `docs/PLAN.md`, `docs/PLAN-ARCHIVE.md` §1.8 |
| 2.2 — due-date reminders | pending | `docs/PLAN.md`, `docs/PLAN-ARCHIVE.md` §2.2 |
| 2.3 — search across projects | pending | `specs/2.3-search-across-projects.md` |
| 2.4 — labels / tags | pending | `docs/PLAN.md`, `docs/PLAN-ARCHIVE.md` §2.4 |
| 2.5 — sub-tasks / checklists | pending | `docs/PLAN.md`, `docs/PLAN-ARCHIVE.md` §2.5 |
| 2.6 — calendar view | pending | `docs/PLAN.md`, `docs/PLAN-ARCHIVE.md` §2.6 |
| 2.7 — task templates | pending | `docs/PLAN.md`, `docs/PLAN-ARCHIVE.md` §2.7 |
| 2.8 — custom statuses per project | pending | `docs/PLAN.md`, `docs/PLAN-ARCHIVE.md` §2.8 |
| 2.9 — focus mode / Pomodoro | pending | `docs/PLAN.md`, `docs/PLAN-ARCHIVE.md` §2.9 |
| 3.1–3.5 — global search shortcut, export, attachments, dark-mode schedule, keyboard shortcuts | pending | `docs/PLAN.md`, `docs/PLAN-ARCHIVE.md` §3 |

## How to pick the next item

1. Read `docs/PLAN.md` first — it is the concise index and states dependencies.
2. If the item has a spec, read `specs/<id>-<slug>.md`; that spec is the contract.
3. If the item has no spec, read only its section in `docs/PLAN-ARCHIVE.md` and create a spec before implementation.
4. Do not read archive sections you are not working on; they exist to preserve history, not as daily reading.

## Completed but unarchived in this index

These are done and already documented elsewhere — listed here so nobody re-plans them.

- **0 — Foundation:** `docs/PLAN-ARCHIVE.md`, section 0 — done.
- **4 — Desktop target:** `docs/PLAN-ARCHIVE.md`, section 4 — done.
- **5 — Desktop layout:** `docs/PLAN-ARCHIVE.md`, section 5 — done.
- **6 — Server connection (6.1, 6.3, 6.2, 6.4):** each in `specs/` with `status: done`; server-side T9.7 still tracked in `TaskTrackerServer/docs/PLAN.md`.
- **7 — Liquid Glass refinements (7.1–7.8):** each in `specs/` with `status: done`.
- **Admin member/project management CLI slice (CLI-1–CLI-16):** done, in `docs/PLAN-ARCHIVE.md`.
- **Standalone completed specs:** `agent-flow-roadmap.md`, `desktop-app-version.md`, `live-member-directory.md`, `portable-kmp-skills.md`, `project-custom-tags.md`, `sync-debug-logging.md`, `license-and-readme-title.md` — each with `status: done`.

## What was intentionally left where it was

- **Completed specs were not moved into an archive folder.** Each one already has `status: done`, and `docs/PLAN.md` now points at it directly. Moving them would split the contract from the roadmap index for no gain.
- **`docs/PLAN-ARCHIVE.md` was not rewritten or split.** It is the historical record for areas 0/4/5 and the admin slice; this index file does not replace it, just points at it.
- **No new files were added to `specs/`.** The active items above already have their home in either `specs/` or `docs/PLAN-ARCHIVE.md`.
