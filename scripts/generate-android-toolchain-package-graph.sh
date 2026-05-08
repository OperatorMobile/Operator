#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/android-runtime-common.sh"

usage() {
  cat <<EOF
Usage: scripts/generate-android-toolchain-package-graph.sh [options]

Reads Android toolchain .deb package controls and writes a tab-separated package
graph report suitable for Runtime SDK validation and provenance manifests.

Options:
  --deb-dir <dir>          Directory containing .deb packages.
  --output <file>          Output TSV file. Defaults to <deb-dir>/operator-package-graph.tsv.
  --help                   Show this help.

Environment:
  ANDROID_TOOLCHAIN_DEB_DIR=/path/to/debs
EOF
}

deb_dir="${ANDROID_TOOLCHAIN_DEB_DIR:-}"
output_path=""

while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --deb-dir)
      shift
      deb_dir="${1:-}"
      ;;
    --output)
      shift
      output_path="${1:-}"
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

if [[ -z "$deb_dir" ]]; then
  echo "Missing .deb directory. Use --deb-dir or ANDROID_TOOLCHAIN_DEB_DIR." >&2
  usage >&2
  exit 2
fi
if [[ ! -d "$deb_dir" ]]; then
  echo ".deb directory not found: $deb_dir" >&2
  exit 1
fi
if [[ -z "$output_path" ]]; then
  output_path="$deb_dir/operator-package-graph.tsv"
fi

require_host_tool find
require_host_tool tar

work_dir="$(mktemp -d "$(build_parent_dir)/package-graph.XXXXXX")"
cleanup() {
  rm -rf "$work_dir"
}
trap cleanup EXIT HUP INT TERM

extract_ar_member() {
  local deb="$1"
  local member_regex="$2"
  local member

  if ! command -v ar >/dev/null 2>&1; then
    return 1
  fi

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

  awk -v deb_file="$(basename "$deb")" '
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
      printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n",
        fields["Package"],
        fields["Version"],
        fields["Architecture"],
        fields["Depends"],
        fields["Recommends"],
        fields["Suggests"],
        fields["Provides"],
        deb_file
    }
  '
}

mkdir -p "$(dirname "$output_path")"
{
  printf 'package\tversion\tarchitecture\tdepends\trecommends\tsuggests\tprovides\tdeb\n'
  find "$deb_dir" -maxdepth 1 -name '*.deb' -type f -print | sort | while IFS= read -r deb; do
    extract_control_file "$deb" | parse_control "$deb"
  done
} > "$output_path.tmp"
mv "$output_path.tmp" "$output_path"

printf 'Android toolchain package graph written: %s\n' "$output_path"
