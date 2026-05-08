#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/android-runtime-sdk-common.sh"

usage() {
  cat <<EOF
Usage: scripts/fetch-android-runtime-sdk.sh [options]

Copies or downloads a prebuilt Android runtime SDK archive, verifies the
expected checksum when supplied, and stages it for Gradle by default.

Options:
  --artifact <file>        Existing local .tar.zst artifact.
  --url <url>              Artifact URL to download with curl.
  --sha256 <hex>           Expected artifact SHA-256.
  --no-stage               Fetch only; do not stage/update local.properties.
  --package-graph <file>   Optional package graph file to validate while staging.
  --help                   Show this help.

Environment:
  OPERATOR_ANDROID_RUNTIME_SDK_ARTIFACT=/path/to/archive.tar.zst
  OPERATOR_ANDROID_RUNTIME_SDK_URL=https://...
  OPERATOR_ANDROID_RUNTIME_SDK_SHA256=<hex>
  OPERATOR_ANDROID_RUNTIME_SDK_STAGE=false
EOF
}

artifact_path="${OPERATOR_ANDROID_RUNTIME_SDK_ARTIFACT:-}"
artifact_url="${OPERATOR_ANDROID_RUNTIME_SDK_URL:-}"
expected_sha="${OPERATOR_ANDROID_RUNTIME_SDK_SHA256:-}"
package_graph_file="${OPERATOR_ANDROID_RUNTIME_SDK_PACKAGE_GRAPH:-}"
stage_after_fetch="${OPERATOR_ANDROID_RUNTIME_SDK_STAGE:-true}"

while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --artifact)
      shift
      artifact_path="${1:-}"
      ;;
    --url)
      shift
      artifact_url="${1:-}"
      ;;
    --sha256)
      shift
      expected_sha="${1:-}"
      ;;
    --package-graph)
      shift
      package_graph_file="${1:-}"
      ;;
    --no-stage)
      stage_after_fetch=false
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

if [[ -n "$artifact_path" && -n "$artifact_url" ]]; then
  echo "Use only one of --artifact or --url." >&2
  exit 2
fi
if [[ -z "$artifact_path" && -z "$artifact_url" ]]; then
  echo "Missing runtime SDK artifact. Use --artifact or --url." >&2
  usage >&2
  exit 2
fi
if [[ -n "$package_graph_file" && ! -f "$package_graph_file" ]]; then
  echo "Package graph file not found: $package_graph_file" >&2
  exit 1
fi

mkdir -p "$OPERATOR_ANDROID_RUNTIME_SDK_CACHE_DIR"

if [[ -n "$artifact_url" ]]; then
  require_host_tool curl
  if [[ -z "$expected_sha" && "${OPERATOR_ANDROID_RUNTIME_SDK_ALLOW_UNPINNED_URL:-false}" != "true" ]]; then
    echo "Refusing to download an unpinned runtime SDK URL without --sha256." >&2
    echo "Set OPERATOR_ANDROID_RUNTIME_SDK_ALLOW_UNPINNED_URL=true only for local experiments." >&2
    exit 1
  fi
  artifact_name="$(basename "${artifact_url%%\?*}")"
  if [[ -z "$artifact_name" || "$artifact_name" == "/" || "$artifact_name" == "." ]]; then
    artifact_name="$(basename "$(runtime_sdk_artifact_path)")"
  fi
  artifact_path="$OPERATOR_ANDROID_RUNTIME_SDK_CACHE_DIR/$artifact_name"
  curl -fL --retry 3 --retry-delay 2 --output "$artifact_path.tmp" "$artifact_url"
  mv "$artifact_path.tmp" "$artifact_path"
else
  if [[ ! -f "$artifact_path" ]]; then
    echo "Runtime SDK artifact not found: $artifact_path" >&2
    exit 1
  fi
fi

runtime_sdk_verify_archive_sha256 "$artifact_path" "$expected_sha"

printf 'Runtime SDK artifact ready: %s\n' "$artifact_path"

case "$stage_after_fetch" in
  true|1|yes)
    stage_args=(--archive "$artifact_path")
    if [[ -n "$expected_sha" ]]; then
      stage_args+=(--sha256 "$expected_sha")
    fi
    if [[ -n "$package_graph_file" ]]; then
      stage_args+=(--package-graph "$package_graph_file")
    fi
    "$ROOT_DIR/scripts/stage-android-runtime-sdk.sh" "${stage_args[@]}"
    ;;
  false|0|no)
    ;;
  *)
    echo "Unsupported OPERATOR_ANDROID_RUNTIME_SDK_STAGE value: $stage_after_fetch" >&2
    exit 2
    ;;
esac
