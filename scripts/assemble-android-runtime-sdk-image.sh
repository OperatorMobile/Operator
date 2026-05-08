#!/usr/bin/env bash
set -euo pipefail

OPERATOR_ANDROID_RUNTIME_SDK_PROFILE="${OPERATOR_ANDROID_RUNTIME_SDK_PROFILE:-bootstrap}"
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/android-runtime-sdk-common.sh"

usage() {
  cat <<EOF
Usage: scripts/assemble-android-runtime-sdk-image.sh [options]

Assembles a clean Android Runtime SDK image from local Android tool artifacts.
This does not compile packages. It creates a curated usr/ tree suitable for
scripts/build-android-runtime-sdk.sh.

Options:
  --output-dir <dir>       Image root to write. Defaults to ignored local-artifacts.
  --profile <name>         Runtime SDK profile. Defaults to bootstrap.
  --help                   Show this help.

Environment:
  OPERATOR_ANDROID_RUNTIME_SDK_PROFILE=bootstrap
  OPERATOR_ANDROID_APP_ID=com.illumination.operator
  ANDROID_ABI=arm64-v8a
  ANDROID_API=26
EOF
}

OPERATOR_ANDROID_RUNTIME_SDK_PROFILE_DIR="$OPERATOR_ANDROID_RUNTIME_SDK_ROOT/profiles/$OPERATOR_ANDROID_RUNTIME_SDK_PROFILE"
output_dir="${OPERATOR_ANDROID_RUNTIME_SDK_IMAGE_DIR:-$ROOT_DIR/local-artifacts/android-runtime-sdk/images/$OPERATOR_ANDROID_RUNTIME_SDK_PROFILE/$OPERATOR_ANDROID_APP_ID/api$ANDROID_API/$ANDROID_ABI}"

while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --output-dir)
      shift
      output_dir="${1:-}"
      ;;
    --profile)
      shift
      OPERATOR_ANDROID_RUNTIME_SDK_PROFILE="${1:-}"
      OPERATOR_ANDROID_RUNTIME_SDK_PROFILE_DIR="$OPERATOR_ANDROID_RUNTIME_SDK_ROOT/profiles/$OPERATOR_ANDROID_RUNTIME_SDK_PROFILE"
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

require_host_tool find
require_host_tool tar
require_host_tool perl

case "$OPERATOR_ANDROID_RUNTIME_SDK_PROFILE" in
  bootstrap|core)
    ;;
  *)
    echo "This assembler currently supports the bootstrap and core profiles only." >&2
    exit 2
    ;;
esac

property_or_default() {
  local key="$1"
  local fallback="$2"
  local value

  value="$(read_local_property "$key")"
  if [[ -n "$value" ]]; then
    printf '%s\n' "$value"
  else
    printf '%s\n' "$fallback"
  fi
}

require_path() {
  local description="$1"
  local path="$2"

  if [[ ! -e "$path" ]]; then
    echo "Missing $description: $path" >&2
    exit 1
  fi
}

require_executable() {
  local description="$1"
  local path="$2"

  if [[ ! -f "$path" || ! -x "$path" ]]; then
    echo "Missing executable $description: $path" >&2
    exit 1
  fi
}

copy_tree_contents() {
  local source_dir="$1"
  local destination_dir="$2"

  require_path "directory" "$source_dir"
  mkdir -p "$destination_dir"
  (
    cd "$source_dir"
    tar -cf - .
  ) | (
    cd "$destination_dir"
    tar -xf -
  )
}

copy_file_preserve() {
  local source="$1"
  local destination="$2"

  require_path "file" "$source"
  mkdir -p "$(dirname "$destination")"
  cp -p "$source" "$destination"
}

copy_executable() {
  local source="$1"
  local destination="$2"

  require_executable "$(basename "$destination")" "$source"
  copy_file_preserve "$source" "$destination"
  chmod 0755 "$destination"
}

copy_optional_executable() {
  local source="$1"
  local destination="$2"

  if [[ -f "$source" && -x "$source" ]]; then
    copy_executable "$source" "$destination"
  fi
}

copy_profile_executable() {
  local source="$1"
  local destination="$2"

  if [[ "$OPERATOR_ANDROID_RUNTIME_SDK_PROFILE" == "core" ]]; then
    copy_executable "$source" "$destination"
  else
    copy_optional_executable "$source" "$destination"
  fi
}

