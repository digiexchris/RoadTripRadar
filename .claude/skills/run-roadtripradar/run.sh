#!/usr/bin/env bash
# run-roadtripradar — entry point for the run-roadtripradar skill.
#
# Builds the debug APK, boots a headless emulator, installs + launches the app,
# takes a screenshot, and prints adb commands the agent can keep running.
#
# Usage:
#   ./.claude/skills/run-roadtripradar/run.sh                  # full pipeline
#   ./.claude/skills/run-roadtripradar/run.sh --skip-build    # reuse existing APK
#   ./.claude/skills/run-roadtripradar/run.sh --skip-emulator # device already attached
#   ./.claude/skills/run-roadtripradar/run.sh --stop          # kill the emulator we started
#
# This is the script a future agent should run first. It is also the
# reference for what the SKILL.md describes.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && while [ ! -f "$PWD/gradlew" ] && [ "$PWD" != / ]; do cd ..; done; pwd)"
[ -f "$REPO_ROOT/gradlew" ] || { echo "could not locate gradlew from $(dirname "${BASH_SOURCE[0]}")" >&2; exit 1; }
cd "$REPO_ROOT"

ANDROID_SDK="${ANDROID_HOME:-/home/chris/Android/Sdk}"
export ANDROID_SDK
export ANDROID_HOME="$ANDROID_SDK"
export PATH="$ANDROID_SDK/platform-tools:$ANDROID_SDK/emulator:$PATH"

AVD="Medium_Phone"
PKG="ca.voiditswarranty.roadtripradar.debug"
ACTIVITY="ca.voiditswarranty.roadtripradar.MainActivity"
APK="$REPO_ROOT/app/build/outputs/apk/debug/app-debug.apk"
SHOTS_DIR="$REPO_ROOT/.claude/skills/run-roadtripradar/shots"
mkdir -p "$SHOTS_DIR"

SKIP_BUILD=0
SKIP_EMULATOR=0
STOP=0
for arg in "$@"; do
  case "$arg" in
    --skip-build)    SKIP_BUILD=1 ;;
    --skip-emulator) SKIP_EMULATOR=1 ;;
    --stop)          STOP=1 ;;
    -h|--help) sed -n '2,15p' "$0"; exit 0 ;;
    *) echo "unknown flag: $arg" >&2; exit 2 ;;
  esac
done

# Stop mode: kill any emulator we (or a prior run) started.
if [ "$STOP" -eq 1 ]; then
  pkill -f "emulator -avd $AVD" 2>/dev/null || true
  pkill -f "qemu-system-x86_64-headless" 2>/dev/null || true
  "$ANDROID_SDK/platform-tools/adb" emu kill 2>/dev/null || true
  echo "stopped"
  exit 0
fi

# 1. Build
if [ "$SKIP_BUILD" -eq 0 ]; then
  echo "==> ./gradlew :app:assembleDebug"
  ./gradlew :app:assembleDebug
fi
[ -f "$APK" ] || { echo "APK missing at $APK; build first" >&2; exit 1; }

# 2. Emulator
if [ "$SKIP_EMULATOR" -eq 0 ]; then
  if ! "$ANDROID_SDK/platform-tools/adb" get-state >/dev/null 2>&1; then
    echo "==> launching emulator (headless, KVM)"
    # -no-snapshot + -wipe-data = clean boot every time. Avoid stale snapshots
    # that can hang on a different host. swiftshader_indirect = software GPU
    # (no GL context needed in the container).
    nohup "$ANDROID_SDK/emulator/emulator" -avd "$AVD" \
      -no-window -no-audio -no-boot-anim -no-snapshot -wipe-data \
      -gpu swiftshader_indirect -accel auto -partition-size 2048 -no-metrics \
      >"$SHOTS_DIR/emulator.log" 2>&1 &
    echo "    emulator pid=$!  log=$SHOTS_DIR/emulator.log"
  fi
  echo "==> waiting for device"
  "$ANDROID_SDK/platform-tools/adb" wait-for-device
  echo "==> waiting for sys.boot_completed"
  for i in $(seq 1 60); do
    b=$("$ANDROID_SDK/platform-tools/adb" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
    if [ "$b" = "1" ]; then echo "    booted after ~${i}0s"; break; fi
    sleep 10
  done
  if [ "$b" != "1" ]; then
    echo "emulator failed to boot in 10min; tail emulator.log:" >&2
    tail -30 "$SHOTS_DIR/emulator.log" >&2
    exit 1
  fi
fi

ADB="$ANDROID_SDK/platform-tools/adb"

# 3. Stable display: no animations, keep screen on, dismiss keyguard.
"$ADB" shell settings put global window_animation_scale 0
"$ADB" shell settings put global transition_animation_scale 0
"$ADB" shell settings put global animator_duration_scale 0
"$ADB" shell svc power stayon true
"$ADB" shell input keyevent KEYCODE_WAKEUP 2>/dev/null || true

# 4. Install
echo "==> adb install -r -g $APK"
"$ADB" install -r -g "$APK" | tail -3

# 5. Launch
echo "==> am start $PKG/$ACTIVITY"
"$ADB" shell am start -W -n "$PKG/$ACTIVITY" | tail -5

# 6. Screenshot (after the splash settles)
sleep 6
SHOT="$SHOTS_DIR/$(date +%Y%m%d-%H%M%S)-home.png"
"$ADB" exec-out screencap -p > "$SHOT"
echo "==> screenshot: $SHOT ($(stat -c%s "$SHOT") bytes)"

cat <<EOF

Ready. App is on the emulator (device: $($ADB get-state)).
Handy adb commands:

  $ADB shell input tap X Y           # tap at (X, Y); use SHOTS_DIR/<file>.png to read coords
  $ADB shell input swipe X1 Y1 X2 Y2 DUR
  $ADB shell input text "string"     # no spaces in the string
  $ADB shell input keyevent 4        # KEYCODE_BACK
  $ADB shell am start -n $PKG/$ACTIVITY   # relaunch
  $ADB exec-out screencap -p > out.png    # screenshot to stdout
  $ADB logcat -d -s AndroidRuntime:E *:S  # recent crashes only
  $ADB logcat -d -t 200 2>&1 | grep -i roadtripradar

Stop everything:
  $0 --stop
EOF