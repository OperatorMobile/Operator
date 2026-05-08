#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT_DIR/scripts/android-runtime-common.sh"

LOCAL_PROPERTIES="$ROOT_DIR/mobile/android/local.properties"
GIT_VERSION="${GIT_VERSION:-2.54.0}"
GIT_SHA256="${GIT_SHA256:-f689162364c10de79ef89aa8dbf48731eb057e34edbbd20aca510ce0154681a3}"
GIT_SOURCE_URL="${GIT_SOURCE_URL:-https://mirrors.kernel.org/pub/software/scm/git/git-$GIT_VERSION.tar.xz}"
CURL_VERSION="${CURL_VERSION:-8.20.0}"
CURL_SHA256="${CURL_SHA256:-4be48e69cf467246cb97d369b85d78a08528f2b37cffef2418ee16e6a4eb596e}"
CURL_SOURCE_URL="${CURL_SOURCE_URL:-https://curl.se/download/curl-$CURL_VERSION.tar.bz2}"
OPENSSL_VERSION="${OPENSSL_VERSION:-3.6.2}"
OPENSSL_SHA256="${OPENSSL_SHA256:-aaf51a1fe064384f811daeaeb4ec4dce7340ec8bd893027eee676af31e83a04f}"
OPENSSL_SOURCE_URL="${OPENSSL_SOURCE_URL:-https://github.com/openssl/openssl/releases/download/openssl-$OPENSSL_VERSION/openssl-$OPENSSL_VERSION.tar.gz}"
ENABLE_GIT_HTTPS="${ENABLE_GIT_HTTPS:-true}"
ANDROID_ABI="${ANDROID_ABI:-arm64-v8a}"
ANDROID_API="${ANDROID_API:-26}"

case "$ANDROID_ABI" in
  arm64-v8a)
    ANDROID_TARGET="${ANDROID_TARGET:-aarch64-linux-android}"
    OPENSSL_ANDROID_TARGET="android-arm64"
    UNAME_M="aarch64"
    ;;
  x86_64)
    ANDROID_TARGET="${ANDROID_TARGET:-x86_64-linux-android}"
    OPENSSL_ANDROID_TARGET="android-x86_64"
    UNAME_M="x86_64"
    ;;
  *)
    echo "Unsupported Android ABI: $ANDROID_ABI" >&2
    exit 1
    ;;
esac

if [[ -f "$LOCAL_PROPERTIES" ]]; then
  SDK_DIR="$(awk -F= '$1 == "sdk.dir" { print $2 }' "$LOCAL_PROPERTIES" | tail -n1)"
else
  SDK_DIR=""
fi

SDK_DIR="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-${SDK_DIR:-}}}"

if [[ -z "$SDK_DIR" || ! -d "$SDK_DIR" ]]; then
  echo "Android SDK not found. Set sdk.dir in mobile/android/local.properties or ANDROID_HOME." >&2
  exit 1
fi

NDK_DIR="${ANDROID_NDK_HOME:-$SDK_DIR/ndk/30.0.14904198}"
TOOLCHAIN_DIR="$NDK_DIR/toolchains/llvm/prebuilt/darwin-x86_64"
CC="$TOOLCHAIN_DIR/bin/${ANDROID_TARGET}${ANDROID_API}-clang"
AR="$TOOLCHAIN_DIR/bin/llvm-ar"
RANLIB="$TOOLCHAIN_DIR/bin/llvm-ranlib"
STRIP="$TOOLCHAIN_DIR/bin/llvm-strip"

for tool in "$CC" "$AR" "$RANLIB"; do
  if [[ ! -x "$tool" ]]; then
    echo "Required Android toolchain executable not found: $tool" >&2
    exit 1
  fi
done

