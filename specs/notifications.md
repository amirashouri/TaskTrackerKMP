# notifications — inbox, badges, in-app popups and deep links

- **status:** in-progress
- **priority:** P1
- **branch:** `main` (inbox merged in #97; badges/local notifications uncommitted)
- **blocked by:** `../TaskTrackerServer/specs/notifications.md` — **the server lands first**;
  the client may not pull `notification` until the server serves the scope and accepts the
  read/dismiss pushes.
- **context:** Requested 2026-11-21: an inbox for "you have been assigned" and "someone
  commented on your task", reachable above Settings on desktop and tablet, with search,
  multi-select, read/delete actions, a 3-second in-app popup and a highlighted row after
  tapping it. Cross-repo wire change: read `../AGENTS.md` §1/§2 and the companion server
  spec before planning. The client already has `feature-notifications-domain`,
  `feature-notifications-data`, `NotificationRecord` in Room (schema v3) and an empty
  `feature-notifications-presentation`, but none of it is wired into the graph or the shell.

## Scope

- `:packages:routing` — the Notifications destination, its stack, the highlight intent.
- `:packages:sync` — the `notifications` pull scope and the `notification` entity.
- `:packages:database` — the notification DAO's missing remote-apply, delete and stream
  queries (the table itself already exists).
- `:packages:feature-notifications-domain` — the new kind and its typed payloads; the port's
  delete, arrival and unread streams.
- `:packages:feature-notifications-data` — the package declaration, outbox writes, the sync
  sink and the arrival broadcast.
- `:packages:feature-notifications-presentation` — the inbox, card, header and popup banner.
- `:packages:design-system` — the bell glyph, badge support and the inbox's strings.
- `:packages:feature-projects-presentation` — an editor that can open on a comment.
- `:shared-ui` — chrome placement, the destination, the banner host and the editor's focus
  argument.
- `:composition-root` — the graph binding.

Ten modules, so the Phase 1 `ACK` is required. No project, work-item, comment or sprint DTO
changes.

## Starting point, and what is already wrong

- `feature-notifications-domain`'s `Notification`/`NotificationKind`/`NotificationPort` exist
  and are tested only for serialization and kind values. The port has no delete-by-id, no
  arrival stream and no unread stream.
- `RoomNotificationRepository.kt` **has no `package` declaration** (it starts at `import`),
  so it compiles into the root package; its `createNotification` dedupe key is
  `kind-user` rather than the notification id; and it defaults `LocalNotifier` to
  `createLocalNotifier(null)`, which throws on Android because the notifier needs an
  application `Context`.
- `feature-notifications-presentation` contains only `build.gradle.kts`.
- `NotificationRecord` is already listed in `TaskTrackerDatabase` at schema version 3, so no
  version bump is needed unless a column is added.

These are part of this task, not drive-by cleanup: the feature cannot work without them.

## Files

- `packages/routing/src/commonMain/kotlin/com/tasktracker/routing/Routes.kt` —
  `AppTab.Notifications`; `isProjectScoped` and `selectsActiveProject` branches;
  `AppRoute.WorkItemEditor.focusCommentID`.
- `packages/routing/src/commonMain/kotlin/com/tasktracker/routing/Router.kt` —
  `notificationsPath`, `pathFor`/`withPath`/`clearedNavigation`, `highlightedNotificationID`,
  `openNotifications(highlight)`, `consumeNotificationHighlight()`.
- `packages/sync/src/commonMain/kotlin/com/tasktracker/sync/SyncScope.kt` — `Notifications`.
- `packages/database/src/commonMain/kotlin/com/tasktracker/database/Outbox.kt` —
  `OutboxEntity.Notification("notification")`.
- `packages/database/.../NotificationRecord.kt` — update-from-remote, delete-by-id, unread
  and observation queries.
- `packages/feature-notifications-domain/.../Notification.kt` — `CommentAdded("comment_added")`;
  typed payloads; tolerant decoding of an unknown kind.
- `packages/feature-notifications-domain/.../NotificationPort.kt` — `delete(id)`,
  `notification(id)`, `notificationsStream`, `unreadCountStream`, `arrivals`.
- `packages/feature-notifications-domain/.../NotificationUseCases.kt` — `DeleteNotification`,
  read/read-all staying as they are.
- `packages/feature-notifications-data/.../RoomNotificationRepository.kt` — package fix,
  write+queue and write+push transactions, arrival emission, host-aware notifier.
- `packages/feature-notifications-data/.../NotificationSyncSink.kt` — new; applies pulled
  create/update/dismiss idempotently and never writes the outbox.
- `packages/feature-notifications-presentation/.../NotificationsModel.kt` — new.
- `.../NotificationsScreen.kt`, `.../NotificationCard.kt`, `.../NotificationArrivalBanner.kt`,
  `.../NotificationFormatting.kt` — new.
- `packages/design-system/.../TabIcons.kt` — a bell glyph.
- `packages/design-system/.../TabBar.kt`, `.../NavigationRail.kt` — optional per-destination
  badge count.
- `packages/design-system/src/commonMain/composeResources/values/strings.xml` — tab label,
  the two preset titles, empty/search/select/read/delete labels, date words.
- `packages/feature-projects-presentation/.../workitem/WorkItemEditor.kt` — `focusCommentID`
  on both sheet overloads, scroll-and-flash in the comments section.
- `packages/feature-projects-presentation/.../workitem/CommentThread.kt` — a highlighted
  comment and a `BringIntoViewRequester` anchor.
- `shared-ui/.../NavigationHost.kt` — destination, banner host, editor focus argument,
  compact entry point, badge wiring.
- `shared-ui/.../NavigationSidebar.kt` — the Notifications row directly above Settings.
- `shared-ui/.../TaskTrackerApp.kt` / `RootScreen.kt` — only if the signed-in resolver seam
  needs the new port; prefer resolving in `NavigationHost` as the settings port is.
- `composition-root/.../AppGraph.kt` — `NotificationPort` binding and the merged sink map.

## Contract

- [ ] **Wide chrome** (desktop `Sidebar`, tablet `Expanded` rail) lists Notifications
      immediately above Settings and renders the inbox as that tab's root. Selecting it
      shows the inbox in both arrangements.
- [ ] **Compact chrome** exposes the same inbox. Recommended: a sixth bar destination
      labelled **Inbox** with the bell glyph and an unread badge, because it keeps one
      navigation state (`AppTab`) and no duplicate route. The alternative — a bell control in
      the shell chrome — is an open decision recorded below. The chosen option is fixed
      before implementation.
- [ ] The inbox renders newest first by `createdAt` (id as a stable tie-break), inside the
      same page surface primitives every other destination uses. There is no second page
      scaffold.
- [ ] A card has: a preset title from its kind ("You have been assigned" / "New comment"), a
      one-line description (the task title, or the comment preview with its task key), a date
      label, an unread marker, a mark-read button and a trash button.
- [ ] Tapping the card (not one of its buttons) navigates to the work item; a
      `comment_added` card opens the editor scrolled to that comment with a brief flash. A
      card whose work item is not local still opens its project's board from `projectId`.
- [ ] The mark-read button sets `isRead` and is absent or disabled on an already-read card.
      Trash deletes the notification immediately.
- [ ] The header carries search on the left and a multi-select button on the trailing edge.
      Search filters client-side over title, task title/key and comment preview. Multi-select
      enters a selection mode with select-all, mark-read, delete and cancel; the card tap then
      toggles selection instead of navigating.
- [ ] An arriving notification (one delivered by a pull while signed in, not an older row
      replayed from the log) shows a popup at the top of the signed-in shell:
      - disappears after 3 seconds;
      - an upward swipe dismisses it;
      - tapping it selects Notifications and sets the highlight for that id;
      - arrivals older than 60 seconds are not shown, only one popup is visible at a time,
        and no popup is shown while the inbox is already open.
- [ ] The highlighted inbox row flashes its accent wash briefly, then the highlight is
      consumed, so returning to the tab later does not flash it again.
- [ ] Read and delete are offline-first: the local row changes immediately and an outbox row
      is queued in the same transaction. Read-all queues one upsert per unread row. The pull
      applies the server's copy idempotently, and a delete applied twice is a no-op.
- [ ] The destination row and the compact entry point show the unread count as a badge; the
      count comes from the local stream, not a request.
- [ ] The client pulls the new `notifications` scope through the existing engine, and
      `SyncEngine`'s sink map stays total: an entity with no sink is still a fatal wiring
      error rather than a silent skip.
- [ ] The pulled DTO declares exactly the server's keys (`id`, `userID`, `kind`, `payload`,
      `isRead`, `createdAt`, `updatedAt`); an unknown kind or an unreadable payload string
      becomes a generic, non-navigating row instead of failing the whole pull.
