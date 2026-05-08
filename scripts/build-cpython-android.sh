#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/android-runtime-common.sh"

configure_android_toolchain
require_host_tool make
require_host_tool sed
require_host_tool perl
require_host_tool cmake
require_host_tool pkg-config
require_host_tool unzip

sync_android_runtime_source cpython
sync_android_runtime_source zlib
sync_android_runtime_source openssl
sync_android_runtime_source libffi
sync_android_runtime_source bzip2
sync_android_runtime_source xz
sync_android_runtime_source sqlite

SOURCE_DIR="$(android_source_dir cpython)"
ZLIB_SOURCE_DIR="$(android_source_dir zlib)"
OPENSSL_SOURCE_DIR="$(android_source_dir openssl)"
LIBFFI_SOURCE_DIR="$(android_source_dir libffi)"
BZIP2_SOURCE_DIR="$(android_source_dir bzip2)"
XZ_SOURCE_DIR="$(android_source_dir xz)"
SQLITE_SOURCE_DIR="$(android_source_dir sqlite)"
CPYTHON_VERSION="$(android_source_field cpython ref | awk -F/ '{ print $NF }')"
ZLIB_VERSION="$(android_source_field zlib ref | awk -F/ '{ print $NF }')"
OPENSSL_VERSION="$(android_source_field openssl ref | awk -F/ '{ print $NF }')"
LIBFFI_VERSION="$(android_source_field libffi ref | awk -F/ '{ print $NF }')"
BZIP2_VERSION="$(android_source_field bzip2 ref | awk -F/ '{ print $NF }')"
XZ_VERSION="$(android_source_field xz ref | awk -F/ '{ print $NF }')"
SQLITE_VERSION="$(android_source_field sqlite ref | awk -F/ '{ print $NF }')"
BUILD_ROOT="$(build_parent_dir)"
HOST_BUILD_DIR="$BUILD_ROOT/cpython-$CPYTHON_VERSION-host"
HOST_PREFIX="$HOST_BUILD_DIR/install"
ANDROID_BUILD_DIR="$BUILD_ROOT/cpython-$CPYTHON_VERSION-$ANDROID_ABI"
DEPS_DIR="$ROOT_DIR/local-artifacts/android-deps/$ANDROID_ABI"
ZLIB_PREFIX="$DEPS_DIR/zlib/$ZLIB_VERSION"
OPENSSL_PREFIX="$DEPS_DIR/openssl/$OPENSSL_VERSION"
LIBFFI_PREFIX="$DEPS_DIR/libffi/$LIBFFI_VERSION"
BZIP2_PREFIX="$DEPS_DIR/bzip2/$BZIP2_VERSION"
XZ_PREFIX="$DEPS_DIR/xz/$XZ_VERSION"
SQLITE_PREFIX="$DEPS_DIR/sqlite/$SQLITE_VERSION"
ZLIB_BUILD_DIR="$BUILD_ROOT/zlib-$ZLIB_VERSION-$ANDROID_ABI"
OPENSSL_BUILD_DIR="$BUILD_ROOT/openssl-$OPENSSL_VERSION-$ANDROID_ABI"
LIBFFI_BUILD_DIR="$BUILD_ROOT/libffi-$LIBFFI_VERSION-$ANDROID_ABI"
BZIP2_BUILD_DIR="$BUILD_ROOT/bzip2-$BZIP2_VERSION-$ANDROID_ABI"
XZ_BUILD_DIR="$BUILD_ROOT/xz-$XZ_VERSION-$ANDROID_ABI"
SQLITE_BUILD_DIR="$BUILD_ROOT/sqlite-$SQLITE_VERSION-$ANDROID_ABI"
ZLIB_PIC_MARKER="$ZLIB_PREFIX/.operator-pic-built"
OUT_DIR="$ROOT_DIR/local-artifacts/android-tools/cpython/$CPYTHON_VERSION/$ANDROID_ABI"
RUNTIME_HOME="$OUT_DIR/python-home"
FINAL_BIN="$OUT_DIR/python3"

build_triplet() {
  local machine
  machine="$(uname -m)"
  case "$(uname -s)" in
    Darwin) printf '%s-apple-darwin\n' "$machine" ;;
    Linux) printf '%s-pc-linux-gnu\n' "$machine" ;;
    *) printf '%s-unknown-unknown\n' "$machine" ;;
  esac
}

mkdir -p "$OUT_DIR"

