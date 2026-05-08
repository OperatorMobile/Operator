#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/android-runtime-common.sh"

configure_android_toolchain
sync_android_runtime_source busybox

SOURCE_DIR="$(android_source_dir busybox)"
BUSYBOX_VERSION="$(android_source_field busybox ref | awk -F/ '{ print $NF }')"
BUILD_DIR="$(build_parent_dir)/busybox-$BUSYBOX_VERSION-$ANDROID_ABI"
OUT_DIR="$ROOT_DIR/local-artifacts/android-tools/busybox/$BUSYBOX_VERSION/$ANDROID_ABI"
FINAL_BIN="$OUT_DIR/busybox"

mkdir -p "$BUILD_DIR" "$OUT_DIR"

case "$ANDROID_ABI" in
  arm64-v8a) BUSYBOX_ARCH=arm64 ;;
  x86_64) BUSYBOX_ARCH=x86_64 ;;
esac

make -C "$SOURCE_DIR" O="$BUILD_DIR" ARCH="$BUSYBOX_ARCH" allnoconfig
enable_busybox_option() {
  local option="$1"
  if grep -q "^# CONFIG_${option} is not set" "$BUILD_DIR/.config"; then
    sed -i.bak "s/^# CONFIG_${option} is not set/CONFIG_${option}=y/" "$BUILD_DIR/.config"
  elif grep -q "^CONFIG_${option}=" "$BUILD_DIR/.config"; then
    sed -i.bak "s/^CONFIG_${option}=.*/CONFIG_${option}=y/" "$BUILD_DIR/.config"
  else
    printf 'CONFIG_%s=y\n' "$option" >> "$BUILD_DIR/.config"
  fi
}
set_busybox_option_value() {
  local option="$1"
  local value="$2"
  if grep -q "^# CONFIG_${option} is not set" "$BUILD_DIR/.config"; then
    sed -i.bak "s/^# CONFIG_${option} is not set/CONFIG_${option}=${value}/" "$BUILD_DIR/.config"
  elif grep -q "^CONFIG_${option}=" "$BUILD_DIR/.config"; then
    sed -i.bak "s/^CONFIG_${option}=.*/CONFIG_${option}=${value}/" "$BUILD_DIR/.config"
  else
    printf 'CONFIG_%s=%s\n' "$option" "$value" >> "$BUILD_DIR/.config"
  fi
}
disable_busybox_option() {
  local option="$1"
  if grep -q "^CONFIG_${option}=y" "$BUILD_DIR/.config"; then
    sed -i.bak "s/^CONFIG_${option}=y/# CONFIG_${option} is not set/" "$BUILD_DIR/.config"
  elif ! grep -q "^# CONFIG_${option} is not set" "$BUILD_DIR/.config"; then
    printf '# CONFIG_%s is not set\n' "$option" >> "$BUILD_DIR/.config"
  fi
}
for option in \
  BUSYBOX SHOW_USAGE LONG_OPTS LFS \
  CAT CP MV RM MKDIR TOUCH CHMOD CHOWN LN LS PWD PRINTF TRUE FALSE TEST TEST1 \
  CLEAR DATE DF ECHO ENV ID KILL PIDOF PS SLEEP STTY TTY UNAME UPTIME WHOAMI YES \
  AWK SED GREP FIND XARGS \
  BASENAME DIFF DIRNAME DU HEAD HEXDUMP LESS MD5SUM OD PATCH READLINK REALPATH \
  SHA1SUM SHA256SUM SORT STAT TAIL TEE TR UNIQ VI WC WGET \
  FEATURE_READLINK_FOLLOW FEATURE_STAT_FORMAT FEATURE_FANCY_HEAD FEATURE_FANCY_TAIL \
  FEATURE_VI_COLON FEATURE_VI_YANKMARK FEATURE_VI_SEARCH FEATURE_VI_USE_SIGNALS \
  FEATURE_WGET_LONG_OPTIONS FEATURE_WGET_TIMEOUT \
  ASH SH_IS_ASH FEATURE_SH_STANDALONE FEATURE_SH_MATH FEATURE_SH_MATH_64 FEATURE_SH_NOFORK \
  ASH_INTERNAL_GLOB ASH_BASH_COMPAT ASH_JOB_CONTROL ASH_ALIAS ASH_RANDOM_SUPPORT ASH_EXPAND_PRMT \
  ASH_ECHO ASH_PRINTF ASH_TEST ASH_SLEEP ASH_HELP ASH_GETOPTS ASH_CMDCMD \
  FEATURE_EDITING FEATURE_TAB_COMPLETION FEATURE_EDITING_SAVEHISTORY FEATURE_EDITING_FANCY_PROMPT \
  FEATURE_EDITING_WINCH; do
  enable_busybox_option "$option"
done
set_busybox_option_value FEATURE_EDITING_HISTORY 255
for option in SH_IS_HUSH BASH_IS_ASH BASH_IS_HUSH HUSH SH_IS_NONE; do
  disable_busybox_option "$option"
done
enable_busybox_option BASH_IS_NONE
set +o pipefail
yes "" | make -C "$SOURCE_DIR" O="$BUILD_DIR" ARCH="$BUSYBOX_ARCH" oldconfig >/dev/null
oldconfig_status="${PIPESTATUS[1]}"
set -o pipefail
if [[ "$oldconfig_status" -ne 0 ]]; then
  exit "$oldconfig_status"
fi
make -C "$SOURCE_DIR" O="$BUILD_DIR" ARCH="$BUSYBOX_ARCH" \
  CC="$CC" AR="$AR" RANLIB="$RANLIB" STRIP="$STRIP" \
  -j"$JOBS" busybox

stage_android_executable "$BUILD_DIR/busybox" "$FINAL_BIN" busybox

upsert_local_property "busybox.path.$ANDROID_ABI" "$FINAL_BIN"
printf '%s\n' "$FINAL_BIN"
