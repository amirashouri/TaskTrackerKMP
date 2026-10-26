# AGENTS.md

Operating contract for TaskTrackerKMP. Facts live in `Project.md` and active backlog is in `docs/PLAN.md`.

## 1. Execution Limits & ACK Protocol
* **Search Cap:** Max 3 consecutive exploratory searches to find a file/symbol. If not found, halt and ask the user.
* **No Style Browsing:** Read only files named in the task and their direct package neighbors.
* **ACK Gate Required if:**
  * Touching 2+ Gradle modules.
  * Changing public signatures, domain ports, or wire DTOs.
  * Touching `tasktracker.architecture.gradle.kts`.
  * Deleting code or removing dependencies.
  * Touching cross-repo boundaries (`../AGENTS.md` applies).
* **Protocol:**
  * **Phase 1:** Max 3-bullet plan with exact file paths and target symbols. Wait for `ACK`.
  * **Phase 2:** Minimal diff edits only after ACK.
  * **Drift Rule:** If an unapproved file must be edited, halt immediately and request secondary ACK.
* **Fast Path (No ACK):** Docs-only, comments/typos, or single-file non-public signature edit. State action in one sentence, then apply.

## 2. KMP & Clean Architecture Boundaries
* **Architecture:** `domain` (rules/ports) ← `data` (implements ports) ← `:composition-root` (binds) ← `presentation` (UI/state) ← `:shared-ui` (assembles navigation).
* Never bypass `:composition-root` or import `data` into `presentation`. Graph verified via `./gradlew verifyArchitectureGraph verifyModuleImports`.
* **Atomicity Rule:** Provide platform `actual`s (including `jvm`) in the **exact same commit** as the `expect`. No partial seams.
* **Tokens & Resources:** All visual tokens, icons, and `compose.resources` belong exclusively in `:packages:design-system`. Use primitives (e.g. `Modifier.liquidGlassCard()`); do not invent styling.
* **Presentation File Rule:** Exactly 1 public `@Composable` per file named after it. Split if >400 lines or >1 public composable.

## 3. Test Loops & Matrix Gates
* **Inner Dev Loop (<1 min):** Focused unit runs while writing code:
  ```bash
  export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
  ./gradlew --offline --no-parallel :packages:<module>:jvmTest --tests '<TestClass or pattern>'
  ```
* **Full Matrix Gate (<10 min):** Run `scripts/check.sh full` for expect/actual changes, rendering/pixel changes, build graph updates, and prior to PR merges.
* Never fix red tests by deleting assertions, modifying `test-known-failures.json`, or hand-editing `test-baseline.json`.

## 4. Test Verification & Mutation Ritual
* **Structural Assertions (Default):** Modifier presence/order, clipping, tokens, tree visibility.
* **Rendered Pixels (Mandatory):** Alpha/fills, ink contrast, blur/hairline, or any property where rasterizers might disagree (must measure on both JVM and iOS).
* **Mutation Rule (Mandatory for pixel tests):**
  1. Run test -> Green.
  2. Revert production fix -> **Must fail naming the defect**.
  3. Restore fix -> Green.
     *(If step 2 stays green, the test is invalid; discard it).*

## 5. Diagnostic Reconciliation
Emit this exact block before hypothesizing on unexpected test failures:
```markdown
### DIAGNOSTIC RECONCILIATION
* **Failing Scenario/Assertion:** [Name]
* **Host Environment:** [JVM / iOS / Android]
* **Expected vs Actual Primitives:** [Token Values/Hex/Dimensions]
* **System State Summary:** [Cache/Outbox/Router dump]
```

## 6. Compose Multiplatform 1.12.1 Rules
1. `BasicText` lacks `textAlign` parameter — put alignment inside `style`.
2. `TextStyle` lacks `overflow` — set on `ParagraphStyle`.
3. `ImageVector.Builder.addPath` requires `fill: Brush`. Direct color or omitted fill renders nothing silently. Use `fill = SolidColor(Color.Black)` with tint.

## 7. Skills
* **`rendered-ui-measurement`** (`.agents/skills/rendered-ui-measurement/SKILL.md`): Load on-demand only when writing or debugging pixel-reading, contrast, or twin-frame differencing tests.

## 8. Git Workflow
* Branch off updated `main`: `feat/<kebab-case-slug>`.
* One branch = one PR = one task. No commits direct to `main`.
* Never perform drive-by dead code cleanup.