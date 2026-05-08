#!/usr/bin/env bash

if [[ -n "${OPERATOR_ANDROID_RUNTIME_SDK_COMMON_SOURCED:-}" ]]; then
  return 0
fi
OPERATOR_ANDROID_RUNTIME_SDK_COMMON_SOURCED=1

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/android-runtime-common.sh"

configure_android_abi

OPERATOR_ANDROID_APP_ID="${OPERATOR_ANDROID_APP_ID:-com.illumination.operator}"
OPERATOR_ANDROID_RUNTIME_SDK_PROFILE="${OPERATOR_ANDROID_RUNTIME_SDK_PROFILE:-core}"
OPERATOR_ANDROID_RUNTIME_SDK_VERSION="${OPERATOR_ANDROID_RUNTIME_SDK_VERSION:-dev}"
OPERATOR_ANDROID_RUNTIME_SDK_ROOT="$ROOT_DIR/third_party/android-runtime-sources/runtime-sdk"
OPERATOR_ANDROID_RUNTIME_SDK_PROFILE_DIR="$OPERATOR_ANDROID_RUNTIME_SDK_ROOT/profiles/$OPERATOR_ANDROID_RUNTIME_SDK_PROFILE"
OPERATOR_ANDROID_RUNTIME_SDK_DIST_DIR="${OPERATOR_ANDROID_RUNTIME_SDK_DIST_DIR:-$ROOT_DIR/local-artifacts/android-runtime-sdk/dist}"
OPERATOR_ANDROID_RUNTIME_SDK_STAGE_ROOT="${OPERATOR_ANDROID_RUNTIME_SDK_STAGE_ROOT:-$ROOT_DIR/local-artifacts/android-runtime-sdk/staged}"
OPERATOR_ANDROID_RUNTIME_SDK_CACHE_DIR="${OPERATOR_ANDROID_RUNTIME_SDK_CACHE_DIR:-$ROOT_DIR/local-artifacts/android-runtime-sdk/downloads}"
OPERATOR_ANDROID_RUNTIME_SDK_TMPDIR="${OPERATOR_ANDROID_RUNTIME_SDK_TMPDIR:-$(build_parent_dir)/runtime-sdk}"
OPERATOR_ANDROID_RUNTIME_SDK_PREFIX_RELATIVE="${OPERATOR_ANDROID_RUNTIME_SDK_PREFIX_RELATIVE:-usr}"
OPERATOR_ANDROID_RUNTIME_SDK_PREFIX="/data/data/$OPERATOR_ANDROID_APP_ID/files/tools/toolchain/usr"

runtime_sdk_profile_file() {
  local name="$1"
  local path="$OPERATOR_ANDROID_RUNTIME_SDK_PROFILE_DIR/$name"

  if [[ ! -f "$path" ]]; then
    echo "Runtime SDK profile file not found: $path" >&2
    exit 1
  fi
  printf '%s\n' "$path"
}

runtime_sdk_artifact_base_name() {
  printf 'operator-android-runtime-sdk-%s-%s-%s-%s-api%s\n' \
    "$OPERATOR_ANDROID_RUNTIME_SDK_PROFILE" \
    "$OPERATOR_ANDROID_RUNTIME_SDK_VERSION" \
    "$OPERATOR_ANDROID_APP_ID" \
    "$ANDROID_ABI" \
    "$ANDROID_API"
}

runtime_sdk_artifact_path() {
  printf '%s/%s.tar.zst\n' "$OPERATOR_ANDROID_RUNTIME_SDK_DIST_DIR" "$(runtime_sdk_artifact_base_name)"
}

runtime_sdk_host_tmp_base() {
  mkdir -p "$OPERATOR_ANDROID_RUNTIME_SDK_TMPDIR"
  printf '%s\n' "$OPERATOR_ANDROID_RUNTIME_SDK_TMPDIR"
}

runtime_sdk_mktemp_dir() {
  local name="${1:-work}"

  mktemp -d "$(runtime_sdk_host_tmp_base)/$name.XXXXXX"
}

