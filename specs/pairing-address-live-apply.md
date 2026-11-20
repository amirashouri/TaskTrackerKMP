# pairing-address-live-apply — apply a confirmed pairing address immediately

- **status:** in-progress
- **priority:** P1
- **branch:** `feat/pairing-address-live-apply`
- **blocked by:** none
- **context:** Scanning `tt expose`'s QR opened Settings with the verified LAN URL prefilled, but
  saving it only changed the preference for the next launch. The installed Android build's
  build-time root (`local.properties` → `http://192.168.31.140:8080`) kept winning, so login
  failed against the old address. The QR itself was correct.

## Scope

- **Targets affected:** commonMain, commonTest
- **Modules affected:** `:packages:network`, `:composition-root`,
  `:packages:feature-settings-data`, `:packages:feature-settings-presentation`,
  `:packages:design-system`, `:shared-ui`

## Files

- `packages/network/src/commonMain/kotlin/com/tasktracker/network/KtorHttpClient.kt` — resolve
  the API root through a provider on every request instead of capturing it at construction.
- `composition-root/src/commonMain/kotlin/com/tasktracker/compositionroot/AppGraph.kt` — hold one
  app-scoped address state initialized from `platforms.apiBaseUrl`, feed every HTTP client from
  it, and pass an update hook into `LiveSettingsService`.
- `packages/feature-settings-data/src/commonMain/kotlin/com/tasktracker/featuresettingsdata/LiveSettingsService.kt`
  — publish a validated saved address into that state immediately; reset restores the
  build-time root.
- `packages/feature-settings-presentation/src/commonMain/kotlin/com/tasktracker/featuresettingspresentation/SettingsModel.kt`,
  `packages/design-system/src/commonMain/composeResources/values/strings.xml`, and focused tests
  in the touched modules — make the saved notice truthful and pin the new behavior.

## Diagnosis

`PlatformBindings.apiBaseUrl` is resolved once when `AppGraph.start` binds every HTTP client.
`KtorHttpClient.urlString` captures that string in its constructor, and
`LiveSettingsService.setServerAddress` only writes the preference. The pairing deep link was
designed to prefill a field the user confirms, not to auto-trust the scanned URL; that rule
stays. What was missing is a path from the confirmed Save back into already-bound clients.

## Contract

- [x] A client built before a valid address save sends the next request to the saved root
      (`KtorHttpClientTest.a confirmed address reaches a client built before the save`).
- [x] `setServerAddress` publishes a validated root before returning; an invalid address
      publishes nothing (`LiveSettingsServiceTest`).
- [x] `resetServerAddress` restores the build-time root for live clients
      (`LiveSettingsServiceTest`).
- [x] The pairing prefill still opens Settings and requires Save; it never auto-applies the
      scanned URL (`ShellScenarios.aConnectLinkOpensSettingsWithTheAddressPrefilled`).
- [x] The saved notice says the address is in use now, not on the next launch
      (`SettingsScenarios.aSavedChangeSaysTheAddressIsInUseNow`).
- [x] Each behavior test fails when its production change is reverted (mutation-checked for the
      publish/reset hook, the graph's live provider, and the saved notice copy).

**KMP Constraint:** no platform-specific APIs in `commonMain`; no new `expect`/`actual` seam.

## Non-goal

- Auto-accepting a scanned pairing URL without the user pressing Save; the custom scheme is not
  a trust decision.
- Changing the Android build-time root in `local.properties`; that value remains a fallback.
- Attaching a new server to an already signed-in session without re-authentication.

## Validation

- **Iteration:** `scripts/check.sh quick <module> '<ClassOrPattern>'` for each touched module.
- **Before handoff:** `scripts/check.sh full` — this changes a shared HTTP seam and notice copy.
- **Counts / Baselines:** regenerated from a green full run only if test counts change.
- **Stays pending:** a physical Android device scan against the running LAN server; the JVM
  scenarios prove the state transition, not the phone's Wi-Fi path.

## Handoff

- **Done:** `KtorHttpClient` reads its root through a provider per request (fixed-root overload
  kept for callers with no live state); `AppGraph` holds one app-scoped `LiveApiBaseUrl`, every
  HTTP client factory and `LiveSettingsService` read it; Save publishes the validated root and
  Reset restores the launch root; saved/reset notice and the sheet body say the address is  in use now. Focused tests green in network, settings-data, settings-presentation and
  composition-root; mutation checks fail without the hook, the provider and the new copy.
  `scripts/check.sh full` passed on JVM, iOS simulator and Android host with no failures
  (counts 2320 / 2255 / 1930, baseline unchanged).
- **Pending:** a physical Android scan against a running LAN server is still unverified — the
  JVM scenarios prove the state transition, not the phone's Wi-Fi path.
- **Next step:** install the branch build on the Android device, scan `tt expose`'s QR, press
  Save, and confirm the login reaches the LAN address without a relaunch; then promote the
  connect-link decision to `docs/DECISIONS.md` and delete this spec.
- **Gotchas:** the pairing link still only prefills; the custom scheme is not a trust decision.
  `AppGraphTest` cannot resolve `SettingsPort` on the Android host (no Robolectric runner in
  `commonTest`), so the row half is pinned in `LiveSettingsServiceTest`. The reset target is the
  root this process launched with, which is the stored override when one existed at launch —
  the next launch is what falls back to the build-time root.

## Promote

- **To `docs/DECISIONS.md`:** extend the connect-link rule with “Save publishes the confirmed
  address to already-bound clients; the scanned link still only prefills.”
