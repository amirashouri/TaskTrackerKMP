# TaskTrackerKMP — Active roadmap

This is the concise index of active and completed roadmap areas. The detailed
plan and implementation history is preserved in
[`PLAN-ARCHIVE.md`](PLAN-ARCHIVE.md). A task spec is the implementation
contract; when an active item has no spec, consult only its section in the
archive and create a spec before implementation.

## How to use this roadmap

- Choose an active item below and respect its dependencies and stated order.
- Read its spec in `specs/` if one exists. Otherwise, read only the matching
  section of [`PLAN-ARCHIVE.md`](PLAN-ARCHIVE.md), then create and review a spec
  before implementation.
- Keep one task on one branch and one PR. Do not bundle neighboring work.
- Follow `AGENTS.md` for ACK, host, test-review, validation, and merge rules.
- Detailed test-harness cleanup has its own linked plan; don't duplicate it here.

## Roadmap status

| Area | State | Details |
| --- | --- | --- |
| 0 — Foundation | Complete | [`PLAN-ARCHIVE.md`](PLAN-ARCHIVE.md), section 0 |
| 1 — Team collaboration | Active | Pending items below |
| 2 — Personal task management | Active | Pending items below |
| 3 — Cross-cutting quality of life | Active | Pending items below |
| 4 — Desktop target | Complete | [`PLAN-ARCHIVE.md`](PLAN-ARCHIVE.md), section 4 |
| 5 — Desktop layout | Complete | [`PLAN-ARCHIVE.md`](PLAN-ARCHIVE.md), section 5 |
| 6 — Server connection | Active | Pending items below |
| Admin member/project management | Complete | See the archived CLI plan; CLI-1–CLI-16 landed |
| Test harness cleanup | In progress | [`TEST-HARNESS-PLAN.md`](TEST-HARNESS-PLAN.md) |
| 7 — Liquid Glass refinements | Active | Pending items below |

## Active feature backlog

| Task | State | Priority | Intent | Depends on | Execution details |
| --- | --- | --- | --- | --- | --- |
| 1.7 | Pending | P3 | Role-based project permissions | None | [`PLAN-ARCHIVE.md`](PLAN-ARCHIVE.md), section 1.7 |
| 1.8 | Pending | P2 | Activity feed / project timeline | None | [`PLAN-ARCHIVE.md`](PLAN-ARCHIVE.md), section 1.8 |
| 2.2 | Pending | P1 | Due-date reminders | Local notifications (1.4, complete) | [`PLAN-ARCHIVE.md`](PLAN-ARCHIVE.md), section 2.2 |
| 2.3 | Pending | P1 | Search across projects | None | [`2.3-search-across-projects.md`](../specs/2.3-search-across-projects.md) |
| 2.4 | Pending | P2 | Labels / tags | None | [`PLAN-ARCHIVE.md`](PLAN-ARCHIVE.md), section 2.4 |
| 2.5 | Pending | P2 | Sub-tasks / checklists | None | [`PLAN-ARCHIVE.md`](PLAN-ARCHIVE.md), section 2.5 |
| 2.6 | Pending | P2 | Calendar view | None | [`PLAN-ARCHIVE.md`](PLAN-ARCHIVE.md), section 2.6 |
| 2.7 | Pending | P2 | Task templates | None | [`PLAN-ARCHIVE.md`](PLAN-ARCHIVE.md), section 2.7 |
| 2.8 | Pending | P3 | Custom statuses per project | None | [`PLAN-ARCHIVE.md`](PLAN-ARCHIVE.md), section 2.8 |
| 2.9 | Pending | P3 | Focus mode / Pomodoro | None | [`PLAN-ARCHIVE.md`](PLAN-ARCHIVE.md), section 2.9 |
| 3.1 | Pending | P2 | Global search shortcut | 2.3 | [`PLAN-ARCHIVE.md`](PLAN-ARCHIVE.md), section 3.1 |
| 3.2 | Pending | P2 | Export project archive | None | [`PLAN-ARCHIVE.md`](PLAN-ARCHIVE.md), section 3.2 |
| 3.3 | Pending | P3 | File attachments | None | [`PLAN-ARCHIVE.md`](PLAN-ARCHIVE.md), section 3.3 |
| 3.4 | Pending | P3 | Dark-mode auto-schedule | None | [`PLAN-ARCHIVE.md`](PLAN-ARCHIVE.md), section 3.4 |
| 3.5 | Pending | P3 | Keyboard shortcuts / quick capture | None | [`PLAN-ARCHIVE.md`](PLAN-ARCHIVE.md), section 3.5 |

