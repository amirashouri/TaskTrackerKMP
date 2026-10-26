# Project.md

Operational manual for building, running, testing, and debugging TaskTrackerKMP. Design rationale and invariants live in [`docs/DECISIONS.md`](docs/DECISIONS.md); testing policy lives in [`docs/TESTING.md`](docs/TESTING.md).

---

## 1. Environment & Requirements

**JDK 25 is required.** Every Gradle invocation must point to it:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

The scripts under `scripts/` export this automatically.

---

## 2. Verification Loops

* **Fast Inner Loop (<1 min):** Focused JVM unit tests while writing code:
  ```bash
  ./gradlew --offline :packages:<module>:jvmTest --tests '<TestClass or pattern>'
  ```
* **Full Matrix Gate (<10 min):** Release gate and multiplatform integrity check:
  ```bash
  scripts/check.sh full
  ```
  Runs JVM, iOS simulator, Android Robolectric, architecture graph checks, and baseline diffs via `--continue`. Output totals and failure identities are verified against `test-baseline.json` and `test-known-failures.json`.

---

## 3. Platform Execution

### iOS (Xcode + Gradle)
Gradle compiles Kotlin into a framework; Xcode links it **by path**:
```
shared-ui/build/bin/iosSimulatorArm64/debugFramework/ComposeApp.framework
```

* **Stale Binary Invariant:** Xcode does not trigger Gradle automatically. `iosApp/project.yml` enforces a `preBuildScripts` phase ("Build ComposeApp") that runs:
  ```sh
  ./gradlew :shared-ui:linkDebugFrameworkIosSimulatorArm64 \
            :shared-ui:syncComposeResourcesForIos --console=plain
  ```
  Never disable this phase. If Xcode links a stale binary, it compiles and runs without error but exhibits outdated behavior.
* **Verify Freshness:**
  ```bash
  ./gradlew :shared-ui:verifyIosSimulatorArm64FrameworkIsFresh
  ```
* **Xcode Project Generation:** If `iosApp/project.yml` is modified, regenerate with `xcodegen generate` inside `iosApp/` (the `.xcodeproj` is never edited directly).

### Android
Open `androidApp` in Android Studio or run:
```bash
./gradlew :androidApp:installDebug
```

### Desktop (Compose JVM Shell)
Launch the JVM desktop application:
```bash
./gradlew :shared-ui:runDesktop
```

---

## 4. Backend URLs & Network Configuration

The resolver `platformApiBaseUrl` in `:composition-root` resolves the server root per platform:

| Shell | Configuration Surface | Override Example |
| :--- | :--- | :--- |
| **Desktop (JVM)** | Environment variable | `TASKTRACKER_API_URL=https://... ./gradlew :shared-ui:runDesktop` |
| **iOS** | `Info.plist` key `TaskTrackerApiBaseUrl` | Build setting `TASKTRACKER_API_BASE_URL` in `iosApp/project.yml` |
| **Android** | Manifest `<meta-data android:name="com.tasktracker.API_BASE_URL">` | Gradle property `-Ptasktracker.apiBaseUrl=https://...` |

* **Offline Fallback:** When unset, defaults to `http://localhost:8080` (or `http://10.0.2.2:8080` on Android emulator).
* **Cleartext Guard:** Android blocks cleartext HTTP by default. `androidApp/src/main/res/xml/network_security_config.xml` permits HTTP only for `localhost` and `10.0.2.2`.
* **Android 17 Local Network Permission:** Access to LAN/mDNS addresses requires runtime `ACCESS_LOCAL_NETWORK`. The app requests this automatically when the API root is local (`apiRootRequiresLocalNetworkAccess`). Without it, requests silently time out.

---

## 5. Testing & Source-Set Structure

### Test Hosts

| Host | Task | Purpose |
| :--- | :--- | :--- |
| **JVM** | `jvmTest` | Fast unit tests, domain logic, and Desktop layout scenarios. |
| **iOS Simulator** | `iosSimulatorArm64Test` | Native rasterization, `expect`/`actual` verification, iOS behavior. |
| **Android Host** | `testAndroidHostTest` | Robolectric sandbox for Android `Context` and phone layout assertions. |

