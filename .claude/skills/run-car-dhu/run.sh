#!/usr/bin/env bash
# run-car-dhu — enable Android Auto dev mode on a Play-image emulator, start the
# AA head unit server, and connect the Desktop Head Unit (DHU) to it so the car
# surface (RoadTripRadar's CarAppService) renders in a desktop window.
#
# Usage:
#   ./.claude/skills/run-car-dhu/run.sh                 # full pipeline (boot → dev mode → HU server → DHU)
#   ./.claude/skills/run-car-dhu/run.sh --skip-emulator  # a device/emulator is already attached
#   ./.claude/skills/run-car-dhu/run.sh --skip-devmode   # AA dev mode + HU server already on; just forward + DHU
#   ./.claude/skills/run-car-dhu/run.sh --stop          # kill the DHU and the emulator we started
#
# THIS IS THE PRIMARY AGENT PATH. See SKILL.md for the manual/verification path
# and the critical AA-version caveat (DHU 2.0 silently exits against AA 5.3).

set -euo pipefail

SKILL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SKILL_DIR" && while [ ! -f "$PWD/gradlew" ] && [ "$PWD" != / ]; do cd ..; done; pwd)"
[ -f "$REPO_ROOT/gradlew" ] || { echo "could not locate gradlew from $SKILL_DIR" >&2; exit 1; }

ANDROID_SDK="${ANDROID_HOME:-/home/chris/Android/Sdk}"
export ANDROID_SDK
export ANDROID_HOME="$ANDROID_SDK"
export PATH="$ANDROID_SDK/platform-tools:$ANDROID_SDK/emulator:$ANDROID_SDK/cmdline-tools/latest/bin:$PATH"

# AVD choice: API 36.1 Play image ships a MODERN Android Auto, which is what DHU
# 2.0 needs to complete its handshake. The API-30 Play image (AA 5.3) connects
# then silently exits — see SKILL.md "The AA-version wall".
AVD="Play_Phone_36"
DHU_BIN="$ANDROID_SDK/extras/google/auto/desktop-head-unit"
SHOTS_DIR="$SKILL_DIR/shots"
mkdir -p "$SHOTS_DIR"

SKIP_EMULATOR=0
SKIP_DEVMODE=0
STOP=0
for arg in "$@"; do
  case "$arg" in
    --skip-emulator) SKIP_EMULATOR=1 ;;
    --skip-devmode)  SKIP_DEVMODE=1  ;;
    --stop)          STOP=1 ;;
    -h|--help) sed -n '2,17p' "$0"; exit 0 ;;
    *) echo "unknown flag: $arg" >&2; exit 2 ;;
  esac
done

# --- Stop mode ---------------------------------------------------------------
if [ "$STOP" -eq 1 ]; then
  pkill -f "desktop-head-unit" 2>/dev/null || true
  adb emu kill 2>/dev/null || true
  echo "stopped (DHU + emulator)"
  exit 0
fi

[ -x "$DHU_BIN" ] || {
  echo "DHU not found at $DHU_BIN." >&2
  echo "Install it: sdkmanager \"extras;google;auto\" (ships DHU 2.0)." >&2
  exit 1
}

ADB="$ANDROID_SDK/platform-tools/adb"

# --- Emulator ----------------------------------------------------------------
if [ "$SKIP_EMULATOR" -eq 0 ]; then
  if ! "$ADB" get-state >/dev/null 2>&1; then
    echo "==> launching emulator (AVD=$AVD, window on DISPLAY=:0)"
    echo "    NOTE: API 36.1 first boot under software rendering can take 10-15 min,"
    echo "    and needs ~7.2 GB free for the userdata partition. See SKILL.md."
    DISPLAY="${DISPLAY:-:0}" setsid "$ANDROID_SDK/emulator/emulator" -avd "$AVD" \
      -no-boot-anim -no-snapshot -wipe-data \
      -gpu host -accel auto -partition-size 2048 -no-metrics \
      >"$SHOTS_DIR/emulator.log" 2>&1 < /dev/null &
    disown
  fi
  echo "==> waiting for boot (this is the slow part on 36.1)..."
  "$ADB" wait-for-device
  for i in $(seq 1 180); do
    b=$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
    [ "$b" = "1" ] && { echo "    booted after ~$((i*10))s"; break; }
    sleep 10
  done
  [ "$b" = "1" ] || { echo "emulator did not report boot_completed; see $SHOTS_DIR/emulator.log" >&2; exit 1; }