- [ ] Reverting the UI change fails a focused test that pins the title/description/navigation
      target; reverting the flash fails its rendered/motion test (mutation rule, §4).

## Non-goal

- No APNs/FCM or background delivery. OS local notifications are scheduled from the sync
  sink for newly inserted remote rows (Android permission/channel, iOS authorization, desktop
  tray); the in-app popup remains a separate, foreground-only surface.
- No mention, due-today or status-changed generation; the server generates only assignment
  and comment notifications. The existing enum entries stay for later local rules.
- No notification preferences, per-project mute, or notification settings screen.
- No realtime transport (websocket/SSE): arrivals reach the inbox through the existing sync
  cycle.
- No pagination or server-side search: the inbox is the local list, filtered locally.
- No new deep-link URL for the inbox in this task.

## Mobile placement — options for the open decision

| Option | Shape | Cost |
| --- | --- | --- |
| **A (recommended)** | Sixth bottom-bar destination labelled **Inbox** | The bar's five-even-segments test becomes six; the label must be short |
| B | Bell with unread badge in the shell's top-trailing chrome, opening the tab | Overlays project headers at the top-right; a second way into one destination |
| C | Sixth destination labelled Notifications | The word does not fit a phone segment at the current type size |

**Opinion:** A. One `AppTab`, one stack and one selected-state source; the badge is a
property of the destination rather than a second control that has to stay in sync. On wide
chrome the same tab is labelled "Notifications" above Settings, exactly as requested. If the
six-segment bar is unacceptable, B is the fallback, but it should keep selecting the same
`AppTab` and not grow a second route.

