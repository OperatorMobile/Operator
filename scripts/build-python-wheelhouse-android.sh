#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/android-runtime-common.sh"

configure_android_abi
require_host_tool python3

CPYTHON_VERSION="$(android_source_field cpython ref | awk -F/ '{ print $NF }')"
PYTHON_MINOR="${CPYTHON_VERSION#v}"
PYTHON_MINOR="${PYTHON_MINOR%.*}"
PYTHON_TAG_VERSION="${PYTHON_MINOR/./}"
REQUIREMENTS_FILE="${ANDROID_PYTHON_WHEELHOUSE_REQUIREMENTS:-$ROOT_DIR/scripts/android-python-wheelhouse-requirements.txt}"
BUILD_ROOT="$(build_parent_dir)/python-wheelhouse-$ANDROID_ABI"
VENV_DIR="$BUILD_ROOT/venv"
OUT_DIR="$ROOT_DIR/local-artifacts/android-wheelhouse/$CPYTHON_VERSION/$ANDROID_ABI"

if [[ ! -f "$REQUIREMENTS_FILE" ]]; then
  echo "Wheelhouse requirements file missing: $REQUIREMENTS_FILE" >&2
  exit 1
fi

rm -rf "$BUILD_ROOT" "$OUT_DIR"
mkdir -p "$BUILD_ROOT" "$OUT_DIR"

if ! grep -Ev '^[[:space:]]*(#|$)' "$REQUIREMENTS_FILE" >/dev/null; then
  printf 'file\tsha256\tbytes\n' > "$OUT_DIR/operator-wheelhouse-manifest.tsv"
  remove_local_property "pythonWheelhouse.path.$ANDROID_ABI"
  printf 'No wheelhouse requirements configured; removed pythonWheelhouse.path.%s from local.properties.\n' "$ANDROID_ABI" >&2
  printf '%s\n' "$OUT_DIR"
  exit 0
fi

python3 -m venv "$VENV_DIR"
PIP_DISABLE_PIP_VERSION_CHECK=1 PIP_NO_INPUT=1 "$VENV_DIR/bin/python" -m pip download \
  --dest "$OUT_DIR" \
  --only-binary=:all: \
  --implementation py \
  --python-version "$PYTHON_TAG_VERSION" \
  --abi none \
  --platform any \
  -r "$REQUIREMENTS_FILE"

{
  printf 'file\tsha256\tbytes\n'
  find "$OUT_DIR" -maxdepth 1 -name '*.whl' -type f -print | sort | while IFS= read -r wheel; do
    checksum="$(shasum -a 256 "$wheel" | awk '{ print $1 }')"
    bytes="$(wc -c < "$wheel" | tr -d ' ')"
    printf '%s\t%s\t%s\n' "$(basename "$wheel")" "$checksum" "$bytes"
  done
} > "$OUT_DIR/operator-wheelhouse-manifest.tsv"

upsert_local_property "pythonWheelhouse.path.$ANDROID_ABI" "$OUT_DIR"

printf '%s\n' "$OUT_DIR"