fi

# Stable display + unlock.
"$ADB" shell settings put global window_animation_scale 0
"$ADB" shell settings put global transition_animation_scale 0
"$ADB" shell settings put global animator_duration_scale 0
"$ADB" shell svc power stayon true
"$ADB" shell input keyevent KEYCODE_WAKEUP 2>/dev/null || true
"$ADB" shell input swipe 540 1800 540 400 200 2>/dev/null || true

# Confirm Android Auto is present (it ships on google_apis_playstore images).
"$ADB" shell pm list packages | grep -q com.google.android.projection.gearhead \
  || { echo "Android Auto (com.google.android.projection.gearhead) not installed on this image." >&2
       echo "Use a google_apis_playstore system image, or sideload a modern AA APK." >&2; exit 1; }

# --- Dev mode + head unit server ---------------------------------------------
# UI navigation helper: dump the hierarchy to a host file, find a node, tap it.
dump_ui() { "$ADB" shell uiautomator dump >/dev/null 2>&1; "$ADB" pull /sdcard/window_dump.xml "$1" >/dev/null 2>&1; }
tap_text() {  # tap_text <file> <text-substring>
  dump_ui "$1"
  local c; c=$(python3 "$SKILL_DIR/find_node.py" "$1" "$2") || { echo "    [$2] NOT FOUND" >&2; return 1; }
  echo "    [$2] @ $c -> tap"
  "$ADB" shell input tap $c
}