JOBS="${JOBS:-4}"
SOURCE_DIR="$ROOT_DIR/local-artifacts/sources"
ANDROID_RUNTIME_SOURCE_ROOT="$ROOT_DIR/third_party/android-runtime-sources/sources"
DEPS_DIR="$ROOT_DIR/local-artifacts/android-deps/$ANDROID_ABI"
OPENSSL_PREFIX="$DEPS_DIR/openssl/v$OPENSSL_VERSION"
CURL_PREFIX="$DEPS_DIR/curl/v$CURL_VERSION-openssl-$OPENSSL_VERSION"
GIT_SOURCE_ARCHIVE="$SOURCE_DIR/git-$GIT_VERSION.tar.xz"
OPENSSL_SOURCE_ARCHIVE="$SOURCE_DIR/openssl-$OPENSSL_VERSION.tar.gz"
CURL_SOURCE_ARCHIVE="$SOURCE_DIR/curl-$CURL_VERSION.tar.bz2"
GIT_SOURCE_DIR="$ANDROID_RUNTIME_SOURCE_ROOT/git"
OPENSSL_SOURCE_DIR="$ANDROID_RUNTIME_SOURCE_ROOT/openssl"
CURL_SOURCE_DIR="$ANDROID_RUNTIME_SOURCE_ROOT/curl"
BUILD_PARENT="${TMPDIR:-/private/tmp}/operator-git-build"
GIT_BUILD_ROOT="$BUILD_PARENT/git-$GIT_VERSION-$ANDROID_ABI"
OPENSSL_BUILD_ROOT="$BUILD_PARENT/openssl-$OPENSSL_VERSION-$ANDROID_ABI"
CURL_BUILD_ROOT="$BUILD_PARENT/curl-$CURL_VERSION-$ANDROID_ABI"
OUT_DIR="$ROOT_DIR/local-artifacts/android-tools/git/v$GIT_VERSION/$ANDROID_ABI"
FINAL_GIT_BIN="$OUT_DIR/git"
FINAL_REMOTE_HTTP_BIN="$OUT_DIR/git-remote-http"
FINAL_REMOTE_HTTPS_BIN="$OUT_DIR/git-remote-https"
TERMUX_PATCH_DIR="$ROOT_DIR/scripts/android-git-patches/termux-reference"
OPERATOR_PATCH_DIR="$ROOT_DIR/scripts/android-git-patches/operator"

mkdir -p "$SOURCE_DIR" "$BUILD_PARENT" "$OUT_DIR"

sync_runtime_source() {
  "$ROOT_DIR/scripts/sync-android-runtime-sources.sh" "$1"
}

download_if_missing() {
  local url="$1"
  local output="$2"
  if [[ ! -f "$output" ]]; then
    curl -sS --max-time 60 -L "$url" -o "$output"
  fi
}

verify_sha256() {
  local file="$1"
  local expected="$2"
  local label="$3"
  local actual
  actual="$(shasum -a 256 "$file" | awk '{ print $1 }')"
  if [[ "$actual" != "$expected" ]]; then
    echo "Unexpected $label sha256: $actual" >&2
    echo "Expected: $expected" >&2
    exit 1
  fi
}

apply_patch_file() {
  local patch_file="$1"
  echo "Applying $(basename "$patch_file")"
  patch -d "$GIT_BUILD_ROOT" -p1 < "$patch_file"
}

