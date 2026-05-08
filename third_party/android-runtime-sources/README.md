# Android Runtime Sources

This directory records the upstream source repositories used to produce the
optional command-line runtimes bundled into the Operator Android app.

The actual source checkouts are intentionally not committed. They can be large
and are not needed to inspect the app code. Run:

```sh
scripts/sync-android-runtime-sources.sh
```

to clone the pinned sources into `third_party/android-runtime-sources/sources/`.
That directory is ignored by git.

The lock file is `android-runtime-sources.tsv`. Each entry includes the upstream
repository URL, ref, pinned commit, license identifier, and purpose. Build
scripts call the same lock file before compiling artifacts, so the published
project stays reproducible and auditable without relying on opaque local files.
