#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/android-runtime-common.sh"

configure_android_abi
require_host_tool find
require_host_tool tar
require_host_tool ar

OPERATOR_ANDROID_APP_ID="${OPERATOR_ANDROID_APP_ID:-com.illumination.operator}"
OPERATOR_ANDROID_APP_DATA_DIR="${OPERATOR_ANDROID_APP_DATA_DIR:-/data/data/$OPERATOR_ANDROID_APP_ID}"
OPERATOR_TOOLCHAIN_ROOTFS="${OPERATOR_TOOLCHAIN_ROOTFS:-$OPERATOR_ANDROID_APP_DATA_DIR/files/tools/toolchain}"
ANDROID_TOOLCHAIN_STAGE_PROFILE="${ANDROID_TOOLCHAIN_STAGE_PROFILE:-full}"
TOOLCHAIN_PACKAGES_DIR="$(android_source_dir termux-packages)"
ANDROID_TOOLCHAIN_DEB_DIR="${ANDROID_TOOLCHAIN_DEB_DIR:-$TOOLCHAIN_PACKAGES_DIR/output/operator/$OPERATOR_ANDROID_APP_ID/$ANDROID_ABI/debs}"
ANDROID_TOOLCHAIN_PACKAGE_GRAPH_FILE="${ANDROID_TOOLCHAIN_PACKAGE_GRAPH_FILE:-$ANDROID_TOOLCHAIN_DEB_DIR/operator-package-graph.tsv}"
UNPACK_DIR="$(build_parent_dir)/toolchain-unpack-$ANDROID_ABI-$OPERATOR_ANDROID_APP_ID"
OUT_DIR="$ROOT_DIR/local-artifacts/android-tools/toolchain/$OPERATOR_ANDROID_APP_ID/$ANDROID_ABI"

case "$ANDROID_TOOLCHAIN_STAGE_PROFILE" in
  c|native|full|zsh|shell-runtime)
    ;;
  *)
    echo "Unsupported Android toolchain staging profile: $ANDROID_TOOLCHAIN_STAGE_PROFILE" >&2
    echo "Expected one of: c, native, full, zsh, shell-runtime" >&2
    exit 1
    ;;
esac

if ! find "$ANDROID_TOOLCHAIN_DEB_DIR" -maxdepth 1 -name '*.deb' -type f | grep -q .; then
  echo "No Android toolchain .deb packages found in $ANDROID_TOOLCHAIN_DEB_DIR" >&2
  echo "Run scripts/build-android-toolchain.sh first." >&2
  exit 1
fi

if [[ ! -f "$ANDROID_TOOLCHAIN_PACKAGE_GRAPH_FILE" ]]; then
  "$ROOT_DIR/scripts/generate-android-toolchain-package-graph.sh" \
    --deb-dir "$ANDROID_TOOLCHAIN_DEB_DIR" \
    --output "$ANDROID_TOOLCHAIN_PACKAGE_GRAPH_FILE"
fi

rm -rf "$UNPACK_DIR" "$OUT_DIR"
mkdir -p "$UNPACK_DIR" "$OUT_DIR"

extract_deb_data() {
  local deb="$1"
  local data_member

  if data_member="$(tar -tf "$deb" 2>/dev/null | grep -E '^data\.tar\.(xz|gz)/?$' | head -n 1)" && [[ -n "$data_member" ]]; then
    data_member="${data_member%/}"
    case "$data_member" in
      data.tar.xz) tar -xOf "$deb" "$data_member" | tar -xJ -C "$UNPACK_DIR" ;;
      data.tar.gz) tar -xOf "$deb" "$data_member" | tar -xz -C "$UNPACK_DIR" ;;
    esac
    return
  fi

  data_member="$(ar t "$deb" | grep -E '^data\.tar\.(xz|gz)/?$' | head -n 1 || true)"
  if [[ -z "$data_member" ]]; then
    echo "Unsupported package data archive in $deb" >&2
    echo "Expected data.tar.xz or data.tar.gz." >&2
    exit 1
  fi
  data_member="${data_member%/}"

  case "$data_member" in
    data.tar.xz) ar p "$deb" "$data_member" | tar -xJ -C "$UNPACK_DIR" ;;
    data.tar.gz) ar p "$deb" "$data_member" | tar -xz -C "$UNPACK_DIR" ;;
  esac
}

find "$ANDROID_TOOLCHAIN_DEB_DIR" -maxdepth 1 -name '*.deb' -type f -print | sort | while IFS= read -r deb; do
  printf 'Extracting %s\n' "$(basename "$deb")"
  extract_deb_data "$deb"
done

