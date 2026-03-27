#!/usr/bin/env bash
set -euo pipefail

YELLOW='\033[1;33m'
NC='\033[0m'

SIGNING_ENV=".devcontainer/signing.env"
INSTALL=false
RUN=false
SIGN=true
DEV=false

usage() {
    echo "Usage: $0 [--dev] [--install] [--run]"
    echo "  (default)  Release APK + AAB, optional signing"
    echo "  --dev      Debug APK only (no AAB, no release signing)"
    echo "  --install  adb install the built APK (see install.sh)"
    echo "  --run      With --install, launch the app after install"
}

for arg in "$@"; do
    case "$arg" in
        --dev) DEV=true ;;
        --install) INSTALL=true ;;
        --run)
            RUN=true
            INSTALL=true
            ;;
        -h|--help) usage; exit 0 ;;
        *)
            echo "Unknown option: $arg"
            usage
            exit 1
            ;;
    esac
done

if [ ! -f "$SIGNING_ENV" ]; then
    echo -e "${YELLOW}Warning: $SIGNING_ENV not found — skipping signing.${NC}"
    echo -e "${YELLOW}  cp .devcontainer/signing.env.example .devcontainer/signing.env${NC}"
    SIGN=false
elif [ -f "$SIGNING_ENV" ]; then
    set -a
    source "$SIGNING_ENV"
    set +a

    KEYSTORE_PATH="${KEYSTORE_PATH:-}"
    if [ -z "$KEYSTORE_PATH" ] || [ ! -f "$KEYSTORE_PATH" ]; then
        echo -e "${YELLOW}Warning: Keystore not found at '${KEYSTORE_PATH:-<unset>}' — skipping signing.${NC}"
        SIGN=false
    fi

    if [ "$SIGN" = true ]; then
        for var in KEYSTORE_PASSWORD KEY_ALIAS KEY_PASSWORD; do
            if [ -z "${!var:-}" ]; then
                echo -e "${YELLOW}Warning: $var is not set in $SIGNING_ENV — skipping signing.${NC}"
                SIGN=false
                break
            fi
        done
    fi
fi

# Pinned to 34 — apksigner in 35+ breaks F-Droid reproducible builds
APKSIGNER="$ANDROID_HOME/build-tools/34.0.0/apksigner"

# Artifact filename: git tag when on a tag, else branch (app versionName is static in build.gradle.kts)
VERSION=$(git describe --tags --exact-match 2>/dev/null | sed 's/^v//' || true)
if [ -z "$VERSION" ]; then
    VERSION=$(git rev-parse --abbrev-ref HEAD)
fi

git submodule update --init

if [ "$DEV" = true ]; then
    echo "=== Building debug APK ==="
    ./gradlew assembleDebug --no-configuration-cache

    APK=$(find app/build/outputs/apk/debug -name '*.apk' | head -1)
    if [ -z "$APK" ] || [ ! -f "$APK" ]; then
        echo "Error: No debug APK found under app/build/outputs/apk/debug/"
        exit 1
    fi

    echo "=== Renaming artifact ==="
    APK_DIR=$(dirname "$APK")
    APK_PATH="$APK_DIR/RoadTripRadar-dev-${VERSION}.apk"
    mv -f "$APK" "$APK_PATH"

    echo ""
    echo "=== Build complete (debug) ==="
    echo "APK: $APK_PATH"

    if [ "$INSTALL" = true ]; then
        echo ""
        INSTALL_ARGS=(--debug "$APK_PATH")
        [ "$RUN" = true ] && INSTALL_ARGS+=(--run)
        bash install.sh "${INSTALL_ARGS[@]}"
    fi
    exit 0
fi

echo "=== Building release APK ==="
./gradlew assembleRelease --no-configuration-cache

echo "=== Building release AAB ==="
./gradlew bundleRelease --no-configuration-cache

APK=$(find app/build/outputs/apk/release -name '*.apk' ! -name 'RoadTripRadar-*.apk' | head -1)
AAB=$(find app/build/outputs/bundle/release -name '*.aab' ! -name 'RoadTripRadar-*.aab' | head -1)

if [ "$SIGN" = true ]; then
    echo "=== Signing APK ==="
    "$APKSIGNER" sign \
        --ks "$KEYSTORE_PATH" \
        --ks-key-alias "$KEY_ALIAS" \
        --ks-pass "pass:$KEYSTORE_PASSWORD" \
        --key-pass "pass:$KEY_PASSWORD" \
        "$APK"

    echo "=== Verifying APK signature ==="
    "$APKSIGNER" verify --print-certs "$APK"

    echo "=== Signing AAB ==="
    jarsigner -verbose -sigalg SHA256withRSA -digestalg SHA-256 \
        -keystore "$KEYSTORE_PATH" \
        -storepass "$KEYSTORE_PASSWORD" \
        -keypass "$KEY_PASSWORD" \
        "$AAB" \
        "$KEY_ALIAS"

    echo "=== Verifying AAB signature ==="
    jarsigner -verify -verbose -certs "$AAB"
fi

echo "=== Renaming artifacts ==="
APK_DIR=$(dirname "$APK")
AAB_DIR=$(dirname "$AAB")
APK_PATH="$APK_DIR/RoadTripRadar-${VERSION}.apk"
AAB_PATH="$AAB_DIR/RoadTripRadar-${VERSION}.aab"
mv -f "$APK" "$APK_PATH"
mv -f "$AAB" "$AAB_PATH"

echo ""
echo "=== Build complete ==="
echo "APK: $APK_PATH"
echo "AAB: $AAB_PATH"
if [ "$SIGN" = false ]; then
    echo -e "${YELLOW}Artifacts are UNSIGNED.${NC}"
fi

if [ "$INSTALL" = true ]; then
    echo ""
    INSTALL_ARGS=("$APK_PATH")
    [ "$RUN" = true ] && INSTALL_ARGS+=(--run)
    bash install.sh "${INSTALL_ARGS[@]}"
fi
