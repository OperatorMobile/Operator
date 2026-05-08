#!/usr/bin/env bash
set -euo pipefail

V8_VERSION="${V8_VERSION:-146.4.0}"
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CARGO_HOME="${CARGO_HOME:-$HOME/.cargo}"
CARGO_BIN="${CARGO:-cargo}"
V8_SRC=""
V8_BUILD_SRC=""
HOST_OS="$(uname -s)"
HOST_ARCH="$(uname -m)"
DOCKER_PLATFORM="${DOCKER_PLATFORM:-linux/amd64}"
ALLOW_DOCKER_X86_EMULATION="${ALLOW_DOCKER_X86_EMULATION:-0}"
NATIVE_HOST_BUILD="${NATIVE_HOST_BUILD:-0}"
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-}"
ANDROID_NDK_ROOT="${ANDROID_NDK_ROOT:-}"
ANDROID_NDK_API_LEVEL="${ANDROID_NDK_API_LEVEL:-29}"
ANDROID_NDK_VERSION="${ANDROID_NDK_VERSION:-r30}"
ANDROID_SDK_PLATFORM_VERSION="${ANDROID_SDK_PLATFORM_VERSION:-36.1}"
ANDROID_SDK_BUILD_TOOLS_VERSION="${ANDROID_SDK_BUILD_TOOLS_VERSION:-36.1.0}"
OUT_DIR="$PROJECT_ROOT/local-artifacts/rusty-v8/v$V8_VERSION"
WORK_ROOT="$PROJECT_ROOT/tmp/rusty-v8-android-build/v$V8_VERSION"
INCLUDE_X86_64_ANDROID="${INCLUDE_X86_64_ANDROID:-0}"
V8_ANDROID_THIRD_PARTY_CACHE=""

if [[ "$NATIVE_HOST_BUILD" == "1" || "$NATIVE_HOST_BUILD" == "true" ]]; then
  WORK_ROOT="$PROJECT_ROOT/tmp/rusty-v8-android-build-native/v$V8_VERSION"
fi
V8_ANDROID_THIRD_PARTY_CACHE="$WORK_ROOT/android-third-party-cache"

if [[ "$NATIVE_HOST_BUILD" != "1" && "$NATIVE_HOST_BUILD" != "true" && ( "$HOST_OS" != "Linux" || "$HOST_ARCH" != "x86_64" ) ]]; then
  if [[ "$ALLOW_DOCKER_X86_EMULATION" != "1" && "$ALLOW_DOCKER_X86_EMULATION" != "true" ]]; then
    cat >&2 <<EOF
rusty_v8 Android source builds are expected to run on a Linux x86_64 builder.

This machine is $HOST_OS/$HOST_ARCH. Running this script here would use Docker's
linux/amd64 emulation to produce Android arm64-v8a output, which is slow and not
the preferred local path for Operator.

Use a real Linux x86_64 CI/builder and run this script there, or set:

  ALLOW_DOCKER_X86_EMULATION=1 scripts/build-rusty-v8-android-artifacts.sh

to force the local emulated builder anyway.
EOF
    exit 2
  fi
fi

local_property() {
  local key="$1"
  local file="$PROJECT_ROOT/mobile/android/local.properties"

  if [[ -f "$file" ]]; then
    sed -n "s/^$key=//p" "$file" | tail -n 1
  fi
}

