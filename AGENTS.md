# AGENTS.md

## Cursor Cloud specific instructions

### Project overview
**gzmy** is a Kotlin Android couple-app with Firebase backend. Two modules:
- `app/` — Android app (Kotlin, Jetpack Compose + Views, Room, Firebase)
- `functions/` — Firebase Cloud Functions (Node.js/JavaScript) for push notifications

### Prerequisites (already installed in the VM snapshot)
- **JDK 17** at `/usr/lib/jvm/java-17-openjdk-amd64`
- **Android SDK** at `/opt/android-sdk` (API 34, build-tools 34.0.0, platform-tools)
- **Node.js 20** via nvm (`nvm use 20`)

### Environment variables
Set via `~/.bashrc`:
- `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64`
- `ANDROID_HOME=/opt/android-sdk`
- `ANDROID_SDK_ROOT=/opt/android-sdk`

If Gradle complains about SDK location, ensure `local.properties` contains `sdk.dir=/opt/android-sdk`.

### google-services.json
`app/google-services.json` contains the real Firebase config for project `gzmy-9fd2e`. It is gitignored and injected from the `GOOGLE_SERVICES_JSON` secret in CI. If missing, the update script creates a placeholder that allows compilation but Firebase features won't work at runtime without the real config.

### Common commands
| Task | Command |
|---|---|
| Build debug APK | `./gradlew assembleDebug` |
| Build release APK | `./gradlew assembleRelease` |
| Android lint | `./gradlew lintDebug` |
| Unit tests | `./gradlew testDebugUnitTest` |
| Cloud Functions deps | `cd functions && npm install` |

### Gotchas
- The project requires **JDK 17** (not 21). The default system JDK may be 21; always set `JAVA_HOME` explicitly.
- `functions/` has `eslint` and `eslint-config-google` as devDependencies but **no `.eslintrc` config file** in the repo. ESLint will error without one.
- There is no lockfile (`package-lock.json`) in `functions/`. Dependency versions may drift.
- The Gradle wrapper (`gradlew`) needs execute permission (`chmod +x gradlew`).
- `nvm use 20` must be run before working with Cloud Functions (the default nvm node version is 22).
- This is a native Android app — no emulator is available in the cloud VM. Testing is limited to building APKs, running lint, and unit tests. Use `aapt dump badging` on the APK to inspect package metadata.
- Cloud Functions can be validated by requiring the module in Node.js: `node -e "console.log(Object.keys(require('./index.js')))"` from `functions/`.
- Kotlin compiler warnings about deprecated `LocalBroadcastManager` are expected and benign.
