# Live member directory — replace the seeded picker fixture

- **status:** done
- **priority:** P1
- **plan:** no `docs/PLAN.md` entry — this came from the board's "new project"
  form showing six seeded people instead of the server's accounts. The
  cross-repo plan was ACK'd before implementation; this file is the client half
  of the record. Server half: `TaskTrackerServer/specs/live-member-directory.md`.

## Scope

`:packages:feature-teams-data` (the transport, the decorator, the deleted
fixture), `:composition-root` (the bind), `:packages:feature-teams-domain`
(KDoc), and comment-only updates in `:packages:feature-projects-presentation`.
No wire-format change: the client calls the existing `GET /v1/users/search`
with the existing `userResponse` shape. No new dependency.

## Files

- `packages/feature-teams-data/.../HttpTeamRemote.kt` *(new)* — the directory
  request: `/v1/users/search`, `q`, authenticated, decoding a list of users.
- `packages/feature-teams-data/.../ServerTeamRepository.kt` *(new)* —
  `TeamPort` over the server directory plus the session-backed local half:
  one fetch, local filtering, selected-id resolution, offline fallback.
- `packages/feature-teams-data/.../UserWire.kt` *(new)* — the shared
  `MemberResponse`/`toUser` mapping, moved out of `HttpMemberAdminRemote` so
  the admin list and the directory cannot drift.
- `packages/feature-teams-data/.../HttpMemberAdminRemote.kt` — uses the shared
  wire type instead of its private copy.
- `packages/feature-teams-data/.../RoomTeamRepository.kt` — is now the
  session-only/offline directory; its blank listing is the signed-in user
  alone.
- `packages/feature-teams-data/.../FakeMemberDirectory.kt` — deleted. It was
  the fixture the picker was showing.
- `packages/feature-teams-domain/.../TeamPort.kt` — contract docs name the
  server directory and the offline fallback rather than the fake.
- `composition-root/.../AppGraph.kt` — binds `ServerTeamRepository` when
  `apiBaseUrl` is set, with an `AuthInterceptor`-bearing client; leaves
  `RoomTeamRepository` alone offline.
- `packages/feature-projects-presentation/.../WorkItemAssigneeRow.kt`,
  `.../shared/ViewPreviewData.kt` — stale fixture comments corrected.
- Tests: `HttpTeamRemoteTest.kt` *(new)*, `ServerTeamRepositoryTest.kt` *(new)*,
  `RoomTeamRepositoryTest.kt` (rewritten to the session-only contract).
- `test-baseline.json` — regenerated.

## Contract

- [x] The picker's blank search over a configured backend is the server's live
      directory, not a fixture. `ServerTeamRepositoryTest` returns two server
      rows and asserts both, sorted by greeting. `RoomTeamRepositoryTest`'s
      blank search returns only the signed-in account, so a fixture leaking
      back in fails.
- [x] The request is `GET /v1/users/search` with `q` and `requiresAuth = true`.
      `HttpTeamRemoteTest` pins both the blank and typed forms.
- [x] A typed query filters the already-fetched directory; it does not cost a
      request per keystroke. The test asserts one request across a blank load
      followed by `"gRaCe"`.
- [x] Opening the picker again refreshes the directory, so a member added since
      the last fetch appears. The test returns one row, then two, and asserts
      two requests.
- [x] Selected ids resolve through the server directory even when the cache was
      cold, so a selected collaborator's pill keeps its name.
      `selectedIdsResolveThroughTheServerDirectory`.
- [x] An unreachable server falls back to the session account instead of
      throwing or showing an empty, unusable picker. The failing-transport test
      asserts the signed-in user is still returned and searchable.
- [x] The shared `MemberResponse` still carries every field the server sends,
      including `created_at`, because `JSONCoding` leaves `ignoreUnknownKeys`
      off. The decode test round-trips a full row and an omitted-optionals row.
- [x] `FakeMemberDirectory` has no remaining references outside `.git` and IDE
      state.
- [x] The online graph resolves `TeamPort` to `ServerTeamRepository`; the
      offline graph keeps `RoomTeamRepository`. The bind is the only change in
      `AppGraph`.
- [x] Reverting to `RoomTeamRepository` for the online bind fails the intended
      behaviour: the picker would again show one person, and the fixture no
      longer exists to hide that.

## Validation

- Run: focused JVM tests for the touched modules (under a minute). The release gate —
  `scripts/check.sh full` — covers JVM, `iosSimulatorArm64Test` and `testAndroidHostTest`,
  which is what proves the shared `commonTest` additions on the two native hosts.
- Counts: **+9 tests on every host** (1831 → 1840 JVM, 1780 → 1789 iOS,
  1486 → 1495 Android). The new tests live in `commonTest`, so all three hosts
  run them; the two existing tests that asserted the six-seed fixture were
  replaced in place. Regenerated with `scripts/test-baseline.py`.
- These are structural/behavioural tests, not pixel tests, so the
  `test-reviewer` subagent is not required.

## Handoff

- **Done:** the directory transport, server-backed `TeamPort`, session-only
  fallback, shared user wire mapper, fixture deletion, graph bind, tests, and
  baseline. Full three-host run is green.
- **Pending:** nothing in this repo. The server half must ship for the blank
  query to answer `200`; until then the repo falls back to the session account
  when the server's `400` surfaces as an `AppError`.
- **Next step:** review and merge both branches. Deploy the server before
  expecting the picker to list colleagues in a build.
- **Gotchas:** a blank query is the refresh point; typed queries are
  intentionally cached and local. Do not add a request per keystroke. Do not
  make `user()` refetch on every miss — a deleted id would turn a board's name
  resolution into a request storm; the blank-query fetch is what notices new
  members. The repository caches a `List<User>` without a mutex, so the current
  callers must stay on the same dispatcher; adding a third concurrent caller
  should bring back a lock, not a second cache. Keep `created_at` declared:
  turning on `ignoreUnknownKeys` globally is not a substitute.

## Risks / notes

- The server's non-empty search includes deleted members while the blank
  listing excludes them. The client filters whatever the server returns; it
  does not re-implement the deletion policy. Do not "fix" the asymmetry on the
  client.
- `RoomTeamRepository` is still the offline fallback and still owns the
  signed-in identity, so it must stay registered even when the decorator is
  bound.
- The decorator caches per `AppGraph`; signing out does not clear it. That is
  acceptable because the server is the same and the directory is already
  visible to every authenticated account, but a future multi-tenant server
  would need a sign-out clear.
