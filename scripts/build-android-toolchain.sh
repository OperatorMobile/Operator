#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/android-runtime-common.sh"

configure_android_abi
require_host_tool git
require_host_tool patch
require_host_tool perl

TOOLCHAIN_PACKAGE_SOURCE="termux-packages"
TOOLCHAIN_PACKAGES_DIR="$(android_source_dir "$TOOLCHAIN_PACKAGE_SOURCE")"
ANDROID_TOOLCHAIN_PROFILE="${ANDROID_TOOLCHAIN_PROFILE:-core}"

runtime_sdk_profile_dir() {
  printf '%s/runtime-sdk/profiles/%s\n' "$ROOT_DIR/third_party/android-runtime-sources" "$1"
}

runtime_sdk_profile_package_names() {
  local profile="$1"
  local packages_file

  packages_file="$(runtime_sdk_profile_dir "$profile")/packages.tsv"
  if [[ ! -f "$packages_file" ]]; then
    echo "Runtime SDK profile package manifest not found: $packages_file" >&2
    exit 1
  fi

  awk -F '\t' '
    $0 !~ /^#/ && NF >= 1 && $1 != "" {
      print $1
    }
  ' "$packages_file"
}

termux_source_package_for_runtime_package() {
  case "$1" in
    bzip2) printf '%s\n' libbz2 ;;
    xz|xz-utils) printf '%s\n' liblzma ;;
    sqlite) printf '%s\n' libsqlite ;;
    zstd-dev) printf '%s\n' zstd ;;
    clang|lld|llvm) printf '%s\n' libllvm ;;
    curl) printf '%s\n' libcurl ;;
    apply-patch|github-cli|ripgrep)
      # These are built by Operator's dedicated scripts, not termux-packages.
      ;;
    *)
      printf '%s\n' "$1"
      ;;
  esac
}

termux_package_list_from_runtime_profile() {
  local profile="$1"
  local package_name

  runtime_sdk_profile_package_names "$profile" | while IFS= read -r package_name; do
    termux_source_package_for_runtime_package "$package_name"
  done | awk 'NF > 0 && !seen[$0]++ { print }' | paste -sd ' ' -
}

toolchain_package_list_contains() {
  local needle="$1"
  case " $ANDROID_TOOLCHAIN_PACKAGES " in
    *" $needle "*) return 0 ;;
    *) return 1 ;;
  esac
}

if [[ -z "${ANDROID_TOOLCHAIN_PACKAGES:-}" ]]; then
  case "$ANDROID_TOOLCHAIN_PROFILE" in
    bootstrap|runtime-sdk-bootstrap)
      ANDROID_TOOLCHAIN_PACKAGES="busybox bash zsh curl ca-certificates coreutils findutils sed grep gawk diffutils tar gzip liblzma unzip file which openssl zlib"
      ;;
    c|native)
      ANDROID_TOOLCHAIN_PACKAGES="build-essential"
      ;;
    core|runtime-sdk-core)
      ANDROID_TOOLCHAIN_PACKAGES="$(termux_package_list_from_runtime_profile core)"
      ;;
    shell-runtime)
      ANDROID_TOOLCHAIN_PACKAGES="busybox bash zsh curl ca-certificates coreutils findutils sed grep gawk diffutils tar gzip liblzma unzip file which openssl zlib"
      ;;
    full)
      ANDROID_TOOLCHAIN_PACKAGES="build-essential git openssh busybox bash zsh perl cmake ninja autoconf automake libtool m4 bison flex patchelf file jq tree rsync zip unzip tar coreutils findutils diffutils patch strace gawk sed grep less which"
      ;;
    *)
      echo "Unsupported ANDROID_TOOLCHAIN_PROFILE: $ANDROID_TOOLCHAIN_PROFILE" >&2
      echo "Use 'c', 'native', 'full', or set ANDROID_TOOLCHAIN_PACKAGES explicitly." >&2
      exit 1
      ;;
  esac
fi
if [[ "${ANDROID_TOOLCHAIN_INCLUDE_RUST:-false}" == "true" ]] && ! toolchain_package_list_contains rust; then
  ANDROID_TOOLCHAIN_PACKAGES+=" rust"
