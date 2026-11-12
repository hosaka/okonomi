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
- The app version is `app` in `gradle/libs.versions.toml`; `versionCode` is DERIVED from it in `androidApp/build.gradle.kts` (`major*10000 + minor*100 + patch`). Never hand-edit `versionCode`, and never add a pre-release suffix to the version — the derivation rejects both.
- Releases and CI are documented in `.forgejo/README.md` (the workflows, writing release notes, cutting a release) and `.forgejo/ci-image/README.md` (the build image, and how to rebuild and push it). Read them before touching `.forgejo/`, the version, or anything a release depends on.
- Every finished feature or user-visible fix ends with its release note: a fragment in `.changes/unreleased/`, written by the agent that did the work with `changie new --kind <Added|Changed|Fixed|…> --body "<one line>"`. Write it for readers of the store listing: what changed for them, with no class names or internals. Then check it with `changie batch auto --dry-run`. The Play "What's new" text keeps only whole bullets up to 500 characters, so keep each note short.
- Do not assume Android emulator/device availability unless explicitly requested by the user.
- The `data/` dictionary sources are fetched and decompressed by the build itself (`:tools:dictgen:fetchDictionarySources`, then `:tools:dictgen:extractDictionarySources`), so a fresh checkout builds without preparation; the first build downloads about 78 MB into `data/archives/`, which is a cache and is never re-fetched (see README.md).
- KanjiVG now extracts to `data/kanji/`, the zip's own top-level name. A checkout that predates that change keeps an orphaned `data/kanjivg/` (~90 MB) which nothing reads any more: delete it.

## High-Level Architecture Overview

- Modules: `:shared` (all UI and logic, Compose Multiplatform, `commonMain` + `androidMain`/`iosMain` for `expect/actual`), `:androidApp` (Android entrypoint), `iosApp/` (Xcode project hosting the `Shared` framework).
- Platform entrypoints only host the shared `App()` composable:
  - Android: `androidApp/src/main/kotlin/cc/hosaka/okonomi/MainActivity.kt`
  - iOS: `shared/src/iosMain/kotlin/cc/hosaka/okonomi/MainViewController.kt`
- `shared/src/commonMain/kotlin/cc/hosaka/okonomi/App.kt` wires `OkonomiTheme { Surface { HomeScreen() } }`.
- Theme (`ui/theme/`): `OkonomiTheme` wraps `MaterialExpressiveTheme` and follows the system light/dark mode. Every typography style carries the Japanese locale (so kanji keep their Japanese shapes on a phone whose first CJK language is not Japanese, or that lists none), so new text styles derive from `MaterialTheme.typography` rather than `TextStyle()`, and a platform view that draws text (a `TextView` in an `AndroidView`) must copy `style.localeList` itself, as `FontPaddingFreeText` does. `appDynamicLightColorScheme()/appDynamicDarkColorScheme()` are `expect` functions: Android 12+ uses dynamic color, older Android and iOS use the default Material schemes. `Dimen`/`Dimens` hold shared spacing values.
- Shared chrome (`ui/`): `ScaffoldColumn`/`ScaffoldLazyColumn` (Material `Scaffold` wrappers that forward inner padding to the content), `toolbar/LargeToolbar` (`LargeFlexibleTopAppBar`) with `toolbar/util/ToolbarBehavior`/`ToolbarColors`, and `SearchTextField` (pill shaped search box with clear action).
- Screen state (`feature/navigation/state/ProduceScreenState.kt`): `produceScreenState(key, initial) { ... }` runs a producer inside a `ScreenStateScope` (`navigation: NavigationController`, `mutablePersistedFlow(key, initial)`) and shares the resulting flow through a `ViewModel` scoped to the back stack entry (in-memory only, no disk persistence).
- Features live in `shared/src/commonMain/kotlin/cc/hosaka/okonomi/feature/*`; user-visible strings live in `shared/src/commonMain/composeResources/values/strings.xml` and are read via `Res.string.*`. Every string also needs its Japanese twin in the same file under `values-ja/`: `TranslationCompletenessTest` reads every file in each folder and fails when a name is missing from either side, when placeholders differ, or when a translation reads the same as the English outside its short allowlist. Licence and attribution wording is the allowlisted exception and stays English. A new UI language is a `values-xx` folder plus one `AppLanguage` entry (`lang/AppLanguage.kt`); a Chinese (or other Han-script) one also needs the theme's Japanese locale revisited (`ui/theme/Theme.kt`). Dictionary tags live beside `strings.xml` in `tags.xml`, as `tag_<code>`, with `-` written `_` and case kept (`iK` and `ik` are two strings). Their English started as the dictionary's own labels and is now the app's wording: a later rewording in the dictionary does not reach the UI unless `tags.xml` is edited. The deinflection rules named in words are `deinflection_rule_*` in `strings.xml`; a new one needs its string and a branch in the `when` in `ui/labels/DictionaryLabels.kt`. The data carries tag codes and rule names, never their text (the Word tab's `DictionaryTag` adds the dictionary's own English label, for the fallback): `ui/labels/` resolves a label in composition, never in state or a producer, so it follows a language switch, and a code with no string falls back to the dictionary's label. A new dictionary code shows up when `:tools:dictgen:syncPosCodes` refreshes `pos-codes.tsv`, which turns `DictionaryLabelsTest` red until `tags.xml` has the code in both languages.
- Two databases, and the split is load-bearing. `shared/src/commonMain/sqldelight/dictionary/` is the bundled read-only dictionary (`okonomi.db`), regenerated wholesale by `:tools:dictgen` and never migrated. `shared/src/commonMain/sqldelight/user/` is the reader's own data (`user.db`: lists and their entries), which can never be regenerated and therefore carries real `.sqm` migrations with verification on. Each database names its own `srcDirs`; putting a `.sq` file in the wrong one compiles it into the wrong database and moves `DICTIONARY_SCHEMA_FINGERPRINT`. `user.db` sits beside the dictionary copy in the same directory, which is only safe because provisioning deletes the dictionary **by name** — never by clearing the directory. `cc.hosaka.okonomi.user.FavouritesStore` is the seam screens use over it.