configure_android_paths() {
  if [[ -z "$ANDROID_SDK_ROOT" ]]; then
    ANDROID_SDK_ROOT="$(local_property sdk.dir)"
  fi
  ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}"

  if [[ -z "$ANDROID_NDK_ROOT" && -d "$ANDROID_SDK_ROOT/ndk" ]]; then
    ANDROID_NDK_ROOT="$(find "$ANDROID_SDK_ROOT/ndk" -mindepth 1 -maxdepth 1 -type d | sort | tail -n 1)"
  fi

  if [[ "$NATIVE_HOST_BUILD" == "1" || "$NATIVE_HOST_BUILD" == "true" ]]; then
    if [[ ! -d "$ANDROID_SDK_ROOT" ]]; then
      echo "ANDROID_SDK_ROOT does not exist: $ANDROID_SDK_ROOT" >&2
      exit 1
    fi
    if [[ ! -d "$ANDROID_NDK_ROOT" ]]; then
      echo "ANDROID_NDK_ROOT does not exist: $ANDROID_NDK_ROOT" >&2
      exit 1
    fi

    local xcode_toolchain_usr="/Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr"
    if [[ -z "${CLANG_BASE_PATH:-}" && -x "$xcode_toolchain_usr/bin/clang" ]]; then
      export CLANG_BASE_PATH="$xcode_toolchain_usr"
    fi
    if [[ -z "${LIBCLANG_PATH:-}" && -f "$xcode_toolchain_usr/lib/libclang.dylib" ]]; then
      export LIBCLANG_PATH="$xcode_toolchain_usr/lib"
    fi
  fi
}

is_native_host_build() {
  [[ "$NATIVE_HOST_BUILD" == "1" || "$NATIVE_HOST_BUILD" == "true" ]]
}

android_gn_args() {
  if is_native_host_build; then
    printf 'android_ndk_root="%s" android_ndk_version="%s" android_ndk_api_level=%s android_sdk_root="%s" android_sdk_build_tools_version="%s" android_sdk_platform_version="%s"' \
      "$ANDROID_NDK_ROOT" \
      "$ANDROID_NDK_VERSION" \
      "$ANDROID_NDK_API_LEVEL" \
      "$ANDROID_SDK_ROOT" \
      "$ANDROID_SDK_BUILD_TOOLS_VERSION" \
      "$ANDROID_SDK_PLATFORM_VERSION"
  else
    printf 'android_ndk_root="//third_party/android_ndk" android_ndk_version="r26" android_ndk_api_level=%s' "$ANDROID_NDK_API_LEVEL"
  fi
}

android_toolchain_prebuilt_dir() {
  local host_tag=""

  if [[ "$HOST_OS" == "Darwin" ]]; then
    host_tag="darwin-x86_64"
  elif [[ "$HOST_OS" == "Linux" ]]; then
    host_tag="linux-x86_64"
  else
    echo "Unsupported native Android NDK host OS: $HOST_OS" >&2
    exit 1
  fi

  printf '%s/toolchains/llvm/prebuilt/%s' "$ANDROID_NDK_ROOT" "$host_tag"
}

export_native_android_toolchain_env() {
  local rust_target="$1"
  local rust_target_env="${rust_target//-/_}"
  local prebuilt_dir
  local clang_prefix

  rust_target_env="$(printf '%s' "$rust_target_env" | tr '[:lower:]' '[:upper:]')"

  prebuilt_dir="$(android_toolchain_prebuilt_dir)"
  if [[ ! -d "$prebuilt_dir" ]]; then
    echo "Android NDK prebuilt toolchain does not exist: $prebuilt_dir" >&2
    exit 1
  fi

  case "$rust_target" in
    aarch64-linux-android)
      clang_prefix="aarch64-linux-android"
      ;;
    x86_64-linux-android)
      clang_prefix="x86_64-linux-android"
      ;;
    *)
      echo "Unsupported Android Rust target: $rust_target" >&2
      exit 1
      ;;
  esac

  export "CC_${rust_target//-/_}=$prebuilt_dir/bin/$clang_prefix$ANDROID_NDK_API_LEVEL-clang"
  export "CXX_${rust_target//-/_}=$prebuilt_dir/bin/$clang_prefix$ANDROID_NDK_API_LEVEL-clang++"
  export "AR_${rust_target//-/_}=$prebuilt_dir/bin/llvm-ar"
  export "CARGO_TARGET_${rust_target_env}_LINKER=$prebuilt_dir/bin/$clang_prefix$ANDROID_NDK_API_LEVEL-clang"
}

