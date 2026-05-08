#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/android-runtime-common.sh"

configure_android_toolchain
require_host_tool make

sync_android_runtime_source gnu-make
sync_android_runtime_source gnulib

SOURCE_DIR="$(android_source_dir gnu-make)"
GNULIB_SOURCE_DIR="$(android_source_dir gnulib)"
MAKE_VERSION="$(android_source_field gnu-make ref | awk -F/ '{ print $NF }')"
BUILD_DIR="$(build_parent_dir)/gnu-make-$MAKE_VERSION-$ANDROID_ABI"
OUT_DIR="$ROOT_DIR/local-artifacts/android-tools/gnu-make/$MAKE_VERSION/$ANDROID_ABI"
FINAL_BIN="$OUT_DIR/make"

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR" "$OUT_DIR"
cp -R "$SOURCE_DIR"/. "$BUILD_DIR"/

(
  cd "$BUILD_DIR"
  if [[ ! -x ./configure ]]; then
    if [[ -x ./bootstrap ]]; then
      GNULIB_SRCDIR="$GNULIB_SOURCE_DIR" ./bootstrap --skip-po --no-git --copy
    else
      autoreconf -fi
    fi
  fi

  export MAKEINFO=true
  export ac_cv_func_getloadavg=no
  export ac_cv_func_getpgrp_void=yes
  export ac_cv_func_setpgrp_void=yes
  export gl_cv_func_working_mktime=yes

  ./configure \
    --host="$GNU_HOST" \
    --prefix=/usr \
    --disable-nls \
    --without-guile

  make -j"$JOBS" MAKE_MAINTAINER_MODE= MAKE_CFLAGS=
)

stage_android_executable "$BUILD_DIR/make" "$FINAL_BIN" make

upsert_local_property "make.path.$ANDROID_ABI" "$FINAL_BIN"
printf '%s\n' "$FINAL_BIN"
