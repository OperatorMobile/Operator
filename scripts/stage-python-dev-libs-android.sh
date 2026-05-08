#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/android-runtime-common.sh"

configure_android_abi
require_host_tool find
require_host_tool sed

CPYTHON_VERSION="$(android_source_field cpython ref | awk -F/ '{ print $NF }')"
ZLIB_VERSION="$(android_source_field zlib ref | awk -F/ '{ print $NF }')"
OPENSSL_VERSION="$(android_source_field openssl ref | awk -F/ '{ print $NF }')"
LIBFFI_VERSION="$(android_source_field libffi ref | awk -F/ '{ print $NF }')"
BZIP2_VERSION="$(android_source_field bzip2 ref | awk -F/ '{ print $NF }')"
XZ_VERSION="$(android_source_field xz ref | awk -F/ '{ print $NF }')"
SQLITE_VERSION="$(android_source_field sqlite ref | awk -F/ '{ print $NF }')"

PYTHON_HOME="$(read_local_property "pythonHome.path.$ANDROID_ABI")"
PYTHON_HOME="${PYTHON_HOME:-$ROOT_DIR/local-artifacts/android-tools/cpython/$CPYTHON_VERSION/$ANDROID_ABI/python-home}"
DEPS_DIR="$ROOT_DIR/local-artifacts/android-deps/$ANDROID_ABI"
OUT_DIR="$ROOT_DIR/local-artifacts/android-tools/python-dev-libs/$CPYTHON_VERSION/$ANDROID_ABI"

required_prefixes=(
  "$PYTHON_HOME"
  "$DEPS_DIR/zlib/$ZLIB_VERSION"
  "$DEPS_DIR/openssl/$OPENSSL_VERSION"
  "$DEPS_DIR/libffi/$LIBFFI_VERSION"
  "$DEPS_DIR/bzip2/$BZIP2_VERSION"
  "$DEPS_DIR/xz/$XZ_VERSION"
  "$DEPS_DIR/sqlite/$SQLITE_VERSION"
)

for prefix in "${required_prefixes[@]}"; do
  if [[ ! -d "$prefix" ]]; then
    echo "Required Android Python build input missing: $prefix" >&2
    echo "Run scripts/build-cpython-android.sh first." >&2
    exit 1
  fi
done

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR/include" "$OUT_DIR/lib/pkgconfig"

copy_tree_contents() {
  local source_dir="$1"
  local target_dir="$2"

  if [[ -d "$source_dir" ]]; then
    mkdir -p "$target_dir"
    cp -R "$source_dir"/. "$target_dir"/
  fi
}

copy_static_libs() {
  local source_dir="$1"
  local target_dir="$2"

  if [[ -d "$source_dir" ]]; then
    mkdir -p "$target_dir"
    find "$source_dir" -maxdepth 1 \( -name '*.a' -o -name '*.so' \) -exec cp {} "$target_dir"/ \;
  fi
}

copy_pkg_config_files() {
  local source_dir="$1"
  local target_dir="$2"

  if [[ -d "$source_dir" ]]; then
    mkdir -p "$target_dir"
    find "$source_dir" -maxdepth 1 -name '*.pc' -exec cp {} "$target_dir"/ \;
  fi
}

copy_tree_contents "$PYTHON_HOME/include/python3.13" "$OUT_DIR/include/python3.13"
copy_tree_contents "$PYTHON_HOME/include/python3.13" "$OUT_DIR/include"
copy_static_libs "$PYTHON_HOME/lib" "$OUT_DIR/lib"
copy_pkg_config_files "$PYTHON_HOME/lib/pkgconfig" "$OUT_DIR/lib/pkgconfig"

for prefix in "${required_prefixes[@]:1}"; do
  copy_tree_contents "$prefix/include" "$OUT_DIR/include"
  copy_static_libs "$prefix/lib" "$OUT_DIR/lib"
  copy_pkg_config_files "$prefix/lib/pkgconfig" "$OUT_DIR/lib/pkgconfig"
done

find "$OUT_DIR/lib/pkgconfig" -maxdepth 1 -name '*.pc' -print0 |
  while IFS= read -r -d '' pc_file; do
    sed -i.bak 's|^prefix=.*|prefix=${pcfiledir}/../..|' "$pc_file"
    rm -f "$pc_file.bak"
  done

{
  printf 'abi\t%s\n' "$ANDROID_ABI"
  printf 'python\t%s\n' "$CPYTHON_VERSION"
  printf 'api\t%s\n' "$ANDROID_API"
  printf 'note\t%s\n' "Headers and libraries are staged for Android package builds; they do not include a native compiler."
} > "$OUT_DIR/operator-python-dev-libs-manifest.tsv"

upsert_local_property "pythonDevLibs.path.$ANDROID_ABI" "$OUT_DIR"

printf '%s\n' "$OUT_DIR"