find_v8_src() {
  V8_SRC=""
  for candidate in "$CARGO_HOME"/registry/src/*/"v8-$V8_VERSION"; do
    if [[ -d "$candidate" ]]; then
      V8_SRC="$candidate"
      break
    fi
  done
}

write_v8_dependency_pins() {
  cat <<EOF
v8 = "=$V8_VERSION"
icu_calendar = "=2.1.0"
icu_locale = "=2.1.0"
icu_locale_core = "=2.1.0"
EOF
}

write_local_v8_dependency_pins() {
  cat <<EOF
v8 = { path = "v8-src-patched" }
icu_calendar = "=2.1.0"
icu_locale = "=2.1.0"
icu_locale_core = "=2.1.0"
EOF
}

find_fetched_crate() {
  local crate_name="$1"
  local crate_version="$2"

  for candidate in "$CARGO_HOME"/registry/src/*/"$crate_name-$crate_version"; do
    if [[ -d "$candidate" ]]; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done

  return 1
}

fetch_single_chromium_crate() {
  local vendor_name="$1"
  local crate_name="$2"
  local crate_version="$3"
  local fetch_dir="$WORK_ROOT/chromium-rust-vendor-fetch/$vendor_name"
  local alias_name

  alias_name="$(printf '%s' "$vendor_name" | tr -c '[:alnum:]_' '_')"
  if [[ "$alias_name" =~ ^[0-9] ]]; then
    alias_name="_$alias_name"
  fi

  rm -rf "$fetch_dir"
  mkdir -p "$fetch_dir/src"
  cat > "$fetch_dir/Cargo.toml" <<EOF
[package]
name = "operator-fetch-$alias_name"
version = "0.1.0"
edition = "2021"

[dependencies]
$alias_name = { package = "$crate_name", version = "=$crate_version" }
EOF
  printf 'fn main() {}\n' > "$fetch_dir/src/main.rs"
  "$CARGO_BIN" fetch --manifest-path "$fetch_dir/Cargo.toml"
}

prune_missing_chromium_vendor_entries() {
  find "$V8_BUILD_SRC/third_party/rust" -name BUILD.gn -print0 \
    | xargs -0 env "V8_BUILD_SRC=$V8_BUILD_SRC" perl -0pi -e 's{^\s*"//third_party/rust/chromium_crates_io/vendor/([^"]+)",\n}{-e "$ENV{V8_BUILD_SRC}/third_party/rust/chromium_crates_io/vendor/$1" ? $& : ""}gme'
}

vendor_chromium_rust_crates() {
  local vendor_list="$WORK_ROOT/chromium-rust-vendor.tsv"
  local vendor_dir="$V8_BUILD_SRC/third_party/rust/chromium_crates_io/vendor"

  find "$V8_BUILD_SRC/third_party/rust" -name BUILD.gn -print0 \
    | xargs -0 perl -0ne 'my ($vendor)=/vendor\/([^\/"\s]+)/s; my ($name)=/cargo_pkg_name = "([^"]+)"/s; my ($version)=/cargo_pkg_version = "([^"]+)"/s; if ($vendor && $name && $version) { print "$vendor\t$name\t$version\n" }' \
    | sort -u > "$vendor_list"

  if [[ ! -s "$vendor_list" ]]; then
    echo "Could not discover Chromium Rust vendor crate metadata in patched v8 source." >&2
    exit 1
  fi

  rm -rf "$V8_BUILD_SRC/third_party/rust/chromium_crates_io"
  mkdir -p "$vendor_dir"
  while IFS=$'\t' read -r vendor_name crate_name crate_version; do
    local source_dir
    source_dir="$(find_fetched_crate "$crate_name" "$crate_version" || true)"

    if [[ -z "$source_dir" ]]; then
      fetch_single_chromium_crate "$vendor_name" "$crate_name" "$crate_version"
      source_dir="$(find_fetched_crate "$crate_name" "$crate_version" || true)"
      if [[ -z "$source_dir" ]]; then
        echo "Could not find fetched crate $crate_name-$crate_version in $CARGO_HOME/registry/src." >&2
        exit 1
      fi
    fi

    cp -R "$source_dir" "$vendor_dir/$vendor_name"
  done < "$vendor_list"

  prune_missing_chromium_vendor_entries
}