runtime_sdk_mktemp_file() {
  local name="${1:-file}"

  mktemp "$(runtime_sdk_host_tmp_base)/$name.XXXXXX"
}

runtime_sdk_staged_dir() {
  printf '%s/%s/%s/%s/api%s/%s\n' \
    "$OPERATOR_ANDROID_RUNTIME_SDK_STAGE_ROOT" \
    "$OPERATOR_ANDROID_RUNTIME_SDK_PROFILE" \
    "$OPERATOR_ANDROID_RUNTIME_SDK_VERSION" \
    "$OPERATOR_ANDROID_APP_ID" \
    "$ANDROID_API" \
    "$ANDROID_ABI"
}

runtime_sdk_package_list() {
  awk -F '\t' '
    $0 !~ /^#/ && NF >= 1 && $1 != "" {
      print $1
    }
  ' "$(runtime_sdk_profile_file packages.tsv)"
}

runtime_sdk_package_list_json() {
  local package_name
  local separator=""

  while IFS= read -r package_name; do
    printf '%s"%s"' "$separator" "$(json_escape "$package_name")"
    separator=","
  done < <(runtime_sdk_package_list)
}

runtime_sdk_forbidden_package_list() {
  local file="$OPERATOR_ANDROID_RUNTIME_SDK_PROFILE_DIR/forbidden-packages.txt"

  if [[ -f "$file" ]]; then
    awk '$0 !~ /^#/ && NF > 0 { print $1 }' "$file"
  fi
}

runtime_sdk_sha256_file() {
  local path="$1"

  if command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$path" | awk '{ print $1 }'
    return
  fi
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$path" | awk '{ print $1 }'
    return
  fi

  echo "Required checksum tool not found: shasum or sha256sum" >&2
  exit 1
}

json_escape() {
  local value="$1"
  value="${value//\\/\\\\}"
  value="${value//\"/\\\"}"
  value="${value//$'\n'/\\n}"
  printf '%s' "$value"
}

runtime_sdk_source_summary_json() {
  awk -F '\t' '
    $0 !~ /^#/ && NF >= 6 {
      gsub(/\\/,"\\\\",$1); gsub(/"/,"\\\"",$1)
      gsub(/\\/,"\\\\",$2); gsub(/"/,"\\\"",$2)
      gsub(/\\/,"\\\\",$3); gsub(/"/,"\\\"",$3)
      gsub(/\\/,"\\\\",$4); gsub(/"/,"\\\"",$4)
      gsub(/\\/,"\\\\",$5); gsub(/"/,"\\\"",$5)
      printf "%s{\"name\":\"%s\",\"repo\":\"%s\",\"ref\":\"%s\",\"commit\":\"%s\",\"license\":\"%s\"}", sep, $1, $2, $3, $4, $5
      sep=","
    }
  ' "$ANDROID_RUNTIME_LOCK"
}