ensure_tool_alias() {
  local target_name="$1"
  local alias_name="$2"
  local alias_path="$output_dir.tmp/usr/bin/$alias_name"

  if [[ -e "$alias_path" ]]; then
    return
  fi
  ln -s "$target_name" "$alias_path"
}

write_executable() {
  local destination="$1"
  shift

  mkdir -p "$(dirname "$destination")"
  printf '%s\n' "$@" > "$destination"
  chmod 0755 "$destination"
}

ensure_python_ensurepip_wheel() {
  local ensurepip_dir="$output_dir.tmp/usr/lib/python3.13/ensurepip"
  local bundled_dir="$ensurepip_dir/_bundled"
  local wheel=""
  local wheel_name
  local pip_version
  local -a wheels=()

  [[ -d "$ensurepip_dir" ]] || return 0
  mkdir -p "$bundled_dir"

  shopt -s nullglob
  wheels=("$bundled_dir"/pip-*.whl)
  shopt -u nullglob

  if [[ "${#wheels[@]}" -eq 0 ]]; then
    shopt -s nullglob
    wheels=(
      "$python_home"/lib/python3.13/ensurepip/_bundled/pip-*.whl
      "$ANDROID_RUNTIME_SOURCE_ROOT"/cpython/Lib/ensurepip/_bundled/pip-*.whl
    )
    shopt -u nullglob
    if [[ "${#wheels[@]}" -gt 0 ]]; then
      wheel="${wheels[$((${#wheels[@]} - 1))]}"
      copy_file_preserve "$wheel" "$bundled_dir/$(basename "$wheel")"
    fi
  fi

  shopt -s nullglob
  wheels=("$bundled_dir"/pip-*.whl)
  shopt -u nullglob
  if [[ "${#wheels[@]}" -eq 0 ]]; then
    echo "Missing ensurepip bundled pip wheel under $bundled_dir" >&2
    exit 1
  fi

  wheel_name="$(basename "${wheels[$((${#wheels[@]} - 1))]}")"
  pip_version="${wheel_name#pip-}"
  pip_version="${pip_version%%-*}"
  if [[ -f "$ensurepip_dir/__init__.py" ]]; then
    perl -0pi -e "s/_PIP_VERSION = \"[^\"]+\"/_PIP_VERSION = \"$pip_version\"/" "$ensurepip_dir/__init__.py"
  fi
}

patch_text_file_if_present() {
  local path="$1"

  if [[ -f "$path" ]]; then
    perl -0pi -e "s#/Users/[^'\" \\n:)]*?/Library/Android/sdk/ndk/[^'\" \\n:)]*?/toolchains/llvm/prebuilt/darwin-x86_64/bin/([^'\" \\n:)]+)#$OPERATOR_ANDROID_RUNTIME_SDK_PREFIX/bin/\$1#g" "$path"
    perl -0pi -e "s#/Users/[^'\" \\n:)]*?/local-artifacts/android-deps/$ANDROID_ABI/[^'\" \\n:)]*?#$OPERATOR_ANDROID_RUNTIME_SDK_PREFIX#g" "$path"
    perl -0pi -e "s#/Users/[^'\" \\n:)]*?/third_party/android-runtime-sources/sources/cpython[^'\" \\n:)]*?#.#g" "$path"
    perl -0pi -e "s#/Users/[^'\" \\n:)]*?/Library/Android/sdk/ndk/[^'\" \\n:)]*?/toolchains/llvm/prebuilt/darwin-x86_64/bin/aarch64-linux-android26-clang\\+\\+#$OPERATOR_ANDROID_RUNTIME_SDK_PREFIX/bin/aarch64-linux-android26-clang++#g" "$path"
    perl -0pi -e "s#/Users/[^'\" \\n:)]*?/Library/Android/sdk/ndk/[^'\" \\n:)]*?/toolchains/llvm/prebuilt/darwin-x86_64/bin/aarch64-linux-android26-clang#$OPERATOR_ANDROID_RUNTIME_SDK_PREFIX/bin/aarch64-linux-android26-clang#g" "$path"
    perl -0pi -e "s#/Users/[^'\" \\n:)]*?/Library/Android/sdk/ndk/[^'\" \\n:)]*?/toolchains/llvm/prebuilt/darwin-x86_64/bin/llvm-ar#$OPERATOR_ANDROID_RUNTIME_SDK_PREFIX/bin/llvm-ar#g" "$path"
    perl -0pi -e "s#/Users/[^'\" \\n:)]*?/Library/Android/sdk/ndk/[^'\" \\n:)]*?/toolchains/llvm/prebuilt/darwin-x86_64/bin/llvm-ranlib#$OPERATOR_ANDROID_RUNTIME_SDK_PREFIX/bin/llvm-ranlib#g" "$path"
    perl -0pi -e "s#/var/folders/[^'\" \\n:)]*?#$OPERATOR_ANDROID_RUNTIME_SDK_PREFIX/tmp#g" "$path"
    perl -0pi -e "s#/home/builder/[^'\" \\n:)]*?#$OPERATOR_ANDROID_RUNTIME_SDK_PREFIX/tmp#g" "$path"
    perl -0pi -e "s#/usr/local#$OPERATOR_ANDROID_RUNTIME_SDK_PREFIX#g" "$path"
    perl -0pi -e "s#com\\.illumination\\.operator\\.debug#${OPERATOR_ANDROID_APP_ID//./\\.}#g" "$path"
  fi
}

patch_pkg_config_prefix() {
  local path="$1"

  if [[ -f "$path" ]]; then
    perl -0pi -e "s#^prefix=.*#prefix=$OPERATOR_ANDROID_RUNTIME_SDK_PREFIX#m" "$path"
    patch_text_file_if_present "$path"
  fi
}

default_toolchain_root="$ROOT_DIR/local-artifacts/android-tools/toolchain/$OPERATOR_ANDROID_APP_ID/$ANDROID_ABI"
if [[ ! -d "$default_toolchain_root/usr" && -d "$ROOT_DIR/local-artifacts/android-tools/toolchain/com.illumination.operator.debug/$ANDROID_ABI/usr" ]]; then
  default_toolchain_root="$ROOT_DIR/local-artifacts/android-tools/toolchain/com.illumination.operator.debug/$ANDROID_ABI"
fi
toolchain_root="$(property_or_default "toolchain.path.$ANDROID_ABI" "$default_toolchain_root")"
case "$toolchain_root" in
  */local-artifacts/android-runtime-sdk/staged/*)
    toolchain_root="${OPERATOR_ANDROID_BOOTSTRAP_TOOLCHAIN_ROOT:-$default_toolchain_root}"
    ;;
esac
toolchain_usr="$toolchain_root/usr"
python_home="$(property_or_default "pythonHome.path.$ANDROID_ABI" "$ROOT_DIR/local-artifacts/android-tools/cpython/v3.13.2/$ANDROID_ABI/python-home")"
python_dev_libs="$(property_or_default "pythonDevLibs.path.$ANDROID_ABI" "$ROOT_DIR/local-artifacts/android-tools/python-dev-libs/v3.13.2/$ANDROID_ABI")"
node_home="$(property_or_default "nodeHome.path.$ANDROID_ABI" "$ROOT_DIR/local-artifacts/android-tools/node/v22.14.0/$ANDROID_ABI/node-home")"
openssh_dir="$(dirname "$(property_or_default "ssh.path.$ANDROID_ABI" "$ROOT_DIR/local-artifacts/android-tools/openssh/V_9_9_P2/$ANDROID_ABI/ssh")")"
git_dir="$(dirname "$(property_or_default "git.path.$ANDROID_ABI" "$ROOT_DIR/local-artifacts/android-tools/git/v2.54.0/$ANDROID_ABI/git")")"
make_bin="$(property_or_default "make.path.$ANDROID_ABI" "$ROOT_DIR/local-artifacts/android-tools/gnu-make/4.4.1/$ANDROID_ABI/make")"
rg_bin="$(property_or_default "ripgrep.path.$ANDROID_ABI" "$ROOT_DIR/local-artifacts/android-tools/ripgrep/v15.1.0/$ANDROID_ABI/rg")"
apply_patch_bin="$(property_or_default "applyPatch.path.$ANDROID_ABI" "$ROOT_DIR/local-artifacts/android-tools/apply_patch/codex-6014b66/$ANDROID_ABI/apply_patch")"
gh_bin="$(property_or_default "gh.path.$ANDROID_ABI" "$ROOT_DIR/local-artifacts/android-tools/gh/v2.90.0/$ANDROID_ABI/gh")"

require_path "toolchain usr prefix" "$toolchain_usr"
require_path "Python home" "$python_home"
require_path "Python dev libs" "$python_dev_libs"
require_path "Node home" "$node_home"

rm -rf "$output_dir.tmp" "$output_dir"
mkdir -p \
  "$output_dir.tmp/usr/bin" \
  "$output_dir.tmp/usr/include" \
  "$output_dir.tmp/usr/libexec/operator" \
  "$output_dir.tmp/usr/lib/pkgconfig" \
  "$output_dir.tmp/usr/lib/cmake" \
  "$output_dir.tmp/usr/etc/tls" \
  "$output_dir.tmp/usr/share" \
  "$output_dir.tmp/usr/tmp"

for tool in \
  sh busybox ash bash zsh curl openssl tar gzip gunzip xz unxz sed grep awk gawk \
  find xargs sort cat ls cp mv rm mkdir mktemp chmod env printf touch ln readlink \
  realpath pwd dirname basename diff patch zstd unzip file
do
  copy_optional_executable "$toolchain_usr/bin/$tool" "$output_dir.tmp/usr/bin/$tool"
done

if [[ "$OPERATOR_ANDROID_RUNTIME_SDK_PROFILE" == "core" ]]; then
  for tool in \
    clang clang++ ld.lld lld llvm-ar llvm-ranlib llvm-strip \
    cmake ctest cpack ninja pkg-config \
    autoconf automake libtool m4 rustc cargo rustdoc rustfmt
  do
    copy_profile_executable "$toolchain_usr/bin/$tool" "$output_dir.tmp/usr/bin/$tool"
  done
  ensure_tool_alias clang cc
  ensure_tool_alias clang++ c++
  ensure_tool_alias clang aarch64-linux-android-clang
  ensure_tool_alias clang++ aarch64-linux-android-clang++
  ensure_tool_alias clang aarch64-linux-android26-clang
  ensure_tool_alias clang++ aarch64-linux-android26-clang++
  ensure_tool_alias llvm-strip strip
fi

write_executable "$output_dir.tmp/usr/bin/which" \
  '#!/system/bin/sh' \
  'for name in "$@"; do' \
  '  command -v "$name" || exit 1' \
  'done'

for tool in ssh scp sftp ssh-add ssh-agent ssh-keygen ssh-keyscan; do
  copy_executable "$openssh_dir/$tool" "$output_dir.tmp/usr/bin/$tool"
done

copy_executable "$git_dir/git" "$output_dir.tmp/usr/bin/git"
copy_executable "$git_dir/git-remote-http" "$output_dir.tmp/usr/bin/git-remote-http"
copy_executable "$git_dir/git-remote-https" "$output_dir.tmp/usr/bin/git-remote-https"
copy_executable "$make_bin" "$output_dir.tmp/usr/bin/make"
copy_executable "$rg_bin" "$output_dir.tmp/usr/bin/rg"
copy_executable "$apply_patch_bin" "$output_dir.tmp/usr/bin/apply_patch"
copy_executable "$gh_bin" "$output_dir.tmp/usr/libexec/operator/gh.real"
write_executable "$output_dir.tmp/usr/bin/gh" \
  '#!/system/bin/sh' \
  'case ",${GODEBUG:-}," in' \
  '  *,netdns=*) ;;' \
  '  *) export GODEBUG="${GODEBUG:+$GODEBUG,}netdns=cgo" ;;' \
  'esac' \
  'exec "${0%/*}/../libexec/operator/gh.real" "$@"'

copy_tree_contents "$python_home/lib/python3.13" "$output_dir.tmp/usr/lib/python3.13"
ensure_python_ensurepip_wheel
copy_tree_contents "$python_home/include/python3.13" "$output_dir.tmp/usr/include/python3.13"
copy_executable "$python_home/bin/python3" "$output_dir.tmp/usr/bin/python3"
ln -sf python3 "$output_dir.tmp/usr/bin/python"
copy_file_preserve "$python_home/lib/libpython3.13.so" "$output_dir.tmp/usr/lib/libpython3.13.so"
if [[ -f "$python_home/lib/libpython3.so" ]]; then
  copy_file_preserve "$python_home/lib/libpython3.so" "$output_dir.tmp/usr/lib/libpython3.so"
else
  ln -sf libpython3.13.so "$output_dir.tmp/usr/lib/libpython3.so"
fi
write_executable "$output_dir.tmp/usr/bin/pip" \
  '#!/system/bin/sh' \
  'exec python3 -m pip "$@"'
ln -sf pip "$output_dir.tmp/usr/bin/pip3"
ln -sf pip "$output_dir.tmp/usr/bin/pip3.13"

copy_file_preserve "$python_home/bin/python3-config" "$output_dir.tmp/usr/lib/python3.13/python3-config.py"
write_executable "$output_dir.tmp/usr/bin/python3-config" \
  '#!/system/bin/sh' \
  'SCRIPT_DIR=${0%/*}' \
  'exec "$SCRIPT_DIR/python3" "$SCRIPT_DIR/../lib/python3.13/python3-config.py" "$@"'
ln -sf python3-config "$output_dir.tmp/usr/bin/python3.13-config"

copy_tree_contents "$node_home/include/node" "$output_dir.tmp/usr/include/node"
copy_tree_contents "$node_home/lib/node_modules" "$output_dir.tmp/usr/lib/node_modules"
find "$output_dir.tmp/usr/lib/node_modules" -type f \( -name 'README.md' -o -name '*_test.py' -o -name '*test.js' \) -delete
copy_executable "$node_home/bin/node" "$output_dir.tmp/usr/bin/node"
write_executable "$output_dir.tmp/usr/bin/npm" \
  '#!/system/bin/sh' \
  'PREFIX=${OPERATOR_TOOLCHAIN_PREFIX:-${0%/*}/..}' \
  'exec "$PREFIX/bin/node" "$PREFIX/lib/node_modules/npm/bin/npm-cli.js" "$@"'
write_executable "$output_dir.tmp/usr/bin/npx" \
  '#!/system/bin/sh' \
  'PREFIX=${OPERATOR_TOOLCHAIN_PREFIX:-${0%/*}/..}' \
  'exec "$PREFIX/bin/node" "$PREFIX/lib/node_modules/npm/bin/npx-cli.js" "$@"'
write_executable "$output_dir.tmp/usr/bin/corepack" \
  '#!/system/bin/sh' \
  'PREFIX=${OPERATOR_TOOLCHAIN_PREFIX:-${0%/*}/..}' \
  'exec "$PREFIX/bin/node" "$PREFIX/lib/node_modules/corepack/dist/corepack.js" "$@"'

find "$toolchain_usr/lib" -maxdepth 1 \( -type f -o -type l \) \( -name '*.so*' -o -name '*.a' \) -print | while IFS= read -r lib; do
  copy_file_preserve "$lib" "$output_dir.tmp/usr/lib/$(basename "$lib")"
done
find "$python_dev_libs/lib" -maxdepth 1 \( -type f -o -type l \) \( -name '*.so*' -o -name '*.a' \) -print | while IFS= read -r lib; do
  copy_file_preserve "$lib" "$output_dir.tmp/usr/lib/$(basename "$lib")"
done
for lib_dir in clang rustlib cmake; do
  if [[ -d "$toolchain_usr/lib/$lib_dir" ]]; then
    copy_tree_contents "$toolchain_usr/lib/$lib_dir" "$output_dir.tmp/usr/lib/$lib_dir"
  fi
done

for include_path in \
  zlib.h bzlib.h lzma.h ffi.h ffitarget.h sqlite3.h sqlite3ext.h \
  curses.h eti.h form.h menu.h ncurses.h ncurses_dll.h panel.h term.h term_entry.h termcap.h unctrl.h
do
  if [[ -f "$python_dev_libs/include/$include_path" ]]; then
    copy_file_preserve "$python_dev_libs/include/$include_path" "$output_dir.tmp/usr/include/$include_path"
  elif [[ -f "$toolchain_usr/include/$include_path" ]]; then
    copy_file_preserve "$toolchain_usr/include/$include_path" "$output_dir.tmp/usr/include/$include_path"
  fi
done
if [[ -d "$toolchain_usr/include/openssl" ]]; then
  copy_tree_contents "$toolchain_usr/include/openssl" "$output_dir.tmp/usr/include/openssl"
fi
for include_dir in lzma readline ncurses ncursesw; do
  if [[ -d "$toolchain_usr/include/$include_dir" ]]; then
    copy_tree_contents "$toolchain_usr/include/$include_dir" "$output_dir.tmp/usr/include/$include_dir"
  fi
done

for pc in openssl libssl libcrypto zlib libcurl liblzma readline ncurses ncursesw curses; do
  if [[ -f "$toolchain_usr/lib/pkgconfig/$pc.pc" ]]; then
    copy_file_preserve "$toolchain_usr/lib/pkgconfig/$pc.pc" "$output_dir.tmp/usr/lib/pkgconfig/$pc.pc"
  fi
done
for pc in libffi sqlite3 python-3.13 python-3.13-embed python3 python3-embed; do
  if [[ -f "$python_dev_libs/lib/pkgconfig/$pc.pc" ]]; then
    copy_file_preserve "$python_dev_libs/lib/pkgconfig/$pc.pc" "$output_dir.tmp/usr/lib/pkgconfig/$pc.pc"
  elif [[ -f "$python_home/lib/pkgconfig/$pc.pc" ]]; then
    copy_file_preserve "$python_home/lib/pkgconfig/$pc.pc" "$output_dir.tmp/usr/lib/pkgconfig/$pc.pc"
  fi
done

if [[ -d "$toolchain_usr/lib/cmake/OpenSSL" ]]; then
  copy_tree_contents "$toolchain_usr/lib/cmake/OpenSSL" "$output_dir.tmp/usr/lib/cmake/OpenSSL"
fi
if [[ "$OPERATOR_ANDROID_RUNTIME_SDK_PROFILE" == "core" ]]; then
  mkdir -p "$output_dir.tmp/usr/etc/cargo"
  cat > "$output_dir.tmp/usr/etc/cargo/config.toml" <<EOF_CARGO_CONFIG
[build]
target = "aarch64-linux-android"

[target.aarch64-linux-android]
linker = "$OPERATOR_ANDROID_RUNTIME_SDK_PREFIX/bin/clang"
ar = "$OPERATOR_ANDROID_RUNTIME_SDK_PREFIX/bin/llvm-ar"
EOF_CARGO_CONFIG
fi
copy_file_preserve "$toolchain_usr/etc/tls/cert.pem" "$output_dir.tmp/usr/etc/tls/cert.pem"
if [[ -f "$toolchain_usr/etc/tls/openssl.cnf" ]]; then
  copy_file_preserve "$toolchain_usr/etc/tls/openssl.cnf" "$output_dir.tmp/usr/etc/tls/openssl.cnf"
fi
if [[ -d "$toolchain_usr/share/terminfo" ]]; then
  copy_tree_contents "$toolchain_usr/share/terminfo" "$output_dir.tmp/usr/share/terminfo"
fi

find "$output_dir.tmp/usr/lib/pkgconfig" -maxdepth 1 -type f -name '*.pc' -print | while IFS= read -r pc; do
  patch_pkg_config_prefix "$pc"
done
find "$output_dir.tmp/usr/include" -type f -print | while IFS= read -r file; do
  patch_text_file_if_present "$file"
done
find "$output_dir.tmp/usr/lib/python3.13" -type f \( -name '_sysconfigdata_*.py' -o -path '*/config-*/Makefile' -o -name 'config.c' \) -print | while IFS= read -r file; do
  patch_text_file_if_present "$file"
done
find "$output_dir.tmp/usr/lib/python3.13" -type f -name '*.py' -print | while IFS= read -r file; do
  perl -0pi -e 's#/C:/Users/foo#/C:/Example/foo#g; s#/opt/homebrew#/operator/homebrew#g' "$file"
done
patch_text_file_if_present "$output_dir.tmp/usr/lib/python3.13/python3-config.py"
if [[ -d "$output_dir.tmp/usr/lib/cmake" ]]; then
  find "$output_dir.tmp/usr/lib/cmake" -type f -print | while IFS= read -r file; do
    patch_text_file_if_present "$file"
  done
fi
find "$output_dir.tmp" -name '._*' -delete

runtime_sdk_validate_image "$output_dir.tmp"

mkdir -p "$(dirname "$output_dir")"
mv "$output_dir.tmp" "$output_dir"

printf 'Runtime SDK image assembled: %s\n' "$output_dir"