Besides Favourites, `user.db` holds one other built-in list, History: words
opened from Search results, newest first, uncapped. The seam's destructive
operations are `replaceList` (and its `replaceListIfEmpty` form), which an
import uses to clear a list and rewrite it in a single transaction, and
`clearList`/`removeFromList`. All are queued behind the same single writer as
`toggleFavourite`, so none can interleave with a heart tap; anything else that
needs to write in bulk belongs on that queue too, never on a second writer.
The interchange format is `FavouritesTransfer.kt` in the same package: entry
ids and a list name, behind a `version` field that decoding requires to be 1.
Export writes the name, import discards it and lands in the list Import was
opened from (carried across the system dialog), so a file moves freely between
Favourites and History.

The file dialogs behind that come from FileKit (`filekit-dialogs-compose`,
MIT), the project's first and only file-picker dependency. Its Compose
launchers need no `FileKit.init` and no manifest permission. Remember them at
a route's root, never inside a dropdown or dialog: FileKit's own docs warn
they are disposed with the surface that holds them on iOS. Note also that the
system dialog is another activity, so the screen behind it stops being
collected and its state producer is cancelled five seconds later — anything a
dialog result must come back to has to outlive that, which is what
`mutablePersistedFlow` is for (see `FavouritesStateProducer`).

