#!/usr/bin/env sh
#
# Auto-installer for the Gradle build toolchain required by this repository.
#
# Why: no Gradle wrapper is committed yet, so a fresh checkout cannot run
# ./gradlew. This script installs Gradle 9.3.1 (the version CI pins) plus a
# JDK 21 when needed, verifies the official SHA-256 of the download, and can
# generate the Gradle wrapper for you.
#
# Toolchain requirements it enforces:
#   - Gradle 9.3.1 (minimum for AGP 9.1.1; matches .github/workflows/android_ci.yml)
#   - JDK 21 (Robolectric unit tests run against targetSdk 36 / Android 16,
#     which requires Java 21 - on JDK 17 every Robolectric test class fails)
#
# Usage:
#   sh scripts/install-gradle.sh             # install Gradle 9.3.1 + JDK 21 if missing
#   sh scripts/install-gradle.sh --wrapper   # also generate ./gradlew (then commit it)
#
# Supported platforms: Linux (apt/dnf/apk), macOS (brew), Android Termux (pkg).
# Override the install location with: GRADLE_INSTALL_DIR=/opt sh scripts/install-gradle.sh
# Skip the JDK auto-install with:             SKIP_JDK_INSTALL=1 sh scripts/install-gradle.sh

set -eu

GRADLE_VERSION="9.3.1"
# SHA-256 of gradle-9.3.1-bin.zip from https://gradle.org/release-checksums/
GRADLE_SHA256="b266d5ff6b90eada6dc3b20cb090e3731302e553a27c5d3e4df1f0d76beaff06"
GRADLE_MAJOR="${GRADLE_VERSION%%.*}"
JDK_MAJOR="21"
DIST_URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"

INSTALL_ROOT="${GRADLE_INSTALL_DIR:-${HOME}/.local}"
GRADLE_HOME_DIR="${INSTALL_ROOT}/gradle-${GRADLE_VERSION}"
BIN_DIR="${INSTALL_ROOT}/bin"

info() { printf "==> %s\n" "$1"; }
warn() { printf "WARNING: %s\n" "$1" >&2; }
die()  { printf "ERROR: %s\n" "$1" >&2; exit 1; }

has_cmd() { command -v "$1" >/dev/null 2>&1; }

# --- temporary workspace ----------------------------------------------------
tmpdir="$(mktemp -d 2>/dev/null || true)"
[ -n "$tmpdir" ] || die "mktemp is required but missing"
trap "rm -rf \"$tmpdir\"" EXIT INT TERM

# --- JDK 21 -----------------------------------------------------------------
java_major() {
  if ! has_cmd java; then
    return 0
  fi
  line="$(java -version 2>&1 | head -n 1)"
  ver="${line#*\"}"
  ver="${ver%%\"*}"
  major="${ver%%.*}"
  if [ "$major" = "1" ]; then
    major="$(printf "%s" "$ver" | cut -d. -f2)"
  fi
  case "$major" in
    *[!0-9]*|"") return 0 ;;
    *)           printf "%s" "$major" ;;
  esac
}