build_zlib() {
  if [[ -f "$ZLIB_PREFIX/lib/libz.a" && -f "$ZLIB_PIC_MARKER" ]]; then
    return
  fi

  rm -rf "$ZLIB_BUILD_DIR"
  mkdir -p "$ZLIB_BUILD_DIR"
  cp -R "$ZLIB_SOURCE_DIR"/. "$ZLIB_BUILD_DIR"/
  (
    cd "$ZLIB_BUILD_DIR"
    CHOST="$ANDROID_TARGET" CFLAGS="-fPIC -O3" ./configure --static --prefix="$ZLIB_PREFIX"
    make -j"$JOBS" CC="$CC" AR="$AR" RANLIB="$RANLIB" CFLAGS="-fPIC -O3"
    make install
  )
  touch "$ZLIB_PIC_MARKER"
}

build_openssl() {
  if [[ -f "$OPENSSL_PREFIX/lib/libssl.a" && -f "$OPENSSL_PREFIX/lib/libcrypto.a" ]]; then
    return
  fi

  rm -rf "$OPENSSL_BUILD_DIR"
  mkdir -p "$OPENSSL_BUILD_DIR"
  cp -R "$OPENSSL_SOURCE_DIR"/. "$OPENSSL_BUILD_DIR"/
  (
    cd "$OPENSSL_BUILD_DIR"
    export ANDROID_NDK_ROOT="$NDK_DIR"
    export PATH="$TOOLCHAIN_DIR/bin:$PATH"
    ./Configure "$OPENSSL_ANDROID_TARGET" \
      -D__ANDROID_API__="$ANDROID_API" \
      no-shared \
      no-tests \
      no-apps \
      --prefix="$OPENSSL_PREFIX" \
      --openssldir=/system/etc/security
    make -j"$JOBS"
    make install_sw
  )
}

write_bzip2_pkg_config() {
  mkdir -p "$BZIP2_PREFIX/lib/pkgconfig"
  {
    printf 'prefix=%s\n' "$BZIP2_PREFIX"
    printf 'exec_prefix=${prefix}\n'
    printf 'libdir=${exec_prefix}/lib\n'
    printf 'includedir=${prefix}/include\n'
    printf '\n'
    printf 'Name: bzip2\n'
    printf 'Description: lossless, block-sorting data compression library\n'
    printf 'Version: 1.0.8\n'
    printf 'Libs: -L${libdir} -lbz2\n'
    printf 'Cflags: -I${includedir}\n'
  } > "$BZIP2_PREFIX/lib/pkgconfig/bzip2.pc"
}

build_bzip2() {
  if [[ -f "$BZIP2_PREFIX/lib/libbz2.a" ]]; then
    write_bzip2_pkg_config
    return
  fi

  rm -rf "$BZIP2_BUILD_DIR"
  mkdir -p "$BZIP2_BUILD_DIR"
  cp -R "$BZIP2_SOURCE_DIR"/. "$BZIP2_BUILD_DIR"/
  (
    cd "$BZIP2_BUILD_DIR"
    make -j"$JOBS" libbz2.a \
      CC="$CC" \
      AR="$AR" \
      RANLIB="$RANLIB" \
      CFLAGS="-Wall -Winline -O3 -fPIC -D_FILE_OFFSET_BITS=64"
  )
  mkdir -p "$BZIP2_PREFIX/include" "$BZIP2_PREFIX/lib"
  cp "$BZIP2_BUILD_DIR/bzlib.h" "$BZIP2_PREFIX/include/bzlib.h"
  cp "$BZIP2_BUILD_DIR/libbz2.a" "$BZIP2_PREFIX/lib/libbz2.a"
  "$RANLIB" "$BZIP2_PREFIX/lib/libbz2.a"
  write_bzip2_pkg_config
}

build_liblzma() {
  if [[ -f "$XZ_PREFIX/lib/liblzma.a" ]]; then
    return
  fi

  rm -rf "$XZ_BUILD_DIR"
  cmake -S "$XZ_SOURCE_DIR" -B "$XZ_BUILD_DIR" \
    -DCMAKE_TOOLCHAIN_FILE="$NDK_DIR/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="$ANDROID_ABI" \
    -DANDROID_PLATFORM="android-$ANDROID_API" \
    -DCMAKE_INSTALL_PREFIX="$XZ_PREFIX" \
    -DCMAKE_BUILD_TYPE=Release \
    -DBUILD_SHARED_LIBS=OFF \
    -DXZ_NLS=OFF \
    -DXZ_DOC=OFF \
    -DXZ_DOXYGEN=OFF \
    -DXZ_TOOL_XZ=OFF \
    -DXZ_TOOL_XZDEC=OFF \
    -DXZ_TOOL_LZMADEC=OFF \
    -DXZ_TOOL_LZMAINFO=OFF \
    -DXZ_TOOL_SCRIPTS=OFF \
    -DXZ_TOOL_SYMLINKS=OFF \
    -DXZ_TOOL_SYMLINKS_LZMA=OFF
  cmake --build "$XZ_BUILD_DIR" --parallel "$JOBS"
  cmake --install "$XZ_BUILD_DIR"
}

