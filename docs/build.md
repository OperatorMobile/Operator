# Build

This repository is set up so a new checkout can build the Android UI without
private files. Full runtime APKs additionally need generated Android-native
tool artifacts, which are intentionally not committed.

## Prerequisites

- JDK 17
- Android SDK platform 35 and build tools 35.0.0
- Android NDK 30.0.14904198
- Rust stable, `cargo`, and `cargo-ndk`
- Git with submodule support

Clone with submodules, or initialize them after cloning:

```bash
git submodule update --init --recursive
```

## Local Configuration

Create `mobile/android/local.properties` locally. This file is ignored and must
not be committed.

```properties
sdk.dir=/path/to/android/sdk
cargo.path=/path/to/cargo
native.abis=arm64-v8a
```

`sdk.dir` may be replaced by `ANDROID_HOME` or `ANDROID_SDK_ROOT`. `cargo.path`
may be replaced by `CARGO`.

## Minimal Build

Use this path to verify the app code, Compose UI, and Gradle setup without
requiring bundled runtime artifacts:

```bash
scripts/validate-android-local-env.sh
cd mobile/android
./gradlew :app:compilePlayDebugKotlin :app:testPlayDebugUnitTest -x buildOperatorEngine -x buildOperatorEngineArm64V8a
```

The GitHub Actions workflow runs the same kind of check on a clean runner.

## APK Builds

```bash
cd mobile/android
./gradlew :app:assemblePlayRelease
./gradlew :app:assembleFullRelease
./gradlew :runtime-extension:assembleRelease
```

The Play APK is the UI-oriented build. The Full and runtime-extension APKs are
only complete when `local.properties` points to staged runtime artifacts.

APK assembly builds the Rust engine JNI library unless that task is skipped.
For repeatable release builds, provide Android `rusty_v8` artifacts through
`local.properties`:

```properties
rustyV8Archive.arm64-v8a=/path/to/librusty_v8_release_aarch64-linux-android.a
rustyV8Binding.arm64-v8a=/path/to/bindings.rs
```

The workflow in `.github/workflows/build-rusty-v8-android.yml` can produce
those artifacts on a Linux builder.

## Runtime Artifacts

Gradle reads optional artifact paths from `local.properties`. The most important
keys are:

```properties
runtimeSdk.path.arm64-v8a=/path/to/staged/runtime-sdk
runtimeSdkArchive.path.arm64-v8a=/path/to/runtime-sdk.tar
toolchain.path.arm64-v8a=/path/to/staged/runtime-sdk
```

Individual tool keys, such as `git.path.arm64-v8a`, `python3.path.arm64-v8a`,
and `androidClang.path.arm64-v8a`, are validated by:

```bash
scripts/validate-android-local-env.sh
```

To build the current development tool set and assemble a full debug APK:

```bash
BUILD_CPYTHON=true BUILD_NODE=true BUILD_ANDROID_TOOLCHAIN=true \
  scripts/build-android-dev-toolchain.sh
```

To package and stage a runtime SDK from already-built Android-native tool
artifacts:

```bash
scripts/sync-android-runtime-sources.sh
scripts/build-local-android-runtime-sdk.sh --profile bootstrap
```

Generated source checkouts, APKs, runtime archives, local properties, and build
outputs are ignored by git.

## Release Notes

Release signing is intentionally not stored in this repository. Configure
signing through local Gradle properties, environment variables, or CI secrets.
For local release signing, add ignored properties like:

```properties
operator.signing.storeFile=/path/to/operator-release.jks
operator.signing.storePassword=...
operator.signing.keyAlias=operator-release
operator.signing.keyPassword=...
```

Before publishing an APK, run:

```bash
cd mobile/android
./gradlew :app:verifyAndroidDistributionProfiles
```