## Validation

- **Iteration:** `scripts/check.sh quick :packages:feature-notifications-domain '<Test>'`,
  then the same for `:packages:feature-notifications-data`, `:packages:sync`, `:packages:routing`,
  `:shared-ui` and `:packages:feature-projects-presentation`.
- **Before handoff:** `scripts/check.sh full` — the task touches rendering, a motion
  highlight, badges and the shell's build graph.
- **Rendered pixels:** the badge, the unread/read contrast and the highlight flash are
  measured on JVM and iOS; each follows the mutation rule (green → revert → must fail
  naming the defect → restore).
- **Counts / baselines:** `test-baseline.json` moves on the JVM host and is regenerated by a
  green full run, never hand-edited.
- **Stays pending:** the OS notification surface itself (Android permission/channel, iOS
  authorization, desktop tray) cannot be exercised by the host tests; verify on a device and
  on a desktop.
- **Cross-stack:** one push/pull round-trip against the real server proves the DTO pair;
  the server's contract test owns the key names.

## Promote

- **To `docs/DECISIONS.md`:** the inbox is one `AppTab` on every width so selected state and
  stacks have one source; the unread count is local; read/dismiss ride the outbox while the
  notification itself is server-generated and never client-created.

## Handoff

### Done

- `AppTab.Notifications` (sixth compact tab, the mobile Option A recorded in this spec),
  the sidebar row above Settings, the routing stack and the one-shot highlight intent.
- `notifications` scope and `notification` entity; the Room DAO's remote-apply and
  delete queries; the repository rewritten with a package, outbox read/dismiss writes,
  live streams, arrival events and the sync sink in one object.
- Inbox screen with search, multi-select, bulk read/delete, per-card read/delete and
  newest-first cards; three-second popup with swipe-up/close dismissal, tap to open the
  inbox and flash the row.
- Comment notifications open the work item with the comment highlighted.
- Graph binding, including a total outbox sink map (notifications never reach the
  projects sink) and the deliberate tab-count/test updates.
- Unread counts on the compact bar, expanded rail and sidebar row, from one live
  `unreadCountStream` subscription in `NavigationHost`; the badge is a reusable
  `ttCountBadge` drawn inside the fixed icon box, so chrome geometry is unchanged.
- OS local notifications for newly inserted, unread remote rows: Android permission plus
  tap-to-open (`POST_NOTIFICATIONS`, channel, launch intent), iOS `UNUserNotificationCenter`
  authorization/schedule/cancel, and desktop `SystemTray`; read/delete/read-all cancels the
  matching local notification. Scheduling is best-effort and cannot fail a sync apply.
- `RoomNotificationRepositoryTest` (20 commonTest cases) covers the pull path, replay and
  stale-update handling, local read/dismiss/read-all writes, outbox contents, recipient
  scoping, retention and the local-notifier calls. The module's Android host test task is
  disabled for the same missing-`Context` reason as the database and projects-data modules.

### Pending / next

- No visual pass on a device/desktop yet, and the OS banners have not been exercised on a
  real Android device, iOS simulator/device or desktop tray.
- The full host matrix and `test-baseline.json` regeneration remain after adding the
  repository tests.
- Work is uncommitted on `main`.

### Gotchas

- The sync log hands back the notification as a JSON **string**, so the sink unwraps a
  string before decoding the document. Both forms are accepted for older rows.
- Read/dismiss queue a whole document (read) or the bare id (delete); the server narrows
  the former and treats the latter as idempotent.
- `OutboxEntity.Notification` must stay routed to `RoomNotificationRepository`; the
  projects sink throws `SyncException.NoSink` if it ever receives one.
- The compact bar's six-tab count is a deliberate literal in `ShellScenarios`
  (`PHONE_TAB_COUNT`) and in the routing test; a seventh tab must change both.
