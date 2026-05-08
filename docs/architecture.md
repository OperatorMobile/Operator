# Architecture

Operator has three Android distribution targets:

| Target | Package | Purpose |
| --- | --- | --- |
| Full | `com.illumination.operator` | Main app plus bundled local runtime SDK for sideload distribution. |
| Play | `com.illumination.operator.gplay` | Google Play-oriented UI app with a small local tool set. |
| Runtime Extension | `com.illumination.operator.runtime` | Sideloaded runtime provider for the Play app. |

Debug variants append `.debug`.

## Codex Runtime

Operator embeds Codex through a Rust JNI bridge in
`operator-rs/mobile-android-engine`. The Android UI talks to the embedded
runtime through app-server-style JSON-RPC methods rather than reimplementing
Codex behavior in Kotlin.

The app tracks an Operator-maintained Codex fork as a submodule:

```text
third_party/codex
https://github.com/OperatorMobile/codex.git
branch: operator/android-port
```

Keep Android-specific integration in Operator-owned paths when possible. Patch
the Codex fork only for changes that belong near Codex runtime or app-server
interfaces.

## Runtime Model

The app manages an app-private home, workspace, temporary directory, and tool
prefix. Shell commands inherit a controlled environment with explicit `PATH`,
`HOME`, `TMPDIR`, certificate, Git, Python, Node, and toolchain settings.

The Play build can include only the small tool profile:

- `rg`
- `apply_patch`
- BusyBox
- OpenSSH client tools

The full build and runtime extension can include the broader runtime SDK:

- Git and GitHub CLI
- Python and Node
- native build tools
- Android C/C++ toolchain
- optional Rust toolchain

## Background Work

Long-running local work is hosted by an Android foreground service when the app
requests background execution. User-visible stop/cancel controls should stop
the active work and release foreground execution.
