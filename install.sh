#!/usr/bin/env bash
set -euo pipefail

YELLOW='\033[1;33m'
GREEN='\033[1;32m'
NC='\033[0m'

ADB_ENV=".devcontainer/adb.env"

RUN=false
ARGS=()
for arg in "$@"; do
    case "$arg" in
        --run) RUN=true ;;
        *) ARGS+=("$arg") ;;
    esac
done
set -- "${ARGS[@]}"

APK="${1:-}"
if [ -z "$APK" ]; then
    APK=$(find app/build/outputs/apk/release -name '*.apk' -printf '%T@ %p\n' 2>/dev/null \
        | sort -rn | head -1 | cut -d' ' -f2-)
fi
if [ -z "$APK" ] || [ ! -f "$APK" ]; then
    echo "Usage: install.sh [path-to-apk] [--run]"
    echo "No APK specified and none found in app/build/outputs/apk/release/."
    exit 1
fi

ADB_DEVICES=()
if [ -f "$ADB_ENV" ]; then
    source "$ADB_ENV"
fi

if [ ${#ADB_DEVICES[@]} -eq 0 ]; then
    echo -e "${YELLOW}Warning: No devices configured in $ADB_ENV — trying any connected device.${NC}"
    echo -e "${YELLOW}  cp .devcontainer/adb.env.example .devcontainer/adb.env${NC}"
    echo ""
    echo "=== Installing $APK ==="
    adb wait-for-device
    adb install -r "$APK"
    echo -e "${GREEN}=== Installed ===${NC}"
    adb devices -l
    if [ "$RUN" = true ]; then
        echo ""
        echo "=== Launching app ==="
        adb shell am start -n ca.voiditswarranty.roadtripradar/.MainActivity
    fi
    exit 0
fi

is_wifi_device() {
    [[ "$1" == *:* ]]
}

is_connected() {
    adb devices | sed '1d' | awk '{print $1}' | grep -qx "$1"
}

TARGET=""
for dev in "${ADB_DEVICES[@]}"; do
    if is_connected "$dev"; then
        echo "Device $dev already connected."
        TARGET="$dev"
        break
    fi

    if is_wifi_device "$dev"; then
        echo "Connecting to $dev over Wi-Fi..."
        if adb connect "$dev" 2>&1 | grep -q "connected"; then
            TARGET="$dev"
            break
        else
            echo -e "${YELLOW}  Could not connect to $dev — trying next device.${NC}"
        fi
    else
        echo -e "${YELLOW}USB device $dev not found — trying next device.${NC}"
    fi
done

if [ -z "$TARGET" ]; then
    echo "Error: None of the configured devices are available."
    echo "Configured: ${ADB_DEVICES[*]}"
    echo "Connected:"
    adb devices -l
    exit 1
fi

echo ""
echo "=== Installing $APK on $TARGET ==="
adb -s "$TARGET" install -r "$APK"
echo -e "${GREEN}=== Installed on $TARGET ===${NC}"

if [ "$RUN" = true ]; then
    echo ""
    echo "=== Launching app on $TARGET ==="
    adb -s "$TARGET" shell am start -n ca.voiditswarranty.roadtripradar/.MainActivity
fi
