#!/usr/bin/env bash
set -u

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_PROJECT="$PROJECT_ROOT/mobile/android"
LOCAL_PROPERTIES="$ANDROID_PROJECT/local.properties"
EXPECTED_NDK_VERSION="30.0.14904198"
REQUIRED_PLATFORM="android-35"
OPTIONAL_PLATFORM="android-36.1"

errors=0
warnings=0

pass() {
  printf 'PASS %s\n' "$1"
}

warn() {
  warnings=$((warnings + 1))
  printf 'WARN %s\n' "$1"
}

fail() {
  errors=$((errors + 1))
  printf 'FAIL %s\n' "$1"
}

read_property() {
  local key="$1"

  if [[ -f "$LOCAL_PROPERTIES" ]]; then
    sed -n "s/^$key=//p" "$LOCAL_PROPERTIES" | tail -n 1
  fi
}

check_executable() {
  local label="$1"
  local path="$2"

  if [[ -x "$path" ]]; then
    pass "$label: $path"
  else
    fail "$label missing or not executable: $path"
  fi
}

command_path() {
  command -v "$1" 2>/dev/null || true
}

abi_to_rust_target() {
  case "$1" in
    arm64-v8a)
      printf 'aarch64-linux-android\n'
      ;;
    x86_64)
      printf 'x86_64-linux-android\n'
      ;;
    armeabi-v7a)
      printf 'armv7-linux-androideabi\n'
      ;;
    x86)
      printf 'i686-linux-android\n'
      ;;
    *)
      printf '\n'
      ;;
  esac
}

property_path_exists() {
  local label="$1"
  local value="$2"
  local resolved="$value"

  resolved="$(resolve_property_path "$value")"

  if [[ -f "$resolved" ]]; then
    pass "$label: $resolved"
  else
    fail "$label points at missing file: $resolved"
  fi
}