fi
if toolchain_package_list_contains rust; then
  # Termux's Rust recipe links rustc against shared libLLVM and expects the
  # same target set as upstream Termux libllvm. A single-target LLVM is useful
  # for a smaller C/C++ SDK, but it leaves rustc with unresolved
  # LLVMInitialize* symbols during the final link.
  OPERATOR_LLVM_TARGETS_TO_BUILD="all"
  OPERATOR_LLVM_EXPERIMENTAL_TARGETS_TO_BUILD="${OPERATOR_LLVM_EXPERIMENTAL_TARGETS_TO_BUILD:-ARC;CSKY;M68k;VE}"
fi
if [[ "${ANDROID_TOOLCHAIN_INCLUDE_GDB:-false}" == "true" ]]; then
  # gdb currently pulls python -> tk -> libx11, and this pinned Android build
  # hits a missing sys/shm.h path in libx11. Keep it opt-in until we package a
  # headless gdb or carry a clean libx11 fix.
  ANDROID_TOOLCHAIN_PACKAGES+=" gdb"
fi
OPERATOR_ANDROID_APP_ID="${OPERATOR_ANDROID_APP_ID:-com.illumination.operator}"
OPERATOR_ANDROID_APP_DATA_DIR="${OPERATOR_ANDROID_APP_DATA_DIR:-/data/data/$OPERATOR_ANDROID_APP_ID}"
OPERATOR_TOOLCHAIN_ROOTFS="${OPERATOR_TOOLCHAIN_ROOTFS:-$OPERATOR_ANDROID_APP_DATA_DIR/files/tools/toolchain}"
OPERATOR_TOOLCHAIN_PREFIX="${OPERATOR_TOOLCHAIN_PREFIX:-$OPERATOR_TOOLCHAIN_ROOTFS/usr}"
ANDROID_TOOLCHAIN_MAKE_PROCESSES="${ANDROID_TOOLCHAIN_MAKE_PROCESSES:-1}"
ANDROID_TOOLCHAIN_BUILD_FLAGS="${ANDROID_TOOLCHAIN_BUILD_FLAGS:-}"
OPERATOR_TOOLCHAIN_BUILD_VARIANT="${OPERATOR_TOOLCHAIN_BUILD_VARIANT:-native-targets}"
OPERATOR_TOOLCHAIN_USE_DOCKER="${OPERATOR_TOOLCHAIN_USE_DOCKER:-true}"
if [[ -z "${TERMUX_TOPDIR:-}" ]]; then
  if [[ -x "$TOOLCHAIN_PACKAGES_DIR/scripts/run-docker.sh" && "$OPERATOR_TOOLCHAIN_USE_DOCKER" == "true" ]]; then
    TERMUX_TOPDIR="/home/builder/.termux-build/operator-$ANDROID_ABI-$OPERATOR_ANDROID_APP_ID-$OPERATOR_TOOLCHAIN_BUILD_VARIANT"
  else
    TERMUX_TOPDIR="$ROOT_DIR/tmp/termux-build/operator-$ANDROID_ABI-$OPERATOR_ANDROID_APP_ID-$OPERATOR_TOOLCHAIN_BUILD_VARIANT"
  fi
fi
ANDROID_TOOLCHAIN_OUTPUT_RELATIVE_DIR="output/operator/$OPERATOR_ANDROID_APP_ID/$ANDROID_ABI/debs"
ANDROID_TOOLCHAIN_OUTPUT_DIR_HOST="${ANDROID_TOOLCHAIN_OUTPUT_DIR:-$TOOLCHAIN_PACKAGES_DIR/$ANDROID_TOOLCHAIN_OUTPUT_RELATIVE_DIR}"
ANDROID_TOOLCHAIN_OUTPUT_DIR_CONTAINER="/home/builder/termux-packages/$ANDROID_TOOLCHAIN_OUTPUT_RELATIVE_DIR"
export TERMUX_PKG_MAKE_PROCESSES="$ANDROID_TOOLCHAIN_MAKE_PROCESSES"
export CMAKE_BUILD_PARALLEL_LEVEL="$ANDROID_TOOLCHAIN_MAKE_PROCESSES"
export MAKEFLAGS="-j$ANDROID_TOOLCHAIN_MAKE_PROCESSES"
export NINJAFLAGS="-j$ANDROID_TOOLCHAIN_MAKE_PROCESSES"

case "$ANDROID_ABI" in
  arm64-v8a) TERMUX_ARCH="aarch64" ;;
  x86_64) TERMUX_ARCH="x86_64" ;;
  *)
    echo "Unsupported Android ABI for Android toolchain: $ANDROID_ABI" >&2
    exit 1
    ;;