if [ "$SKIP_DEVMODE" -eq 0 ]; then
  echo "==> enabling Android Auto developer mode (10 taps on the AA version)"
  # Open the phone Settings app FRESH (force-stop first so it opens at the top).
  "$ADB" shell am force-stop com.android.settings
  sleep 1
  "$ADB" shell am start -a android.settings.SETTINGS >/dev/null 2>&1
  sleep 2.5

  tap_text /tmp/rcd_cd.xml "Connected devices"        || exit 1; sleep 2
  tap_text /tmp/rcd_cp.xml "Connection preferences"   || exit 1; sleep 2
  tap_text /tmp/rcd_aa.xml "Android Auto"             || exit 1; sleep 2

  # Confirm we landed in the AA settings (title "Android Auto settings").
  dump_ui /tmp/rcd_aaset.xml
  grep -q 'text="Android Auto settings"' /tmp/rcd_aaset.xml \
    || { echo "did not reach Android Auto settings" >&2; exit 1; }

  # Scroll to the About section so the version number is on screen.
  for i in 1 2 3 4 5 6 7 8; do "$ADB" shell input swipe 540 1900 540 700 250; sleep 0.4; done
  sleep 1
  dump_ui /tmp/rcd_bot.xml
  grep -q 'text="Version"' /tmp/rcd_bot.xml \
    || { echo "could not find the Version row (scrolled past?)" >&2; exit 1; }

  # 10 taps on the version NUMBER (e.g. "5.3.801658-preinstall"), NOT the "Version" label.
  # 10 taps surfaces the "Allow development settings?" dialog. The 11th tap dismisses it
  # without accepting — do exactly 10, then tap OK.
  VERC=$("$ADB" shell uiautomator dump >/dev/null 2>&1; "$ADB" pull /sdcard/window_dump.xml /tmp/rcd_bot.xml >/dev/null 2>&1; \
         python3 "$SKILL_DIR/find_node.py" /tmp/rcd_bot.xml "preinstall" \
         || python3 "$SKILL_DIR/find_node.py" /tmp/rcd_bot.xml --last "5.")
  # Fallback: pick the node whose text starts with a digit-dot (a version string).
  [ -n "${VERC:-}" ] || VERC=$(python3 - <<'PY'
import re
xml=open('/tmp/rcd_bot.xml').read()
for m in re.finditer(r'<node[^>]*text="(\d+\.\d+\.\d+[^"]*)"[^>]*>',xml):
    bm=re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',m.group(0))
    if bm:
        x1,y1,x2,y2=map(int,bm.groups()); print((x1+x2)//2,(y1+y2)//2); break
PY
)
  echo "    version number @ $VERC"
  for i in $(seq 1 10); do "$ADB" shell input tap $VERC; sleep 0.3; done
  sleep 1.5

  # Tap the OK button on the "Allow development settings?" dialog.
  dump_ui /tmp/rcd_dlg.xml
  grep -q 'text="Allow development settings?"' /tmp/rcd_dlg.xml \
    && echo "    dev-mode dialog appeared" \
    || echo "    (no dev-mode dialog — dev mode may already be on)"
  OKC=$(python3 "$SKILL_DIR/find_node.py" /tmp/rcd_dlg.xml "OK")
  [ -n "$OKC" ] && { "$ADB" shell input tap $OKC; echo "    tapped OK @ $OKC"; sleep 2; } || true

  # Open the overflow (top-right "More options") and start the head unit server.
  echo "==> starting the Android Auto head unit server"
  "$ADB" shell input tap 1024 143   # "More options" overflow (content-desc; top-right)
  sleep 1.5
  dump_ui /tmp/rcd_ovf.xml
  HUS=$(python3 "$SKILL_DIR/find_node.py" /tmp/rcd_ovf.xml "Start head unit server")
  if [ -z "$HUS" ]; then
    # If "Start head unit server" is absent but "Stop head unit server" is present,
    # the server is already running — nothing to do.
    if grep -q 'text="Stop head unit server"' /tmp/rcd_ovf.xml; then
      echo "    head unit server already running"
    else
      echo "    'Start head unit server' not in overflow — dev mode not enabled?" >&2; exit 1
    fi
  else
    echo "    Start head unit server @ $HUS -> tap"
    "$ADB" shell input tap $HUS
    sleep 2.5
  fi
fi

# Confirm the head unit server process is up (and listening on 5277).
"$ADB" shell "ps -A 2>/dev/null | grep gearhead:projection" | head -1 \
  || echo "    (warning: gearhead:projection process not found)"

# --- adb forward + DHU -------------------------------------------------------
echo "==> adb forward tcp:5277 tcp:5277"
"$ADB" forward tcp:5277 tcp:5277
"$ADB" forward --list

echo "==> launching DHU (--adb=5277) on DISPLAY=${DISPLAY:-:0}"
# Headless (-h) suppresses the Qt window; drop it to get the visible car surface.
DISPLAY="${DISPLAY:-:0}" setsid "$DHU_BIN" --adb=5277 >"$SHOTS_DIR/dhu.log" 2>&1 < /dev/null &
DHU_PID=$!
disown
echo "    DHU PID: $DHU_PID  log: $SHOTS_DIR/dhu.log"
sleep 8

if kill -0 $DHU_PID 2>/dev/null; then
  echo "==> DHU is ALIVE — handshake completed. Take a window screenshot:"
  echo "    DISPLAY=${DISPLAY:-:0} xwd -root -silent | convert xwd:- $SHOTS_DIR/dhu-window.png"
  echo "    (or: import -window root $SHOTS_DIR/dhu-window.png)"
else
  echo "==> DHU EXITED shortly after 'connected'." >&2
  echo "    This is the AA-version wall: DHU 2.0 needs a MODERN Android Auto." >&2
  echo "    The API-30 image ships AA 5.3 (too old). Use the API 36.1 Play image" >&2
  echo "    (AVD=$AVD) or sideload a current AA APK. See SKILL.md." >&2
  tail -8 "$SHOTS_DIR/dhu.log" | grep -vE "^ALSA|^Jack|^Cannot connect" >&2 || true
  exit 3
fi

cat <<EOF

Ready. DHU connected to the emulator's AA head unit server (port 5277).
Handy commands:

  $ADB shell dumpsys window | grep mCurrentFocus        # what's on the phone
  tail -f $SHOTS_DIR/dhu.log                              # DHU console
  DISPLAY=${DISPLAY:-:0} xwd -root -silent | convert xwd:- $SHOTS_DIR/shot.png
  $0 --stop                                              # tear it all down

If the DHU window shows the AA launcher, tap the RoadTripRadar entry to load
the car surface (CarAppService). The app's car package is
ca.voiditswarranty.roadtripradar.debug (debug build) — it must be installed
on the emulator first (./gradlew :app:assembleDebug && $ADB install -r app/build/outputs/apk/debug/app-debug.apk).
EOF