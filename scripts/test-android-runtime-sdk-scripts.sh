#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/android-runtime-sdk-common.sh"

make_synthetic_image() {
  local image_root="$1"
  local kind
  local name
  local relative_path
  local mode
  local notes
  local target_path

  while IFS=$'\t' read -r kind name relative_path mode notes; do
    case "$kind" in
      ""|\#*) continue ;;
    esac
    target_path="$image_root/$relative_path"
    case "$mode" in
      dir)
        mkdir -p "$target_path"
        ;;
      executable)
        mkdir -p "$(dirname "$target_path")"
        printf '#!/system/bin/sh\nexit 0\n' > "$target_path"
        chmod 0755 "$target_path"
        ;;
      file)
        mkdir -p "$(dirname "$target_path")"
        : > "$target_path"
        ;;
    esac
  done < "$(runtime_sdk_profile_file required-paths.tsv)"
}

expect_failure() {
  local description="$1"
  shift

  if "$@" >/dev/null 2>&1; then
    echo "Expected failure did not occur: $description" >&2
    exit 1
  fi
}

require_host_tool tar
require_host_tool zstd

work_dir="$(runtime_sdk_mktemp_dir test)"
cleanup() {
  rm -rf "$work_dir"
}
trap cleanup EXIT HUP INT TERM

valid_root="$work_dir/valid-image"
archive="$work_dir/operator-runtime-sdk-smoke.tar.zst"
stage_root="$work_dir/staged"
host_path_root="$work_dir/host-path-image"
forbidden_graph="$work_dir/forbidden-package-graph.txt"

mkdir -p "$valid_root" "$host_path_root" "$stage_root"
make_synthetic_image "$valid_root"

scripts/validate-android-runtime-sdk.sh "$valid_root" >/dev/null
scripts/build-android-runtime-sdk.sh --input-root "$valid_root" --output "$archive" >/dev/null
scripts/stage-android-runtime-sdk.sh \
  --archive "$archive" \
  --sha256 "$(cat "$archive.sha256")" \
  --output-dir "$stage_root/sdk" \
  --no-local-properties >/dev/null
scripts/validate-android-runtime-sdk.sh "$stage_root/sdk" >/dev/null

make_synthetic_image "$host_path_root"
printf '/Users/builder/Android/sdk/toolchains/darwin-x86_64/bin/clang\n' > "$host_path_root/usr/lib/pkgconfig/bad.pc"
expect_failure "host path metadata rejection" scripts/validate-android-runtime-sdk.sh "$host_path_root"

printf 'busybox tk openssl\n' > "$forbidden_graph"
expect_failure \
  "forbidden package graph rejection" \
  scripts/build-android-runtime-sdk.sh \
  --input-root "$valid_root" \
  --output "$work_dir/forbidden.tar.zst" \
  --package-graph "$forbidden_graph"

printf 'Android runtime SDK script tests passed.\n'
