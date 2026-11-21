# creator-card — add a creator card to Settings

- **status:** in-progress
- **priority:** P3
- **branch:** `feat/creator-card`
- **blocked by:** none
- **context:** The user asked for a card at the bottom of Settings that introduces the creator and
  offers the email and LinkedIn contacts from `README.md`: Amirreza Ashouri,
  `mailto:amirashouri72@gmail.com`, `https://www.linkedin.com/in/amirreza-ashouri-229019156`.
  Single-repo UI work; no server spec.

## Scope

- **Targets affected:** commonMain, commonTest (existing JVM/iOS/Android hosts inherit scenarios)
- **Modules affected:** `:packages:design-system`, `:packages:feature-settings-presentation`

## Files

- `packages/design-system/src/commonMain/kotlin/com/tasktracker/designsystem/SettingsIcons.kt` — add a `linkedin` glyph to `ttSettingsIcons` beside the existing `email` glyph.
- `packages/design-system/src/commonMain/composeResources/values/strings.xml` — add `settings_creator_*` strings for the title, intro, name, contact labels and content descriptions.
- `packages/feature-settings-presentation/src/commonMain/kotlin/com/tasktracker/featuresettingspresentation/CreatorCard.kt` — new file, one public composable; both contact rows go through `LocalUriHandler` (available in commonMain on Android/iOS/Desktop in Compose 1.12.1).
- `packages/feature-settings-presentation/src/commonMain/kotlin/com/tasktracker/featuresettingspresentation/SettingsScreen.kt` — append the card as the last item of the wide right pane and the single-column list.
- `packages/feature-settings-presentation/src/commonTest/kotlin/com/tasktracker/featuresettingspresentation/SettingsScenarios.kt` — scenario with a recording `UriHandler` that asserts both exact URIs and the card's presence at the end of the list.
- `packages/feature-settings-presentation/src/commonTest/kotlin/com/tasktracker/featuresettingspresentation/SettingsViewScenarios.kt` — one inherited `@Test` entry point for that scenario.
- `test-baseline.json` — regenerated from a green full run by `scripts/test-baseline.py`, never hand-edited.

## Contract

- [ ] The card renders the creator's name and intro at the bottom of Settings, below Version when a version is present (`SettingsScenarios.theCreatorCardOpensBothContacts`).
- [ ] Tapping the email row opens exactly `mailto:amirashouri72@gmail.com`; tapping the LinkedIn row opens exactly `https://www.linkedin.com/in/amirreza-ashouri-229019156` (`SettingsScenarios.theCreatorCardOpensBothContacts`).
- [ ] Both contacts are exposed as labelled, clickable rows in semantics, not decorative text (`SettingsScenarios.theCreatorCardOpensBothContacts`).
- [ ] No platform-specific API is used in `commonMain`: URI dispatch goes through `LocalUriHandler`, which Compose provides on all three targets.
- [ ] Both list branches (wide two-pane and narrow single-column) place the card last.

**KMP Constraint:** no `expect`/`actual` added; Compose's own platform `UriHandler` actuals cover Android, iOS and Desktop.

## Non-goal

- No server, DTO, wire-format or sync changes — the contacts are static app copy from the README.
- No new dependency, preference, model, or routing entry.
- No redesign of the existing Settings rows; drive-by cleanup stays out of scope.

## Validation

- **Iteration:** `scripts/check.sh quick :packages:feature-settings-presentation 'SettingsViewJvmTest'` after each edit round.
- **Before handoff:** `scripts/check.sh full` — the change adds UI copy and a glyph, so the full matrix (JVM, iOS simulator, Android host, architecture graph) is required.
- **Counts / Baselines:** one new inherited scenario runs on all three hosts; regenerate `test-baseline.json` from the green full run with `python3 scripts/test-baseline.py`.
- **Stays pending:** no physical-device or release-installer check; the fake `UriHandler` proves dispatch, not that the OS has a mail/browser handler installed.

## Handoff

- **Done:** creator card with Email and LinkedIn rows appended to both Settings list branches;
  LinkedIn glyph and strings in `:packages:design-system`; recording-`UriHandler` scenario added
  and inherited by all three hosts. Focused JVM loop green; new scenario passed on JVM, iOS and
  Android. Mutation check: removing `item { CreatorCard() }` made the scenario fail with "No node
  found that matches TestTag = 'settings.creator.card'"; restoring it went green. Architecture
  graph and module imports passed. Durable decision promoted to `docs/DECISIONS.md`.
- **Pending:** full gate is red on a pre-existing failure —
  `ProjectsUiAndroidHostTest#reducedMotionShowsTheBarWithoutTravel` (`assertExists` on
  `board.selection.bar`, line 236 of `BoardSelectionScenarios.kt`). Reproduced on a clean `main`
  worktree with the same 1/516 Android failures; the failure is in an untouched module, so no fix
  was attempted here. `test-baseline.json` is deliberately unchanged: regeneration requires a
  green run (DECISIONS §UI, rendering and tests), and `main` is already +1 test over the committed
  baseline, with this branch adding one inherited scenario (+1 more).
- **Next step:** file a separate spec/ACK for the reduced-motion selection-bar failure on `main`;
  after it is fixed, regenerate `test-baseline.json` from a green `scripts/check.sh full` and
  delete this spec in the final task-branch commit.
- **Gotchas:** `strings.xml` lives in `:packages:design-system` only; generated accessors need
  their own `import com.tasktracker.resources.<name>`. `LocalUriHandler` needs no `expect`/`actual`
  in Compose 1.12.1. Do not add the pre-existing failure to `test-known-failures.json` or
  regenerate the baseline from a red run.

## Promote

- **To `docs/DECISIONS.md`:** note that external contacts in Settings use Compose's `LocalUriHandler` (no `expect`/`actual` seam) and static creator copy lives in the design-system string layer.
