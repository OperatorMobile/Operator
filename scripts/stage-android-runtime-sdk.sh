#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/android-runtime-sdk-common.sh"

usage() {
  cat <<EOF
Usage: scripts/stage-android-runtime-sdk.sh <archive.tar.zst> [options]

Verifies and unpacks a runtime SDK archive into ignored local artifacts, then
records the staged SDK in mobile/android/local.properties.

Options:
  --archive <file>         Runtime SDK archive. Same as positional argument.
  --sha256 <hex>           Expected archive SHA-256.
  --package-graph <file>   Optional package graph file to validate.
  --output-dir <dir>       Staged output directory.
  --install-archive <file> Runtime SDK .tar packaged into APK assets.
  --no-local-properties    Do not update mobile/android/local.properties.
  --help                   Show this help.

Environment:
  OPERATOR_ANDROID_RUNTIME_SDK_SHA256=<hex>
  OPERATOR_ANDROID_RUNTIME_SDK_PACKAGE_GRAPH=/path/to/package-graph.txt
EOF
}

require_host_tool tar
require_host_tool zstd

archive_path="${OPERATOR_ANDROID_RUNTIME_SDK_ARTIFACT:-}"
expected_sha="${OPERATOR_ANDROID_RUNTIME_SDK_SHA256:-}"
package_graph_file="${OPERATOR_ANDROID_RUNTIME_SDK_PACKAGE_GRAPH:-}"
output_dir="$(runtime_sdk_staged_dir)"
install_archive_path=""
update_local_properties=true

while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --archive)
      shift
      archive_path="${1:-}"
      ;;
    --sha256)
      shift
      expected_sha="${1:-}"
      ;;
    --package-graph)
      shift
      package_graph_file="${1:-}"
      ;;
    --output-dir)
      shift
      output_dir="${1:-}"
      ;;
    --install-archive)
      shift
      install_archive_path="${1:-}"
      ;;
    --no-local-properties)
      update_local_properties=false
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    -*)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
    *)
      if [[ -n "$archive_path" ]]; then
        echo "Only one runtime SDK archive may be provided." >&2
        exit 2
      fi
      archive_path="$1"
      ;;
  esac
  shift
done

if [[ -z "$archive_path" ]]; then
  echo "Missing runtime SDK archive." >&2
  usage >&2
  exit 2
fi
if [[ ! -f "$archive_path" ]]; then
  echo "Runtime SDK archive not found: $archive_path" >&2
  exit 1
fi
if [[ -n "$package_graph_file" && ! -f "$package_graph_file" ]]; then
  echo "Package graph file not found: $package_graph_file" >&2
  exit 1
fi

runtime_sdk_verify_archive_sha256 "$archive_path" "$expected_sha"

work_dir="$(runtime_sdk_mktemp_dir stage)"
extract_dir="$work_dir/image"
cleanup() {
  rm -rf "$work_dir"
}
trap cleanup EXIT HUP INT TERM

mkdir -p "$extract_dir"
case "$archive_path" in
  *.tar)
    tar -xf "$archive_path" -C "$extract_dir"
    ;;
  *)
    zstd -dc "$archive_path" | tar -xf - -C "$extract_dir"
    ;;
esac

runtime_sdk_validate_image "$extract_dir" "$package_graph_file"

rm -rf "$output_dir.tmp" "$output_dir"
mkdir -p "$(dirname "$output_dir")"
mv "$extract_dir" "$output_dir.tmp"
mv "$output_dir.tmp" "$output_dir"

if [[ "$update_local_properties" == "true" ]]; then
  if [[ -z "$install_archive_path" && "$archive_path" == *.tar.zst && -f "${archive_path%.zst}" ]]; then
    install_archive_path="${archive_path%.zst}"
  fi
  if [[ -z "$install_archive_path" ]]; then
    install_archive_path="$archive_path"
  fi
  upsert_local_property "runtimeSdk.path.$ANDROID_ABI" "$output_dir"
  upsert_local_property "runtimeSdkArchive.path.$ANDROID_ABI" "$install_archive_path"
  upsert_local_property "runtimeSdkDistributionArchive.path.$ANDROID_ABI" "$archive_path"
  upsert_local_property "toolchain.path.$ANDROID_ABI" "$output_dir"
fi

printf 'Runtime SDK staged: %s\n' "$output_dir"
if [[ "$update_local_properties" == "true" ]]; then
  printf 'Updated %s for %s\n' "$LOCAL_PROPERTIES" "$ANDROID_ABI"
fi
