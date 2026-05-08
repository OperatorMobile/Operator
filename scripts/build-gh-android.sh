#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/android-runtime-common.sh"

require_host_tool go
sync_android_runtime_source github-cli
configure_android_toolchain

SOURCE_DIR="$(android_source_dir github-cli)"
GH_VERSION="$(android_source_field github-cli ref | awk -F/ '{ print $NF }')"
BUILD_DIR="$(build_parent_dir)/github-cli-$GH_VERSION-$ANDROID_ABI"
OUT_DIR="$ROOT_DIR/local-artifacts/android-tools/gh/$GH_VERSION/$ANDROID_ABI"
FINAL_BIN="$OUT_DIR/gh"
DNS_PATCH="$SOURCE_DIR/cmd/gh/operator_android_dns.go"

mkdir -p "$BUILD_DIR" "$OUT_DIR"

case "$ANDROID_ABI" in
  arm64-v8a)
    GOARCH_VALUE=arm64
    ;;
  x86_64)
    GOARCH_VALUE=amd64
    ;;
  *)
    echo "Unsupported Android ABI for gh: $ANDROID_ABI" >&2
    exit 1
    ;;
esac

printf 'Building GitHub CLI %s for %s\n' "$GH_VERSION" "$ANDROID_ABI"

(
  cd "$SOURCE_DIR"
  cat > "$DNS_PATCH" <<'EOF_DNS_PATCH'
//go:build android

package main

import (
	"os"
	"strings"
	_ "unsafe"
)

//go:linkname netDefaultNS net.defaultNS
var netDefaultNS []string

func init() {
	servers := operatorDNSServers(os.Getenv("OPERATOR_DNS_SERVERS"))
	if len(servers) == 0 {
		servers = []string{"1.1.1.1:53", "8.8.8.8:53", "9.9.9.9:53"}
	}
	netDefaultNS = servers
}

func operatorDNSServers(value string) []string {
	fields := strings.FieldsFunc(value, func(r rune) bool {
		return r == ',' || r == ';' || r == ' ' || r == '\t' || r == '\n'
	})
	servers := make([]string, 0, len(fields))
	for _, field := range fields {
		if field == "" {
			continue
		}
		servers = append(servers, operatorDNSAddr(field))
	}
	return servers
}

func operatorDNSAddr(server string) string {
	if strings.HasPrefix(server, "[") {
		if strings.Contains(server, "]:") {
			return server
		}
		return server + ":53"
	}
	if strings.Count(server, ":") > 1 {
		return "[" + server + "]:53"
	}
	if !strings.Contains(server, ":") {
		return server + ":53"
	}
	return server
}
EOF_DNS_PATCH
  cleanup_dns_patch() {
    rm -f "$DNS_PATCH"
  }
  trap cleanup_dns_patch EXIT
  env \
    GOOS=android \
    GOARCH="$GOARCH_VALUE" \
    CGO_ENABLED=1 \
    CC="$CC" \
    CXX="$CXX" \
    GOMODCACHE="$BUILD_DIR/gomod" \
    GOCACHE="$BUILD_DIR/gocache" \
    go build \
      -trimpath \
      -tags netcgo \
      -ldflags "-s -w -X github.com/cli/cli/v2/internal/build.Version=${GH_VERSION#v}" \
      -o "$FINAL_BIN" \
      ./cmd/gh
)

chmod 0755 "$FINAL_BIN"
upsert_local_property "gh.path.$ANDROID_ABI" "$FINAL_BIN"

printf 'GitHub CLI staged at %s\n' "$FINAL_BIN"