build_libffi() {
  if [[ -f "$LIBFFI_PREFIX/lib/libffi.a" ]]; then
    return
  fi

  require_host_tool autoreconf
  rm -rf "$LIBFFI_BUILD_DIR"
  mkdir -p "$LIBFFI_BUILD_DIR"
  cp -R "$LIBFFI_SOURCE_DIR"/. "$LIBFFI_BUILD_DIR"/
  (
    cd "$LIBFFI_BUILD_DIR"
    export LIBTOOLIZE="${LIBTOOLIZE:-glibtoolize}"
    ./autogen.sh
    ./configure \
      --host="$GNU_HOST" \
      --build="$(build_triplet)" \
      --prefix="$LIBFFI_PREFIX" \
      --disable-shared \
      --enable-static \
      --disable-docs \
      --disable-multi-os-directory \
      --with-pic
    make -j"$JOBS"
    make install
  )
}

build_sqlite() {
  if [[ -f "$SQLITE_PREFIX/lib/libsqlite3.a" ]]; then
    return
  fi

  rm -rf "$SQLITE_BUILD_DIR"
  mkdir -p "$SQLITE_BUILD_DIR"
  (
    cd "$SQLITE_BUILD_DIR"
    CC="$CC" \
    AR="$AR" \
    RANLIB="$RANLIB" \
    CFLAGS="-O3 -fPIC" \
      "$SQLITE_SOURCE_DIR/configure" \
        --host="$GNU_HOST" \
        --build="$(build_triplet)" \
        --prefix="$SQLITE_PREFIX" \
        --disable-shared \
        --disable-readline \
        --disable-tcl \
        --disable-load-extension \
        --column-metadata
    make -j"$JOBS" libsqlite3.a sqlite3.h
    make install-lib install-headers install-pc
  )
}

pkg_config_value() {
  PKG_CONFIG_PATH= PKG_CONFIG_LIBDIR="$PKG_CONFIG_LIBDIR" pkg-config "$@"
}

install_pip_into_runtime() {
  local pip_wheel
  local site_packages
  local distlib_scripts
  local script_name

  pip_wheel="$(find "$RUNTIME_HOME/lib/python3.13/ensurepip/_bundled" -name 'pip-*.whl' | sort | tail -n 1)"
  if [[ -z "$pip_wheel" || ! -f "$pip_wheel" ]]; then
    echo "Expected bundled pip wheel under $RUNTIME_HOME/lib/python3.13/ensurepip/_bundled" >&2
    exit 1
  fi

  site_packages="$RUNTIME_HOME/lib/python3.13/site-packages"
  mkdir -p "$site_packages" "$RUNTIME_HOME/bin"
  rm -rf "$site_packages/pip" "$site_packages"/pip-*.dist-info
  unzip -q "$pip_wheel" -d "$site_packages"

  distlib_scripts="$site_packages/pip/_vendor/distlib/scripts.py"
  if [[ -f "$distlib_scripts" && ! "$(grep -F "/system/bin/env python3" "$distlib_scripts" || true)" ]]; then
    perl -0pi -e "s/(elif not sysconfig\\.is_python_build\\(\\):\\n\\s+executable = get_executable\\(\\)\\n)/\$1            if sys.platform == 'android':\\n                executable = '\\/system\\/bin\\/env python3'\\n/" "$distlib_scripts"
  fi

  for script_name in pip pip3 pip3.13; do
    {
      printf '%s\n' '#!/system/bin/sh'
      printf '%s\n' 'exec python3 -m pip "$@"'
    } > "$RUNTIME_HOME/bin/$script_name"
    chmod 0755 "$RUNTIME_HOME/bin/$script_name"
  done
}

