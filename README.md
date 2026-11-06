# TaskTracker

[![LinkedIn](https://img.shields.io/badge/LinkedIn-Amirreza%20Ashouri-0A66C2?logo=linkedin&logoColor=white)](https://www.linkedin.com/in/amirreza-ashouri-229019156)
[![Gmail](https://img.shields.io/badge/Gmail-amirashouri72%40gmail.com-EA4335?logo=gmail&logoColor=white)](mailto:amirashouri72@gmail.com)
![Platforms](https://img.shields.io/badge/platforms-Android%20%7C%20iOS%20%7C%20Desktop-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Multiplatform-7F52FF?logo=kotlin&logoColor=white)

> **Your tasks. Your server. Your rules.**
> TaskTracker is an open, self-hosted task and project manager for **Android, iOS, and Desktop** — one shared UI and one shared domain model, built with Kotlin Multiplatform and Compose Multiplatform. Plan boards, run sprints, assign work, and sync across devices without handing your data to anyone. It talks to your own [TaskTracker server](https://github.com/amirashouri/TaskTracker), and it keeps working when the network does not.

---

## ✨ Highlights

- **Work management** — boards with custom columns, backlog projections, sprint planning, and month-grouped archives.
- **Rich work items** — details, statuses, priorities, story points, assignees, due dates, recurrence, and dependencies.
- **Offline-first** — a local Room store with an outbox queue. Changes commit locally first and reconcile with the server in the background.
- **Native security** — tokens live in `AndroidKeyStore`, iOS `Security.framework`, and macOS `Security.framework` via JNA.
- **Liquid Glass design** — a shared glassmorphism design system that adapts across phone, tablet, and wide desktop screens.
- **Switchable backend** — point the app at any server in Settings or via a pairing link (`tasktracker://connect?url=...`) without rebuilding.

---

## 📥 Download the app

Prebuilt installers are published on every release tag. Grab the latest from
[**GitHub Releases**](https://github.com/amirashouri/TaskTrackerKMP/releases/latest).

| Platform | Download from the latest release |
| :--- | :--- |
| **Android** (mobile) | `tasktracker-android-<version>.apk` |
| **macOS** | `TaskTracker-<version>.dmg` |
| **Windows** | `TaskTracker-<version>.msi` |
| **Linux** | `TaskTracker-<version>.deb` |
| **iOS** | Build from source (Quick Start below) — no release asset yet |

> The Android APK is a **debug** build; enable "Install from unknown sources" if prompted. On macOS the app is not notarized, so the first launch needs right-click → Open.

Prefer to build it yourself? Jump to [Quick Start](#-quick-start) below.

---

## 🚀 Quick Start

### 1. Install the toolchain

| Requirement | Needed for | Install |
| :--- | :--- | :--- |
| **JDK 25** | All builds | Use Android Studio's bundled JBR (see below) |
| **Android SDK 37** | Android | Android Studio → SDK Manager |
| **Xcode** + **XcodeGen** | iOS | `xcodebuild -version` and `brew install xcodegen` |

Every Gradle command needs JDK 25 on the path:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

### 2. Clone and run

```bash
git clone https://github.com/amirashouri/TaskTrackerKMP.git
cd TaskTrackerKMP
```

Pick a target:

```bash
# Desktop (JVM) — fastest way to see the app
./gradlew :shared-ui:runDesktop

# Android — installs on a connected device or emulator
./gradlew :androidApp:installDebug
```

```bash
# iOS — generate the Xcode project, then build and run from Xcode
cd iosApp && xcodegen generate && open TaskTracker.xcodeproj
```

> On iOS, Xcode links a Gradle-built framework and a `preBuildScripts` phase compiles it for you. Don't disable that phase — a stale link builds and runs but shows old behavior.

### 3. Start a backend

The app is offline-first but needs a server to sync. Run one locally with the [server repo](https://github.com/amirashouri/TaskTracker) (one command) and the app defaults to it:

| Target | Default server |
| :--- | :--- |
| Desktop / iOS | `http://localhost:8080` |
| Android emulator | `http://10.0.2.2:8080` |

To use a different server, set it at launch or in the app:

```bash
# Desktop — env var at launch
TASKTRACKER_API_URL=https://api.example.com ./gradlew :shared-ui:runDesktop
```

Or scan the pairing link/QR printed by `tt expose` on your server (`tasktracker://connect?url=...`).

A physical Android device differs from the emulator: point it at your machine's LAN address via the gitignored `local.properties`, and make sure the server is published on the LAN (`tt expose --provider=lan`):

```properties
# local.properties
tasktracker.apiBaseUrl=http://192.168.1.42:8080
```

---

## 📦 Package desktop installers

```bash
./gradlew :shared-ui:packageDmg    # macOS (.dmg)
./gradlew :shared-ui:packageMsi    # Windows (.msi)
./gradlew :shared-ui:packageDeb    # Linux (.deb)
```

Each target needs a full JDK with `jpackage` on the matching host OS.

---

## 🧪 Tests

```bash
# Focused JVM unit tests while developing (<1 min)
./gradlew --offline :packages:<module>:jvmTest --tests '<pattern>'

# Full matrix gate before a release (<10 min)
scripts/check.sh full
```

The full gate runs JVM, iOS Simulator, and Android Robolectric tests plus architecture-graph validation.

---

## 🏗 Architecture at a glance

Clean Architecture, split across Gradle modules and strictly enforced — `./gradlew verifyArchitectureGraph verifyModuleImports` fails the build on any illegal dependency edge.

```
androidApp/         Android shell          iosApp/       SwiftUI host
shared-ui/          Shared Compose UI      composition-root/  DI wiring
packages/           core · domain · database · network · sync · keychain
                    user-defaults · routing · di · design-system · feature-*
```

---

## 📚 Documentation

- [`Project.md`](Project.md) — build, test, and platform internals (backend URLs, iOS linking, request flows).
- [`docs/DECISIONS.md`](docs/DECISIONS.md) — design record and durable architectural invariants.
- [`AGENTS.md`](AGENTS.md) — engineering rules and boundaries.
- [`specs/`](specs/README.md) — the task index; one spec per unit of work in progress.
- [Install guide](https://github.com/amirashouri/TaskTracker/blob/main/docs/INSTALL.md) — host the backend and get the app on a device.

---

## 📄 License

[MIT + Commons Clause](LICENSE), plus a non-rebranding condition.

Use and modify the client and backend for personal, commercial, or team work. You may not resell the code or repackage it under your own name and brand. See [`LICENSE`](LICENSE) for full details.

---

*Made by [Amirreza Ashouri](https://www.linkedin.com/in/amirreza-ashouri-229019156) — stars and issues welcome.*