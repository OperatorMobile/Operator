#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/android-runtime-common.sh"

usage() {
  cat <<EOF
Usage: scripts/consolidate-android-toolchain-debs.sh [options]

Copies dependency .deb files from fallback termux-packages output directories
into the Operator-specific .deb directory, but only when the package payload is
built for the current Operator app prefix and is part of the dependency closure.

Options:
  --destination-deb-dir <dir>  Operator-specific destination .deb directory.
  --fallback-deb-dir <dir>     Additional directory to search for .deb files.
                               May be passed more than once.
  --root-packages <packages>   Space/comma-separated package roots to include.
  --help                      Show this help.

Environment:
  ANDROID_TOOLCHAIN_DEB_DIR
  ANDROID_TOOLCHAIN_FALLBACK_DEB_DIRS  Colon-separated fallback .deb dirs.
  ANDROID_TOOLCHAIN_CONSOLIDATE_ROOT_PACKAGES
EOF
}

configure_android_abi
require_host_tool ar
require_host_tool awk
require_host_tool cp
require_host_tool find
require_host_tool grep
require_host_tool sort
require_host_tool tar

OPERATOR_ANDROID_APP_ID="${OPERATOR_ANDROID_APP_ID:-com.illumination.operator}"
OPERATOR_ANDROID_APP_DATA_DIR="${OPERATOR_ANDROID_APP_DATA_DIR:-/data/data/$OPERATOR_ANDROID_APP_ID}"
OPERATOR_TOOLCHAIN_ROOTFS="${OPERATOR_TOOLCHAIN_ROOTFS:-$OPERATOR_ANDROID_APP_DATA_DIR/files/tools/toolchain}"
TOOLCHAIN_PACKAGES_DIR="$(android_source_dir termux-packages)"
destination_deb_dir="${ANDROID_TOOLCHAIN_DEB_DIR:-$TOOLCHAIN_PACKAGES_DIR/output/operator/$OPERATOR_ANDROID_APP_ID/$ANDROID_ABI/debs}"
root_packages="${ANDROID_TOOLCHAIN_CONSOLIDATE_ROOT_PACKAGES:-}"
declare -a fallback_deb_dirs=()

if [[ -n "${ANDROID_TOOLCHAIN_FALLBACK_DEB_DIRS:-}" ]]; then
  IFS=':' read -r -a fallback_deb_dirs <<< "$ANDROID_TOOLCHAIN_FALLBACK_DEB_DIRS"
else
  fallback_deb_dirs=("$TOOLCHAIN_PACKAGES_DIR/output")
fi

while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --destination-deb-dir)
      shift
      destination_deb_dir="${1:-}"
      ;;
    --fallback-deb-dir)
      shift
      fallback_deb_dirs+=("${1:-}")
      ;;
    --root-packages)
      shift
      root_packages="${1:-}"
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

if [[ -z "$destination_deb_dir" ]]; then
  echo "Missing destination .deb directory." >&2
  exit 2
fi
if [[ ! -d "$destination_deb_dir" ]]; then
  echo "Destination .deb directory not found: $destination_deb_dir" >&2
  exit 1
fi

work_dir="$(mktemp -d "$(build_parent_dir)/deb-consolidate.XXXXXX")"
cleanup() {
  rm -rf "$work_dir"
}
trap cleanup EXIT HUP INT TERM

normalize_package_roots() {
  printf '%s\n' "$root_packages" |
    tr ',\t\r\n' '    ' |
    tr ' ' '\n' |
    awk 'NF > 0 && !seen[$0]++ { print }'
}

extract_ar_member() {
  local deb="$1"
  local member_regex="$2"
  local member

  member="$(ar t "$deb" 2>/dev/null | grep -E "$member_regex" | head -n 1 || true)"
  if [[ -z "$member" ]]; then
    return 1
  fi

  ar p "$deb" "$member"
}

extract_deb_member() {
  local deb="$1"
  local member_regex="$2"
  local member

  member="$(tar -tf "$deb" 2>/dev/null | grep -E "$member_regex" | head -n 1 || true)"
  if [[ -n "$member" ]]; then
    tar -xOf "$deb" "$member"
    return
  fi

  extract_ar_member "$deb" "$member_regex"
}

extract_control_file() {
  local deb="$1"
  local control_archive="$work_dir/control.tar"
  local member_regex='^control\.tar\.(xz|gz)/?$'

  rm -f "$control_archive"
  if ! extract_deb_member "$deb" "$member_regex" > "$control_archive"; then
    echo "Unable to read control archive from $(basename "$deb")" >&2
    return 1
  fi

  case "$(file "$control_archive" 2>/dev/null || true)" in
    *gzip*)
      tar -xOzf "$control_archive" ./control 2>/dev/null || tar -xOzf "$control_archive" control
      ;;
    *)
      tar -xOJf "$control_archive" ./control 2>/dev/null || tar -xOJf "$control_archive" control
      ;;
  esac
}