resolve_property_path() {
  local value="$1"

  if [[ "$value" == /* ]]; then
    printf '%s\n' "$value"
  else
    printf '%s\n' "$ANDROID_PROJECT/$value"
  fi
}

optional_tool_property() {
  local key="$1"
  local label="$2"
  local value
  local resolved

  value="$(read_property "$key")"
  if [[ -z "$value" ]]; then
    warn "$label not configured; set $key for fuller on-device development support"
    return
  fi

  resolved="$(resolve_property_path "$value")"
  if [[ -x "$resolved" && -f "$resolved" ]]; then
    pass "$label: $resolved"
  else
    fail "$label configured but missing or not executable: $resolved"
  fi
}

optional_runtime_dir_property() {
  local key="$1"
  local label="$2"
  local value
  local resolved

  value="$(read_property "$key")"
  if [[ -z "$value" ]]; then
    warn "$label not configured; set $key to package the runtime support files"
    return
  fi

  resolved="$(resolve_property_path "$value")"
  if [[ -d "$resolved" ]]; then
    pass "$label: $resolved"
  else
    fail "$label configured but missing directory: $resolved"
  fi
}

check_optional_android_toolchain_contents() {
  local key="$1"
  local value
  local resolved

  value="$(read_property "$key")"
  if [[ -z "$value" ]]; then
    return
  fi

  resolved="$(resolve_property_path "$value")"
  if [[ ! -d "$resolved/usr" ]]; then
    fail "Android toolchain configured but missing usr tree: $resolved/usr"
    return
  fi

  if [[ -f "$resolved/operator-toolchain-manifest.tsv" ]]; then
    pass "Android toolchain manifest present"
  elif [[ -f "$resolved/manifest/operator-runtime-sdk.json" ]]; then
    pass "Android toolchain is backed by Runtime SDK manifest"
  else
    warn "Android toolchain manifest missing: $resolved/operator-toolchain-manifest.tsv"
  fi
}

check_optional_runtime_sdk_contents() {
  local key="$1"
  local value
  local resolved

  value="$(read_property "$key")"
  if [[ -z "$value" ]]; then
    return
  fi

  resolved="$(resolve_property_path "$value")"
  if [[ ! -d "$resolved/usr" ]]; then
    fail "Runtime SDK configured but missing usr tree: $resolved/usr"
    return
  fi

  if [[ -f "$resolved/manifest/operator-runtime-sdk.json" ]]; then
    pass "Runtime SDK manifest present"
  else
    fail "Runtime SDK manifest missing: $resolved/manifest/operator-runtime-sdk.json"
  fi
  if [[ -f "$resolved/manifest/operator-runtime-sdk.lock" ]]; then
    pass "Runtime SDK lock present"
  else
    fail "Runtime SDK lock missing: $resolved/manifest/operator-runtime-sdk.lock"
  fi
  if [[ -f "$resolved/manifest/SHA256SUMS" ]]; then
    pass "Runtime SDK SHA256SUMS present"
  else
    fail "Runtime SDK SHA256SUMS missing: $resolved/manifest/SHA256SUMS"
  fi
}

check_python_runtime_contents() {
  local key="$1"
  local value
  local resolved
  local tool
  local module
  local dynload_dir

  value="$(read_property "$key")"
  if [[ -z "$value" ]]; then
    return
  fi

  resolved="$(resolve_property_path "$value")"
  if [[ ! -d "$resolved" ]]; then
    return
  fi

  for tool in pip pip3 pip3.13; do
    if [[ -x "$resolved/bin/$tool" ]]; then
      pass "CPython bundled $tool: $resolved/bin/$tool"
    else
      fail "CPython bundled $tool missing or not executable: $resolved/bin/$tool"
    fi
  done

  dynload_dir="$(find "$resolved/lib" -type d -name lib-dynload | head -n 1)"
  if [[ -z "$dynload_dir" ]]; then
    fail "CPython lib-dynload directory missing under $resolved/lib"
    return
  fi

  for module in _bz2 _ctypes _hashlib _lzma _sqlite3 _ssl; do
    if find "$dynload_dir" -maxdepth 1 -name "$module.cpython-*.so" | grep -q .; then
      pass "CPython extension module $module present"
    else
      fail "CPython extension module $module missing from $dynload_dir"
    fi
  done
}

check_python_dev_libs_contents() {
  local key="$1"
  local value
  local resolved
  local header

  value="$(read_property "$key")"
  if [[ -z "$value" ]]; then
    return
  fi

  resolved="$(resolve_property_path "$value")"
  if [[ ! -d "$resolved" ]]; then
    return
  fi

  for header in Python.h openssl/ssl.h sqlite3.h ffi.h bzlib.h lzma.h zlib.h; do
    if [[ -f "$resolved/include/$header" ]]; then
      pass "Python dev header $header present"
    else
      fail "Python dev header $header missing from $resolved/include"
    fi
  done

  if find "$resolved/lib/pkgconfig" -maxdepth 1 -name '*.pc' | grep -q .; then
    pass "Python dev pkg-config files present"
  else
    fail "Python dev pkg-config files missing from $resolved/lib/pkgconfig"
  fi
}

check_python_wheelhouse_contents() {
  local key="$1"
  local value
  local resolved

  value="$(read_property "$key")"
  if [[ -z "$value" ]]; then
    return
  fi

  resolved="$(resolve_property_path "$value")"
  if [[ ! -d "$resolved" ]]; then
    fail "Python wheelhouse configured but missing directory: $resolved"
    return
  fi

  if find "$resolved" -maxdepth 1 -name '*.whl' | grep -q .; then
    pass "Python wheelhouse wheels present"
  elif [[ -f "$resolved/operator-wheelhouse-manifest.tsv" ]]; then
    pass "Python wheelhouse configured with no bundled wheels"
  else
    warn "Python wheelhouse configured but contains no wheels: $resolved"
  fi

  if [[ -f "$resolved/operator-wheelhouse-manifest.tsv" ]]; then
    pass "Python wheelhouse manifest present"
  else
    warn "Python wheelhouse manifest missing: $resolved/operator-wheelhouse-manifest.tsv"
  fi
}

printf 'Operator Android local environment validation\n'
printf 'Project root: %s\n\n' "$PROJECT_ROOT"

if [[ -f "$LOCAL_PROPERTIES" ]]; then
  pass "local.properties found"
else
  fail "local.properties missing at $LOCAL_PROPERTIES"
fi

sdk_dir="$(read_property sdk.dir)"
if [[ -z "$sdk_dir" ]]; then
  sdk_dir="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
fi
if [[ -z "$sdk_dir" ]]; then
  fail "Android SDK not configured; set sdk.dir in mobile/android/local.properties"
else
  if [[ -d "$sdk_dir" ]]; then
    pass "Android SDK: $sdk_dir"
  else
    fail "Android SDK directory missing: $sdk_dir"
  fi
fi

cargo_bin="$(read_property cargo.path)"
if [[ -z "$cargo_bin" ]]; then
  cargo_bin="${CARGO:-$(command_path cargo)}"
fi
if [[ -n "$cargo_bin" && -x "$cargo_bin" ]]; then
  pass "Cargo: $cargo_bin"
else
  fail "Cargo missing or not executable; set cargo.path in local.properties"
fi

if [[ -n "$sdk_dir" ]]; then
  check_executable "adb" "$sdk_dir/platform-tools/adb"
  check_executable "emulator" "$sdk_dir/emulator/emulator"

  if [[ -d "$sdk_dir/platforms/$REQUIRED_PLATFORM" ]]; then
    pass "required SDK platform: $REQUIRED_PLATFORM"
  else
    fail "required SDK platform missing: $sdk_dir/platforms/$REQUIRED_PLATFORM"
  fi

  if [[ -d "$sdk_dir/platforms/$OPTIONAL_PLATFORM" ]]; then
    pass "Android 16 SDK platform available: $OPTIONAL_PLATFORM"
  else
    warn "Android 16 SDK platform not found: $OPTIONAL_PLATFORM"
  fi

  ndk_dir="$sdk_dir/ndk/$EXPECTED_NDK_VERSION"
  if [[ -d "$ndk_dir" ]]; then
    pass "Android NDK: $ndk_dir"
  else
    fail "Android NDK $EXPECTED_NDK_VERSION missing under $sdk_dir/ndk"
  fi
fi

java_bin="$(command_path java)"
if [[ -n "$java_bin" ]]; then
  pass "Java: $java_bin"
else
  fail "java not found on PATH"
fi

if [[ -x "$ANDROID_PROJECT/gradlew" ]]; then
  pass "Gradle wrapper executable"
else
  fail "Gradle wrapper missing or not executable: $ANDROID_PROJECT/gradlew"
fi

rustup_bin="$(command_path rustup)"
if [[ -n "$rustup_bin" ]]; then
  pass "rustup: $rustup_bin"
  installed_targets="$(rustup target list --installed 2>/dev/null || true)"
else
  warn "rustup not found on PATH; cannot verify installed Rust Android targets"
  installed_targets=""
fi

if [[ -n "$cargo_bin" && -x "$cargo_bin" ]]; then
  if "$cargo_bin" ndk --version >/dev/null 2>&1; then
    pass "cargo-ndk available through cargo ndk"
  else
    fail "cargo-ndk is not available through cargo ndk"
  fi
fi

native_abis="$(read_property native.abis)"
if [[ -z "$native_abis" ]]; then
  native_abis="arm64-v8a"
fi
pass "native.abis: $native_abis"

IFS=',' read -r -a abi_array <<< "$native_abis"
for raw_abi in "${abi_array[@]}"; do
  abi="$(printf '%s' "$raw_abi" | sed 's/^ *//;s/ *$//')"
  [[ -z "$abi" ]] && continue

  rust_target="$(abi_to_rust_target "$abi")"
  if [[ -z "$rust_target" ]]; then
    fail "unsupported native ABI in local.properties: $abi"
    continue
  fi

  if [[ -n "$installed_targets" ]]; then
    if printf '%s\n' "$installed_targets" | grep -qx "$rust_target"; then
      pass "Rust target for $abi: $rust_target"
    else
      fail "Rust target for $abi not installed: $rust_target"
    fi
  fi

  archive_property="$(read_property "rustyV8Archive.$abi")"
  binding_property="$(read_property "rustyV8Binding.$abi")"

  if [[ -n "$archive_property" ]]; then
    property_path_exists "rusty_v8 archive for $abi" "$archive_property"
  else
    warn "rustyV8Archive.$abi not set; full embedded Codex build will fail until artifact exists"
  fi

  if [[ -n "$binding_property" ]]; then
    property_path_exists "rusty_v8 binding for $abi" "$binding_property"
  else
    warn "rustyV8Binding.$abi not set; full embedded Codex build will fail until artifact exists"
  fi

  optional_tool_property "busybox.path.$abi" "BusyBox for $abi"
  optional_tool_property "ssh.path.$abi" "OpenSSH ssh for $abi"
  optional_tool_property "scp.path.$abi" "OpenSSH scp for $abi"
  optional_tool_property "sftp.path.$abi" "OpenSSH sftp for $abi"
  optional_tool_property "sshAdd.path.$abi" "OpenSSH ssh-add for $abi"
  optional_tool_property "sshAgent.path.$abi" "OpenSSH ssh-agent for $abi"
  optional_tool_property "sshKeygen.path.$abi" "OpenSSH ssh-keygen for $abi"
  optional_tool_property "sshKeyscan.path.$abi" "OpenSSH ssh-keyscan for $abi"
  optional_tool_property "gh.path.$abi" "GitHub CLI for $abi"
  optional_tool_property "python3.path.$abi" "CPython launcher for $abi"
  optional_runtime_dir_property "pythonHome.path.$abi" "CPython runtime home for $abi"
  check_python_runtime_contents "pythonHome.path.$abi"
  optional_runtime_dir_property "pythonDevLibs.path.$abi" "Python build headers/libs for $abi"
  check_python_dev_libs_contents "pythonDevLibs.path.$abi"
  check_python_wheelhouse_contents "pythonWheelhouse.path.$abi"
  optional_tool_property "node.path.$abi" "Node.js launcher for $abi"
  optional_tool_property "npm.path.$abi" "npm launcher for $abi"
  optional_tool_property "npx.path.$abi" "npx launcher for $abi"
  optional_runtime_dir_property "nodeHome.path.$abi" "Node.js runtime home for $abi"
  if [[ -n "$(read_property "runtimeSdk.path.$abi")" ]]; then
    optional_runtime_dir_property "runtimeSdk.path.$abi" "Android Runtime SDK for $abi"
    check_optional_runtime_sdk_contents "runtimeSdk.path.$abi"
  fi
  if [[ -n "$(read_property "toolchain.path.$abi")" ]]; then
    optional_runtime_dir_property "toolchain.path.$abi" "Android C/C++ toolchain for $abi"
    check_optional_android_toolchain_contents "toolchain.path.$abi"
    optional_tool_property "androidClang.path.$abi" "Android clang for $abi"
    optional_tool_property "androidClangxx.path.$abi" "Android clang++ for $abi"
    optional_tool_property "androidCc.path.$abi" "Android cc for $abi"
    optional_tool_property "androidCxx.path.$abi" "Android c++ for $abi"
    optional_tool_property "androidLdLld.path.$abi" "Android ld.lld for $abi"
    optional_tool_property "androidLld.path.$abi" "Android lld for $abi"
    optional_tool_property "androidLlvmAr.path.$abi" "Android llvm-ar for $abi"
    optional_tool_property "androidLlvmRanlib.path.$abi" "Android llvm-ranlib for $abi"
    optional_tool_property "androidLlvmStrip.path.$abi" "Android llvm-strip for $abi"
    optional_tool_property "androidRustc.path.$abi" "Android rustc for $abi"
    optional_tool_property "androidCargo.path.$abi" "Android cargo for $abi"
    optional_tool_property "androidRustdoc.path.$abi" "Android rustdoc for $abi"
    optional_tool_property "androidRustfmt.path.$abi" "Android rustfmt for $abi"
    optional_tool_property "androidPkgConfig.path.$abi" "Android pkg-config for $abi"
    optional_tool_property "androidCmake.path.$abi" "Android cmake for $abi"
    optional_tool_property "androidCtest.path.$abi" "Android ctest for $abi"
    optional_tool_property "androidCpack.path.$abi" "Android cpack for $abi"
    optional_tool_property "androidNinja.path.$abi" "Android ninja for $abi"
    optional_tool_property "androidM4.path.$abi" "Android m4 for $abi"
    optional_tool_property "androidBison.path.$abi" "Android bison for $abi"
    optional_tool_property "androidFlex.path.$abi" "Android flex for $abi"
    optional_tool_property "androidPatchelf.path.$abi" "Android patchelf for $abi"
    optional_tool_property "androidFile.path.$abi" "Android file for $abi"
    optional_tool_property "androidJq.path.$abi" "Android jq for $abi"
    optional_tool_property "androidTree.path.$abi" "Android tree for $abi"
    optional_tool_property "androidRsync.path.$abi" "Android rsync for $abi"
    optional_tool_property "androidZip.path.$abi" "Android zip for $abi"
    optional_tool_property "androidUnzip.path.$abi" "Android unzip for $abi"
    optional_tool_property "androidTar.path.$abi" "Android tar for $abi"
    optional_tool_property "androidGdb.path.$abi" "Android gdb for $abi"
    optional_tool_property "androidStrace.path.$abi" "Android strace for $abi"
    optional_tool_property "androidPerl.path.$abi" "Android perl for $abi"
    optional_tool_property "androidBash.path.$abi" "Android bash for $abi"
    optional_tool_property "androidZsh.path.$abi" "Android zsh for $abi"
  fi
done

if [[ -n "$sdk_dir" && -x "$sdk_dir/emulator/emulator" ]]; then
  avd_count="$("$sdk_dir/emulator/emulator" -list-avds 2>/dev/null | sed '/^[[:space:]]*$/d' | wc -l | tr -d '[:space:]' || true)"
  if [[ "${avd_count:-0}" -gt 0 ]]; then
    pass "Android virtual devices listed"
  else
    warn "No Android virtual devices listed by emulator -list-avds"
  fi
fi

printf '\nValidation summary: %s error(s), %s warning(s)\n' "$errors" "$warnings"

if [[ "$errors" -gt 0 ]]; then
  exit 1
fi