*Desktop is not a separate test host.* Desktop source code lives in `shared-ui/src/jvmMain`, and its tests execute inside `shared-ui/src/jvmTest`.

### Where Tests Belong

1. **Pure Logic / Ports:** Plain `@Test` in `commonTest`.
2. **Compose UI Behavior:** Scenario object in `commonTest` (`suspend ComposeUiTest.() -> Unit`). Thin entry-point classes in each host:
    * JVM: `jvmTest`
    * iOS: `iosTest`
    * Android: `androidHostTest` with:
      ```kotlin
      @RunWith(RobolectricTestRunner::class)
      @Config(sdk = [34])
      @GraphicsMode(GraphicsMode.Mode.NATIVE)
      ```
3. **Pixel & Contrast Tests (`:packages:design-system`):** Plain `@Test` in `commonTest` gated with `rasterising`:
   ```kotlin
   @Test
   fun myVisualAssertion() = rasterising {
       runComposeUiTest { /* read pixels */ }
   }
   ```
   Pixel tests skip on Android (where text rasterization is unsupported) and execute on JVM and iOS.
4. **Platform Seams / Reflection:** `jvmTest` or `androidHostTest`.

### Critical Test Invariants
* **Test Names:** Backtick sentences with **no commas or periods** (Kotlin/Native compiler rejects punctuation in method names).
* **Frame Differencing:** Use Skia `readPixels` with explicit `RGBA_8888` / `UNPREMUL`. Never use `ImageBitmap.toPixelMap()`.
* **Click Actions:** Always dispatch `performSemanticsAction(SemanticsActions.OnClick)`. Avoid `performClick()` (leaves virtual pointer pressed and darkens segments by ~0.14 luminance).
* **Dual-Host Calibration:** Measure text contrast on both JVM and iOS. Small glyph strokes render at ~85% coverage on iOS; set ink values from the weaker host.

---

## 6. Architecture & Dependency Rules

Gradle modules define the architectural boundary. Enforced via `./gradlew verifyArchitectureGraph verifyModuleImports`.

### Dependency Hierarchy

```
layer 0   :packages:core                          (zero project dependencies)
          :packages:domain                        ← core

layer 1   :packages:design-system                 ← core
          :packages:keychain                      ← core
          :packages:user-defaults                 ← core
          :packages:di                            ← core
          :packages:routing                       ← core, domain
          :packages:database                      ← core, domain
          :packages:network                       ← core
          :packages:sync                          ← core, database, network

layer 2   :packages:feature-projects-domain       ← core, domain
          :packages:feature-auth-domain           ← core, domain
          :packages:feature-settings-domain       ← core, domain
          :packages:feature-projects-data         ← core, domain, database, network, sync,
                                                    keychain, feature-projects-domain
          :packages:feature-auth-data             ← core, domain, database, network, sync,
                                                    keychain, user-defaults, feature-auth-domain
          :packages:feature-settings-data         ← core, domain, database, network, sync,
                                                    feature-settings-domain

layer 3   :packages:feature-projects-presentation ← core, domain, design-system, routing, di,
                                                    feature-projects-domain
          :packages:feature-auth-presentation     ← core, design-system, feature-auth-domain
          :packages:feature-settings-presentation ← core, domain, design-system, routing, di,
                                                    feature-settings-domain

layer 4   :composition-root                       ← every :packages module
          :shared-ui                              ← layer 3, feature domains, core, domain,
                                                    design-system, routing, di, user-defaults,
                                                    composition-root
          :androidApp                             ← composition-root, shared-ui, di
```

