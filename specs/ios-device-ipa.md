# iOS device IPA — ship an unsigned `.ipa` plus re-sign instructions

- **status:** pending
- **priority:** P2
- **context:** Raised by the v0.1.12 artifact investigation. An installable iOS
  build today would need either a paid Apple Developer Program membership
  ($99/yr, plus being the App Store *seller* for an app whose sync target is a
  server the user hosts) or a per-user re-sign. Decision taken: **no paid
  account and no App Store listing.** The deliverable is the middle path — an
  unsigned device build users re-sign with their own Apple ID — because "build
  from source" requires a Mac, Xcode, a JDK, and a Kotlin/Native link, which is
  not a serious option for the audience of a task tracker.

## Scope

The iOS shell's build wiring and the release packaging of a device artifact.
No app code, no domain/data/presentation changes, no signing credentials.

- **Targets affected:** iosMain (build wiring only; no Kotlin source changes expected)
- **Modules affected:** `iosApp` (Xcode project manifest), `:shared-ui` (verify only)

## Files

- `iosApp/project.yml` — the `TaskTracker` target's framework wiring: the
  pre-build script phase (hardcodes `linkDebugFrameworkIosSimulatorArm64`),
  `FRAMEWORK_SEARCH_PATHS` (hardcodes `iosSimulatorArm64/debugFramework`), and
  the `dependencies:` framework path (one static reference).
- `.github/workflows/release.yml` — a packaging step producing the `.ipa`
  (after `ios-simulator-release-artifact.md` lands, so the simulator job is
  known-good first).
- `README.md` — the iOS install instructions and the honest caveat.
- `shared-ui/build.gradle.kts` — **verify only.** `iosArm64()` is already
  declared (alongside `iosSimulatorArm64()`), so `linkDebugFrameworkIosArm64`
  should already exist; if it does not, stop and re-plan, because then this is a
  Gradle change and not the small task it is scoped as.

## Contract

- [ ] `xcodebuild -destination 'generic/platform=iOS' CODE_SIGNING_ALLOWED=NO
      CODE_SIGNING_REQUIRED=NO` produces `TaskTracker.app` for `arm64`/`iphoneos`
      with no Apple account present.
- [ ] The link uses the `iosArm64` framework variant, not the simulator one; the
      build is not simulator-specific (the pre-build script selects the Gradle
      link task and the search path from `PLATFORM_NAME`, not from a constant).
- [ ] The release asset is `TaskTracker-<tag>-ios-unsigned.ipa` containing
      exactly `Payload/TaskTracker.app`; `unzip -l` shows that structure.
- [ ] CI holds **no** Apple credentials, team ID, or provisioning profile —
      grep the workflow for `DEVELOPMENT_TEAM`, `CODE_SIGN_IDENTITY`,
      `APPLE_ID` and expect no matches.
- [ ] The README states, next to the download: unsigned, will not install as-is,
      re-sign with a free Apple ID, certificate expires after 7 days.
- [ ] **KMP Constraint:** no `expect`/`actual` changes; this is packaging, not a
      platform seam.

## Non-goal

- [ ] No App Store submission, no TestFlight, no ad-hoc distribution to other
      people's devices, no notarization, and no paid developer account. Adding
      any of those is a new spec AND a product decision, not an extension of
      this one.

## Validation

- **Local:** the device build command above, run from `iosApp/`, ends in
  `** BUILD SUCCEEDED **`; `codesign -dv TaskTracker.app` reports
  `code object is not signed at all` (expected — that is the deliverable);
  `xcrun simctl install` is NOT the check, since this artifact targets devices.
- **Structure check:** `unzip -l` on the packaged `.ipa` shows
  `Payload/TaskTracker.app/TaskTracker` and `Info.plist`.
- **CI check:** on the next tag, the run's `ios-unsigned-ipa` artifact exists and
  its size is non-trivial; a green job with a missing artifact is the failure
  mode this project has already been bitten by.
- **Counts / Baselines:** unaffected — no test sources change.

## Handoff

- **Done:** nothing yet; scoping only.
- **Pending:** every line of the Contract.
- **Next step:** confirm `linkDebugFrameworkIosArm64` exists
  (`./gradlew :shared-ui:tasks --all | grep -i iosArm64` via the project's own
  wrapper path), then design the `PLATFORM_NAME` conditional in
  `iosApp/project.yml`.
- **Gotchas:** the framework `dependencies:` entry is a single static path and
  XcodeGen has no per-SDK dependency selector — the two-variant problem is the
  real design work here, so resolve it before touching CI. Do not attempt to
  automate the re-sign in CI: it needs the user's Apple ID and device UDID.

## Risks / notes

- `deploymentTarget: iOS 26.0` (`iosApp/project.yml`) makes the app installable
  only on the newest OS. Decide whether to lower it before shipping a device
  artifact; it changes the audience the artifact is for.
- A free Apple ID yields a 7-day certificate, needs a re-sign weekly, and is
  limited to a handful of apps per device. State this plainly in the README
  rather than letting users discover it.
- When this spec completes, promote the standing limit to
  `docs/DECISIONS.md` §5 (known intentional product gaps): no paid Apple
  Developer Program, therefore no IPA that installs unaided, no App Store, no
  TestFlight.
