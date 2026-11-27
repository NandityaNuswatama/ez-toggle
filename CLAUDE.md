# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

Implements `docs/PRD.md` (lock-screen-safe connectivity controls). Layout:
- `Toggles.kt`: `ToggleType`/`ToggleState`/`ToggleAction`, `DeviceSettings` (system reads/writes, faked in tests), `SettingsStateReader`, `ToggleActionHandler`.
- `KeyguardGuard.kt`: every entry point (MainActivity tap, TrampolineActivity, widget `DndToggleCallback`) goes through it. Never change a setting while `isKeyguardLocked`.
- `ControlViewModel.kt`: MVI (`ControlIntent` in, `StateFlow<ControlUiState>` + `ControlEffect` channel out).
- `TrampolineActivity`: translucent hop widget → system panel → `refreshWidgets()` → finish. Deliberately not `noHistory` (it would finish before the result returns).
- `widget/SafeToggleWidget.kt`: Glance widget, receiver, DND callback, `refreshWidgets()` (bumps a prefs key so live sessions recompose).
- `ui/`: `ControlScreen`, `ChecklistScreen` (ticks in DataStore `checklist`, keys `item_<index>`: append only).

Normal apps can't toggle Wi-Fi/data/location/airplane directly; those open system panels. Only DND toggles directly.

## Commands

```bash
./gradlew assembleDebug                 # build debug APK
./gradlew installDebug                  # build + install on connected device/emulator
./gradlew test                          # JVM unit tests (app/src/test)
./gradlew connectedAndroidTest          # instrumented tests (app/src/androidTest), needs device
./gradlew lint                          # Android lint
./gradlew :app:testDebugUnitTest --tests "com.nandits.eztoggle.KeyguardGuardTest"   # single unit test class (append .methodName for one test)
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.nandits.eztoggle.ControlScreenTest
```

## Setup

- Gradle needs a JDK: `java` is not on PATH here, use `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.

- Single module `:app`, package/applicationId `com.nandits.eztoggle`.
- UI: Jetpack Compose + Material 3 only (no XML layouts, no Views). Theme in `ui/theme/` (`EztoggleTheme`, wraps `MaterialExpressiveTheme`, dynamic color on 31+, M3 baseline scheme below). Theme tokens only: no raw `Color(...)`, no hard-coded `sp`.
- AGP 9.x with built-in Kotlin: there is no `org.jetbrains.kotlin.android` plugin, only `kotlin.compose`. Don't add the Kotlin Android plugin.
- `compileSdk`/`targetSdk` 37, `minSdk` 29, Java 11 bytecode.
- Dependencies go through the version catalog `gradle/libs.versions.toml`; Compose versions come from the Compose BOM, so Compose artifacts have no explicit version.
- Release builds enable R8 via the AGP 9 `optimization { }` block (not `isMinifyEnabled`); no `proguard-rules.pro` exists yet.
