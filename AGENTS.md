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
- `data/kanjivg/` is a build prerequisite alongside the other `data/` sources, so an existing checkout fails its next `assembleDebug` until it is populated (see README.md).

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
- Two databases, and the split is load-bearing. `shared/src/commonMain/sqldelight/dictionary/` is the bundled read-only dictionary (`okonomi.db`), regenerated wholesale by `:tools:dictgen` and never migrated. `shared/src/commonMain/sqldelight/user/` is the reader's own data (`user.db`: lists and their entries), which can never be regenerated and therefore carries real `.sqm` migrations with verification on. Each database names its own `srcDirs`; putting a `.sq` file in the wrong one compiles it into the wrong database and moves `DICTIONARY_SCHEMA_FINGERPRINT`. `user.db` sits beside the dictionary copy in the same directory, which is only safe because provisioning deletes the dictionary **by name** — never by clearing the directory. `cc.hosaka.okonomi.user.FavouritesStore` is the seam screens use over it.
- Persisted settings (`prefs/`): `PreferenceStore` is the seam every screen uses; `appPreferences()` is the app-lifetime instance over `androidx.datastore` (one per process — DataStore rejects two over one file). Reads that fail yield the default and writes that fail are dropped, so a broken store can never take a screen down. Tests inject `FakePreferenceStore`.

Practical rule: for new screens, follow the same split (see `feature/search/`):

- `XxxRoute.kt`: `@Serializable data object XxxRoute : Route` whose `Content()` calls `produceXxxScreenState()` and `XxxScreen(state)`; register it in `navigationSavedStateConfiguration`,
- `XxxScreen.kt` for rendering (pure renderer),
- `XxxState.kt` for the UI contract (nullable callbacks mean disabled),
- `XxxStateProducer.kt` with `produceXxxScreenState()` and `suspend fun ScreenStateScope.xxxScreenStateProducer(): Flow<XxxState>` for state composition, persistence, and side effects.

Verification: `./gradlew :androidApp:assembleDebug :shared:compileKotlinIosArm64 :shared:compileTestKotlinIosSimulatorArm64 :shared:testAndroidHostTest :tools:dictgen:test :shared:verifySqlDelightMigration`.

`:shared:verifySqlDelightMigration` is in the list because the user database
(`user.db`, the reader's saved words) is migrated rather than replaced. It
replays the checked-in `<version>.db` snapshots through the `.sqm` files and
fails if the result is not the schema the `.sq` files describe. It is wired to
`check`, which nothing else here runs, so without it a schema change could ship
with no migration behind it — and unlike the dictionary there is no re-copy to
repair that.

`:shared:compileTestKotlinIosSimulatorArm64` is in the list because the other
iOS task compiles main sources only. `commonTest` grew for months without ever
being compiled for Kotlin/Native, and by the time anyone looked, 20 test names
had commas in their backticks — which Kotlin/Native rejects outright.

Compose UI tests live in `commonTest` and run on the JVM through
`:shared:testAndroidHostTest`, with no emulator or device. Extend
`cc.hosaka.okonomi.ui.test.ComposeUiTestBase` and call
`androidx.compose.ui.test.v2.runComposeUiTest`; see that class and the
`androidHostTest` dependency block for why the runner and Robolectric are wired
the way they are.

`:tools:dictgen` is part of the check, not an optional extra: it owns the dictionary's
shape and its ranking rules, so a regression there changes search results while every
`:shared` test stays green.

## Change-Safety Rules

- Do not modify signing, notarization, release packaging, or deployment workflow files unless explicitly requested.
- Do not change build type behavior (release variants) unless required by the task.
- Avoid cross-module moves/renames in first-pass changes; prefer local modifications.

## Tests That Cannot Fail

Every increment in this project so far has shipped at least one test that could not
fail for the reason its name gives. Reviewers keep finding them by mutation. They
share one shape: **the assertion observes a proxy for the behaviour rather than the
behaviour.** Before trusting a test, break the thing it names and watch it go red.

Real examples from this repo, each found only after it had shipped:

- A test asserting a part-of-speech rule using `cop-da`, a code carried by **zero**
  rows in the dictionary. It passed, and would have kept passing with the real codes
  deleted from the rule.
- Two tests with no assertion at all, ending in a comment saying so. They could fail
  only by throwing.
- Three headline behaviours — conjugation furigana, headword ruby, base/ruby order —
  each deletable with the whole suite green, because ruby draws through an
  `AndroidView` that never reaches the semantics tree at the default SDK.
- A test named for catching a full table scan that passed when the scan was
  reintroduced: it ran `EXPLAIN QUERY PLAN` over SQL the test typed itself, never over
  the query the code runs.
- A guard test relying on a closed database driver throwing. It does not; the empty
  result came from the data, so the guard could be deleted unnoticed.
- A "write failure is swallowed" test whose write ran on a scope the test did not own,
  so it passed with the `try/catch` removed — on Android that catch is load-bearing.

Practical rules that follow:

- **Assert the thing, not a stand-in.** If the claim is "no query runs", count queries.
  If it is "this index is used", plan the query the code actually issues.
- **A test with no assertion is not a test.** Neither is one whose expected value
  equals the default it would get anyway.
- **Check what the harness can actually see.** Robolectric lays every glyph out to
  zero width and shadows Android APIs without using its regex or font engines, so
  layout, ruby positioning and ICU behaviour are invisible here. Where something is
  genuinely unobservable, say so in the KDoc — do not write an assertion that passes
  either way.
- **Prove new guards by mutation.** Break it, see it red, restore it. Say so in the
  report.

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