runtime_sdk_write_manifest_files() {
  local image_root="$1"
  local package_graph_file="${2:-}"
  local manifest_dir="$image_root/manifest"
  local manifest_file="$manifest_dir/operator-runtime-sdk.json"
  local lock_file="$manifest_dir/operator-runtime-sdk.lock"
  local package_list
  local package_json
  local source_json
  local artifact_name

  mkdir -p "$manifest_dir"
  package_list="$(runtime_sdk_package_list | paste -sd ' ' -)"
  package_json="$(runtime_sdk_package_list_json)"
  source_json="$(runtime_sdk_source_summary_json)"
  artifact_name="$(basename "$(runtime_sdk_artifact_path)")"

  cat > "$manifest_file" <<EOF
{
  "schemaVersion": 1,
  "artifact": "$(json_escape "$artifact_name")",
  "profile": "$(json_escape "$OPERATOR_ANDROID_RUNTIME_SDK_PROFILE")",
  "version": "$(json_escape "$OPERATOR_ANDROID_RUNTIME_SDK_VERSION")",
  "appId": "$(json_escape "$OPERATOR_ANDROID_APP_ID")",
  "abi": "$(json_escape "$ANDROID_ABI")",
  "androidApi": "$(json_escape "$ANDROID_API")",
  "androidTarget": "$(json_escape "$ANDROID_TARGET")",
  "prefix": "$(json_escape "$OPERATOR_ANDROID_RUNTIME_SDK_PREFIX")",
  "prefixRelative": "$(json_escape "$OPERATOR_ANDROID_RUNTIME_SDK_PREFIX_RELATIVE")",
  "packages": [$package_json],
  "sources": [$source_json]
}
EOF

  {
    printf 'schemaVersion\t1\n'
    printf 'artifact\t%s\n' "$artifact_name"
    printf 'profile\t%s\n' "$OPERATOR_ANDROID_RUNTIME_SDK_PROFILE"
    printf 'version\t%s\n' "$OPERATOR_ANDROID_RUNTIME_SDK_VERSION"
    printf 'appId\t%s\n' "$OPERATOR_ANDROID_APP_ID"
    printf 'abi\t%s\n' "$ANDROID_ABI"
    printf 'androidApi\t%s\n' "$ANDROID_API"
    printf 'androidTarget\t%s\n' "$ANDROID_TARGET"
    printf 'prefix\t%s\n' "$OPERATOR_ANDROID_RUNTIME_SDK_PREFIX"
    printf 'prefixRelative\t%s\n' "$OPERATOR_ANDROID_RUNTIME_SDK_PREFIX_RELATIVE"
    printf 'packages\t%s\n' "$package_list"
    if [[ -n "$package_graph_file" && -f "$package_graph_file" ]]; then
      printf 'packageGraph\t%s\n' "$package_graph_file"
    fi
  } > "$lock_file"

  if [[ -n "$package_graph_file" && -f "$package_graph_file" ]]; then
    cp "$package_graph_file" "$manifest_dir/package-graph.txt"
  fi

  runtime_sdk_write_third_party_notices "$image_root"
}

runtime_sdk_write_third_party_notices() {
  local image_root="$1"
  local output="$image_root/manifest/THIRD_PARTY_NOTICES.md"

  {
    printf '# Third-Party Notices\n\n'
    printf 'This runtime SDK artifact is assembled from the pinned source repositories listed below.\n'
    printf 'Package-specific license files must be preserved under `usr/share` when supplied by upstream packages.\n\n'
    printf '| Source | Repository | Ref | Commit | License |\n'
    printf '| --- | --- | --- | --- | --- |\n'
    awk -F '\t' '
      $0 !~ /^#/ && NF >= 6 {
        printf "| `%s` | %s | `%s` | `%s` | %s |\n", $1, $2, $3, $4, $5
      }
    ' "$ANDROID_RUNTIME_LOCK"
  } > "$output"
}

runtime_sdk_write_sha256sums() {
  local image_root="$1"
  local output="$image_root/manifest/SHA256SUMS"

  (
    cd "$image_root"
    if command -v shasum >/dev/null 2>&1; then
      find . -type f ! -path './manifest/SHA256SUMS' -print0 \
        | sort -z \
        | xargs -0 shasum -a 256
    elif command -v sha256sum >/dev/null 2>&1; then
      find . -type f ! -path './manifest/SHA256SUMS' -print0 \
        | sort -z \
        | xargs -0 sha256sum
    else
      echo "Required checksum tool not found: shasum or sha256sum" >&2
      exit 1
    fi
  ) > "$output"
}

runtime_sdk_validate_required_paths() {
  local image_root="$1"
  local required_file
  local kind
  local name
  local relative_path
  local mode
  local notes
  local path
  local failed=0

  required_file="$(runtime_sdk_profile_file required-paths.tsv)"
  while IFS=$'\t' read -r kind name relative_path mode notes; do
    case "$kind" in
      ""|\#*) continue ;;
    esac
    path="$image_root/$relative_path"
    case "$mode" in
      dir)
        if [[ ! -d "$path" ]]; then
          echo "Missing runtime SDK directory: $relative_path ($name)" >&2
          failed=1
        fi
        ;;
      executable)
        if [[ ! -f "$path" || ! -x "$path" ]]; then
          echo "Missing runtime SDK executable: $relative_path ($name)" >&2
          failed=1
        fi
        ;;
      file)
        if [[ ! -f "$path" ]]; then
          echo "Missing runtime SDK file: $relative_path ($name)" >&2
          failed=1
        fi
        ;;
      *)
        echo "Unsupported required path mode '$mode' in $required_file" >&2
        failed=1
        ;;
    esac
  done < "$required_file"

  return "$failed"
}

