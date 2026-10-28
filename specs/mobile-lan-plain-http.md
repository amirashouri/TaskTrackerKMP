# mobile-lan-plain-http — let mobile clients reach the plain-HTTP LAN server like desktop

- **status:** in-progress
- **priority:** P0
- **plan:** [`docs/PLAN.md`](../docs/PLAN.md) §10, entry `10.1`. This reverses a recorded
  product decision ("`lan` does not serve the Android client over plain HTTP"), so this
  file is the record of the reversal and its rationale, not just its execution.

## Scope

Config and docs only: no Kotlin source change, no module change, no public signature, no
wire-format change, no server code. The 6.3 validator already implements the product
policy — `https` anywhere; `http` only for loopback, RFC1918, link-local,
CGNAT/Tailscale (`100.64.0.0/10`), `.local`, and single-label names — and gates every
entry point (the Settings sheet, the pairing prefill) identically on all three platforms.
What keeps a phone off the `lan` provider is two platform transport gates plus one
pending permission acceptance:

1. Android's cleartext allow-list (`network_security_config.xml`: `10.0.2.2` and
   `localhost` only).
2. iOS: no ATS exception is declared, and `NSLocalNetworkUsageDescription` is absent —
   so on iOS 14+ the local-network prompt cannot even appear and local connections are
   denied.
3. `android-local-network-permission.md` is code-complete but its device acceptance is
   pending. Completing it is part of this task.

The server needs nothing: T9.3 binds and publishes `0.0.0.0`, and T9.8's pairing QR
already emits `http://<LAN-IP>:<port>`.

## Files

Confirmed at Phase 1 (presented for ACK together with this spec).

Client:

- `androidApp/src/main/res/xml/network_security_config.xml` — replace the two-domain
  allow-list with `<base-config cleartextTrafficPermitted="true"/>`; rewrite the comment:
  the transport gate is open and the policy gate is 6.3's `validateServerAddress` (cite
  it), so the comment explains why no domain list exists instead of listing exceptions.
- `iosApp/TaskTracker/Info.plist` — add `NSAppTransportSecurity` →
  `NSAllowsLocalNetworking = true` (cleartext to local resources only) and
  `NSLocalNetworkUsageDescription` with a purpose string; without it iOS 14+ denies
  local connections without prompting. The plist is hand-written
  (`GENERATE_INFOPLIST_FILE: NO`), so no `project.yml` change.
- `Project.md` — the "Cleartext Guard" bullet states the new posture.
- `specs/android-local-network-permission.md` — its pending device acceptance moves into
  this task's validation; update its Handoff when it completes.
- `docs/PLAN.md`, this file — index and status.

Server (docs only; no Go change, so no server spec):

- `TaskTrackerServer/docs/INSTALL.md` — the provider-table Android note, the LAN-section
  caveat, and the troubleshooting row now say mobile works over `lan`, keeping the
  trusted-LAN warning and naming the iOS prompt and the iOS ATS boundary.
- `TaskTrackerServer/docs/SELF-HOSTING.md` — §8's Android caveat becomes the mobile note.
- `TaskTrackerServer/docs/PLAN.md` — the Milestone 9 preamble sentence claiming phones
  need a remote HTTPS provider.
- `TaskTrackerServer/docs/PLAN-ARCHIVE.md` — a dated addendum to the T9.8 rationale;
  history is not rewritten.
- `/TaskTracker/AGENTS.md` §7 — the "`lan` does not serve the Android client over plain
  HTTP" sentence records the reversal. The file itself warns that edits there are not
  reviewable; the sentence is a cross-repo rule and must not keep asserting the old
  posture.

## Contract

- [x] Android, emulator: the app reaches `http://<host-LAN-IP>:<port>/healthz` and signs
      in. Verified against `192.168.31.140:8080` (API 37, `Pixel_10_Pro`): logcat shows
      the app uid connecting with `err=0`, the server log shows `POST /v1/auth/login 200`
      followed by the three sync pulls, and the app lands on Projects. This address was
      cleartext-refused before the config change — the login is the proof the widening
      works.
- [x] Android, emulator (in lieu of a real device, same targetSdk 37 image): the first
      launch against the LAN root prompts `ACCESS_LOCAL_NETWORK` once; granted, the login
      POST appears in the server log; denied, the screen stays usable with the ordinary
      retryable `AppError.Transport` ("Connect timeout has expired", naming the URL), and
      a relaunch re-asks. Completes `android-local-network-permission.md`'s pending
      device acceptance. Real-device confirmation stays pending below.
- [ ] iOS, real device: the Local Network prompt appears on the first local connection;
      after granting, `http://<mac-LAN-IP>:<port>` (or a `*.local` name) signs in. A
      public `http://` host is still refused — by the validator at save time on all
      platforms, and by ATS on iOS even before that. **Pending hardware.**
