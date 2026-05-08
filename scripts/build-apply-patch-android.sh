#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT_DIR/scripts/android-runtime-common.sh"

LOCAL_PROPERTIES="$ROOT_DIR/mobile/android/local.properties"
ANDROID_ABI="${ANDROID_ABI:-arm64-v8a}"
ANDROID_TARGET="${ANDROID_TARGET:-aarch64-linux-android}"
ANDROID_API="${ANDROID_API:-26}"
BUILD_PROFILE="${BUILD_PROFILE:-release}"

if [[ -f "$LOCAL_PROPERTIES" ]]; then
  SDK_DIR="$(awk -F= '$1 == "sdk.dir" { print $2 }' "$LOCAL_PROPERTIES" | tail -n1)"
  CARGO_BIN="$(awk -F= '$1 == "cargo.path" { print $2 }' "$LOCAL_PROPERTIES" | tail -n1)"
else
  SDK_DIR=""
  CARGO_BIN=""
fi

SDK_DIR="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-${SDK_DIR:-}}}"
CARGO_BIN="${CARGO:-${CARGO_BIN:-cargo}}"

if [[ -z "$SDK_DIR" || ! -d "$SDK_DIR" ]]; then
  echo "Android SDK not found. Set sdk.dir in mobile/android/local.properties or ANDROID_HOME." >&2
  exit 1
fi

NDK_DIR="${ANDROID_NDK_HOME:-$SDK_DIR/ndk/30.0.14904198}"
TOOLCHAIN_DIR="$NDK_DIR/toolchains/llvm/prebuilt/darwin-x86_64"
LINKER="$TOOLCHAIN_DIR/bin/${ANDROID_TARGET}${ANDROID_API}-clang"
AR="$TOOLCHAIN_DIR/bin/llvm-ar"
STRIP="$TOOLCHAIN_DIR/bin/llvm-strip"

if [[ ! -x "$LINKER" ]]; then
  echo "Android linker not found: $LINKER" >&2
  exit 1
fi

if [[ ! -x "$AR" ]]; then
  echo "Android llvm-ar not found: $AR" >&2
  exit 1
fi

CODEX_REF="$(git -C "$ROOT_DIR/third_party/codex" rev-parse --short HEAD 2>/dev/null || echo local)"
OUT_DIR="$ROOT_DIR/local-artifacts/android-tools/apply_patch/codex-$CODEX_REF/$ANDROID_ABI"
FINAL_BIN="$OUT_DIR/apply_patch"
PROFILE_DIR="$BUILD_PROFILE"
if [[ "$BUILD_PROFILE" == "dev" ]]; then
  PROFILE_DIR="debug"
fi

mkdir -p "$OUT_DIR"

export "CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER=$LINKER"
export "CARGO_TARGET_AARCH64_LINUX_ANDROID_AR=$AR"
export "CC_aarch64_linux_android=$LINKER"
export "AR_aarch64_linux_android=$AR"

BUILD_ARGS=(
  build
  -p codex-apply-patch
  --bin apply_patch
  --target "$ANDROID_TARGET"
  --locked
)

if [[ "$BUILD_PROFILE" == "release" ]]; then
  BUILD_ARGS+=(--release)
fi

"$CARGO_BIN" "${BUILD_ARGS[@]}" \
  --manifest-path "$ROOT_DIR/third_party/codex/codex-rs/Cargo.toml"

stage_android_executable "$ROOT_DIR/third_party/codex/codex-rs/target/$ANDROID_TARGET/$PROFILE_DIR/apply_patch" "$FINAL_BIN" apply_patch

echo "$FINAL_BIN"
