#!/usr/bin/env bash

if [[ -n "${OPERATOR_ANDROID_RUNTIME_COMMON_SOURCED:-}" ]]; then
  return 0
fi
OPERATOR_ANDROID_RUNTIME_COMMON_SOURCED=1

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_PROJECT="$ROOT_DIR/mobile/android"
LOCAL_PROPERTIES="$ANDROID_PROJECT/local.properties"
ANDROID_RUNTIME_LOCK="$ROOT_DIR/third_party/android-runtime-sources/android-runtime-sources.tsv"
ANDROID_RUNTIME_SOURCE_ROOT="$ROOT_DIR/third_party/android-runtime-sources/sources"
ANDROID_API="${ANDROID_API:-26}"
ANDROID_ABI="${ANDROID_ABI:-arm64-v8a}"
JOBS="${JOBS:-4}"

read_local_property() {
  local key="$1"

  if [[ -f "$LOCAL_PROPERTIES" ]]; then
    awk -F= -v key="$key" '$1 == key { print substr($0, length(key) + 2) }' "$LOCAL_PROPERTIES" | tail -n 1
  fi
}

upsert_local_property() {
  local key="$1"
  local value="$2"
  local tmp_file

  touch "$LOCAL_PROPERTIES"
  tmp_file="$(mktemp)"
  awk -F= -v key="$key" -v value="$value" '
    BEGIN { written = 0 }
    $1 == key {
      print key "=" value
      written = 1
      next
    }
    { print }
    END {
      if (!written) {
        print key "=" value
      }
    }
  ' "$LOCAL_PROPERTIES" > "$tmp_file"
  mv "$tmp_file" "$LOCAL_PROPERTIES"
}

remove_local_property() {
  local key="$1"
  local tmp_file

  if [[ ! -f "$LOCAL_PROPERTIES" ]]; then
    return
  fi

  tmp_file="$(mktemp)"
  awk -F= -v key="$key" '$1 != key { print }' "$LOCAL_PROPERTIES" > "$tmp_file"
  mv "$tmp_file" "$LOCAL_PROPERTIES"
}

stage_android_executable() {
  local source="$1"
  local destination="$2"

  if [[ ! -x "$source" ]]; then
    echo "Expected executable not found: $source" >&2
    exit 1
  fi

  mkdir -p "$(dirname "$destination")"
  cp "$source" "$destination"
  if [[ -n "${STRIP:-}" && -x "$STRIP" ]]; then
    "$STRIP" "$destination" || true
  fi
  chmod 0755 "$destination"
}

android_source_field() {
  local name="$1"
  local field="$2"

  awk -F '\t' -v name="$name" -v field="$field" '
    BEGIN {
      fields["repo"] = 2
      fields["ref"] = 3
      fields["commit"] = 4
      fields["license"] = 5
      fields["purpose"] = 6
    }
    $0 !~ /^#/ && $1 == name {
      print $fields[field]
      found = 1
      exit
    }
    END {
      if (!found) {
        exit 1
      }
    }
  ' "$ANDROID_RUNTIME_LOCK"
}

android_source_dir() {
  printf '%s/%s\n' "$ANDROID_RUNTIME_SOURCE_ROOT" "$1"
}

sync_android_runtime_source() {
  local name="$1"
  local repo
  local ref
  local commit
  local directory

  repo="$(android_source_field "$name" repo)"
  ref="$(android_source_field "$name" ref)"
  commit="$(android_source_field "$name" commit)"
  directory="$(android_source_dir "$name")"

  mkdir -p "$ANDROID_RUNTIME_SOURCE_ROOT"
  if [[ ! -d "$directory/.git" ]]; then
    git clone --filter=blob:none "$repo" "$directory"
  fi

  git -C "$directory" fetch --depth 1 origin "$ref"
  git -C "$directory" checkout --detach "$commit"

  actual_commit="$(git -C "$directory" rev-parse HEAD)"
  if [[ "$actual_commit" != "$commit" ]]; then
    echo "Unexpected $name checkout: $actual_commit, expected $commit" >&2
    exit 1
  fi
}

require_host_tool() {
  local tool="$1"

  if ! command -v "$tool" >/dev/null 2>&1; then
    echo "Required host tool not found on PATH: $tool" >&2
    exit 1
  fi
}

resolve_android_sdk_dir() {
  local sdk_dir

  sdk_dir="$(read_local_property sdk.dir)"
  sdk_dir="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-${sdk_dir:-}}}"
  if [[ -z "$sdk_dir" || ! -d "$sdk_dir" ]]; then
    echo "Android SDK not found. Set sdk.dir in mobile/android/local.properties or ANDROID_HOME." >&2
    exit 1
  fi
  printf '%s\n' "$sdk_dir"
}

configure_android_abi() {
  case "$ANDROID_ABI" in
    arm64-v8a)
      ANDROID_TARGET="${ANDROID_TARGET:-aarch64-linux-android}"
      ANDROID_CPU="arm64"
      OPENSSL_ANDROID_TARGET="android-arm64"
      GNU_HOST="$ANDROID_TARGET"
      ;;
    x86_64)
      ANDROID_TARGET="${ANDROID_TARGET:-x86_64-linux-android}"
      ANDROID_CPU="x64"
      OPENSSL_ANDROID_TARGET="android-x86_64"
      GNU_HOST="$ANDROID_TARGET"
      ;;
    *)
      echo "Unsupported Android ABI: $ANDROID_ABI" >&2
      exit 1
      ;;
  esac
}

configure_android_toolchain() {
  configure_android_abi

  SDK_DIR="$(resolve_android_sdk_dir)"
  NDK_DIR="${ANDROID_NDK_HOME:-$SDK_DIR/ndk/30.0.14904198}"
  TOOLCHAIN_DIR="$NDK_DIR/toolchains/llvm/prebuilt/darwin-x86_64"
  if [[ ! -d "$TOOLCHAIN_DIR" ]]; then
    TOOLCHAIN_DIR="$NDK_DIR/toolchains/llvm/prebuilt/linux-x86_64"
  fi
  if [[ ! -d "$TOOLCHAIN_DIR" ]]; then
    echo "Android NDK LLVM toolchain not found under $NDK_DIR" >&2
    exit 1
  fi

  export PATH="$TOOLCHAIN_DIR/bin:$PATH"
  export CC="$TOOLCHAIN_DIR/bin/${ANDROID_TARGET}${ANDROID_API}-clang"
  export CXX="$TOOLCHAIN_DIR/bin/${ANDROID_TARGET}${ANDROID_API}-clang++"
  export AR="$TOOLCHAIN_DIR/bin/llvm-ar"
  export RANLIB="$TOOLCHAIN_DIR/bin/llvm-ranlib"
  export STRIP="$TOOLCHAIN_DIR/bin/llvm-strip"
  export LD="$TOOLCHAIN_DIR/bin/ld.lld"

  for tool in "$CC" "$CXX" "$AR" "$RANLIB"; do
    if [[ ! -x "$tool" ]]; then
      echo "Required Android toolchain executable not found: $tool" >&2
      exit 1
    fi
  done
}

build_parent_dir() {
  local base="${TMPDIR:-/private/tmp}"
  if ! mkdir -p "$base" 2>/dev/null; then
    if mkdir -p /tmp 2>/dev/null; then
      base="/tmp"
    else
      base="$ROOT_DIR/tmp"
      mkdir -p "$base"
    fi
  fi

  local dir="$base/operator-android-runtime-build"
  mkdir -p "$dir"
  printf '%s\n' "$dir"
}