- [x] Pairing end-to-end on mobile: `tasktracker://connect?url=http%3A%2F%2F192.168.31.140%3A8080`
      fired by intent opens Settings with the address prefilled; Save runs 6.3's probe
      (`GET /healthz 200` in the server log) and persists the root. Desktop's flow is
      unchanged.
- [x] No `.kt` file changes (`git diff --name-only` shows only the config, plist, and
      docs); 6.3's validator tests pass untouched; `scripts/check.sh full` green on all
      three hosts (JVM 2083 / iOS 2021 / Android 1715, 0 failures) with `test-baseline.json`
      unmoved.
- [x] Grep for the old claim ("Android client blocks cleartext", "phones need a TLS
      provider") returns no active doc asserting it; PLAN-ARCHIVE history keeps its
      addendum instead of being edited.

## Non-goal

- [ ] No `NSAllowsArbitraryLoads`: iOS keeps a transport boundary for non-local
      cleartext (App Store posture, defense in depth) above the same validator policy.
- [ ] No validator change: public `http` stays refused everywhere; the CGNAT/Tailscale
      range stays local.
- [ ] No Bonjour/mDNS discovery: the QR / `tasktracker://connect` link is the pairing
      path.
- [ ] No server TLS work and no new exposure provider; T9.11 stays deferred.

## Validation

- Android: `./gradlew :androidApp:installDebug` on the API 37 emulator; set the root to
  the host's LAN IP; compare the server log against
  `adb shell cmd appops get com.tasktracker.android ACCESS_LOCAL_NETWORK`. A real device
  exercises both gates at once and is preferred when available.
- iOS: a real-device build from Xcode pointed at the Mac's LAN IP; the prompt-and-grant
  flow is not observable on the simulator.
- `scripts/check.sh full` before merge (PR gate); expected: no count movement — there is
  no test change and no Kotlin change.
- Server: docs-only, so no Go test and no release-gate run.

## Handoff

- **Done:** both config changes (Android `base-config`, iOS ATS + purpose string),
  `Project.md` posture bullet, the full server docs sweep (INSTALL, SELF-HOSTING, PLAN
  preamble, PLAN-ARCHIVE addendum) and the workspace `AGENTS.md` §7 reversal. Emulator
  acceptance complete: LAN-root login 200, prompt/grant/deny/re-ask flows, pairing
  deep-link → prefill → probe → save. `scripts/check.sh full` green on all three hosts,
  baseline unmoved, no `.kt` changed.
- **Pending:** iOS real-device validation (prompt + LAN sign-in) and an optional
  real-Android-device confirmation — both blocked on hardware, not on code. Then push
  and MR.
- **Next step:** validate on real devices when available; push `feat/mobile-lan-plain-http`
  and open the PR.
- **Gotchas:**
  - On the emulator, a **routed** LAN address (`192.168.31.x`, reached through the
    guest's NAT) is *not* gated by a denied `ACCESS_LOCAL_NETWORK` — sign-in succeeds
    even after denial. The block is only observable against an **on-link** address
    (`10.0.2.2`). On a real phone the LAN address is on-link, so denial does block it.
    The prompt appears for both, because `apiRootRequiresLocalNetworkAccess` is a pure
    host-range rule. Do not "fix" the classifier to match the emulator's routing.
  - The policy layer is `validateServerAddress` — do not "harden" the config files back
    into a domain list, and do not widen the validator to public `http`. iOS ATS covers
    RFC1918, link-local, and `.local`; a raw CGNAT `http://100.x.x.x` root may still be
    transport-blocked on iOS, and the tailscale provider's supported root is the
    `https://…ts.net` name, which needs no exception.
  - The server for the run was `go run ./cmd/server` with `SERVER_BIND=0.0.0.0` and
    `DATABASE_URL=sqlite://data/tasktracker.db` (the committed `.env` points at the
    container path `/data/...` and must be overridden locally).

## Risks / notes

- The asymmetry is deliberate: Android's transport gate becomes fully open (the
  validator is the only public-`http` guard), while iOS keeps the ATS boundary for
  non-local hosts. Both sit above the same app-level policy, which is also what desktop
  runs.
- The 6.4 contract line "Android's cleartext allow-list is **not** widened" and the
  permission spec's "cleartext is still the second gate" note are superseded by this
  spec; their historical records stand, and the reversal is recorded here and in the
  workspace `AGENTS.md`.
- Android 16 (API 36) enforces local-network protection under `NEARBY_WIFI_DEVICES`; the
  manifest declares the API 37 permission only, per the permission spec's
  split-permission note — unchanged here.
