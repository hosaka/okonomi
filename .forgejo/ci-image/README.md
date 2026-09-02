# Okonomi CI image

Carries Zulu 21, the Android SDK and Node, so no workflow spends time installing them. Built and pushed by hand: it changes only when the SDK, Node or the JDK moves, which is rarely enough that automating it would cost more than it saves.

## Build and push

```sh
podman build -t code.hosaka.cc/hosaka/okonomi/ci:0.1.0 .forgejo/ci-image
podman login code.hosaka.cc
podman push code.hosaka.cc/hosaka/okonomi/ci:0.1.0
```

Docker works the same way. The tag is referenced by every workflow's `container.image`, so bump it there in the same commit that bumps it here.

## Why the name is `okonomi/ci`

In order to show the image as a package under the repository's "Packages" Forgejo requires images follow the repo name `{registry}/{owner}/{image}` or adding the `org.opencontainers.image.source` label in the Containerfile. We do both here, since ["if both methods match a repository, the repository referenced in the label is preferred"](https://forgejo.org/docs/latest/user/packages/container/#linking-an-image-to-a-repository).

## What is pinned, and why

| Pin | Value | Reason |
|---|---|---|
| Base | `azul/zulu-openjdk-debian:21` | `gradle/gradle-daemon-jvm.properties` pins `toolchainVendor=AZUL`, any other JDK makes Gradle download Zulu again |
| Node | `v24.20.0` | `actions/checkout` and `actions/cache` actions and need a NodeJS in the image |
| cmdline-tools | `16111833` | `cmdline-tools;latest` currently resolves to rev `23.0` |
| Platform | `platforms/android-37.0` | `compileSdk = 37`, the SDK publishes `37.0`/`37.1`/`37.2`, not a bare `37` |
| Build tools | `build-tools/36.0.0` | `android-buildTools` Tracks AGP, **not** `compileSdk`, also provides `apksigner` for release verification |

## Verifying a build

```sh
podman run --rm code.hosaka.cc/hosaka/okonomi/ci:0.1.0 java -version        # Zulu 21
podman run --rm code.hosaka.cc/hosaka/okonomi/ci:0.1.0 node --version       # v24.x
podman run --rm code.hosaka.cc/hosaka/okonomi/ci:0.1.0 apksigner --version  # 0.9
```