install_jdk() {
  info "Installing JDK ${JDK_MAJOR} via the system package manager"
  SUDO=""
  if [ "$(id -u)" -ne 0 ] && has_cmd sudo; then
    SUDO="sudo"
  fi
  case "${PREFIX:-}" in
    */com.termux/*)
      pkg install -y "openjdk-${JDK_MAJOR}"
      ;;
    *)
      if has_cmd apt-get; then
        if [ "$(id -u)" -ne 0 ] && [ -z "$SUDO" ]; then
          die "Not root and sudo not found - rerun with sudo or install JDK ${JDK_MAJOR} manually"
        fi
        $SUDO apt-get update
        $SUDO apt-get install -y "openjdk-${JDK_MAJOR}-jdk"
      elif has_cmd dnf; then
        if [ "$(id -u)" -ne 0 ] && [ -z "$SUDO" ]; then
          die "Not root and sudo not found - rerun with sudo or install JDK ${JDK_MAJOR} manually"
        fi
        $SUDO dnf install -y "java-${JDK_MAJOR}-openjdk-devel"
      elif has_cmd apk; then
        if [ "$(id -u)" -ne 0 ] && [ -z "$SUDO" ]; then
          die "Not root and sudo not found - rerun with sudo or install JDK ${JDK_MAJOR} manually"
        fi
        $SUDO apk add --no-cache "openjdk${JDK_MAJOR}"
      elif has_cmd brew; then
        brew install --cask "temurin@${JDK_MAJOR}" || brew install "openjdk@${JDK_MAJOR}"
      else
        die "No supported package manager found. Install JDK ${JDK_MAJOR} from https://adoptium.net or via SDKMAN, then rerun."
      fi
      ;;
  esac
}

major="$(java_major)"
if [ -z "$major" ] || [ "$major" -lt "$JDK_MAJOR" ]; then
  warn "JDK ${JDK_MAJOR}+ required, found: ${major:-none}. Robolectric tests on targetSdk 36 fail on older Java."
  if [ "${SKIP_JDK_INSTALL:-0}" = "1" ]; then
    die "SKIP_JDK_INSTALL=1 is set but JDK ${JDK_MAJOR} is missing - install it from https://adoptium.net and rerun"
  fi
  install_jdk
  major="$(java_major)"
  case "$major" in
    *[!0-9]*|"") die "JDK install finished but java is still unusable - install JDK ${JDK_MAJOR} manually and rerun" ;;
  esac
  [ "$major" -ge "$JDK_MAJOR" ] || die "Installed JDK is ${major}, need ${JDK_MAJOR}+ - install Temurin 21 from https://adoptium.net"
else
  info "JDK ${major} found - OK"
fi

# --- existing Gradle --------------------------------------------------------
need_gradle=1
GRADLE_BIN=""
if has_cmd gradle; then
  gv="$(gradle --version 2>/dev/null | grep "^Gradle " | head -n 1)"
  gv="${gv#Gradle }"
  gv="$(printf "%s" "$gv" | tr -d " \r")"
  gmajor="${gv%%.*}"
  case "$gmajor" in
    *[!0-9]*|"") gmajor=0 ;;
  esac
  if [ "$gmajor" -ge "$GRADLE_MAJOR" ]; then
    info "Existing Gradle ${gv} found - using it (CI pins ${GRADLE_VERSION})"
    need_gradle=0
    GRADLE_BIN="$(command -v gradle)"
  else
    warn "Existing Gradle ${gv} is older than ${GRADLE_MAJOR}.x - installing ${GRADLE_VERSION}"
  fi
fi

# --- download + verify + extract ----------------------------------------------
fetch() {
  if has_cmd curl; then
    curl -fL --retry 3 --retry-delay 2 --connect-timeout 15 -o "$1" "$2"
  elif has_cmd wget; then
    wget --tries=3 --timeout=15 -O "$1" "$2"
  else
    die "Neither curl nor wget is available - install one and rerun"
  fi
}

verify_checksum() {
  zip_file="$1"
  if has_cmd sha256sum; then
    printf "%s  %s\n" "$GRADLE_SHA256" "$zip_file" | sha256sum -c - >/dev/null
  elif has_cmd shasum; then
    printf "%s  %s\n" "$GRADLE_SHA256" "$zip_file" | shasum -a 256 -c - >/dev/null
  else
    die "No sha256 tool found (sha256sum or shasum) - cannot verify the download"
  fi
}

if [ "$need_gradle" -eq 1 ]; then
  info "Downloading Gradle ${GRADLE_VERSION} (~130 MB) from services.gradle.org"
  zip_file="$tmpdir/gradle-${GRADLE_VERSION}-bin.zip"
  fetch "$zip_file" "$DIST_URL" || die "Download failed - check the network connection and rerun"
  info "Verifying SHA-256 checksum"
  verify_checksum "$zip_file" || die "Checksum mismatch - the download is corrupt; delete and rerun"

  info "Extracting to ${GRADLE_HOME_DIR}"
  if has_cmd unzip; then
    unzip -q "$zip_file" -d "$tmpdir/extract"
  elif has_cmd bsdtar; then
    mkdir -p "$tmpdir/extract"
    bsdtar -xf "$zip_file" -C "$tmpdir/extract"
  elif has_cmd python3; then
    mkdir -p "$tmpdir/extract"
    python3 -m zipfile -e "$zip_file" "$tmpdir/extract/"
  else
    die "unzip (or bsdtar / python3) is required to extract the distribution"
  fi
  [ -d "$tmpdir/extract/gradle-${GRADLE_VERSION}" ] || die "Extraction did not produce gradle-${GRADLE_VERSION}"
  rm -rf "$GRADLE_HOME_DIR"
  mkdir -p "$INSTALL_ROOT"
  mv "$tmpdir/extract/gradle-${GRADLE_VERSION}" "$GRADLE_HOME_DIR"
  mkdir -p "$BIN_DIR"
  ln -sf "$GRADLE_HOME_DIR/bin/gradle" "$BIN_DIR/gradle"
  GRADLE_BIN="$BIN_DIR/gradle"
  info "Installed Gradle ${GRADLE_VERSION} at ${GRADLE_HOME_DIR}"

  # --- make it visible on PATH -----------------------------------------------
  case ":${PATH}:" in
    *":${BIN_DIR}:"*) ;;
    *)
      profile=""
      for f in "$HOME/.profile" "$HOME/.bashrc" "$HOME/.zshrc"; do
        if [ -f "$f" ]; then
          profile="$f"
          break
        fi
      done
      [ -n "$profile" ] || profile="$HOME/.profile"
      if ! grep -q "gradle-installer" "$profile" 2>/dev/null; then
        {
          printf "\n# gradle-installer: Gradle ${GRADLE_VERSION} added by scripts/install-gradle.sh\n"
          printf "export PATH=\"%s:\$PATH\"\n" "$BIN_DIR"
        } >> "$profile"
        info "Added ${BIN_DIR} to PATH via ${profile} - open a new shell, or run: export PATH=\"${BIN_DIR}:\$PATH\""
      fi
      ;;
  esac
fi

# --- verify the toolchain works -----------------------------------------------
export PATH="${BIN_DIR}:${PATH}"
"$GRADLE_BIN" --version || die "gradle --version failed - see the output above"

# --- optional: generate the wrapper ------------------------------------------
if [ "${1:-}" = "--wrapper" ]; then
  repo_root="$(cd "$(dirname "$0")/.." && pwd)"
  info "Generating the Gradle wrapper in ${repo_root}"
  (cd "$repo_root" && "$GRADLE_BIN" wrapper --gradle-version "$GRADLE_VERSION") || die "gradle wrapper failed"
  info "Wrapper created. Commit it so CI and collaborators no longer need this script:"
  printf "  git add gradlew gradlew.bat gradle/wrapper/\n"
  printf "  git commit -m \"build: commit Gradle ${GRADLE_VERSION} wrapper\"\n"
  printf "Then build with: ./gradlew assembleDebug\n"
fi

info "Done. Build the debug APK: ${GRADLE_BIN} assembleDebug"
info "Run the unit tests:        ${GRADLE_BIN} :app:testDebugUnitTest"
printf "Tip: create a .env file with GEMINI_API_KEY / FCS_API_KEY / OPENROUTER_API_KEY / ALPHAVANTAGE_API_KEY for live-data builds (tests pass without them).\n"
