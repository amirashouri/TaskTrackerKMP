# android-local-network-permission — let the Android client reach a local API root

- **status:** in-progress
- **priority:** P0
- **plan:** no `docs/PLAN.md` entry — found from an emulator's sign-in failure rather than
  picked off the roadmap, so this file *is* the record. It is a prerequisite for this repo's
  §6 (server connection) and for Milestone 9's `lan` provider in
  `../TaskTrackerServer/docs/PLAN.md`: without it no Android client can reach a LAN server,
  whatever the CLI prints.

## Scope

Two modules: `:composition-root` (the classification rule, `commonMain` + `commonTest`) and
`:androidApp` (the manifest declaration and the launch-time request). No network change, no
wire-format change, no server change, no dependency added.

## Why

Android 17 (API 37) enforces **Local Network Protection**: an app targeting 37 may not talk
to addresses on its own local network without `android.permission.ACCESS_LOCAL_NETWORK`
(permission group `NEARBY_DEVICES`), declared in the manifest and requested at runtime. The
enforcement is in the networking stack, and it fails **silently** — a blocked TCP connect is
not refused, it times out — so the client reports `AppError.Transport("Could not reach the
server. Connect timeout has expired …")` against a server that is running and reachable.

This app is `targetSdk = 37` and declared only `INTERNET` + `ACCESS_NETWORK_STATE`, so every
connection from its uid to `10.0.2.2` — the Android fallback API root — was dropped.

## Files

- `composition-root/src/commonMain/kotlin/com/tasktracker/compositionroot/LocalNetworkAddress.kt`
  — `apiRootRequiresLocalNetworkAccess(apiBaseUrl)`: the pure classification (Android's
  documented IPv4/IPv6 local ranges, `.local` and single-label mDNS names; loopback excluded
  because it needs no permission).
- `composition-root/src/commonTest/kotlin/com/tasktracker/compositionroot/LocalNetworkAddressTest.kt`
  — one case per range, one per exclusion, the boundaries between them, and the shape
  (userinfo/path/query/no port) that must not hide the host.
- `androidApp/src/main/AndroidManifest.xml` — declares the permission, with the measured
  symptom in the comment so the next reader does not remove it as unused.
- `androidApp/src/main/kotlin/com/tasktracker/android/TaskTrackerApplication.kt` — exposes the
  API root it already computes, so the activity does not read the manifest a second time.
- `androidApp/src/main/kotlin/com/tasktracker/android/MainActivity.kt` — requests the
  permission on create, from API 36 up, only when the root is local and the permission is not
  already granted.
- `test-baseline.json` — regenerated: the new class lives in `commonTest`, so its tests run on
  all three hosts and every total moves.

## Measurements (API 37 emulator, `Pixel_10_Pro`, virtual Wi-Fi, gateway `10.0.2.2`)

| Probe | Result |
| --- | --- |
| host `curl 127.0.0.1:8080/healthz` | `200 ok` (compose publishes `0.0.0.0:8080`) |
| emulator **shell** → `10.0.2.2:8080` | `200 ok` |
| emulator **shell** → `10.0.2.2:80` | `Connection refused` — packets reach the host |
| **app uid** → `10.0.2.2:8080`, `:5432`, `:80`, `:53` | `nc: Timeout` on every port |
| **app uid** → host LAN `192.168.31.140:8080` | connects (not the guest's local network) |
| **app uid** → `127.0.0.1:8080` after `adb reverse tcp:8080 tcp:8080` | connects (loopback is exempt) |
| **Chrome** → `http://10.0.2.2:8080/healthz` | renders `ok`; server logs `GET /healthz 200` |
| `cmd appops get com.android.chrome ACCESS_LOCAL_NETWORK` | `allow` |
| `cmd appops get com.tasktracker.android ACCESS_LOCAL_NETWORK` | `Uid mode: … ignore` + `rejectTime` |

`10.0.2.3:53` answering from the app uid is consistent with the same gate: Android exempts
DNS traffic to a local DNS server on port 53.

## Contract

- [ ] `apiRootRequiresLocalNetworkAccess("http://10.0.2.2:8080")` is `true`; loopback
      (`127.0.0.1`, `localhost`, `::1`) is `false` — pinned by tests, not by device state.
- [ ] The ranges are exactly Android's documented set. A change that widens them (e.g. all
      `255.*` as broadcast, or all of `172.*`) must fail the boundary test.
- [ ] On an API 37 emulator, sign-in with the default root reaches the server: the permission
      prompt appears once, and after it is granted `POST /v1/auth/login` appears in the Go
      server's log and the app's `ACCESS_LOCAL_NETWORK` uid mode is no longer `ignore`.
- [ ] A root that is not local (a public HTTPS hostname) produces **no prompt**.
- [ ] A denied request leaves the screen usable: the failure is still the ordinary retryable
      `AppError.Transport`, and pressing Sign In again re-asks the platform.
- [ ] Mutation check: widen `172.16.0.0/12` to `172.16.0.0/11` and the boundary test fails
      naming `172.32.0.1`; restore and it passes.

## Validation

- Run: `scripts/check.sh full` — the test is in `commonTest`, so the Android and iOS hosts must
  compile and run it too.
- Counts: regenerate `test-baseline.json` with `scripts/test-baseline.py`; all three hosts move.
- Device: `./gradlew :androidApp:installDebug`, then compare `docker logs` for the login POST
  with `adb shell cmd appops get com.tasktracker.android ACCESS_LOCAL_NETWORK`.
- `LocalNetworkAddressTest` is a structural assertion over a pure function; the `test-reviewer`
  subagent is not required.

## Handoff

- **Done:** the rule and its tests, the manifest declaration, the launch-time request, the
  `Application` API-root accessor, and the docs (`Project.md`, `specs/6.1-…`).
- **Pending:** the device acceptance in the contract above, then the full gate and the
  regenerated baseline. Not attempted in this change: a prompt at *pairing* time for §6.3's
  runtime URL — that task owns the `/healthz` probe and should report a blocked address there.
- **Next step:** run the two commands in Validation, then verify on the emulator.
- **Gotchas:** the classifier is deliberately blind to a **hostname that resolves** to a local
  address (`nas.tailnet.ts.net`): resolving it would be a network round trip, not a pure rule.
  Such a root is classified remote and its first request times out. That is the documented
  boundary, and why §6.3's probe matters. Do not "fix" it by asking for the permission on every
  launch — a public-server deployment would get a nearby-devices prompt it cannot use.

## Risks / notes

- **Loopback is not local network.** `adb reverse tcp:8080 tcp:8080` plus
  `-Ptasktracker.apiBaseUrl=http://localhost:8080` works today with no permission at all; that
  remains the cheapest way to run the app against a dev machine, and the reason `localhost` is
  in the cleartext allow-list.
- **`100.64.0.0/10` covers Tailscale**, so this is not only a developer concern: once a
  customer points the app at a tailnet address, the permission is the difference between a
  working sync and a timeout.
- **Cleartext is still the second gate** for a LAN root (`network_security_config.xml` allows
  only `localhost` and `10.0.2.2`), and it is a separate decision recorded in `docs/PLAN.md`
  §6. This change does not widen it.
- Android 16 enforces the same idea under `NEARBY_WIFI_DEVICES`; the manifest declares the
  Android 17 permission only, because that is where this app's `targetSdk` sits. An app whose
  target moves below 37 must drop the declaration *and* the request, per Android's split
  permission model.