restore_v8_android_third_party_cache() {
  local build_src="$1"
  local third_party_dir="$build_src/third_party"

  mkdir -p "$third_party_dir"
  for dependency in android_ndk android_platform catapult; do
    if [[ -d "$V8_ANDROID_THIRD_PARTY_CACHE/$dependency" && ! -e "$third_party_dir/$dependency" ]]; then
      cp -Rp "$V8_ANDROID_THIRD_PARTY_CACHE/$dependency" "$third_party_dir/$dependency"
    fi
  done
}

save_v8_android_third_party_cache() {
  local build_src="$1"
  local third_party_dir="$build_src/third_party"

  mkdir -p "$V8_ANDROID_THIRD_PARTY_CACHE"
  for dependency in android_ndk android_platform catapult; do
    if [[ -d "$third_party_dir/$dependency" ]]; then
      rm -rf "$V8_ANDROID_THIRD_PARTY_CACHE/$dependency"
      cp -Rp "$third_party_dir/$dependency" "$V8_ANDROID_THIRD_PARTY_CACHE/$dependency"
    fi
  done
}

prepare_v8_build_src() {
  V8_BUILD_SRC="$WORK_ROOT/v8-src-patched"
  rm -rf "$V8_BUILD_SRC"
  mkdir -p "$V8_BUILD_SRC"
  cp -Rp "$V8_SRC"/. "$V8_BUILD_SRC"/

  mkdir -p "$V8_BUILD_SRC/build/rust"
  cat > "$V8_BUILD_SRC/build/rust/known-target-triples.txt" <<'EOF'
aarch64-linux-android
x86_64-linux-android
x86_64-unknown-linux-gnu
aarch64-unknown-linux-gnu
EOF

  # The published v8 crate source omits Android test-runner metadata that is
  # irrelevant to the rusty_v8 static library. Keep GN's JSON project generation
  # scoped to the library/bindgen headers and disable that test-only data edge.
  perl -0pi -e 's/\.arg\("--ide=json"\)/.arg("--ide=json")\n      .arg("--filters=\/\/:rusty_v8;\/\/v8:v8_headers")/' "$V8_BUILD_SRC/build.rs"
  perl -0pi -e 's/if \(is_android && !build_with_chromium\) \{/if (false) {/' "$V8_BUILD_SRC/v8/tools/BUILD.gn"
  perl -0pi -e 's/if !Path::new\("\.\/third_party\/android_ndk\/toolchains\/llvm\/prebuilt\/linux-x86_64\/bin\/aarch64-linux-android24-clang\+\+"\)\.exists\(\) \{/if env::var_os("OPERATOR_SKIP_ANDROID_NDK_DOWNLOAD").is_none() \&\& !Path::new(".\/third_party\/android_ndk\/toolchains\/llvm\/prebuilt\/linux-x86_64\/bin\/aarch64-linux-android24-clang++").exists() {/' "$V8_BUILD_SRC/build.rs"
  perl -0pi -e 's#(let target_os = env::var\("CARGO_CFG_TARGET_OS"\)\.unwrap\(\);\n)#$1  if target_os == "android" {\n    let android_target_arch = env::var("CARGO_CFG_TARGET_ARCH").unwrap();\n    let android_target = if android_target_arch == "x86_64" {\n      "x86_64-linux-android"\n    } else {\n      "aarch64-linux-android"\n    };\n    let android_sysroot = "third_party/android_ndk/toolchains/llvm/prebuilt/linux-x86_64/sysroot";\n    clang_args.push(format!("--target={}", android_target));\n    clang_args.push(format!("--sysroot={}", android_sysroot));\n    clang_args.push(format!("-isystem{}/usr/include", android_sysroot));\n    clang_args.push(format!("-isystem{}/usr/include/{}", android_sysroot, android_target));\n  }\n#' "$V8_BUILD_SRC/build.rs"
  perl -0pi -e 's/apt install -y curl/apt install -y curl libclang-19-dev/' "$V8_BUILD_SRC/Dockerfile"
  cat >> "$V8_BUILD_SRC/Dockerfile" <<'EOF'

ENV LIBCLANG_PATH=/usr/lib/llvm-19/lib
EOF

  if ! grep -q -- '--filters=//:rusty_v8;//v8:v8_headers' "$V8_BUILD_SRC/build.rs"; then
    echo "Failed to scope GN JSON generation in patched v8 build.rs." >&2
    exit 1
  fi
  if grep -q 'if (is_android && !build_with_chromium)' "$V8_BUILD_SRC/v8/tools/BUILD.gn"; then
    echo "Failed to disable Android test-runner data deps in patched v8 tools BUILD.gn." >&2
    exit 1
  fi
  if ! grep -q 'OPERATOR_SKIP_ANDROID_NDK_DOWNLOAD' "$V8_BUILD_SRC/build.rs"; then
    echo "Failed to add native Android NDK download skip in patched v8 build.rs." >&2
    exit 1
  fi
  if ! grep -q 'android_sysroot' "$V8_BUILD_SRC/build.rs"; then
    echo "Failed to add Android NDK sysroot paths for bindgen." >&2
    exit 1
  fi
  if ! grep -q 'CARGO_CFG_TARGET_ARCH' "$V8_BUILD_SRC/build.rs"; then
    echo "Failed to make bindgen target selection ABI-aware." >&2
    exit 1
  fi
  if ! grep -q 'libclang-19-dev' "$V8_BUILD_SRC/Dockerfile"; then
    echo "Failed to add libclang-19-dev to the rusty_v8 Docker builder." >&2
    exit 1
  fi

  vendor_chromium_rust_crates
}

