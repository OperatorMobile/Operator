#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/android-runtime-common.sh"

configure_android_toolchain
require_host_tool perl
require_host_tool make

sync_android_runtime_source openssh-portable
sync_android_runtime_source zlib
sync_android_runtime_source openssl

OPENSSH_SOURCE_DIR="$(android_source_dir openssh-portable)"
ZLIB_SOURCE_DIR="$(android_source_dir zlib)"
OPENSSL_SOURCE_DIR="$(android_source_dir openssl)"
OPENSSH_VERSION="$(android_source_field openssh-portable ref | awk -F/ '{ print $NF }')"
ZLIB_VERSION="$(android_source_field zlib ref | awk -F/ '{ print $NF }')"
OPENSSL_VERSION="$(android_source_field openssl ref | awk -F/ '{ print $NF }')"
DEPS_DIR="$ROOT_DIR/local-artifacts/android-deps/$ANDROID_ABI"
ZLIB_PREFIX="$DEPS_DIR/zlib/$ZLIB_VERSION"
OPENSSL_PREFIX="$DEPS_DIR/openssl/$OPENSSL_VERSION"
BUILD_ROOT="$(build_parent_dir)"
ZLIB_BUILD_DIR="$BUILD_ROOT/zlib-$ZLIB_VERSION-$ANDROID_ABI"
OPENSSL_BUILD_DIR="$BUILD_ROOT/openssl-$OPENSSL_VERSION-$ANDROID_ABI"
OPENSSH_BUILD_DIR="$BUILD_ROOT/openssh-$OPENSSH_VERSION-$ANDROID_ABI"
OUT_DIR="$ROOT_DIR/local-artifacts/android-tools/openssh/$OPENSSH_VERSION/$ANDROID_ABI"

