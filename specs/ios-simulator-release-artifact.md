# iOS simulator release artifact — make the `ios` job produce something usable

- **status:** in-progress
- **priority:** P1
- **context:** Found while checking v0.1.12, which shipped without a macOS `.dmg`
  (that was GitHub's macos arm64 pool never handing out a runner — a separate,
  infrastructure-only problem). The same run showed the `ios` job red, and
  `gh api .../runs/42067766419/jobs` shows the v0.1.8 job failed at the same step,
  so **no release has ever produced an iOS artifact**: the upload step has been
  `skipped` every time and `if-no-files-found: warn` kept it quiet.

## Scope

CI only. No app code, no Gradle changes, no signing.

- **Targets affected:** none
- **Modules affected:** none

## Files

- `.github/workflows/release.yml` — `ios` job: `Build iOS for arm64 simulator`,
  new `Package the simulator app`, `Upload iOS build artifact`.
- `README.md` — the download table's iOS row and the note under it.

## Contract

Three defects had to be fixed together; fixing any one alone leaves the job green
and empty:

- [ ] The build step uses `-destination 'generic/platform=iOS Simulator'`; no
      simulator device name appears anywhere in the workflow. A named device is
      unsafe because `xcodebuild` does not fail on a name that matches nothing —
      it falls back to "My Mac" and errors there. v0.1.12 died exactly so:
      `name=iPhone 16` against an image whose devices are iPhone 17/18 with iOS
      26.5+.
- [ ] The build step passes `ARCHS=arm64` as a build setting. `Release` sets
      `ONLY_ACTIVE_ARCH: NO` (`iosApp/project.yml`), so a generic simulator
      destination resolves to `ARCHS = arm64 x86_64`, and `:shared-ui` cannot
      supply the x86_64 slice — it links `iosSimulatorArm64` only, since Compose
      1.12.1 publishes no Intel simulator variant. It must be the `ARCHS` setting,
      not `-arch`: xcodebuild rejects the flag alongside a destination
      (`destination implies architecture, architecture must not also be specified`).
- [ ] The build step passes `-derivedDataPath build`, so the product lands at
      `iosApp/build/Build/Products/Release-iphonesimulator/TaskTracker.app`
      rather than in `~/Library/Developer/Xcode/DerivedData`, where the previous
      `iosApp/build/` upload path could never find it.
- [ ] The uploaded artifact is one zip named `TaskTracker-<tag>-ios-simulator.zip`
      whose payload is `TaskTracker.app` (so it can be dropped on a booted
      simulator without reassembling a bundle).
- [ ] `if-no-files-found: error`, not `warn`: a green job that ships nothing is
      the bug being fixed. The job keeps `continue-on-error: true`, so a failed
      build still does not block the release.
- [ **KMP Constraint:** No `commonMain` or `expect`/`actual` change — this spec
  touches CI and docs only.]

## Non-goal

- [ ] No device build, no `.ipa`, no signing, and no iOS entry in
      `release-assets/`. Those are `ios-device-ipa.md`.

## Validation

- **Local check (the real one):** the exact command from the workflow, run from
  `iosApp/`, must end in `** BUILD SUCCEEDED **` and leave
  `build/Build/Products/Release-iphonesimulator/TaskTracker.app` in place.
  `scripts/check.sh` does not cover this path — it runs tests, not packaging.
- **CI check:** cannot be exercised by a dispatch (`release.yml` would publish a
  release for the requested version). It is verified on the next real tag, by
  confirming the job is green *and* that the run's `ios-build` artifact is
  non-empty.
- **Counts / Baselines:** unaffected — no test sources change.

## Handoff

- **Done:** `release.yml` `ios` job rewritten (generic destination, `ARCHS=arm64`,
  `-derivedDataPath build`, `ditto` zip, `if-no-files-found: error`); README iOS
  row corrected. Verified locally with the exact command from the workflow:
  `** BUILD SUCCEEDED **`, `iosApp/build/Build/Products/Release-iphonesimulator/TaskTracker.app`
  exists, `lipo -archs` reports `arm64` alone (so the x86_64 slice really was the
  blocker), and the `ditto` step yields a 23 MB
  `TaskTracker-v0.1.12-ios-simulator.zip` with `TaskTracker.app/` as its root.
  Bundle reads `com.tasktracker.app`, `MinimumOSVersion 26.0`.
- **Pending:** CI confirmation on the next tag push, and removing this spec once
  it is green (promote nothing to `DECISIONS.md` — this is CI plumbing, not a
  rule that a bug once violated).
- **Next step:** push a tag, then
  `gh run view <run> --json artifacts` confirming `ios-build` exists and unzips
  to `TaskTracker.app`.
- **Gotchas:** do not "fix" a destination failure by swapping the device name for
  a newer one — that is the same trap one image update later. Use the generic
  destination.

## Risks / notes

- `ditto` is macOS-only, which is fine while this job stays on `macos-15`, but a
  Linux port of this step needs `zip -ry`.
- The zip name embeds the tag, so a re-run of the same tag overwrites cleanly
  rather than accumulating two artifacts.
