# sprint-member-summary — per-member sprint participation and progress

- **status:** in-progress
- **priority:** P1
- **context:** Cross-repo feature approved in `ACK` on 2026-11-19. Companion spec in
  `../TaskTrackerServer/specs/sprint-member-summary.md`. The server must land first; this
  client may only push `sprintMember` once the server accepts it.

## Scope

- **Targets affected:** commonMain (plus the three host test targets).
- **Modules affected:** `packages:domain`, `packages:feature-projects-domain`,
  `packages:database`, `packages:feature-projects-data`, `packages:sync`,
  `packages:routing`, `packages:feature-projects-presentation`, `packages:design-system`,
  `shared-ui`, `:composition-root`.

## Files

- `packages/domain/src/commonMain/kotlin/com/tasktracker/domain/Entities.kt` — `SprintMember`
  as its own sync entity. `Sprint` deliberately gains no field: installed clients
  strict-decode sprint payloads, so membership rides the separate `sprintMembers` scope.
- `packages/domain/src/commonMain/kotlin/com/tasktracker/domain/SprintSummary.kt` — per-member
  arithmetic, derived from work items by `assigneeID`.
- `packages/feature-projects-domain/.../ProjectRepositoryPort.kt` — member reads/writes.
- `packages/feature-projects-domain/.../SprintUseCases.kt` — add/edit/remove member use cases
  guarded by the existing `requireMembershipEditable`.
- `packages/database/.../SprintMemberRecord.kt` — new Room entity and DAO, no FK.
- `packages/database/.../TaskTrackerDatabase.kt` — version 2 → 3, register the entity.
- `packages/database/schemas/.../3.json` — exported schema.
- `packages/database/.../Outbox.kt` — new `SprintMember` outbox entity.
- `packages/feature-projects-data/.../RecordMapping.kt` — both mapping directions.
- `packages/feature-projects-data/.../RoomProjectRepository.kt` — atomic writes + queue.
- `packages/feature-projects-data/.../LocalDataSyncSink.kt` — apply pulled members.
- `packages/sync/.../SyncTransport.kt`, `HttpSyncTransport.kt` — new pull scope.
- `packages/routing/.../Routes.kt` — `AppRoute.SprintMemberProgress`.
- `shared-ui/.../NavigationHost.kt` — destination and summary wiring.
- `packages/feature-projects-presentation/.../sprint/SprintSummaryScreen.kt` — member section,
  scroll container, progress link.
- `packages/feature-projects-presentation/.../sprint/SprintDetailScreen.kt`,
  `SprintDetailModel.kt` — member list and add action.
- New `SprintMemberSheet.kt`, `SprintMemberProgressScreen.kt`, `SprintMemberProgressModel.kt`.
- `packages/design-system/src/commonMain/composeResources/values/strings.xml` — new strings.
- Scenario suites in `packages/feature-projects-presentation/src/commonTest/...` and
  `shared-ui/src/commonTest/...`; append new indices, never insert mid-list.

## Contract

- [x] A member may be added/edited/removed only while `!isLocked && !isFinished`; the model
      refuses a direct call even when the UI button is bypassed.
- [x] Per-member delivered points count `Done` items only; `Closed` items appear but earn no
      points; unestimated items contribute zero; unassigned items get a derived row.
- [x] The summary opens for scheduled, in-progress and finished sprints, and its member
      section remains scrollable at compact widths.
- [x] Tapping "Member progress" reaches `AppRoute.SprintMemberProgress` and back returns to
      the summary; shell scenario indices are append-only.
- [x] `RecordCompletenessTest` passes with the new column mapped in both directions.
- [x] No existing DTO gains a key; `sprintMember` rides only its own new scope.
- [ **KMP Constraint:** No platform-specific APIs introduced into `commonMain` without an
      explicit `expect/actual` contract.]

## Non-goal

- No new animation or pixel-reading test; this work is structural and behavioural.
- No server-specific lock rule on the client's wire payload.

## Validation

- `scripts/check.sh quick :packages:domain '<TestClass>'` and the same for
  `feature-projects-data`, `feature-projects-presentation`, `shared-ui`, `routing`.
- `scripts/check.sh` before handoff; `scripts/check.sh full` before merge (all three hosts).
- Regenerate `test-baseline.json` only from a green full run.

## Handoff

- **Done:** domain arithmetic, repository port, guarded use cases, Room table/version 3 and
  exported schema, mapping, local writes/outbox, sync sink/scope, strings, routing,
  summary/progress screens, roster sheet, shell destinations, and scenario/model tests.
  `scripts/check.sh full` is green on all three hosts: JVM 2283, iOS 2220, Android 1895
  (44 skipped), zero failures. `test-baseline.json` was regenerated from that run and the
  full gate was rerun green. `RecordCompletenessTest` passes on JVM and iOS.
- **Pending:** final diff review and commit on `feat/sprint-member-summary`.
- **Next step:** review the two-repo diff once more, then commit each branch; do not push.
- **Gotchas:** the roster is a separate `sprintMember` entity, not a sprint field — adding a
  key to `Sprint` would break installed clients. `SprintSummary` is now a pushed route rather
  than local state so progress can pop back to it. The architecture graph is an allow-list
  but no new edge is needed. A `remember` inside a destination dies on tab switch;
  preferences and selections belong in the shell. A Compose scenario may call `setContent`
  only once per Android host test; recompose through state instead.

## Risks / notes

- The summary scroll container is required: member rows and the existing Delivered section
  are both present, so a fixed-height root would clip on compact screens.
- Directory lookups resolve display names; the domain keeps ids only.
