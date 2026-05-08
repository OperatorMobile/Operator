# Release Checklist

Before publishing source:

- Start from a clean working tree.
- Confirm `third_party/codex` points to the intended fork commit.
- Confirm no generated binaries, SDK paths, APKs, runtime archives, screenshots,
  caches, or local config files are tracked.
- Run a tracked-file scan for local paths, credentials, device identifiers, and
  internal-only notes.
- Run `git diff --check`.

Before publishing APKs:

- Build from a clean checkout.
- Use a non-debug signing configuration.
- Verify package IDs and distribution profile boundaries.
- Verify third-party notices are included with runtime artifacts.
- Verify Play artifacts do not include full runtime SDK assets.
- Run runtime doctor checks on the final installed build.

Repository history for public release should be intentionally curated. Avoid
publishing internal notes, local verification artifacts, or machine-specific
build traces.
