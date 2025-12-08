#!/usr/bin/env bash
set -euo pipefail

# Generates:
#   certs/localhost.pem
#   certs/localhost-key.pem
# for nginx:
#   ssl_certificate     /certs/localhost.pem;
#   ssl_certificate_key /certs/localhost-key.pem;

CERT_DIR="certs"
CERT_FILE="$CERT_DIR/localhost.pem"
KEY_FILE="$CERT_DIR/localhost-key.pem"

MKCERT_BIN=""

download_mkcert() {
  local os_raw os_tag arch_raw arch_tag url dest bin_dir

  os_raw="$(uname -s | tr '[:upper:]' '[:lower:]')"
  case "$os_raw" in
    linux*)
      os_tag="linux"
      ;;
    mingw*|msys*|cygwin*)
      # Git Bash / MSYS on Windows
      os_tag="windows"
      ;;
    *)
      echo "Unsupported OS: $os_raw"
      echo "Install mkcert manually: https://github.com/FiloSottile/mkcert"
      exit 1
      ;;
  esac

  arch_raw="$(uname -m)"
  case "$arch_raw" in
    x86_64|amd64)
      arch_tag="amd64"
      ;;
    aarch64|arm64)
      arch_tag="arm64"
      ;;
    *)
      # Fallback; most dev machines are amd64
      arch_tag="amd64"
      ;;
  esac

  url="https://dl.filippo.io/mkcert/latest?for=${os_tag}/${arch_tag}"

  # Put mkcert binary in project-local bin/ next to this script
  bin_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/../bin"
  mkdir -p "$bin_dir"

  if [[ "$os_tag" == "windows" ]]; then
    dest="$bin_dir/mkcert.exe"
  else
    dest="$bin_dir/mkcert"
  fi

  echo "Downloading mkcert from: $url"
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "$url" -o "$dest"
  elif command -v wget >/dev/null 2>&1; then
    wget -qO "$dest" "$url"
  else
    echo "Neither curl nor wget is available to download mkcert."
    exit 1
  fi

  chmod +x "$dest"
  MKCERT_BIN="$dest"
}

ensure_mkcert() {
  if command -v mkcert >/dev/null 2>&1; then
    MKCERT_BIN="$(command -v mkcert)"
    return
  fi

  echo "mkcert not found in PATH, downloading a local copy..."
  download_mkcert

  if [[ ! -x "$MKCERT_BIN" ]]; then
    echo "Failed to download mkcert binary."
    exit 1
  fi
}

main() {
  ensure_mkcert

  # Install local CA (no-op if already installed)
  "$MKCERT_BIN" -install

  mkdir -p "$CERT_DIR"

  if [[ -f "$CERT_FILE" && -f "$KEY_FILE" ]]; then
    echo "Certificates already exist:"
    echo "  $CERT_FILE"
    echo "  $KEY_FILE"
    echo "Delete them if you want to regenerate."
    exit 0
  fi

  echo "Generating localhost certificate..."
  "$MKCERT_BIN" -key-file "$KEY_FILE" -cert-file "$CERT_FILE" localhost

  echo "Done. Generated:"
  echo "  $CERT_FILE"
  echo "  $KEY_FILE"
}

main "$@"