esac

sync_android_runtime_source "$TOOLCHAIN_PACKAGE_SOURCE"
git -C "$TOOLCHAIN_PACKAGES_DIR" reset --hard "$(android_source_field "$TOOLCHAIN_PACKAGE_SOURCE" commit)" >/dev/null
mkdir -p "$ANDROID_TOOLCHAIN_OUTPUT_DIR_HOST"

patch_toolchain_source_properties() {
  local properties_file="$TOOLCHAIN_PACKAGES_DIR/scripts/properties.sh"
  local escaped_app_id
  local escaped_rootfs_subdir

  escaped_app_id="${OPERATOR_ANDROID_APP_ID//\\/\\\\}"
  escaped_app_id="${escaped_app_id//&/\\&}"
  escaped_rootfs_subdir="files\\/tools\\/toolchain"

  perl -0pi -e "s/TERMUX_APP__PACKAGE_NAME=\"com\\.termux\"/TERMUX_APP__PACKAGE_NAME=\"$escaped_app_id\"/" "$properties_file"
  perl -0pi -e "s/TERMUX__ROOTFS_SUBDIR=\"files\"/TERMUX__ROOTFS_SUBDIR=\"$escaped_rootfs_subdir\"/" "$properties_file"
}

apply_toolchain_patch() {
  local patch_name="$1"
  local patch_file="$ROOT_DIR/scripts/android-toolchain-patches/$patch_name"

  if ! patch --forward --silent -p1 -d "$TOOLCHAIN_PACKAGES_DIR" < "$patch_file"; then
    if patch --reverse --dry-run --silent -p1 -d "$TOOLCHAIN_PACKAGES_DIR" < "$patch_file"; then
      return
    fi
    echo "Failed to apply Android toolchain patch: $patch_file" >&2
    exit 1
  fi
}

