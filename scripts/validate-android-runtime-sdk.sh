#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/android-runtime-sdk-common.sh"

usage() {
  cat <<EOF
Usage: scripts/validate-android-runtime-sdk.sh <image-root>

Validates a staged Android-native runtime SDK image against the selected
profile. Set OPERATOR_ANDROID_RUNTIME_SDK_PROFILE, OPERATOR_ANDROID_APP_ID,
ANDROID_ABI, and ANDROID_API to validate a non-default profile.

Optional:
  OPERATOR_ANDROID_RUNTIME_SDK_PACKAGE_GRAPH=/path/to/package-graph.txt
EOF
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  usage
  exit 0
fi

if [[ "$#" -ne 1 ]]; then
  usage >&2
  exit 2
fi

runtime_sdk_validate_image "$1" "${OPERATOR_ANDROID_RUNTIME_SDK_PACKAGE_GRAPH:-}"
printf 'Runtime SDK image validated: %s\n' "$1"
