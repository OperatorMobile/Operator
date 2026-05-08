#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage:
  scripts/install-operator-android.sh --apk PATH [--package PACKAGE] [--wipe-data] [--incremental]

Installs an Operator Android APK with data preservation by default.

Options:
  --apk PATH        APK to install.
  --package NAME   Package to verify after install, and to uninstall first
                   when --wipe-data is set.
  --wipe-data      Explicitly uninstall the package before installing the APK.
  --incremental    Opt into adb incremental install. Avoid this for full
                   runtime APKs; streamed install is the default.

Without --wipe-data this script preserves data with `adb install --no-incremental -r`.
USAGE
}

apk_path=""
package_name=""
wipe_data=false
incremental=false

while [ "$#" -gt 0 ]; do
  case "$1" in
    --apk)
      apk_path="${2:-}"
      shift 2
      ;;
    --package)
      package_name="${2:-}"
      shift 2
      ;;
    --wipe-data)
      wipe_data=true
      shift
      ;;
    --incremental)
      incremental=true
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [ -z "$apk_path" ]; then
  echo "--apk is required" >&2
  usage >&2
  exit 2
fi

if [ ! -f "$apk_path" ]; then
  echo "APK not found: $apk_path" >&2
  exit 1
fi

if [ "$wipe_data" = true ]; then
  if [ -z "$package_name" ]; then
    echo "--package is required with --wipe-data" >&2
    exit 2
  fi
  echo "Wiping package data for $package_name before install." >&2
  adb uninstall "$package_name" >/dev/null || true
fi

if [ "$incremental" = true ]; then
  adb install -r "$apk_path"
else
  adb install --no-incremental -r "$apk_path"
fi

if [ -n "$package_name" ]; then
  if ! adb shell pm path "$package_name" >/dev/null 2>&1; then
    echo "Install reported success, but package is not installed: $package_name" >&2
    exit 1
  fi
fi
