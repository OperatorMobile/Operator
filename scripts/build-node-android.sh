#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/android-runtime-common.sh"

configure_android_toolchain
require_host_tool make
require_host_tool patch
require_host_tool python3

sync_android_runtime_source node

SOURCE_DIR="$(android_source_dir node)"
NODE_PATCH_DIR="$ROOT_DIR/scripts/android-node-patches"
NODE_VERSION="$(android_source_field node ref | awk -F/ '{ print $NF }')"
BUILD_ROOT="$(build_parent_dir)"
BUILD_DIR="$BUILD_ROOT/node-$NODE_VERSION-$ANDROID_ABI"
OUT_DIR="$ROOT_DIR/local-artifacts/android-tools/node/$NODE_VERSION/$ANDROID_ABI"
RUNTIME_HOME="$OUT_DIR/node-home"
FINAL_NODE="$OUT_DIR/node"
LAUNCHER_C="$ROOT_DIR/scripts/android-node-tool-launcher.c"
NPM_LAUNCHER="$BUILD_ROOT/operator-node-npm-launcher"
NPX_LAUNCHER="$BUILD_ROOT/operator-node-npx-launcher"

node_host_os() {
  case "$(uname -s)" in
    Darwin) printf 'darwin\n' ;;
    Linux) printf 'linux\n' ;;
    *)
      echo "Unsupported Node Android build host: $(uname -s)" >&2
      exit 1
      ;;
  esac
}

apply_node_android_patches() {
  local patch_file

  for patch_file in "$NODE_PATCH_DIR"/*.patch; do
    patch -d "$BUILD_DIR" -p1 < "$patch_file"
  done
}

mkdir -p "$OUT_DIR"
rm -rf "$BUILD_DIR" "$RUNTIME_HOME"
mkdir -p "$BUILD_DIR"
cp -R "$SOURCE_DIR"/. "$BUILD_DIR"/
apply_node_android_patches

(
  cd "$BUILD_DIR"
  export CC_host="${CC_host:-cc}"
  export CXX_host="${CXX_host:-c++}"
  export AR_host="${AR_host:-ar}"
  export CC_target="$CC"
  export CXX_target="$CXX"
  export AR_target="$AR"
  export LINK_target="$CXX"
  export GYP_DEFINES="target_arch=$ANDROID_CPU v8_target_arch=$ANDROID_CPU android_target_arch=$ANDROID_CPU host_os=$(node_host_os) OS=android android_ndk_path=$NDK_DIR"

  ./configure \
    --cross-compiling \
    --dest-cpu="$ANDROID_CPU" \
    --dest-os=android \
    --openssl-no-asm \
    --prefix=/usr/local \
    --without-intl

  make -C out BUILDTYPE=Release V=0 -j"$JOBS" node
  python3 tools/install.py install --dest-dir "$BUILD_DIR/install-root" --prefix /usr/local
)

stage_android_executable "$BUILD_DIR/out/Release/node" "$FINAL_NODE" node

mkdir -p "$RUNTIME_HOME"
cp -R "$BUILD_DIR/install-root/usr/local"/. "$RUNTIME_HOME"/

"$CC" -fPIE -pie -O2 "$LAUNCHER_C" -o "$NPM_LAUNCHER"
"$CC" -fPIE -pie -O2 "$LAUNCHER_C" -o "$NPX_LAUNCHER"
stage_android_executable "$NPM_LAUNCHER" "$OUT_DIR/npm" npm
stage_android_executable "$NPX_LAUNCHER" "$OUT_DIR/npx" npx

upsert_local_property "node.path.$ANDROID_ABI" "$FINAL_NODE"
upsert_local_property "npm.path.$ANDROID_ABI" "$OUT_DIR/npm"
upsert_local_property "npx.path.$ANDROID_ABI" "$OUT_DIR/npx"
upsert_local_property "nodeHome.path.$ANDROID_ABI" "$RUNTIME_HOME"

printf '%s\n' "$FINAL_NODE"
printf '%s\n' "$RUNTIME_HOME"