build_zlib() {
  if [[ -f "$ZLIB_PREFIX/lib/libz.a" ]]; then
    return
  fi

  rm -rf "$ZLIB_BUILD_DIR"
  mkdir -p "$ZLIB_BUILD_DIR"
  cp -R "$ZLIB_SOURCE_DIR"/. "$ZLIB_BUILD_DIR"/
  (
    cd "$ZLIB_BUILD_DIR"
    CHOST="$ANDROID_TARGET" ./configure --static --prefix="$ZLIB_PREFIX"
    make -j"$JOBS" CC="$CC" AR="$AR" RANLIB="$RANLIB"
    make install
  )
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

build_zlib
build_openssl

rm -rf "$OPENSSH_BUILD_DIR"
mkdir -p "$OPENSSH_BUILD_DIR" "$OUT_DIR"
cp -R "$OPENSSH_SOURCE_DIR"/. "$OPENSSH_BUILD_DIR"/

patch_android_openssh_sources() {
  perl -0pi -e 's/#include <string\.h>/#include <string.h>\n#if defined(__ANDROID__) \&\& !defined(HAVE_EXPLICIT_BZERO)\n#ifdef bzero\n#undef bzero\n#endif\nstatic void operator_bzero(void *p, size_t n) { memset(p, 0, n); }\n#define bzero operator_bzero\n#endif/' \
    "$OPENSSH_BUILD_DIR/openbsd-compat/explicit_bzero.c"

  perl -pi -e 's/copy->pw_name = xstrdup\(pw->pw_name\);/copy->pw_name = xstrdup(pw->pw_name == NULL ? "shell" : pw->pw_name);/' "$OPENSSH_BUILD_DIR/misc.c"
  perl -pi -e 's/copy->pw_gecos = xstrdup\(pw->pw_gecos\);/copy->pw_gecos = xstrdup(pw->pw_gecos == NULL ? "" : pw->pw_gecos);/' "$OPENSSH_BUILD_DIR/misc.c"
  perl -pi -e 's/copy->pw_class = xstrdup\(pw->pw_class\);/copy->pw_class = xstrdup(pw->pw_class == NULL ? "" : pw->pw_class);/' "$OPENSSH_BUILD_DIR/misc.c"
  perl -pi -e 's#copy->pw_dir = xstrdup\(pw->pw_dir\);#copy->pw_dir = xstrdup(pw->pw_dir == NULL ? (getenv("HOME") == NULL ? "/" : getenv("HOME")) : pw->pw_dir);#' "$OPENSSH_BUILD_DIR/misc.c"
  perl -pi -e 's#copy->pw_shell = xstrdup\(pw->pw_shell\);#copy->pw_shell = xstrdup(pw->pw_shell == NULL ? "/system/bin/sh" : pw->pw_shell);#' "$OPENSSH_BUILD_DIR/misc.c"

  cat > "$OPENSSH_BUILD_DIR/openbsd-compat/getrrsetbyname.c" <<'EOF_GETRRSET_ANDROID'
#include "includes.h"
#include "getrrsetbyname.h"

#ifndef HAVE_GETRRSETBYNAME
int
getrrsetbyname(const char *hostname, unsigned int rdclass,
    unsigned int rdtype, unsigned int flags, struct rrsetinfo **res)
{
	(void)hostname;
	(void)rdclass;
	(void)rdtype;
	(void)flags;
	if (res != NULL)
		*res = NULL;
	return ERRSET_FAIL;
}

void
freerrset(struct rrsetinfo *rrset)
{
	(void)rrset;
}
#endif
EOF_GETRRSET_ANDROID
}

patch_android_openssh_sources

(
  cd "$OPENSSH_BUILD_DIR"
  if [[ ! -x ./configure && -x ./autoreconf.sh ]]; then
    ./autoreconf.sh
  fi

  export CPPFLAGS="-DHAVE_ATTRIBUTE__SENTINEL__=1 -I$OPENSSL_PREFIX/include -I$ZLIB_PREFIX/include"
  export LDFLAGS="-L$OPENSSL_PREFIX/lib -L$ZLIB_PREFIX/lib"
  export LIBS="-lssl -lcrypto -lz -ldl"
  export ac_cv_func_bzero=yes
  export ac_cv_func_getpgrp_void=yes
  export ac_cv_func_setpgrp_void=yes
  export ac_cv_have_decl_AI_NUMERICSERV=yes
  export ac_cv_have_decl_BROKEN_STRNVIS=no
  export ac_cv_search_clock_gettime=no
  export ac_cv_header_sys_un_h=yes

  ./configure \
    --host="$GNU_HOST" \
    --prefix=/usr \
    --sysconfdir=/data/local/tmp/operator-ssh \
    --with-ssl-dir="$OPENSSL_PREFIX" \
    --with-zlib="$ZLIB_PREFIX" \
    --without-openssl-header-check \
    --without-shadow \
    --without-pam \
    --disable-pkcs11 \
    --disable-security-key \
    --disable-strip

  make -j"$JOBS" ssh scp sftp ssh-add ssh-agent ssh-keygen ssh-keyscan
)

for tool in ssh scp sftp ssh-add ssh-agent ssh-keygen ssh-keyscan; do
  stage_android_executable "$OPENSSH_BUILD_DIR/$tool" "$OUT_DIR/$tool" "$tool"
done

upsert_local_property "ssh.path.$ANDROID_ABI" "$OUT_DIR/ssh"
upsert_local_property "scp.path.$ANDROID_ABI" "$OUT_DIR/scp"
upsert_local_property "sftp.path.$ANDROID_ABI" "$OUT_DIR/sftp"
upsert_local_property "sshAdd.path.$ANDROID_ABI" "$OUT_DIR/ssh-add"
upsert_local_property "sshAgent.path.$ANDROID_ABI" "$OUT_DIR/ssh-agent"
upsert_local_property "sshKeygen.path.$ANDROID_ABI" "$OUT_DIR/ssh-keygen"
upsert_local_property "sshKeyscan.path.$ANDROID_ABI" "$OUT_DIR/ssh-keyscan"

printf '%s\n' "$OUT_DIR"