## Liquid Glass refinements (Area 7)

This section covers refinements to the Liquid Glass design system in `:packages:design-system`. Each task is a focused improvement that preserves existing pixel tests and the intensity slider contract.

| Task | State | Priority | Intent | Depends on | Execution details |
| --- | --- | --- | --- | --- | --- |
| 7.1 | Complete | P1 | Vestigial hairline at 0% intensity (Reduce Transparency) | None | [`PLAN-ARCHIVE.md`](PLAN-ARCHIVE.md), section 7.1 |
| 7.2 | Complete | P1 | Spring-based interpolation for glass intensity value | None | [`PLAN-ARCHIVE.md`](PLAN-ARCHIVE.md), section 7.2 |
| 7.3 | Complete | P1 | Haptic feedback on glass intensity slider | None | [`PLAN-ARCHIVE.md`](PLAN-ARCHIVE.md), section 7.3 |
| 7.4 | Complete | P2 | Per-component glass thickness tokens (thin/standard/thick) | 7.1 | [`PLAN-ARCHIVE.md`](PLAN-ARCHIVE.md), section 7.4 |
| 7.5 | Complete | P2 | Subsurface scattering horizontal gradient on wide cards | 7.4 | [`PLAN-ARCHIVE.md`](PLAN-ARCHIVE.md), section 7.5 |
| 7.6 | Pending | P3 | Chromatic aberration at rim (subtle prism effect) | 7.1 | [`PLAN-ARCHIVE.md`](PLAN-ARCHIVE.md), section 7.6 |
| 7.7 | Pending | P3 | Motion blur on fast lens travel (segmented picker, tab bar) | None | [`PLAN-ARCHIVE.md`](PLAN-ARCHIVE.md), section 7.7 |
| 7.8 | Pending | P3 | Accelerometer-driven specular parallax (platform seam) | 7.1 | [`PLAN-ARCHIVE.md`](PLAN-ARCHIVE.md), section 7.8 |

The intended order is 7.1 → 7.2 → 7.3 → 7.4 → 7.5. Tasks 7.6–7.8 are independent polish items.

## Active server-connection work

This section is cross-repository work. Read the parent `../AGENTS.md` and use
the dual-repo ACK protocol before changing either side of the shared connect
link contract.

| Task | State | Priority | Intent | Depends on | Execution details |
| --- | --- | --- | --- | --- | --- |
| 6.1 | Complete | P0 | Runtime API root override | None | [`6.1-runtime-api-root.md`](../specs/6.1-runtime-api-root.md) |
| 6.3 | Complete | P0 | Validate a server URL before saving | 6.1 | [`6.3-server-url-validation.md`](../specs/6.3-server-url-validation.md) |
| 6.2 | Complete | P0 | Server field in Settings | 6.1, 6.3 | [`6.2-server-field-in-settings.md`](../specs/6.2-server-field-in-settings.md) |
| 6.4 | Complete | P0 | `tasktracker://connect?url=…` pairing link | 6.1–6.3; server T9.7 | [`6.4-connect-deep-link.md`](../specs/6.4-connect-deep-link.md) |

The intended order is 6.1 → 6.3 → 6.2 → 6.4. The server-side CLI work and shared link grammar are tracked in
[`TaskTrackerServer/docs/PLAN.md`](../../TaskTrackerServer/docs/PLAN.md), T9.7.
