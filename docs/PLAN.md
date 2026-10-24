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
| 6 — Server connection | Complete | Each task has its own spec in `specs/` |
| Admin member/project management | Complete | See the archived CLI plan; CLI-1–CLI-16 landed |
| Test harness cleanup | In progress | [`TEST-HARNESS-PLAN.md`](TEST-HARNESS-PLAN.md) |
| 7 — Liquid Glass refinements | Complete | Each task has its own spec in `specs/` |
| 8 — Expanded-shell navigation | Active | Pending items below |

## Completed server-connection tasks (Area 6)

All four §6 server-connection tasks are done. Their specs live in `specs/`.

| Task | State | Priority | Intent | Execution details |
| --- | --- | --- | --- | --- |
| 6.1 | Complete | P0 | Runtime API root override | [`6.1-runtime-api-root.md`](../specs/6.1-runtime-api-root.md) |
| 6.3 | Complete | P0 | Validate a server URL before saving | [`6.3-server-url-validation.md`](../specs/6.3-server-url-validation.md) |
| 6.2 | Complete | P0 | Server field in Settings | [`6.2-server-field-in-settings.md`](../specs/6.2-server-field-in-settings.md) |
| 6.4 | Complete | P0 | `tasktracker://connect?url=…` pairing link | [`6.4-connect-deep-link.md`](../specs/6.4-connect-deep-link.md) |

The server-side CLI work and shared link grammar are tracked in
[`TaskTrackerServer/docs/PLAN.md`](../../TaskTrackerServer/docs/PLAN.md), T9.7.

## Liquid Glass refinements (Area 7)

All eight §7 Liquid Glass tasks are done. Each has its own spec in `specs/`.

| Task | State | Priority | Intent | Depends on | Execution details |
| --- | --- | --- | --- | --- | --- |
| 7.1 | Complete | P1 | Vestigial hairline at 0% intensity (Reduce Transparency) | None | [`7.1-vestigial-hairline.md`](../specs/7.1-vestigial-hairline.md) |
| 7.2 | Complete | P1 | Spring-based interpolation for glass intensity value | None | [`7.2-spring-intensity.md`](../specs/7.2-spring-intensity.md) |
| 7.3 | Complete | P1 | Haptic feedback on glass intensity slider | None | [`7.3-haptic-slider.md`](../specs/7.3-haptic-slider.md) |
| 7.4 | Complete | P2 | Per-component glass thickness tokens (thin/standard/thick) | 7.1 | [`7.4-glass-thickness.md`](../specs/7.4-glass-thickness.md) |
| 7.5 | Complete | P2 | Subsurface scattering horizontal gradient on wide cards | 7.4 | [`7.5-subsurface-scattering.md`](../specs/7.5-subsurface-scattering.md) |
| 7.6 | Complete | P3 | Chromatic aberration at rim (subtle prism effect) | 7.1 | [`7.6-chromatic-aberration.md`](../specs/7.6-chromatic-aberration.md) |
| 7.7 | Complete | P3 | Motion blur on fast lens travel (segmented picker, tab bar) | None | [`7.7-motion-blur-lens.md`](../specs/7.7-motion-blur-lens.md) |
| 7.8 | Complete | P3 | Accelerometer-driven specular parallax (platform seam) | 7.1 | [`7.8-accelerometer-specular.md`](../specs/7.8-accelerometer-specular.md) |

The intended order was 7.1 → 7.2 → 7.3 → 7.4 → 7.5. Tasks 7.6–7.8 were independent polish items.

## Expanded-shell navigation (Area 8)

Section 5 ended with a rail for every window at or past 840dp. Measured, that rail is
**146dp of cell per destination** in a 736dp rail and under 100dp wide — a full-height
leading edge carrying five icons and nothing else. At a genuinely wide window the right
chrome is a sidebar: the same five destinations with readable labels, and a project
switcher below them, because Board, Backlog and Sprints are all scoped to one project.

The boundary is derived rather than chosen. A 280dp sidebar leaves exactly
`ShellExpandedMinimumWidth` (840dp) of content, which the layout class's own KDoc already
calls the narrowest genuine list-detail pair. So
`ShellSidebarMinimumWidth = 840 + 280 = 1120dp`, and the Desktop window's 1280dp default
opens in the new class. The rail is unchanged between 840 and 1119dp; its cell density is
8.3, a separate question with a separate PR.

Nothing in this area is cross-repository: no wire format, no response field, no shared
link contract.

| Task | State | Priority | Intent | Depends on | Execution details |
| --- | --- | --- | --- | --- | --- |
| 8.1 | Pending | P1 | A `Sidebar` layout class and the wide shell's leading chrome | None | [`8.1-wide-shell-sidebar.md`](../specs/8.1-wide-shell-sidebar.md) |
| 8.2 | Done | P1 | List and switch projects in the wide sidebar | 8.1 | [`8.2-sidebar-project-switcher.md`](../specs/8.2-sidebar-project-switcher.md) |
| 8.3 | Deferred | P3 | Rail cell density in the 840–1119dp band | 8.1, 8.2 | No spec yet — decide after 8.2 ships: fixed ~64dp cells top-aligned instead of five 146dp cells (see 8.1's risks) |

The intended order is 8.1 → 8.2 → (8.3 if the mid-width rail still reads as empty).

## Active feature backlog

### 9.1 — Remove the deprecated WorkItemEditor expand/collapse parameters
- status: done
- files: `packages/feature-projects-presentation/.../WorkItemEditor.kt`; spec at
  `specs/9.1-remove-workitemeditor-expand-collapse-deprecation.md`

### 9.2 — Remove the deprecated Glass fill-alpha helper
- status: done
- files: `packages/design-system/.../Glass.kt`, `packages/design-system/.../GlassIntensity.kt`;
  spec at `specs/9.2-remove-deprecated-glass-fill-alpha.md`


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