parse_control() {
  local deb="$1"

  awk -v deb_file="$deb" '
    function trim(value) {
      sub(/^[ \t]+/, "", value)
      sub(/[ \t]+$/, "", value)
      return value
    }
    /^[A-Za-z0-9-]+: / {
      key = $1
      sub(/:$/, "", key)
      value = substr($0, index($0, ": ") + 2)
      fields[key] = value
      current = key
      next
    }
    /^[ \t]/ && current != "" {
      fields[current] = fields[current] " " trim($0)
      next
    }
    END {
      if (fields["Package"] == "") {
        exit 1
      }
      printf "%s\t%s\t%s\n", fields["Package"], fields["Depends"], deb_file
    }
  '
}

candidate_deb_paths() {
  find "$destination_deb_dir" -maxdepth 1 -name '*.deb' -type f -print | sort
  for fallback_dir in "${fallback_deb_dirs[@]}"; do
    [[ -d "$fallback_dir" ]] || continue
    find "$fallback_dir" -maxdepth 1 -name '*.deb' -type f -print | sort
  done
}

candidate_metadata="$work_dir/candidates.tsv"
candidate_deb_paths | while IFS= read -r deb; do
  extract_control_file "$deb" | parse_control "$deb"
done | awk -F '\t' '
  # Prefer packages already in the destination directory because those are the
  # direct outputs from the current Operator build-package invocation.
  !seen[$1]++ { print }
' > "$candidate_metadata"

prefix_without_slash="${OPERATOR_TOOLCHAIN_ROOTFS#/}"

deb_has_operator_prefix_payload() {
  local deb="$1"
  local data_archive="$work_dir/data.tar"
  local member_regex='^data\.tar\.(xz|gz)/?$'

  rm -f "$data_archive"
  if ! extract_deb_member "$deb" "$member_regex" > "$data_archive"; then
    return 1
  fi

  case "$(file "$data_archive" 2>/dev/null || true)" in
    *gzip*) tar -tzf "$data_archive" ;;
    *) tar -tJf "$data_archive" ;;
  esac | awk -v prefix="$prefix_without_slash" '
    function clean(path) {
      sub(/^\.\//, "", path)
      sub(/\/$/, "", path)
      return path
    }
    {
      path = clean($0)
      if (path == "" || path == ".") {
        next
      }
      if (path == prefix || index(path, prefix "/") == 1) {
        matched = 1
        next
      }
      if (index(prefix "/", path "/") == 1) {
        next
      }
      bad = 1
    }
    END {
      exit !(matched && !bad)
    }
  '
}

closure_file="$work_dir/closure.txt"
normalize_package_roots > "$work_dir/requested-roots.txt"
find "$destination_deb_dir" -maxdepth 1 -name '*.deb' -type f -print | sort | while IFS= read -r deb; do
  extract_control_file "$deb" | awk '
    /^Package: / {
      print $2
      exit
    }
  '
done >> "$work_dir/requested-roots.txt"

awk -F '\t' '
  FNR == NR {
    roots[$1] = 1
    next
  }
  {
    packages[$1] = 1
    depends[$1] = $2
  }
  function trim(value) {
    sub(/^[ \t]+/, "", value)
    sub(/[ \t]+$/, "", value)
    return value
  }
  function package_name(value) {
    value = trim(value)
    sub(/[ \t]*\(.*$/, "", value)
    sub(/:.*/, "", value)
    return trim(value)
  }
  function enqueue(package) {
    if (package == "" || !(package in packages) || queued[package]) {
      return
    }
    queue[++tail] = package
    queued[package] = 1
  }
  function add_dependencies(package, raw, dependency_count, dependency_index, alternative_count, alternatives, alternative_index, candidate) {
    raw = depends[package]
    dependency_count = split(raw, dependency, /,/)
    for (dependency_index = 1; dependency_index <= dependency_count; dependency_index++) {
      alternative_count = split(dependency[dependency_index], alternatives, /\|/)
      for (alternative_index = 1; alternative_index <= alternative_count; alternative_index++) {
        candidate = package_name(alternatives[alternative_index])
        if (candidate in packages) {
          enqueue(candidate)
          break
        }
      }
    }
  }
  END {
    for (package in roots) {
      enqueue(package)
    }
    for (head = 1; head <= tail; head++) {
      package = queue[head]
      add_dependencies(package)
    }
    for (package in queued) {
      print package
    }
  }
' "$work_dir/requested-roots.txt" "$candidate_metadata" | sort > "$closure_file"

copied_count=0
skipped_count=0
while IFS= read -r metadata_line; do
  package="${metadata_line%%$'\t'*}"
  metadata_rest="${metadata_line#*$'\t'}"
  _depends="${metadata_rest%%$'\t'*}"
  deb="${metadata_rest#*$'\t'}"

  if ! grep -qxF "$package" "$closure_file"; then
    continue
  fi
  destination="$destination_deb_dir/$(basename "$deb")"
  if [[ -f "$destination" ]]; then
    continue
  fi
  if deb_has_operator_prefix_payload "$deb"; then
    cp -p "$deb" "$destination"
    copied_count=$((copied_count + 1))
    printf 'Copied dependency package %s\n' "$(basename "$deb")"
  else
    skipped_count=$((skipped_count + 1))
    printf 'Skipped non-Operator-prefix package %s\n' "$(basename "$deb")" >&2
  fi
done < "$candidate_metadata"

printf 'Android toolchain .deb consolidation complete: copied=%s skipped=%s destination=%s\n' \
  "$copied_count" "$skipped_count" "$destination_deb_dir"