### Deliberately Forbidden Edges
* **`presentation` → `data`:** Screens talk to domain ports, never to database or repositories directly.
* **`:shared-ui` → any `data` module:** Shell resolves ports; it cannot instantiate data adapters.
* **`:composition-root` → `:shared-ui` / `:androidApp`:** Composition root binds libraries; the shell sits above it.
* **`:packages:network` → `:packages:domain`:** Transport stays domain-agnostic.
* **`:packages:sync` → `:packages:domain`:** Sync engine syncs opaque payloads; it never learns what a `WorkItem` is.

---

## 7. Core Request Flows

### Signing In
```
[1] :shared-ui (RootScreen)
    └── Resolves AuthSessionPort and provides via LocalAuthSession.
[2] :packages:feature-auth-presentation (SignInModel.submit())
    └── Invokes SignIn use case (validates/normalizes input).
[3] :packages:feature-auth-domain (SignIn)
    └── Calls AuthSessionPort.signIn().
[4] :packages:feature-auth-data (AuthRepository)
    └── Calls HttpAuthRemote -> stores tokens in KeychainStore -> caches profile -> emits SessionState.SignedIn.
[5] :composition-root (SessionCoordinator)
    └── Observes SignedIn -> writes identity to Room SessionCache -> calls router.enterMain().
[6] :shared-ui (RootScreen)
    └── Observes RootDestination.Main -> transitions from Auth to Main UI.
```
*Token Retention Invariant:* Tokens must be saved in keychain *before* announcing the session on the bus.

### Moving a Work Item (Offline-First Outbox)
```
[1] :packages:feature-projects-presentation (ProjectBoardScreen)
    └── Dispatches onMove to ProjectBoardModel.
[2] :packages:feature-projects-domain (MoveWorkItem)
    └── Validates state transition via ProjectRules / WorkItemRules.
[3] :packages:feature-projects-data (RoomProjectRepository)
    └── Executes ONE inTransaction(database): updates WorkItem record AND enqueues Outbox row.
[4] :packages:feature-projects-data (changeBus)
    └── Emits local change to update active UI models.
[5] :packages:sync (SyncCoordinator)
    └── Triggers SyncEngine.push().
[6] :packages:sync (SyncEngine)
    └── Claims oldest pending outbox rows scoped to signed-in user -> posts via SyncTransport.
[7] :packages:feature-projects-data (LocalDataSyncSink)
    └── On remote pull, writes records to DB directly (NEVER enqueues outbox rows).
```

### Creating a Project
```
[1] :packages:feature-projects-presentation (ProjectEditorModel.save())
    └── Invokes CreateProject use case.
[2] :packages:feature-projects-data (RoomProjectRepository.createProject())
    └── Folds creator into memberIDs -> writes Project record AND Outbox row in one transaction.
[3] :packages:sync (SyncEngine.push())
    └── Pushes outbox row to server.
```
*Creator Membership Invariant:* The creator must be injected into `memberIDs` before writing/enqueuing. The server validates project creation against the payload's member list; omitting the creator causes immediate server-side rejection (`403 Rejected`), permanently dead-lettering the creation.

### Ending a Session (Sign Out / Token Invalidation)
```
[1] :packages:feature-settings-presentation (SettingsScreen)
    └── Dispatches onSignOut -> calls SettingsPort.endSession().
[2] :composition-root (SessionCoordinator.signOut())
    └── Sets pendingReason = UserLogout -> calls session.signOut().
[3] :packages:feature-auth-data (AuthRepository)
    └── Clears KeychainVault, drops cached profile, emits SessionState.SignedOut.
[4] :composition-root (SessionTeardown.run())
    └── Clears SessionCache -> wipes Sync metadata -> stops SyncCoordinator (RETAINS Outbox rows).
[5] :composition-root (SessionCoordinator)
    └── Calls router.enterAuth() -> wipes per-tab navigation stacks.
[6] :shared-ui (RootScreen)
    └── Observes RootDestination.Auth -> renders SignInView.
```
*Teardown Invariants:*
* **Outbox rows are preserved** during teardown so unsynced user work is not deleted on logout.
* **Pull metadata is wiped** so incoming cursors are reset for subsequent accounts.
* `SessionCoordinator` is the sole modifier of `Router.root`.