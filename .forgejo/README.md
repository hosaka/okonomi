# CI

Workflows run inside [a purpose-built image](./ci-image) carrying Zulu 21, the Android SDK and Node.

| Workflow | Runs on | Runs what |
|---|---|---|
| `pr-test.yml` | every PR | tests, migration verification, AGP lint, Android and iOS compilation |
| `build-android.yml` | merge to `main` | builds a release APK to prove the packaging path still works |
| `build-ios.yml` | manual dispatch | placeholder until a macOS runner exists |
| `release.yml` | tag `v*` | builds a signed APK and publishes it as a Forgejo release |
| `version.yml` | manual dispatch | bumps the version, commits and tags - which is what triggers `release.yml` |


**Cutting a release:** dispatch `version.yml` with `patch`, `minor` or `major`. It rewrites `app` in the version catalogue, commits with `[skip ci]`, and pushes tag `vX.Y.Z` which then triggers `release.yml`.

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

