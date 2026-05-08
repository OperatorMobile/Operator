# Operator

Operator is an Android app for running Codex from a mobile-first interface. It
combines chat, sessions, terminal access, Git-aware file review, and an
Android-owned shell runtime.

This is an independent project and is not an official OpenAI product. The
embedded Codex source is tracked as a submodule:

```text
third_party/codex -> https://github.com/OperatorMobile/codex.git
```

## Packages

- `com.illumination.operator` - full sideload build with the local runtime SDK.
- `com.illumination.operator.gplay` - Google Play-oriented UI build.
- `com.illumination.operator.runtime` - sideloaded runtime extension for the
  Play build.
- Debug builds append `.debug`.

## Repository Layout

```text
mobile/android/                      Android app and runtime extension
operator-rs/mobile-android-engine/   Rust JNI bridge
third_party/codex/                   Operator Codex fork submodule
third_party/android-runtime-sources/ Runtime source manifest and profiles
scripts/                             Runtime, build, staging, and install tools
docs/                                Concise project documentation
assets/                              Public project assets
```

## Build

Create `mobile/android/local.properties` with local SDK/tool paths. The file is
ignored and should not be committed.

```bash
scripts/validate-android-local-env.sh
cd mobile/android
./gradlew :app:compilePlayDebugKotlin :app:compileFullDebugKotlin -x buildOperatorEngine
```

See [docs/build.md](docs/build.md) for release and runtime artifact notes.

## Docs

- [Architecture](docs/architecture.md)
- [Build](docs/build.md)
- [Runtime SDK](docs/runtime-sdk.md)
- [Release Checklist](docs/release.md)
- [User Guide](docs/user-guide.md)

## License

Operator is source-available under the Functional Source License, Version 1.1,
ALv2 Future License. See [LICENSE.md](LICENSE.md).

Third-party components retain their own licenses. See [NOTICE](NOTICE).
