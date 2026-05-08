#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/android-runtime-common.sh"

configure_android_toolchain
sync_android_runtime_source ripgrep

RIPGREP_VERSION="${RIPGREP_VERSION:-$(android_source_field ripgrep ref | awk -F/ '{ print $NF }')}"
RIPGREP_SOURCE_DIR="$(android_source_dir ripgrep)"
CARGO_BIN="${CARGO:-${CARGO_BIN:-$(read_local_property cargo.path)}}"
CARGO_BIN="${CARGO_BIN:-cargo}"
CARGO_TARGET_ENV="$(printf '%s' "$ANDROID_TARGET" | tr '[:lower:]-' '[:upper:]_')"

OUT_DIR="$ROOT_DIR/local-artifacts/android-tools/ripgrep/v$RIPGREP_VERSION/$ANDROID_ABI"
FINAL_BIN="$OUT_DIR/rg"
PROFILE_DIR="release"

mkdir -p "$OUT_DIR"

export "CARGO_TARGET_${CARGO_TARGET_ENV}_LINKER=$CC"
export "CARGO_TARGET_${CARGO_TARGET_ENV}_AR=$AR"
export "CC_${ANDROID_TARGET//-/_}=$CC"
export "AR_${ANDROID_TARGET//-/_}=$AR"

"$CARGO_BIN" build \
  --manifest-path "$RIPGREP_SOURCE_DIR/Cargo.toml" \
  --bin rg \
  --target "$ANDROID_TARGET" \
  --locked \
  --release

stage_android_executable "$RIPGREP_SOURCE_DIR/target/$ANDROID_TARGET/$PROFILE_DIR/rg" "$FINAL_BIN" rg

echo "$FINAL_BIN"
