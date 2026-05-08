#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/android-runtime-common.sh"

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  cat <<EOF
Usage: scripts/sync-android-runtime-sources.sh [source-name ...]

Clone or update pinned Android runtime source repositories from:
  $ANDROID_RUNTIME_LOCK

When no source names are provided, all locked sources are synced.
EOF
  exit 0
fi

if [[ "$#" -eq 0 ]]; then
  sources=()
  while IFS= read -r source_name; do
    sources+=("$source_name")
  done < <(awk -F '\t' '$0 !~ /^#/ && NF >= 4 { print $1 }' "$ANDROID_RUNTIME_LOCK")
else
  sources=("$@")
fi

for source_name in "${sources[@]}"; do
  printf 'Syncing %s\n' "$source_name"
  sync_android_runtime_source "$source_name"
done

printf '\nSynced %s source checkout(s) into %s\n' "${#sources[@]}" "$ANDROID_RUNTIME_SOURCE_ROOT"
