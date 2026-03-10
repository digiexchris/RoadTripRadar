#!/usr/bin/env bash
set -euo pipefail

echo "=== Android SDK ==="
echo "ANDROID_HOME: $ANDROID_HOME"
sdkmanager --list_installed 2>/dev/null | head -20

echo ""
echo "=== ADB ==="
echo "adb version: $(adb version | head -1)"

echo ""
echo "=== Device Connection ==="
echo "USB:     Devices connected via USB should be detected automatically (privileged mode)."
echo "TCP/IP:  Run 'adb connect <phone-ip>:5555' for wireless debugging."
echo ""
echo "Run 'adb devices' to verify connected devices."

echo ""
echo "=== Signing ==="
if [ -f .devcontainer/signing.env ]; then
    echo "signing.env found."
else
    echo "No signing.env found. Copy the template to get started:"
    echo "  cp .devcontainer/signing.env.example .devcontainer/signing.env"
    echo "Then fill in your keystore credentials and place keystore.jks in the project root."
fi

chmod +x gradlew 2>/dev/null || true
