#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/android-runtime-sdk-common.sh"

usage() {
  cat <<EOF
Usage: scripts/build-android-runtime-sdk.sh --input-root <dir> [options]
       scripts/build-android-runtime-sdk.sh --input-prefix <usr-dir> [options]

Packages a prepared Android-native runtime SDK prefix into the Operator SDK
artifact format. This script does not compile the SDK package graph; source
builds stay explicit and builder/CI oriented.

Required input:
  --input-root <dir>       Directory containing usr/
  --input-prefix <dir>     Existing usr prefix directory to package as usr/

Options:
  --output <file>          Output .tar.zst path
  --install-tar-output <file>
                           Output uncompressed .tar used by Android installers
  --package-graph <file>   Package graph report to validate and embed
  --help                   Show this help

Environment:
  OPERATOR_ANDROID_RUNTIME_SDK_PROFILE=core
  OPERATOR_ANDROID_RUNTIME_SDK_VERSION=2026.05.07
  OPERATOR_ANDROID_APP_ID=com.illumination.operator
  ANDROID_ABI=arm64-v8a
  ANDROID_API=26
EOF
}

require_host_tool find
require_host_tool tar
require_host_tool zstd

input_root=""
input_prefix=""
output_path="$(runtime_sdk_artifact_path)"
install_tar_output=""
package_graph_file="${OPERATOR_ANDROID_RUNTIME_SDK_PACKAGE_GRAPH:-}"

while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --input-root)
      shift
      input_root="${1:-}"
      ;;
    --input-prefix)
      shift
      input_prefix="${1:-}"
      ;;
    --output)
      shift
      output_path="${1:-}"
      ;;
    --install-tar-output)
      shift
      install_tar_output="${1:-}"
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

if [[ -n "$input_root" && -n "$input_prefix" ]]; then
  echo "Use only one of --input-root or --input-prefix." >&2
  exit 2
fi
if [[ -z "$input_root" && -z "$input_prefix" ]]; then
  echo "Missing runtime SDK input. Use --input-root or --input-prefix." >&2
  usage >&2
  exit 2
fi
if [[ -n "$input_root" && ! -d "$input_root/usr" ]]; then
  echo "Input root must contain usr/: $input_root" >&2
  exit 1
fi
if [[ -n "$input_prefix" && ! -d "$input_prefix" ]]; then
  echo "Input prefix not found: $input_prefix" >&2
  exit 1
fi
if [[ -n "$package_graph_file" && ! -f "$package_graph_file" ]]; then
  echo "Package graph file not found: $package_graph_file" >&2
  exit 1
fi

work_dir="$(runtime_sdk_mktemp_dir build)"
image_root="$work_dir/image"
archive_tmp="$work_dir/$(basename "$output_path").tmp"
if [[ -z "$install_tar_output" && "$output_path" == *.tar.zst ]]; then
  install_tar_output="${output_path%.zst}"
fi
install_tar_tmp="$work_dir/$(basename "${install_tar_output:-runtime-sdk.tar}").tmp"
cleanup() {
  rm -rf "$work_dir"
}
trap cleanup EXIT HUP INT TERM

mkdir -p "$image_root"

if [[ -n "$input_root" ]]; then
  (
    cd "$input_root"
    tar -cf - .
  ) | (
    cd "$image_root"
    tar -xf -
  )
else
  mkdir -p "$image_root/usr"
  (
    cd "$input_prefix"
    tar -cf - .
  ) | (
    cd "$image_root/usr"
    tar -xf -
  )
fi

runtime_sdk_write_manifest_files "$image_root" "$package_graph_file"
runtime_sdk_validate_image "$image_root" "$package_graph_file"
runtime_sdk_write_sha256sums "$image_root"

mkdir -p "$(dirname "$output_path")"
if [[ -n "$install_tar_output" ]]; then
  mkdir -p "$(dirname "$install_tar_output")"
fi
(
  cd "$image_root"
  tar -cf - .
) > "$install_tar_tmp"
zstd -T0 -19 -q "$install_tar_tmp" -o "$archive_tmp"
mv "$archive_tmp" "$output_path"
if [[ -n "$install_tar_output" ]]; then
  mv "$install_tar_tmp" "$install_tar_output"
  runtime_sdk_sha256_file "$install_tar_output" > "$install_tar_output.sha256"
else
  rm -f "$install_tar_tmp"
fi
runtime_sdk_sha256_file "$output_path" > "$output_path.sha256"

printf 'Runtime SDK artifact written: %s\n' "$output_path"
printf 'SHA-256: %s\n' "$(cat "$output_path.sha256")"
if [[ -n "$install_tar_output" ]]; then
  printf 'Runtime SDK install tar written: %s\n' "$install_tar_output"
  printf 'Install tar SHA-256: %s\n' "$(cat "$install_tar_output.sha256")"
fi