- Persisted settings (`prefs/`): `PreferenceStore` is the seam every screen uses, for Booleans and strings alike, with writes of both types on one ordered writer; `appPreferences()` is the app-lifetime instance over `androidx.datastore` (one per process — DataStore rejects two over one file). Reads that fail yield the default and writes that fail are dropped, so a broken store can never take a screen down. Tests inject `FakePreferenceStore`.
- Send to Anki (`anki/`, Android only): talks to AnkiDroid's `com.ichi2.anki.flashcards` provider directly through `ContentResolver` — no AnkiDroid API dependency. Its permission and `<queries>` entry live in `shared/src/androidMain/AndroidManifest.xml`, which merges into the app. Duplicates are keyed by the note's EntryId field.
- Deep link `okonomi://entry/<id>` (the cards' "More in Okonomi"): parsed and queued in `:shared` (`feature/navigation/EntryLinks.kt`), opened by `HomeScreen` over Search's root; `MainActivity` (`launchMode="singleTask"`) only forwards the URI from `onCreate`/`onNewIntent`. System back on the linked entry leaves the app for the sender.
- Text lookup (Android only; `ACTION_PROCESS_TEXT`, "Okonomi" in the text selection toolbar): tidied, capped and queued in `:shared` (`feature/navigation/TextLookups.kt`, a second queue beside `EntryLinks`, not a merge). The filter is on `ProcessTextActivity`, a no-UI trampoline: the toolbar starts it for a result, which puts it in the caller's task whatever the launch mode, so it forwards the query to `MainActivity` with `NEW_TASK` and finishes without a result. Both activities are thin calls into `shared/src/androidMain/.../feature/navigation/AndroidHandOffs.kt` (`processTextForward`, `handOffToShell`), which holds the routing and the Recents and for-result guards and is tested in `androidHostTest`. `HomeScreen` opens a lookup from another app as a marked `SearchRoute` over Search's root, and system back on it leaves for that app; text selected in Okonomi itself (the referrer is Okonomi) opens an ordinary search over the current screen instead. Check with `adb shell am start -n cc.hosaka.okonomi/.ProcessTextActivity -a android.intent.action.PROCESS_TEXT -t text/plain --es android.intent.extra.PROCESS_TEXT '食べた'` (an implicit intent opens the app chooser).

Practical rule: for new screens, follow the same split (see `feature/search/`):

- `XxxRoute.kt`: `@Serializable data object XxxRoute : Route` whose `Content()` calls `produceXxxScreenState()` and `XxxScreen(state)`; register it in `navigationSavedStateConfiguration`,
- `XxxScreen.kt` for rendering (pure renderer),
- `XxxState.kt` for the UI contract (nullable callbacks mean disabled),
- `XxxStateProducer.kt` with `produceXxxScreenState()` and `suspend fun ScreenStateScope.xxxScreenStateProducer(): Flow<XxxState>` for state composition, persistence, and side effects.

Verification: `./gradlew :androidApp:assembleDebug :androidApp:compileDebugAndroidTestKotlin :shared:compileKotlinIosArm64 :shared:compileTestKotlinIosSimulatorArm64 :shared:testAndroidHostTest :tools:dictgen:test :shared:verifySqlDelightMigration`.

`fastlane test` runs all of that except `:androidApp:assembleDebug`, plus `:androidApp:lintDebug`; `fastlane unit_tests`, `migrations`, `compile` and `lint` are its parts, and the task lists live at the top of `fastlane/Fastfile`. Add `:androidApp:assembleDebug` separately when a change can affect packaging — it is the only step that needs the dictionary and therefore the only slow one.

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

`:androidApp:compileDebugAndroidTestKotlin` is in the list for the same kind
of reason: `androidApp/src/androidTest` holds only the screenshot flow, which
runs on an emulator through `fastlane screenshots` and nothing else, so no
check ever compiled it and it could stop compiling unnoticed. It pulls in no
dictionary task.

Compose UI tests live in `commonTest` and run on the JVM through
`:shared:testAndroidHostTest`, with no emulator or device. Extend
`cc.hosaka.okonomi.ui.test.ComposeUiTestBase` and call
`androidx.compose.ui.test.v2.runComposeUiTest`; see that class and the
`androidHostTest` dependency block for why the runner and Robolectric are wired
the way they are.

A test that hosts `HomeScreen` and depends on the learning language, or on
whether furigana is drawn, must pass `deviceLanguage = "en"`. Otherwise it
runs whatever the machine's language makes it: until a reader chooses, a
Japanese-language device is assumed to be learning English, and that hides
furigana throughout.

Settings → Appearance → Language overrides the locale of the whole process
(`LocaleList.setDefault` on Android, the app-domain `AppleLanguages` on iOS),
because Compose Multiplatform resources have no per-composition locale. A test
that applies a UI language must undo it, the way `ProvideAppLanguageUiTest`
does with `snapshotPhoneLanguage()` and `forgetAppliedAppLanguage()`. Otherwise
every later test in the same JVM reads its strings in the wrong language.

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