disable_git_gui_svn_subpackages() {
  rm -f \
    "$TOOLCHAIN_PACKAGES_DIR/packages/git/git-gui.subpackage.sh" \
    "$TOOLCHAIN_PACKAGES_DIR/packages/git/git-gitk.subpackage.sh" \
    "$TOOLCHAIN_PACKAGES_DIR/packages/git/git-svn.subpackage.sh" \
    "$TOOLCHAIN_PACKAGES_DIR/packages/git"/*.rej
}

patch_toolchain_source_properties
apply_toolchain_patch python-headless-no-tk.patch
apply_toolchain_patch libllvm-operator-native-targets.patch
apply_toolchain_patch no-fuse-overlay-toolchain-copy.patch
apply_toolchain_patch proot-safe-libtool-lalib.patch
apply_toolchain_patch sed-build-use-makefile.patch
apply_toolchain_patch tar-avoid-automake-regeneration.patch
apply_toolchain_patch openssh-headless-no-krb5.patch
apply_toolchain_patch docker-no-sudo-apparmor.patch
apply_toolchain_patch ncurses-skip-foot-terminfo.patch
apply_toolchain_patch git-headless-no-gui-svn.patch
disable_git_gui_svn_subpackages

cat <<EOF
Building Android-native toolchain packages through termux-packages:
  profile: $ANDROID_TOOLCHAIN_PROFILE
  packages: $ANDROID_TOOLCHAIN_PACKAGES
  arch: $TERMUX_ARCH
  make processes: $ANDROID_TOOLCHAIN_MAKE_PROCESSES
  build flags: ${ANDROID_TOOLCHAIN_BUILD_FLAGS:-<none>}
  build variant: $OPERATOR_TOOLCHAIN_BUILD_VARIANT
  llvm targets: ${OPERATOR_LLVM_TARGETS_TO_BUILD:-<default>}
  llvm experimental targets: ${OPERATOR_LLVM_EXPERIMENTAL_TARGETS_TO_BUILD:-<none>}
  app id: $OPERATOR_ANDROID_APP_ID
  prefix: $OPERATOR_TOOLCHAIN_PREFIX
  output: $ANDROID_TOOLCHAIN_OUTPUT_DIR_HOST

Important: these packages are prefix-specific. Do not reuse packages built for
com.termux or for a different Operator application id/prefix.
EOF

if [[ "${OPERATOR_TOOLCHAIN_CONFIGURE_ONLY:-false}" == "true" ]]; then
  printf '\nConfigured pinned Android toolchain package source only; skipping package build.\n'
  exit 0
fi

(
  cd "$TOOLCHAIN_PACKAGES_DIR"

  if [[ -x ./scripts/run-docker.sh && "$OPERATOR_TOOLCHAIN_USE_DOCKER" == "true" ]]; then
    TERMUX_DOCKER_EXEC_EXTRA_ARGS="${TERMUX_DOCKER_EXEC_EXTRA_ARGS:-} --env TERMUX_TOPDIR=$TERMUX_TOPDIR --env TERMUX_PKG_API_LEVEL=$ANDROID_API --env TERMUX_PKG_MAKE_PROCESSES=$ANDROID_TOOLCHAIN_MAKE_PROCESSES --env CMAKE_BUILD_PARALLEL_LEVEL=$ANDROID_TOOLCHAIN_MAKE_PROCESSES --env MAKEFLAGS=-j$ANDROID_TOOLCHAIN_MAKE_PROCESSES --env NINJAFLAGS=-j$ANDROID_TOOLCHAIN_MAKE_PROCESSES --env OPERATOR_LLVM_TARGETS_TO_BUILD=${OPERATOR_LLVM_TARGETS_TO_BUILD:-} --env OPERATOR_LLVM_EXPERIMENTAL_TARGETS_TO_BUILD=${OPERATOR_LLVM_EXPERIMENTAL_TARGETS_TO_BUILD:-} --env OPERATOR_LLVM_ENABLE_PROJECTS=${OPERATOR_LLVM_ENABLE_PROJECTS:-} --env OPERATOR_LLVM_HOST_ENABLE_PROJECTS=${OPERATOR_LLVM_HOST_ENABLE_PROJECTS:-}" \
      ./scripts/run-docker.sh ./build-package.sh $ANDROID_TOOLCHAIN_BUILD_FLAGS -a "$TERMUX_ARCH" -o "$ANDROID_TOOLCHAIN_OUTPUT_DIR_CONTAINER" $ANDROID_TOOLCHAIN_PACKAGES
  else
    TERMUX_TOPDIR="$TERMUX_TOPDIR" TERMUX_PKG_API_LEVEL="$ANDROID_API" \
      CMAKE_BUILD_PARALLEL_LEVEL="$ANDROID_TOOLCHAIN_MAKE_PROCESSES" \
      MAKEFLAGS="-j$ANDROID_TOOLCHAIN_MAKE_PROCESSES" \
      NINJAFLAGS="-j$ANDROID_TOOLCHAIN_MAKE_PROCESSES" \
      ./build-package.sh $ANDROID_TOOLCHAIN_BUILD_FLAGS -a "$TERMUX_ARCH" -o "$ANDROID_TOOLCHAIN_OUTPUT_DIR_HOST" $ANDROID_TOOLCHAIN_PACKAGES
  fi
)

consolidate_root_packages="$ANDROID_TOOLCHAIN_PACKAGES"
case "$ANDROID_TOOLCHAIN_PROFILE" in
  core|runtime-sdk-core)
    consolidate_root_packages+=" $(runtime_sdk_profile_package_names core)"
    ;;
esac

ANDROID_TOOLCHAIN_CONSOLIDATE_ROOT_PACKAGES="${ANDROID_TOOLCHAIN_CONSOLIDATE_ROOT_PACKAGES:-$consolidate_root_packages}" \
  "$ROOT_DIR/scripts/consolidate-android-toolchain-debs.sh" \
    --destination-deb-dir "$ANDROID_TOOLCHAIN_OUTPUT_DIR_HOST"

"$ROOT_DIR/scripts/generate-android-toolchain-package-graph.sh" \
  --deb-dir "$ANDROID_TOOLCHAIN_OUTPUT_DIR_HOST" \
  --output "$ANDROID_TOOLCHAIN_OUTPUT_DIR_HOST/operator-package-graph.tsv"

printf '\nBuilt Android toolchain package outputs under %s\n' "$ANDROID_TOOLCHAIN_OUTPUT_DIR_HOST"
printf 'Package graph written: %s\n' "$ANDROID_TOOLCHAIN_OUTPUT_DIR_HOST/operator-package-graph.tsv"
printf 'Next: scripts/stage-android-toolchain.sh\n'