if [[ ! -x "$HOST_PREFIX/bin/python3" ]]; then
  rm -rf "$HOST_BUILD_DIR"
  mkdir -p "$HOST_BUILD_DIR"
  (
    cd "$HOST_BUILD_DIR"
    unset CC CXX AR RANLIB STRIP LD CPPFLAGS CFLAGS CXXFLAGS LDFLAGS
    "$SOURCE_DIR/configure" \
      --prefix="$HOST_PREFIX" \
      --without-ensurepip \
      --disable-test-modules
    make -j"$JOBS"
    make install
  )
fi

build_zlib
build_openssl
build_bzip2
build_liblzma
build_libffi
build_sqlite

rm -rf "$ANDROID_BUILD_DIR" "$RUNTIME_HOME"
mkdir -p "$ANDROID_BUILD_DIR"

(
  cd "$ANDROID_BUILD_DIR"
  export ac_cv_file__dev_ptmx=no
  export ac_cv_file__dev_ptc=no
  export ac_cv_func_getentropy=no
  export ac_cv_func_sendfile=no
  export ac_cv_func_shm_open=no
  export ac_cv_func_shm_unlink=no
  export ac_cv_header_bluetooth_bluetooth_h=no
  export ac_cv_header_bluetooth_h=no
  export PKG_CONFIG_LIBDIR="$OPENSSL_PREFIX/lib/pkgconfig:$ZLIB_PREFIX/lib/pkgconfig:$BZIP2_PREFIX/lib/pkgconfig:$XZ_PREFIX/lib/pkgconfig:$LIBFFI_PREFIX/lib/pkgconfig:$SQLITE_PREFIX/lib/pkgconfig"
  export PKG_CONFIG_PATH="$PKG_CONFIG_LIBDIR"
  export BZIP2_CFLAGS="$(pkg_config_value --cflags bzip2)"
  export BZIP2_LIBS="$(pkg_config_value --libs bzip2)"
  export LIBLZMA_CFLAGS="$(pkg_config_value --cflags liblzma)"
  export LIBLZMA_LIBS="$(pkg_config_value --libs liblzma)"
  export LIBFFI_CFLAGS="$(pkg_config_value --cflags libffi)"
  export LIBFFI_LIBS="$(pkg_config_value --libs libffi)"
  export LIBSQLITE3_CFLAGS="$(pkg_config_value --cflags 'sqlite3 >= 3.15.2')"
  export LIBSQLITE3_LIBS="$(pkg_config_value --static --libs 'sqlite3 >= 3.15.2')"
  export CPPFLAGS="-I$ZLIB_PREFIX/include -I$OPENSSL_PREFIX/include -I$BZIP2_PREFIX/include -I$XZ_PREFIX/include -I$SQLITE_PREFIX/include $LIBFFI_CFLAGS"
  export LDFLAGS="-L$ZLIB_PREFIX/lib -L$OPENSSL_PREFIX/lib -L$BZIP2_PREFIX/lib -L$XZ_PREFIX/lib -L$LIBFFI_PREFIX/lib -L$SQLITE_PREFIX/lib"
  export LIBS="-ldl"

  "$SOURCE_DIR/configure" \
    --host="$GNU_HOST" \
    --build="$(build_triplet)" \
    --prefix=/usr/local \
    --with-build-python="$HOST_PREFIX/bin/python3" \
    --with-openssl="$OPENSSL_PREFIX" \
    --enable-shared \
    --without-ensurepip \
    --disable-test-modules

  if ! make -j"$JOBS"; then
    make -j1
  fi
  make install DESTDIR="$ANDROID_BUILD_DIR/install-root"
)

PYTHON_LAUNCHER="$ANDROID_BUILD_DIR/install-root/usr/local/bin/python3.13"
if [[ ! -x "$PYTHON_LAUNCHER" ]]; then
  echo "Expected Android Python launcher not found: $PYTHON_LAUNCHER" >&2
  exit 1
fi
stage_android_executable "$PYTHON_LAUNCHER" "$FINAL_BIN" python3

mkdir -p "$RUNTIME_HOME"
cp -R "$ANDROID_BUILD_DIR/install-root/usr/local"/. "$RUNTIME_HOME"/
install_pip_into_runtime

upsert_local_property "python3.path.$ANDROID_ABI" "$FINAL_BIN"
upsert_local_property "pythonHome.path.$ANDROID_ABI" "$RUNTIME_HOME"

printf '%s\n' "$FINAL_BIN"
printf '%s\n' "$RUNTIME_HOME"
