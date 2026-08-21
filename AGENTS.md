# AGENTS.md

## Purpose & Boundaries

- Optimize for safe, incremental, reviewable changes.
- Keep edits tightly scoped to the user request. Avoid opportunistic refactors.
- Prefer shared changes in `:shared` when behavior should be consistent across Android and iOS.
- Treat `androidApp`, `iosApp`, and release/deployment config as higher-risk surfaces; only touch them when required.
- Preserve existing architecture and naming patterns inside each feature area.

## Environment Baseline

- Use JDK `21` (provisioned via `gradle/gradle-daemon-jvm.properties`).
- Android SDK Platform 37 (`compileSdk` 37), AGP 9.1.1, Gradle 9.3.1 (bumped for AboutLibraries 15.0.4).
- Assume Kotlin Multiplatform + Compose Multiplatform project conventions.
- Do not assume Android emulator/device availability unless explicitly requested by the user.

## High-Level Architecture Overview

- Modules: `:shared` (all UI and logic, Compose Multiplatform, `commonMain` + `androidMain`/`iosMain` for `expect/actual`), `:androidApp` (Android entrypoint), `iosApp/` (Xcode project hosting the `Shared` framework).
- Platform entrypoints only host the shared `App()` composable:
  - Android: `androidApp/src/main/kotlin/cc/hosaka/okonomi/MainActivity.kt`
  - iOS: `shared/src/iosMain/kotlin/cc/hosaka/okonomi/MainViewController.kt`
- `shared/src/commonMain/kotlin/cc/hosaka/okonomi/App.kt` wires `OkonomiTheme { Surface { HomeScreen() } }`.
- Theme (`ui/theme/`): `OkonomiTheme` wraps `MaterialExpressiveTheme` and follows the system light/dark mode. `appDynamicLightColorScheme()/appDynamicDarkColorScheme()` are `expect` functions: Android 12+ uses dynamic color, older Android and iOS use the default Material schemes. `Dimen`/`Dimens` hold shared spacing values.
- Shared chrome (`ui/`): `ScaffoldColumn`/`ScaffoldLazyColumn` (Material `Scaffold` wrappers that forward inner padding to the content), `toolbar/LargeToolbar` (`LargeFlexibleTopAppBar`) with `toolbar/util/ToolbarBehavior`/`ToolbarColors`, and `SearchTextField` (pill shaped search box with clear action).
- Screen state (`feature/navigation/state/ProduceScreenState.kt`): `produceScreenState(key, initial) { ... }` runs a producer inside a `ScreenStateScope` (`navigation: NavigationController`, `mutablePersistedFlow(key, initial)`) and shares the resulting flow through a `ViewModel` scoped to the back stack entry (in-memory only, no disk persistence).
- Features live in `shared/src/commonMain/kotlin/cc/hosaka/okonomi/feature/*`; user-visible strings live in `shared/src/commonMain/composeResources/values/strings.xml` and are read via `Res.string.*`.

Practical rule: for new screens, follow the same split (see `feature/search/`):

- `XxxRoute.kt`: `@Serializable data object XxxRoute : Route` whose `Content()` calls `produceXxxScreenState()` and `XxxScreen(state)`; register it in `navigationSavedStateConfiguration`,
- `XxxScreen.kt` for rendering (pure renderer),
- `XxxState.kt` for the UI contract (nullable callbacks mean disabled),
- `XxxStateProducer.kt` with `produceXxxScreenState()` and `suspend fun ScreenStateScope.xxxScreenStateProducer(): Flow<XxxState>` for state composition, persistence, and side effects.

Verification: `./gradlew :androidApp:assembleDebug :shared:compileKotlinIosArm64 :shared:testAndroidHostTest`.

## Change-Safety Rules

- Do not modify signing, notarization, release packaging, or deployment workflow files unless explicitly requested.
- Do not change build type behavior (release variants) unless required by the task.
- Avoid cross-module moves/renames in first-pass changes; prefer local modifications.

## Code Standards

- Write self-explanatory code with clear naming.
- No need to decorate comments with symbols like dashes or stars.
- Add comments in English only when they provide non-obvious value:
- - DO write comments for:
- - - Complex business logic or algorithms
- - - Non-obvious design decisions and trade-offs
- - - Public APIs, exported functions, and package documentation
- - DON'T write comments for:
- - - Self-evident code (e.g., getters/setters)
- - - Repeating what the code already says
- - - Implementation details that naming makes clear