apply_git_android_patches() {
  apply_patch_file "$TERMUX_PATCH_DIR/config.mak.uname.patch"
  apply_patch_file "$TERMUX_PATCH_DIR/compat-posix.h.patch"
  apply_patch_file "$TERMUX_PATCH_DIR/config.c.patch"
  apply_patch_file "$TERMUX_PATCH_DIR/disable-fdsan.patch"
  apply_patch_file "$TERMUX_PATCH_DIR/disable-daemon-syslog.patch"
  apply_patch_file "$TERMUX_PATCH_DIR/run-command.c.patch"
  apply_patch_file "$OPERATOR_PATCH_DIR/ident-android-passwd-fallback.patch"
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

build_openssl() {
  if [[ -f "$OPENSSL_PREFIX/lib/libssl.a" && -f "$OPENSSL_PREFIX/lib/libcrypto.a" ]]; then
    return
  fi

  sync_runtime_source openssl

  rm -rf "$OPENSSL_BUILD_ROOT"
  mkdir -p "$OPENSSL_BUILD_ROOT"
  cp -R "$OPENSSL_SOURCE_DIR"/. "$OPENSSL_BUILD_ROOT"/

  (
    cd "$OPENSSL_BUILD_ROOT"
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

build_curl() {
  if [[ -f "$CURL_PREFIX/lib/libcurl.a" ]]; then
    return
  fi

  build_openssl
  sync_runtime_source curl

  rm -rf "$CURL_BUILD_ROOT"
  mkdir -p "$CURL_BUILD_ROOT"
  cp -R "$CURL_SOURCE_DIR"/. "$CURL_BUILD_ROOT"/

  (
    cd "$CURL_BUILD_ROOT"
    if [[ ! -x ./configure && -x ./buildconf ]]; then
      ./buildconf
    fi

    export PATH="$TOOLCHAIN_DIR/bin:$PATH"
    export CC="$CC"
    export AR="$AR"
    export RANLIB="$RANLIB"
    export STRIP="$STRIP"
    export CPPFLAGS="-I$OPENSSL_PREFIX/include"
    export LDFLAGS="-L$OPENSSL_PREFIX/lib"
    export LIBS="-ldl"
    export PKG_CONFIG_PATH="$OPENSSL_PREFIX/lib/pkgconfig"
    export PKG_CONFIG_LIBDIR="$OPENSSL_PREFIX/lib/pkgconfig"
    ./configure \
      --host="$ANDROID_TARGET" \
      --prefix="$CURL_PREFIX" \
      --disable-shared \
      --enable-static \
      --with-openssl="$OPENSSL_PREFIX" \
      --without-ca-bundle \
      --with-ca-path=/system/etc/security/cacerts \
      --without-zlib \
      --without-brotli \
      --without-zstd \
      --without-nghttp2 \
      --without-nghttp3 \
      --without-ngtcp2 \
      --without-libidn2 \
      --without-libpsl \
      --disable-ldap \
      --disable-ldaps \
      --disable-rtsp \
      --disable-dict \
      --disable-telnet \
      --disable-tftp \
      --disable-pop3 \
      --disable-imap \
      --disable-smb \
      --disable-smtp \
      --disable-gopher \
      --disable-mqtt \
      --disable-manual \
      --disable-threaded-resolver
    make -j"$JOBS"
    make install
  )
}

sync_runtime_source git

rm -rf "$GIT_BUILD_ROOT"
mkdir -p "$GIT_BUILD_ROOT"
cp -R "$GIT_SOURCE_DIR"/. "$GIT_BUILD_ROOT"/
apply_git_android_patches

GIT_TARGETS=(git)
GIT_MAKE_FLAGS=(
  SHELL=/bin/sh
  CC="$CC"
  AR="$AR"
  uname_S=Linux
  uname_M="$UNAME_M"
  NO_NSEC=YesPlease
  NO_GETTEXT=YesPlease
  NO_ICONV=YesPlease
  NO_OPENSSL=YesPlease
  NO_EXPAT=YesPlease
  NO_PERL=YesPlease
  NO_PYTHON=YesPlease
  NO_TCLTK=YesPlease
  NO_INSTALL_HARDLINKS=YesPlease
  NO_GECOS_IN_PWENT=YesPlease
  PTHREAD_LIBS=
  CSPRNG_METHOD=arc4random
  SHELL_PATH=/bin/sh
  DEFAULT_PAGER=cat
  DEFAULT_EDITOR=vi
)

if [[ "$ENABLE_GIT_HTTPS" == "true" ]]; then
  build_curl
  GIT_TARGETS+=(git-remote-http)
  GIT_MAKE_FLAGS+=(
    CURL_CFLAGS="-I$CURL_PREFIX/include"
    CURL_LDFLAGS="$CURL_PREFIX/lib/libcurl.a $OPENSSL_PREFIX/lib/libssl.a $OPENSSL_PREFIX/lib/libcrypto.a -ldl"
  )
else
  GIT_MAKE_FLAGS+=(NO_CURL=YesPlease)
fi

make -C "$GIT_BUILD_ROOT" -j"$JOBS" "${GIT_TARGETS[@]}" "${GIT_MAKE_FLAGS[@]}"

stage_android_executable "$GIT_BUILD_ROOT/git" "$FINAL_GIT_BIN" git
if [[ "$ENABLE_GIT_HTTPS" == "true" ]]; then
  stage_android_executable "$GIT_BUILD_ROOT/git-remote-http" "$FINAL_REMOTE_HTTP_BIN" git-remote-http
  stage_android_executable "$GIT_BUILD_ROOT/git-remote-http" "$FINAL_REMOTE_HTTPS_BIN" git-remote-https
fi

for output in "$FINAL_GIT_BIN" "$FINAL_REMOTE_HTTP_BIN" "$FINAL_REMOTE_HTTPS_BIN"; do
  if [[ -f "$output" ]]; then
    echo "$output"
  fi
done

upsert_local_property "git.path.$ANDROID_ABI" "$FINAL_GIT_BIN"
if [[ "$ENABLE_GIT_HTTPS" == "true" ]]; then
  upsert_local_property "gitRemoteHttp.path.$ANDROID_ABI" "$FINAL_REMOTE_HTTP_BIN"
fi
