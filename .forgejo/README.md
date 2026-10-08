# CI

Workflows run inside [a purpose-built image](./ci-image) carrying Zulu 21, the Android SDK, Node and changie.

| Workflow | Runs on | Runs what |
|---|---|---|
| `pr-test.yml` | every PR | tests, migration verification, AGP lint, Android and iOS compilation (including the screenshot test sources) |
| `build-android.yml` | merge to `main` | builds a release APK to prove the packaging path still works |
| `build-ios.yml` | manual dispatch | placeholder until a macOS runner exists |
| `release.yml` | tag `v*` | builds a signed APK and publishes it as a release, with that version's notes as the body |
| `version.yml` | manual dispatch | batches the release notes, bumps the version, commits and tags - then runs `release.yml` |


**Writing release notes:** run `changie new` ([changie](https://changie.dev)) and commit the fragment it writes to `.changes/unreleased/` with the change it describes. Each fragment has a kind: `Added`, `Changed`, `Deprecated` and `Removed` bump the minor version, `Fixed` and `Security` bump the patch. Nothing bumps major; 1.0 is a deliberate `major` dispatch.

**Cutting a release:** dispatch `version.yml`. The default `auto` bump picks the version from the pending fragments and fails if there are none; `patch`, `minor` or `major` force a bump and allow an empty notes section. The job batches the fragments into `.changes/vX.Y.Z.md`, regenerates `CHANGELOG.md`, rewrites `app` in the version catalogue, writes the Play "What's new" text to `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (whole lines up to 500 characters), commits `chore(release): vX.Y.Z` and pushes tag `vX.Y.Z`. `release.yml` then publishes `.changes/vX.Y.Z.md` as the release body. `app` and `changie latest` must agree or the job stops before writing anything.

**Publishing to mirrors:** `release.yml` also publishes the same notes and the same signed APK to other mirrors. Each needs a token stored as a secret, and its step warns and skips without one:

| Secret | Token |
|---|---|
| `GH_RELEASE_TOKEN` | GitHub fine-grained personal access token for mirror repo, with *Contents: read and write* |
| `CODEBERG_RELEASE_TOKEN` | Codeberg access token for mirror repo, with *repository: read and write* |


**Refreshing data:** merges reuse the archives cached under the `okonomi-data-v1` key in `build-android.yml` and `release.yml`, so upstream is fetched from only once. Cached entries are immutable, so bumping that key to `v2` is how dictionaries and data can be updated.

## Versioning

The app's version lives in one place, `app` under `[versions]` in [gradle/libs.versions.toml](./gradle/libs.versions.toml). `versionCode` is derived from it (`0.1.0` becomes `100`, `1.2.3` becomes `10203`), so the two can never drift and the same commit produces the same numbers everywhere. Each component must be `99` or lower otherwise the build will fail.

## Signing

When nothing is configured `assembleRelease` uses the debug keystore. For a real signature a `keystore.properties` can be used:

If `keystore.properties` is present in the repository root it will be used for signing a release. This file must remain .gitignored.
```properties
storeFile=okonomi-release.jks
storePassword=...
keyAlias=okonomi
```

Alternatively, the following environment variables can be set.
| Property | Environment variable | Meaning |
|---|---|---|
| `storeFile` | `OKONOMI_KEYSTORE_FILE` | Path to the keystore |
| `storePassword` | `OKONOMI_KEYSTORE_PASSWORD` | Keystore password, which is also the key password |
| `keyAlias` | `OKONOMI_KEY_ALIAS` | Key alias within the keystore |

It is worth noting that a partial set fails the build rather than falling back to debug and if both `keystore.properties` and env vars are present the file is given priority.

