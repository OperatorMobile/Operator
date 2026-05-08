#!/usr/bin/env bash
set -euo pipefail

OPERATOR_ANDROID_RUNTIME_SDK_PROFILE="${OPERATOR_ANDROID_RUNTIME_SDK_PROFILE:-bootstrap}"
OPERATOR_ANDROID_RUNTIME_SDK_VERSION="${OPERATOR_ANDROID_RUNTIME_SDK_VERSION:-local-$(date +%Y%m%d)}"
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/android-runtime-sdk-common.sh"

usage() {
  cat <<EOF
Usage: scripts/build-local-android-runtime-sdk.sh [options]

Assembles, packages, and stages the local bootstrap Android Runtime SDK from
already-built Android tool artifacts.

Options:
  --profile <name>         Runtime SDK profile. Defaults to bootstrap.
  --version <version>      Runtime SDK version. Defaults to local-YYYYMMDD.
  --package-graph <file>   Package graph report to validate and embed.
  --help                   Show this help.
EOF
}

package_graph_file="${OPERATOR_ANDROID_RUNTIME_SDK_PACKAGE_GRAPH:-}"
if [[ -z "$package_graph_file" ]]; then
  package_graph_file="$(read_local_property "androidToolchainPackageGraph.path.$ANDROID_ABI")"
fi

while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --profile)
      shift
      OPERATOR_ANDROID_RUNTIME_SDK_PROFILE="${1:-}"
      ;;
    --version)
      shift
      OPERATOR_ANDROID_RUNTIME_SDK_VERSION="${1:-}"
      ;;
    --package-graph)
      shift
      package_graph_file="${1:-}"
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
  shift
done

export OPERATOR_ANDROID_RUNTIME_SDK_PROFILE
export OPERATOR_ANDROID_RUNTIME_SDK_VERSION

image_dir="${OPERATOR_ANDROID_RUNTIME_SDK_IMAGE_DIR:-$ROOT_DIR/local-artifacts/android-runtime-sdk/images/$OPERATOR_ANDROID_RUNTIME_SDK_PROFILE/$OPERATOR_ANDROID_APP_ID/api$ANDROID_API/$ANDROID_ABI}"
archive_path="$(runtime_sdk_artifact_path)"
build_args=(--input-root "$image_dir" --output "$archive_path")
if [[ -n "$package_graph_file" ]]; then
  build_args+=(--package-graph "$package_graph_file")
fi

"$ROOT_DIR/scripts/assemble-android-runtime-sdk-image.sh" \
  --profile "$OPERATOR_ANDROID_RUNTIME_SDK_PROFILE" \
  --output-dir "$image_dir"
"$ROOT_DIR/scripts/build-android-runtime-sdk.sh" "${build_args[@]}"
stage_args=(--archive "$archive_path" --sha256 "$(cat "$archive_path.sha256")")
if [[ -n "$package_graph_file" ]]; then
  stage_args+=(--package-graph "$package_graph_file")
fi
"$ROOT_DIR/scripts/stage-android-runtime-sdk.sh" "${stage_args[@]}"

printf 'Local Android Runtime SDK ready: %s\n' "$archive_path"
