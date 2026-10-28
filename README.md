# TaskTracker

[![LinkedIn](https://img.shields.io/badge/LinkedIn-Amirreza%20Ashouri-0A66C2?logo=linkedin&logoColor=white)](https://www.linkedin.com/in/amirreza-ashouri-229019156)
[![Gmail](https://img.shields.io/badge/Gmail-amirashouri72%40gmail.com-EA4335?logo=gmail&logoColor=white)](mailto:amirashouri72@gmail.com)

A cross-platform task and project tracker for **Android, iOS, and Desktop (macOS, Windows, Linux)**, built with Kotlin Multiplatform and Compose Multiplatform. One shared UI, one shared domain model, and strictly enforced Clean Architecture boundaries.

---

## Highlights

- **Work Management:** Boards with custom columns, backlog projections, sprint planning, and month-grouped archive views.
- **Rich Work Items:** Details, statuses, priorities, story points, assignee assignments, due dates, recurrence rules, and dependency links.
- **Offline-First Sync:** Local Room store with an outbox queue. Changes are committed locally first and reconciled with the server in the background.
- **Native Security:** Token storage using `AndroidKeyStore` on Android, `Security.framework` on iOS, and `Security.framework` via JNA on macOS.
- **Adaptive Liquid Glass Design:** Shared custom glassmorphism design system in `:packages:design-system` adapting across phone, tablet, and wide desktop screens.
- **Runtime API Root:** Switch backend servers dynamically via in-app Settings or pairing links (`tasktracker://connect?url=...`) without rebuilding the app.

---

## Tech Stack

| Layer | Technology |
| :--- | :--- |
| **UI** | Compose Multiplatform 1.12.1 (shared screen tree across Android, iOS, Desktop) |
| **Language** | Kotlin 2.3.20 (Android SDK 37 / min 26, iOS device + simulator arm64, Desktop JVM) |
| **Architecture** | Clean Architecture (Domain / Data / Presentation separated by Gradle modules) |
| **Database** | Room KMP (SQLite) + Outbox pattern for sync |
| **Preferences** | `:packages:user-defaults` (Platform file/properties store for appearance & window geometry) |
| **Key Storage** | `:packages:keychain` (Platform-backed secure credential vaults) |
| **Networking** | Ktor 3.6.0 (OkHttp on Android/JVM, Darwin on iOS) |
| **Serialization** | `kotlinx.serialization` (strict JSON configuration) |
| **DI** | Koin 4.2.2 behind `:packages:di`'s `Resolver` interface |
| **Navigation** | Hand-rolled `:packages:routing` (pure state, no Compose dependency) |

Architecture boundaries are strictly validated: `./gradlew verifyArchitectureGraph verifyModuleImports` fails the build on any unauthorized dependency edge.

---

## Project Structure

```
androidApp/                  Android application shell
iosApp/                      SwiftUI host shell (generated via XcodeGen)
shared-ui/                   Shared Compose screen tree, navigation host & Desktop entry point
composition-root/            Dependency injection wiring hub (binds ports to adapters)
packages/
  core/                      Shared identifiers, errors, loggers, JSON/Date utilities
  domain/                    Core business vocabulary and entity rules
  database/                  Room database schemas, DAOs, and outbox queues
  network/                   Ktor HTTP client, interceptors, and network monitoring
  sync/                      Offline outbox queue coordination and sync engine
  keychain/                  Platform-specific secure credential storage
  user-defaults/             Non-sensitive preferences and window geometry persistence
  routing/                   Immutable router state, routes, and deep-link parsing
  di/                        Dependency resolution interfaces
  design-system/             Liquid Glass visual tokens, components, and Compose resources
  feature-<name>-{domain,data,presentation}/
                             Feature triplets: auth, projects, settings, teams, notifications
```

---

## Getting Started

### Prerequisites
* **JDK 25:** Developed against Android Studio's JBR:
  ```bash
  export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
  ```
* **Android SDK 37:** With a device or emulator.
* **Xcode:** For compiling and running the iOS target.
* **XcodeGen:** (`brew install xcodegen`) to generate the iOS project file.

---

## Running the App

### Android
Open the project in Android Studio, select the **`androidApp`** run configuration, and run. Alternatively:
```bash
./gradlew :androidApp:installDebug
```

#### Physical device

The build reads the API root from `tasktracker.apiBaseUrl` in `local.properties`
(gitignored), so the Run/Debug button needs no command-line flag:

```properties
tasktracker.apiBaseUrl=http://192.168.1.42:8080
```

A physical phone differs from the emulator in two ways:

* **The server must be published on the LAN.** The server repository's developer compose
  publishes `8080` on `127.0.0.1` only (by design, `specs/t9.3-server-bind.md`), so the
  app reports `Failed to connect to /<lan-ip>:8080` even though the phone is on the same
  network. Use `tt expose --provider=lan`, or add a local (never committed)
  `docker-compose.override.yml` to the server checkout:

  ```yaml
  services:
    server-pg:
      ports: !override
        - "0.0.0.0:8080:8080"
  ```

* **No `ACCESS_LOCAL_NETWORK` prompt is expected below Android 17 (API 37).** The permission
  exists only on the platform that enforces local-network protection; on an older device
  `INTERNET` is enough and the app asks for nothing. A missing prompt there is not the
  failure.

### iOS
Generate the `.xcodeproj` file and open it in Xcode:
```bash
cd iosApp && xcodegen generate
open TaskTracker.xcodeproj
```
*Note: A build phase automatically compiles and links `ComposeApp.framework` from Gradle.*

### Desktop (JVM)
Run the desktop app directly:
```bash
./gradlew :shared-ui:runDesktop
```
To point the app to a custom backend URL at launch:
```bash
TASKTRACKER_API_URL=https://api.example.com ./gradlew :shared-ui:runDesktop
```

### Desktop Packaging
Generate native installers:
```bash
./gradlew :shared-ui:packageDmg    # macOS (.dmg)
./gradlew :shared-ui:packageMsi    # Windows (.msi)
./gradlew :shared-ui:packageDeb    # Linux (.deb)
```
*(Requires a full JDK with `jpackage` installed on the host OS).*

---

## Testing & Quality Assurance

* **Fast Inner Dev Loop (<1 min):** Focused JVM unit tests while writing code:
  ```bash
  ./gradlew --offline :packages:<module>:jvmTest --tests '<pattern>'
  ```
* **Full Matrix Gate (<10 min):** Release gate and multiplatform integrity check:
  ```bash
  scripts/check.sh full
  ```
  Executes JVM tests, iOS Simulator tests, Android Robolectric tests, and architecture graph validations. Failure totals and identities are strictly guarded by `test-baseline.json`.

---

## Documentation Index

* [`Project.md`](Project.md) — Operational manual, iOS linking invariants, and detailed request flows.
* [`AGENTS.md`](AGENTS.md) — Engineering rules, execution boundaries, and test mutation protocol.
* [`docs/DECISIONS.md`](docs/DECISIONS.md) — Design record and structural architectural invariants.
* [`docs/TESTING.md`](docs/TESTING.md) — Full test command and host reference.
* [`docs/PLAN.md`](docs/PLAN.md) — Active roadmap and upcoming features.

---

## License

[MIT + Commons Clause](LICENSE), plus a non-rebranding condition.

You are welcome to use and modify the client and backend for personal, commercial, or team use. You may not resell the code or repackage it under your own name and brand. See [`LICENSE`](LICENSE) for full details.