runtime_sdk_validate_no_forbidden_text() {
  local image_root="$1"
  local failed=0
  local pattern
  local grep_output

  grep_output="$(runtime_sdk_mktemp_file forbidden-text)"
  trap 'rm -f "$grep_output"' RETURN

  for pattern in \
    '/Users/' \
    '/opt/homebrew' \
    '/Applications/' \
    '/home/builder' \
    'darwin-x86_64' \
    'com.termux'
  do
    if LC_ALL=C grep -RFIl --exclude='SHA256SUMS' -- "$pattern" "$image_root" > "$grep_output" 2>/dev/null; then
      echo "Forbidden runtime SDK text '$pattern' found in:" >&2
      sed 's/^/  /' "$grep_output" >&2
      failed=1
    fi
    : > "$grep_output"
  done

  rm -f "$grep_output"
  trap - RETURN
  return "$failed"
}

runtime_sdk_validate_package_graph() {
  local package_graph_file="$1"
  local failed=0
  local forbidden

  if [[ -z "$package_graph_file" || ! -f "$package_graph_file" ]]; then
    return 0
  fi

  while IFS= read -r forbidden; do
    [[ -z "$forbidden" ]] && continue
    if awk -v pkg="$forbidden" '
      $0 !~ /^#/ {
        for (i = 1; i <= NF; i++) {
          if ($i == pkg) {
            found = 1
          }
        }
      }
      END { exit found ? 0 : 1 }
    ' "$package_graph_file"; then
      echo "Forbidden package '$forbidden' found in runtime SDK package graph: $package_graph_file" >&2
      failed=1
    fi
  done < <(runtime_sdk_forbidden_package_list)

  return "$failed"
}

runtime_sdk_validate_image() {
  local image_root="$1"
  local package_graph_file="${2:-}"
  local failed=0

  runtime_sdk_validate_identity || failed=1

  if [[ ! -d "$image_root" ]]; then
    echo "Runtime SDK image root not found: $image_root" >&2
    return 1
  fi

  runtime_sdk_validate_required_paths "$image_root" || failed=1
  runtime_sdk_validate_no_forbidden_text "$image_root" || failed=1
  runtime_sdk_validate_package_graph "$package_graph_file" || failed=1

  return "$failed"
}

runtime_sdk_validate_identity() {
  local expected_prefix="/data/data/$OPERATOR_ANDROID_APP_ID/files/tools/toolchain/usr"
  local failed=0

  if [[ ! "$OPERATOR_ANDROID_APP_ID" =~ ^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$ ]]; then
    echo "Invalid Android app id for runtime SDK: $OPERATOR_ANDROID_APP_ID" >&2
    failed=1
  fi

  if [[ "$OPERATOR_ANDROID_RUNTIME_SDK_PREFIX" != "$expected_prefix" ]]; then
    echo "Runtime SDK prefix mismatch:" >&2
    echo "  expected: $expected_prefix" >&2
    echo "  actual:   $OPERATOR_ANDROID_RUNTIME_SDK_PREFIX" >&2
    failed=1
  fi

  return "$failed"
}

runtime_sdk_verify_archive_sha256() {
  local archive="$1"
  local expected="${2:-}"
  local actual

  if [[ -z "$expected" ]]; then
    return 0
  fi

  actual="$(runtime_sdk_sha256_file "$archive")"
  if [[ "$actual" != "$expected" ]]; then
    echo "Runtime SDK archive checksum mismatch: $archive" >&2
    echo "  expected: $expected" >&2
    echo "  actual:   $actual" >&2
    return 1
  fi
}