prefix_without_slash="${OPERATOR_TOOLCHAIN_ROOTFS#/}"
if [[ ! -d "$UNPACK_DIR/$prefix_without_slash/usr" ]]; then
  echo "Expected custom toolchain prefix not found after unpack: $UNPACK_DIR/$prefix_without_slash/usr" >&2
  echo "The packages may have been built for a different app id or prefix." >&2
  exit 1
fi

cp -R "$UNPACK_DIR/$prefix_without_slash"/. "$OUT_DIR"/

materialize_absolute_prefix_symlinks() {
  local link
  local target
  local staged_target
  local target_relative
  local tmp

  find "$OUT_DIR" -type l -print | while IFS= read -r link; do
    target="$(readlink "$link")"
    case "$target" in
      "$OPERATOR_TOOLCHAIN_ROOTFS"/*)
        target_relative="${target#"$OPERATOR_TOOLCHAIN_ROOTFS"/}"
        staged_target="$OUT_DIR/$target_relative"
        if [[ ! -e "$staged_target" ]]; then
          case "$link" in
            "$OUT_DIR/usr/var/service/"*)
              rm "$link"
              continue
              ;;
          esac
          echo "Expected staged symlink target missing: $link -> $target" >&2
          exit 1
        fi
        tmp="$link.operator-materialized"
        if [[ -d "$staged_target" ]]; then
          rm "$link"
          mkdir -p "$link"
          cp -R "$staged_target"/. "$link"/
        else
          cp -p "$staged_target" "$tmp"
          rm "$link"
          mv "$tmp" "$link"
        fi
        ;;
    esac
  done
}

materialize_absolute_prefix_symlinks

rm -rf "$OUT_DIR/usr/var/service"

ensure_staged_symlink() {
  local link_relative="$1"
  local target_name="$2"
  local link_path="$OUT_DIR/$link_relative"
  local target_path

  target_path="$(dirname "$link_path")/$target_name"
  if [[ -e "$link_path" ]]; then
    return
  fi
  if [[ ! -e "$target_path" ]]; then
    echo "Expected staged symlink target missing: $link_path -> $target_name" >&2
    exit 1
  fi
  ln -s "$target_name" "$link_path"
}

case "$ANDROID_ABI" in
  arm64-v8a)
    ensure_staged_symlink "usr/bin/aarch64-linux-android${ANDROID_API}-clang" "aarch64-linux-android-clang"
    ensure_staged_symlink "usr/bin/aarch64-linux-android${ANDROID_API}-clang++" "aarch64-linux-android-clang++"
    ;;
  x86_64)
    ensure_staged_symlink "usr/bin/x86_64-linux-android${ANDROID_API}-clang" "x86_64-linux-android-clang"
    ensure_staged_symlink "usr/bin/x86_64-linux-android${ANDROID_API}-clang++" "x86_64-linux-android-clang++"
    ;;
esac

prune_staged_shell_runtime_metadata() {
  case "$ANDROID_TOOLCHAIN_STAGE_PROFILE" in
    zsh|shell-runtime)
      rm -rf \
        "$OUT_DIR/usr/share/doc" \
        "$OUT_DIR/usr/share/info" \
        "$OUT_DIR/usr/share/man"
      ;;
  esac
}

prune_staged_shell_runtime_metadata

require_staged_executable() {
  local property="$1"
  local relative_path="$2"
  local path="$OUT_DIR/$relative_path"

  if [[ -x "$path" && -f "$path" ]]; then
    upsert_local_property "$property.$ANDROID_ABI" "$path"
  else
    echo "Expected toolchain executable missing: $path" >&2
    exit 1
  fi
}

optional_staged_executable() {
  local property="$1"
  local relative_path="$2"
  local path="$OUT_DIR/$relative_path"

  if [[ -x "$path" && -f "$path" ]]; then
    upsert_local_property "$property.$ANDROID_ABI" "$path"
  else
    remove_local_property "$property.$ANDROID_ABI"
  fi
}

upsert_local_property "toolchain.path.$ANDROID_ABI" "$OUT_DIR"
upsert_local_property "androidToolchainPackageGraph.path.$ANDROID_ABI" "$ANDROID_TOOLCHAIN_PACKAGE_GRAPH_FILE"

if [[ "$ANDROID_TOOLCHAIN_STAGE_PROFILE" == "zsh" || "$ANDROID_TOOLCHAIN_STAGE_PROFILE" == "shell-runtime" ]]; then
  require_staged_executable "androidZsh.path" "usr/bin/zsh"
  optional_staged_executable "androidBash.path" "usr/bin/bash"

  {
    printf 'abi\t%s\n' "$ANDROID_ABI"
    printf 'app_id\t%s\n' "$OPERATOR_ANDROID_APP_ID"
    printf 'profile\t%s\n' "$ANDROID_TOOLCHAIN_STAGE_PROFILE"
    printf 'prefix\t%s\n' "$OPERATOR_TOOLCHAIN_ROOTFS/usr"
    printf 'source_debs\t%s\n' "$ANDROID_TOOLCHAIN_DEB_DIR"
    printf 'package_graph\t%s\n' "$ANDROID_TOOLCHAIN_PACKAGE_GRAPH_FILE"
    printf 'source_build_system\t%s\n' "termux-packages"
  } > "$OUT_DIR/operator-toolchain-manifest.tsv"

  printf '%s\n' "$OUT_DIR"
  exit 0
fi

require_staged_executable "androidClang.path" "usr/bin/clang"
require_staged_executable "androidClangxx.path" "usr/bin/clang++"
require_staged_executable "androidCc.path" "usr/bin/cc"
require_staged_executable "androidCxx.path" "usr/bin/c++"
require_staged_executable "androidLdLld.path" "usr/bin/ld.lld"
require_staged_executable "androidLld.path" "usr/bin/lld"
require_staged_executable "androidLlvmAr.path" "usr/bin/llvm-ar"
require_staged_executable "androidLlvmRanlib.path" "usr/bin/llvm-ranlib"
require_staged_executable "androidLlvmStrip.path" "usr/bin/llvm-strip"
require_staged_executable "androidPkgConfig.path" "usr/bin/pkg-config"
optional_staged_executable "androidCmake.path" "usr/bin/cmake"
optional_staged_executable "androidCtest.path" "usr/bin/ctest"
optional_staged_executable "androidCpack.path" "usr/bin/cpack"
optional_staged_executable "androidNinja.path" "usr/bin/ninja"
optional_staged_executable "androidM4.path" "usr/bin/m4"
optional_staged_executable "androidBison.path" "usr/bin/bison"
optional_staged_executable "androidFlex.path" "usr/bin/flex"
optional_staged_executable "androidPatchelf.path" "usr/bin/patchelf"
optional_staged_executable "androidFile.path" "usr/bin/file"
optional_staged_executable "androidJq.path" "usr/bin/jq"
optional_staged_executable "androidTree.path" "usr/bin/tree"
optional_staged_executable "androidRsync.path" "usr/bin/rsync"
optional_staged_executable "androidZip.path" "usr/bin/zip"
optional_staged_executable "androidUnzip.path" "usr/bin/unzip"
optional_staged_executable "androidTar.path" "usr/bin/tar"
optional_staged_executable "androidGdb.path" "usr/bin/gdb"
optional_staged_executable "androidStrace.path" "usr/bin/strace"
optional_staged_executable "androidPerl.path" "usr/bin/perl"
optional_staged_executable "androidBash.path" "usr/bin/bash"
optional_staged_executable "androidZsh.path" "usr/bin/zsh"
if [[ "${ANDROID_TOOLCHAIN_REQUIRE_RUST:-false}" == "true" ]]; then
  require_staged_executable "androidRustc.path" "usr/bin/rustc"
  require_staged_executable "androidCargo.path" "usr/bin/cargo"
  require_staged_executable "androidRustdoc.path" "usr/bin/rustdoc"
  require_staged_executable "androidRustfmt.path" "usr/bin/rustfmt"
else
  optional_staged_executable "androidRustc.path" "usr/bin/rustc"
  optional_staged_executable "androidCargo.path" "usr/bin/cargo"
  optional_staged_executable "androidRustdoc.path" "usr/bin/rustdoc"
  optional_staged_executable "androidRustfmt.path" "usr/bin/rustfmt"
fi

{
  printf 'abi\t%s\n' "$ANDROID_ABI"
  printf 'app_id\t%s\n' "$OPERATOR_ANDROID_APP_ID"
  printf 'profile\t%s\n' "$ANDROID_TOOLCHAIN_STAGE_PROFILE"
  printf 'prefix\t%s\n' "$OPERATOR_TOOLCHAIN_ROOTFS/usr"
  printf 'source_debs\t%s\n' "$ANDROID_TOOLCHAIN_DEB_DIR"
  printf 'package_graph\t%s\n' "$ANDROID_TOOLCHAIN_PACKAGE_GRAPH_FILE"
  printf 'source_build_system\t%s\n' "termux-packages"
} > "$OUT_DIR/operator-toolchain-manifest.tsv"

printf '%s\n' "$OUT_DIR"
