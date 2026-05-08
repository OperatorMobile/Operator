# Runtime SDK

The runtime SDK is an Android-native tool prefix packaged for Operator. It is
not a generic Linux distribution and should not pretend to be glibc Linux.
Tools and packages must target Android/Bionic.

## Prefix

Runtime files install under the app-owned `files/tools` tree. The shell
environment exports stable values for:

- `HOME`
- `TMPDIR`
- `PATH`
- `LD_LIBRARY_PATH`
- `SSL_CERT_FILE`
- `GIT_CONFIG_GLOBAL`
- `PYTHONHOME` / Python-related paths
- Node and npm paths
- compiler and package-config paths

## Source Manifest

Pinned runtime sources are listed in:

```text
third_party/android-runtime-sources/android-runtime-sources.tsv
```

The actual upstream source checkouts are ignored and can be recreated with:

```bash
scripts/sync-android-runtime-sources.sh
```

Source checkouts and downloads are local generated state. They are not expected
to exist in a fresh clone.

## Profiles

Runtime SDK profiles live in:

```text
third_party/android-runtime-sources/runtime-sdk/profiles/
```

Profiles define package groups and required runtime paths. Keep profiles small
and auditable. Do not commit generated package output.

## Build Flow

The SDK packaging scripts expect Android-native binaries and libraries as input.
They do not hide a prebuilt private toolchain in git.

Common flow:

```bash
scripts/sync-android-runtime-sources.sh
scripts/build-local-android-runtime-sdk.sh --profile bootstrap
scripts/validate-android-local-env.sh
```

`scripts/stage-android-runtime-sdk.sh` verifies an SDK archive, unpacks it into
ignored local artifacts, and updates `mobile/android/local.properties` unless
`--no-local-properties` is passed.

## Packaging Rules

- Keep generated binaries out of git.
- Preserve upstream license and notice files in binary artifacts.
- Sanitize host paths from interpreter, compiler, and package metadata.
- Prefer Android-native package builds over Linux compatibility shims.
- Treat package manager caches as local/generated state.
