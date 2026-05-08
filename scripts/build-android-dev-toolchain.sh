#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

ANDROID_ABI="${ANDROID_ABI:-arm64-v8a}"
export ANDROID_ABI

BUILD_BASE_TOOLS="${BUILD_BASE_TOOLS:-true}"
BUILD_BUSYBOX="${BUILD_BUSYBOX:-true}"
BUILD_OPENSSH="${BUILD_OPENSSH:-true}"
BUILD_GH="${BUILD_GH:-true}"
BUILD_GNU_MAKE="${BUILD_GNU_MAKE:-false}"
BUILD_CPYTHON="${BUILD_CPYTHON:-false}"
BUILD_PYTHON_DEV_LIBS="${BUILD_PYTHON_DEV_LIBS:-$BUILD_CPYTHON}"
BUILD_PYTHON_WHEELHOUSE="${BUILD_PYTHON_WHEELHOUSE:-false}"
BUILD_NODE="${BUILD_NODE:-false}"
BUILD_ANDROID_TOOLCHAIN="${BUILD_ANDROID_TOOLCHAIN:-false}"
BUILD_ANDROID_RUST_TOOLCHAIN="${BUILD_ANDROID_RUST_TOOLCHAIN:-true}"
STAGE_ANDROID_TOOLCHAIN="${STAGE_ANDROID_TOOLCHAIN:-$BUILD_ANDROID_TOOLCHAIN}"
BUILD_APK="${BUILD_APK:-true}"

run_step() {
  local label="$1"
  shift
  printf '\n==> %s\n' "$label"
  "$@"
}

if [[ "$BUILD_BASE_TOOLS" == "true" ]]; then
  run_step "Build ripgrep" "$ROOT_DIR/scripts/build-ripgrep-android.sh"
  run_step "Build apply_patch" "$ROOT_DIR/scripts/build-apply-patch-android.sh"
  run_step "Build Git and HTTPS remote" "$ROOT_DIR/scripts/build-git-android.sh"
fi

if [[ "$BUILD_BUSYBOX" == "true" ]]; then
  run_step "Build BusyBox" "$ROOT_DIR/scripts/build-busybox-android.sh"
fi

if [[ "$BUILD_OPENSSH" == "true" ]]; then
  run_step "Build OpenSSH client tools" "$ROOT_DIR/scripts/build-openssh-android.sh"
fi

if [[ "$BUILD_GH" == "true" ]]; then
  run_step "Build GitHub CLI" "$ROOT_DIR/scripts/build-gh-android.sh"
fi

if [[ "$BUILD_GNU_MAKE" == "true" ]]; then
  run_step "Build GNU make" "$ROOT_DIR/scripts/build-gnu-make-android.sh"
fi

if [[ "$BUILD_CPYTHON" == "true" ]]; then
  run_step "Build CPython" "$ROOT_DIR/scripts/build-cpython-android.sh"
fi

if [[ "$BUILD_PYTHON_DEV_LIBS" == "true" ]]; then
  run_step "Stage Python build headers and libraries" "$ROOT_DIR/scripts/stage-python-dev-libs-android.sh"
fi

if [[ "$BUILD_PYTHON_WHEELHOUSE" == "true" ]]; then
  run_step "Build Python wheelhouse" "$ROOT_DIR/scripts/build-python-wheelhouse-android.sh"
fi

if [[ "$BUILD_NODE" == "true" ]]; then
  run_step "Build Node.js and npm/npx launchers" "$ROOT_DIR/scripts/build-node-android.sh"
fi

if [[ "$BUILD_ANDROID_TOOLCHAIN" == "true" ]]; then
  export ANDROID_TOOLCHAIN_INCLUDE_RUST="$BUILD_ANDROID_RUST_TOOLCHAIN"
  run_step "Build Android C/C++ and Rust toolchain packages" "$ROOT_DIR/scripts/build-android-toolchain.sh"
fi

if [[ "$STAGE_ANDROID_TOOLCHAIN" == "true" ]]; then
  export ANDROID_TOOLCHAIN_REQUIRE_RUST="$BUILD_ANDROID_RUST_TOOLCHAIN"
  run_step "Stage Android C/C++ and Rust toolchain" "$ROOT_DIR/scripts/stage-android-toolchain.sh"
fi

if [[ "$BUILD_APK" == "true" ]]; then
  run_step "Assemble full Android debug APK" "$ROOT_DIR/mobile/android/gradlew" -p "$ROOT_DIR/mobile/android" :app:assembleFullDebug
fi

printf '\nAndroid development toolchain build complete for %s\n' "$ANDROID_ABI"
printf 'Current package inputs are recorded in %s\n' "$ROOT_DIR/mobile/android/local.properties"