mkdir -p "$OUT_DIR" "$WORK_ROOT"
configure_android_paths
find_v8_src

if [[ -z "$V8_SRC" ]]; then
  fetch_dir="$WORK_ROOT/fetch-v8-crate"
  mkdir -p "$fetch_dir/src"
  cat > "$fetch_dir/Cargo.toml" <<EOF
[package]
name = "operator-fetch-v8"
version = "0.1.0"
edition = "2021"

[dependencies]
$(write_v8_dependency_pins)
EOF
  printf 'fn main() {}\n' > "$fetch_dir/src/main.rs"
  "$CARGO_BIN" fetch --manifest-path "$fetch_dir/Cargo.toml"
  find_v8_src
fi

if [[ -z "$V8_SRC" ]]; then
  echo "Could not find v8-$V8_VERSION in $CARGO_HOME/registry/src after cargo fetch." >&2
  exit 1
fi

prepare_v8_build_src

build_target() {
  local rust_target="$1"
  local abi="$2"
  local cross_base="$3"
  local image="operator-rusty-v8:$rust_target-v$V8_VERSION"
  local work_dir="$WORK_ROOT/$rust_target"
  local extra_gn_args="${EXTRA_GN_ARGS:-}"
  local gn_args="${GN_ARGS:-}"
  local cargo_jobs="${CARGO_BUILD_JOBS:-4}"

  if [[ " $extra_gn_args " != *" use_sysroot="* ]]; then
    extra_gn_args="${extra_gn_args:+$extra_gn_args }use_sysroot=false"
  fi
  gn_args="${gn_args:+$gn_args }$(android_gn_args)"

  mkdir -p "$work_dir"
  rm -rf "$work_dir/v8-src-patched"
  cp -Rp "$V8_BUILD_SRC" "$work_dir/v8-src-patched"
  restore_v8_android_third_party_cache "$work_dir/v8-src-patched"

  cat > "$work_dir/Cargo.toml" <<EOF
[package]
name = "operator-rusty-v8-artifact-$abi"
version = "0.1.0"
edition = "2021"

[lib]
name = "operator_rusty_v8_artifact"
path = "src/lib.rs"

[dependencies]
$(write_local_v8_dependency_pins)
EOF
  rm -rf "$work_dir/src"
  mkdir -p "$work_dir/src"
  printf 'pub fn operator_rusty_v8_artifact_probe() {}\n' > "$work_dir/src/lib.rs"

  if is_native_host_build; then
    export_native_android_toolchain_env "$rust_target"
    (
      cd "$work_dir"
      env \
        V8_FROM_SOURCE=1 \
        CARGO_BUILD_JOBS="$cargo_jobs" \
        EXTRA_GN_ARGS="$extra_gn_args" \
        GN_ARGS="$gn_args" \
        OPERATOR_SKIP_ANDROID_NDK_DOWNLOAD=1 \
        ANDROID_HOME="$ANDROID_SDK_ROOT" \
        ANDROID_SDK_ROOT="$ANDROID_SDK_ROOT" \
        ANDROID_NDK_HOME="$ANDROID_NDK_ROOT" \
        ANDROID_NDK_ROOT="$ANDROID_NDK_ROOT" \
        "$CARGO_BIN" build --release --target "$rust_target" -j "$cargo_jobs"
    )
  else
    docker build \
      --platform "$DOCKER_PLATFORM" \
      --build-arg "CROSS_BASE_IMAGE=$cross_base" \
      -t "$image" \
      "$V8_BUILD_SRC"

    if ! docker run --rm \
      --platform "$DOCKER_PLATFORM" \
      -e V8_FROM_SOURCE=1 \
      -e "CARGO_BUILD_JOBS=$cargo_jobs" \
      -e "EXTRA_GN_ARGS=$extra_gn_args" \
      -e "GN_ARGS=$gn_args" \
      -e SCCACHE_DIR=/workspace/target/sccache \
      -v "$work_dir:/workspace" \
      -w /workspace \
      "$image" \
      bash -lc "export PATH=/usr/local/cargo/bin:\$PATH && rustup target add $rust_target >/dev/null && cargo build --release --target $rust_target -j $cargo_jobs"; then
      save_v8_android_third_party_cache "$work_dir/v8-src-patched"
      return 1
    fi
    save_v8_android_third_party_cache "$work_dir/v8-src-patched"
  fi

  local target_dir="$work_dir/target/$rust_target/release/gn_out"
  local archive_name="librusty_v8_release_$rust_target.a"
  local binding_name="src_binding_release_$rust_target.rs"

  cp "$target_dir/obj/librusty_v8.a" "$OUT_DIR/$archive_name"
  cp "$target_dir/src_binding.rs" "$OUT_DIR/$binding_name"

  echo "$abi archive: $OUT_DIR/$archive_name"
  echo "$abi binding: $OUT_DIR/$binding_name"
}

build_target "aarch64-linux-android" "arm64-v8a" "rust:1.93.0-bookworm"
if [[ "$INCLUDE_X86_64_ANDROID" == "1" || "$INCLUDE_X86_64_ANDROID" == "true" ]]; then
  build_target "x86_64-linux-android" "x86_64" "rust:1.93.0-bookworm"
fi

cat <<EOF

Add these lines to mobile/android/local.properties:

native.abis=arm64-v8a
rustyV8Archive.arm64-v8a=$OUT_DIR/librusty_v8_release_aarch64-linux-android.a
rustyV8Binding.arm64-v8a=$OUT_DIR/src_binding_release_aarch64-linux-android.rs
EOF

if [[ "$INCLUDE_X86_64_ANDROID" == "1" || "$INCLUDE_X86_64_ANDROID" == "true" ]]; then
  cat <<EOF
native.abis=arm64-v8a,x86_64
rustyV8Archive.x86_64=$OUT_DIR/librusty_v8_release_x86_64-linux-android.a
rustyV8Binding.x86_64=$OUT_DIR/src_binding_release_x86_64-linux-android.rs
EOF
fi
