# settings-user-backend-profile — Hook the Settings identity block to the signed-in user's backend record

- **status:** done
- **priority:** P2
- **plan:** none (standalone; no `docs/PLAN.md` entry). The server half is
  `TaskTrackerServer/specs/user-self-profile.md`; this file is the client half and records the
  shared wire contract.
- **ACK:** granted 2026-10-21; all five decisions under "Decisions to confirm at ACK" are
  accepted as recommended. Server is implemented first, then this side.

## Scope

Client: `:packages:feature-settings-domain` (the profile gateway, the presence wire mapping),
`:packages:feature-settings-data` (the `/v1/me` remote, read-through/cache, write-through),
`:packages:feature-settings-presentation` (name editor, commit points, failure banner),
`:composition-root` (build the authenticated gateway when `apiBaseUrl` is set), and
`:packages:feature-settings-data/build.gradle.kts` (the wire DTO's serialization dependency).

Server: a new `0006_user_presence` migration, `internal/entities` profile read/update,
`internal/profile` handler, two routes, `docs/API.md`, and a contract test.

**No existing response gains or loses a field.** `GET /v1/users/search` and the admin user
object keep their shape; the new keys live only on the new `/v1/me` shape, so installed
clients that strict-decode `userResponse` (no `ignoreUnknownKeys`) are untouched.

Anything outside this list is the drift case in `../AGENTS.md` — abort, re-plan, new `ACK`.

## Files

Client:

- `packages/feature-settings-domain/.../UserDetails.kt` — add `UserPosition.fromLabel()`
  (maps the server's human label, e.g. `"Product owner"`, case-insensitively), and
  `UserStatus.wireValue`/`fromPresence()` (`active`, `away`, `be_right_back`, `busy`).
  Leave `fromWireValue()` alone: it parses the **persisted enum name**, not the wire value,
  and changing it would invalidate every stored `DefaultsKey.UserProfile`.
- `packages/feature-settings-domain/.../UserProfileGateway.kt` *(new)* — `load(): UserDetails`
  and `update(changes: UserProfileChanges): UserDetails`, both `@Throws(AppError::class,
  CancellationException::class)`; `UserProfileChanges` carries `null` for "unchanged".
  A closure/port rather than a module dependency, for the reason `endSession` is a closure.
- `packages/feature-settings-domain/.../SettingsPort.kt` — `userDetails()` becomes
  read-through; `setUserDetails()` stays the local optimistic write; new
  `commitUserDetails(): UserDetails` performs the PATCH and throws on failure.
- `packages/feature-settings-data/.../HttpUserProfileGateway.kt` *(new)* — `GET /v1/me` and
  `PATCH /v1/me`, both `requiresAuth = true`; strict snake_case DTO with a default on every
  field the server can omit; maps `UserDetails` in both directions.
- `packages/feature-settings-data/.../LiveSettingsService.kt` — inject the gateway; keep the
  last server-confirmed snapshot; read-through on `userDetails()`; delta-only PATCH on
  `commitUserDetails()`; on failure restore the flow and the store from the snapshot **before**
  rethrowing. Rethrow `CancellationException`; never swallow it with `runCatching`.
- `packages/feature-settings-presentation/.../SettingsModel.kt` — `profileFailure: AppError?`
  plus `dismissProfileFailure()`; `saveUserDetails()` catches, re-reads `port.userDetails()`
  (already reverted), sets the banner; one save at a time, coalescing a commit that arrives
  while another is in flight.
- `packages/feature-settings-presentation/.../UserNameSheet.kt` *(new)* — prefilled draft,
  Save disabled when blank/unchanged, 120-character cap, Cancel discards.
- `packages/feature-settings-presentation/.../UserDetailsRow.kt` — a name edit affordance;
  the email stays display-only.
- `packages/feature-settings-presentation/.../StatusTextEditor.kt` — an `onCommit` callback
  (IME Done and focus loss) so the note is not PATCHed per keystroke; 280-character cap.
- `packages/feature-settings-presentation/.../SettingsScreen.kt` — open the name sheet;
  discrete pickers apply **and** commit; render the dismissible banner; email labelled as
  the account address.
- `shared-ui/.../NavigationHost.kt` — pass `onUserDetailsChange`, `onUserDetailsCommit` and
  `onDismissProfileFailure` from the settings host to the model's `applyUserDetails`,
  `saveUserDetails` and `dismissProfileFailure`.
- `packages/feature-settings-data/build.gradle.kts` — `libs.kotlinx.serialization.json`.
- `composition-root/.../AppGraph.kt` — in `registerSettings` (already after `registerAuth`),
  bind `UserProfileGateway` with `platforms.httpClient(url, [AuthInterceptor(TokenVault)])`
  only when `platforms.apiBaseUrl != null`; pass `null` otherwise.
- Tests: `UserDetailsMappingTest` (the two new mappings; `SettingsVocabularyTest` is the
  connectivity vocabulary and stays about that), `HttpUserProfileGatewayTest` *(new)*,
  `LiveSettingsServiceTest` (read-through, fallback, delta, failure), `SettingsModelTest`
  (staged commit, revert + banner), `SettingsScenarios` (the name sheet, the note commit and
  cap, the banner), `HostedGraphScenarios` (no client is built without a URL), and
  `TextLimitsTest` (the code-point cap). Regenerate `test-baseline.json`.

## Wire contract

`GET /v1/me` — authenticated, any role, always the caller's own row.

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "email": "jane@example.com",
  "display_name": "Jane Doe",
  "position": "Product owner",
  "avatar_id": "7",
  "presence": "away",
  "presence_note": "At the dentist"
}
```

`PATCH /v1/me` — same auth; every key optional, only present keys applied; response is the
`GET` shape. `email` and `role` are not accepted. An empty patch is `400`.

| key | wire type | client field | writable |
| --- | --- | --- | --- |
| `id` | string | identity check only | no |
| `email` | string | `UserDetails.email` | **no** — account identity; no verification flow exists |
| `display_name` | string, omitempty | `fullName` | yes, 1–120 runes |
| `position` | string, omitempty | `position` via `fromLabel` | yes, 0–120 runes; empty clears |
| `avatar_id` | string, omitempty | `avatarId` (decimal `0`–`9`) | yes; empty clears |
| `presence` | enum string | `status` via `fromPresence` | yes; one of `active`, `away`, `be_right_back`, `busy` |
| `presence_note` | string, omitempty | `statusText` | yes, 0–280 runes; empty clears |

Mapping rules:

- **Position** is the existing free-text `users.position` column, written as the enum's
  `displayName` (matching the admin API's own `"position": "Designer"` example). Unknown
  legacy text (e.g. `"Senior Designer"`) maps to `null` and is **never** rewritten unless the
  user picks a value — `UserProfileChanges.position == null` means "unchanged", so an avatar
  edit cannot erase an admin's text.
- **Avatar** is the existing `users.avatar_id` string, written as the decimal portrait id.
  Unparseable or out-of-range values map to `null` (initials fallback).
- **Presence** is *not* `users.status`: that column is account lifecycle
  (`active`/`inactive`/`deleted`) and is admin-owned. The new keys are backed by a new
  `presence`/`presence_note` pair (server spec).

## Contract

- [x] Opening Settings with `apiBaseUrl != null` issues exactly one `GET /v1/me` and renders
      its six values. `LiveSettingsServiceTest.openingSettingsReadsTheServerProfileAndConfirmsIt`
      supplies a profile the cache does not contain and asserts both the returned value and
      the persisted `DefaultsKey.UserProfile`.
- [x] With no backend, an unreachable one, or a response that cannot be decoded,
      `userDetails()` returns the cached profile and does not throw or show a banner. The
      failing-transport test is the assertion; a decode-failure test covers the second.
- [x] A PATCH is not sent on every edit: `setUserDetails()` updates only the flow/store.
      `commitUserDetails()` sends the delta. The test records zero requests after an edit and
      one after a commit.
- [x] A commit sends only changed keys. Changing the name against a profile whose position is
      unparseable sends `display_name` and no `position`; the pinned request body proves it.
- [x] A failed PATCH restores the last server-confirmed profile and surfaces
      `profileFailure`; dismissing clears it. `SettingsModelTest` asserts the reverted row and
      the banner, and fails if the revert is removed. The model keeps the confirmed value as a
      plain field rather than re-reading `port.userDetails()`: that method is a networked
      read-through, and calling it here would add a GET on the failure path, exactly when the
      connection is known bad. The adapter has already restored its flow and store, so the
      model's snapshot and the observed stream agree.
- [x] `email` never appears in a PATCH body, even when the local cache was seeded from a
      different account. The request-body test pins the exact key set.
- [x] Presence round-trips: `away` ↔ `UserStatus.Away`; a persisted prefs payload written
      before this change still decodes with `fromWireValue` (name-based) and is not migrated.
- [x] The name sheet cannot save a blank or unchanged name; the status note caps at 280
      **code points** and commits on Done/blur, not per keystroke. The cap is
      `capToCodePoints`, not `take(280)`: the server counts runes and a unit-based cut can
      split a surrogate pair. `TextLimitsTest` pins the boundary, and
      `SettingsScenarios.theStatusNoteCommitsOnDoneAndStopsAtTheLimit` drives the Done path.
- [x] The account email is rendered as its own labelled, read-only row
      (`settings_email_label`, `SettingsEmailRowTag`), not as an editable field and not only
      as the name's fallback.
- [x] A rename reaches the shell greeting without a root change, and a failed PATCH reverts it.
      `ShellPreferencesTest` seeds `CachedProfile` and `UserProfile` and asserts that the
      persisted profile name wins over the sign-in snapshot, that a profile whose account email
      differs from the cached identity is ignored, and that `applyUserDetails()` renames and
      falls back to `preferredGreeting` when the name is cleared.
- [x] With no base URL the graph never constructs an HTTP client for the gateway
      (`HostedGraphScenarios` keeps graph construction free of platform calls).
- [x] Reverting the read-through to the cache-only body fails the first test; reverting the
      delta to a full-body PATCH fails the fourth.

## Validation

- Run `scripts/check.sh full` (JVM, `iosSimulatorArm64Test`, `testAndroidHostTest`) before
  merge; `scripts/check.sh` (JVM + architecture + baseline) while iterating.
- Counts: the new mapping, transport, service, model and graph tests live in `commonTest`,
  so they run on all three hosts. Record the exact delta in `test-baseline.json` at
  implementation; `scripts/check-baseline.sh` must pass.
- Structural/behavioural tests, so the `test-reviewer` subagent is not required unless the
  name sheet gains a pixel-reading test.

## Handoff

- **Done:** the server half on `TaskTrackerServer` `feat/user-self-profile` (`0255f03`), and
  the client half on `feat/settings-user-backend-profile`: the domain gateway and wire
  mappings, `HttpUserProfileGateway` (`GET`/`PATCH /v1/me`), read-through/cache/delta/revert in
  `LiveSettingsService`, the name sheet, picker commits, the note's Done/blur commit and
  code-point cap, the read-only account-email row, the dismissible failure banner, graph
  wiring, and the tests named above. The greeting follow-up recorded in the previous handoff is
  closed here too: `ShellPreferences` prefers the persisted `UserProfile` name and `RootScreen`
  applies `SettingsPort.userDetailsUpdates()` while the root is `Main`, so a rename no longer
  waits for sign-in to rewrite `CachedProfile`. `scripts/check.sh full` is green: JVM 1937, iOS 1886,
  Android host 1592 (62 skipped, 0 failed); `test-baseline.json` is regenerated. Nothing is
  pushed.
- **Pending:** nothing. The contract is checked, the diff is reviewed and the branch is
  merge-ready; the merge itself is the only remaining repository action.
- **Next step:** merge after `TaskTrackerServer`'s `feat/user-self-profile`. `/v1/me` is
  additive, so the order is a courtesy rather than a compatibility requirement.
- **Gotchas:** the client is the fixed point; `userResponse` must not grow. `users.status` is
  not presence. `UserStatus.fromWireValue()` means *persisted enum name*; only
  `fromPresence()` reads the server's `active`/`be_right_back` spelling. `UserProfileChanges`
  has no email field and a `null` field means "unchanged", so an unrelated edit cannot clear a
  free-text `position`. The comment on the model's failure path is load-bearing: it restores a
  confirmed snapshot rather than re-reading `port.userDetails()`, which is a networked
  read-through and would add a GET exactly when the connection is known bad. Cap free text
  with `capToCodePoints`, not `take` — the server counts runes and a unit cut can split a
  surrogate pair. The shell greeting reads the name Settings edits, not the sign-in snapshot:
  `ShellPreferences.refreshCurrentUser()` prefers a nonblank `UserDetails.fullName` from
  `DefaultsKey.UserProfile` (when its account email is blank or matches the cached identity)
  and falls back to `UserIdentity.preferredGreeting`; `RootScreen` collects
  `SettingsPort.userDetailsUpdates()` while the root is `Main` and applies each emission
  through `ShellPreferences.applyUserDetails()`, so a rename — and a failed-PATCH revert —
  reaches the greeting without a root change. A profile left by another account is ignored by
  the email check, because the app-scoped settings flow outlives a sign-out.

## Decisions to confirm at ACK

1. **Position** is written as the human label (`"Product owner"`) into the existing
   `users.position`; legacy free text shows as "—" but is never overwritten by an unrelated
   edit. *(Recommended.)*
2. **Presence** gets new `presence`/`presence_note` columns; `users.status` stays lifecycle.
   *(Recommended; reusing `status` would let a presence picker inactivate an account.)*
3. **Email and role are read-only.** Changing an email needs a verification flow that does
   not exist. *(Recommended.)*
4. **Offline writes are refused, not queued:** a failed PATCH reverts and says so. A profile
   edit is a server-owned identity write, not offline work. *(Recommended; a queue would show
   an unsaved value as saved.)*
5. **A member may edit their own display name**, avatar, position, presence and note. Admin
   account fields (email, role, lifecycle status) stay admin-only. *(Recommended.)*

## Risks / notes

- The shell greeting used to read only `DefaultsKey.CachedProfile` via
  `ShellPreferences.refreshCurrentUser()`, so a rename refreshed the Settings row but not the
  greeting until sign-in rewrote that cache. Closed on this branch: the name now prefers the
  persisted `DefaultsKey.UserProfile` (what `LiveSettingsService` writes on every save) and the
  shell subscribes to `SettingsPort.userDetailsUpdates()` while the root is `Main`, so a save
  and a failed-PATCH revert both land immediately. `ShellPreferencesTest` pins the precedence,
  the other-account guard, and the live apply/fallback path; the collector itself is shell
  wiring and has no rendered greeting to assert against yet.
- The status note commits on Done or focus loss, not per keystroke. A screen close in the same
  frame as the last keystroke relies on that focus-loss callback; the value is staged locally
  either way, so the worst case is one lost note edit, not a wrong one.
- The server may omit optional keys (`omitempty`); every client DTO field after `id` needs a
  default or strict decoding fails.
