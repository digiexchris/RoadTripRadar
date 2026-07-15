#!/usr/bin/env bash
# run-car-dhu — enable Android Auto dev mode on the Medium_Phone AVD, start
# the AA head unit server, and connect the Desktop Head Unit (DHU) to it so
# the car surface (RoadTripRadar's CarAppService) renders in a desktop window.
#
# Usage:
#   ./.claude/skills/run-car-dhu/run.sh                  # full pipeline (boot → dev mode → HU server → DHU)
#   ./.claude/skills/run-car-dhu/run.sh --skip-emulator  # AVD already attached
#   ./.claude/skills/run-car-dhu/run.sh --skip-devmode   # AA dev mode + HU server already on; just forward + DHU
#   ./.claude/skills/run-car-dhu/run.sh --setup          # idempotent one-time AVD setup (Magisk/aa4mg/AA)
#   ./.claude/skills/run-car-dhu/run.sh --stop           # kill the DHU and the emulator we started
#
# THIS IS THE PRIMARY AGENT PATH. See SKILL.md for background on the one-time
# AVD setup (Magisk + aa4mg + real AA APK) that this script handles via
# `--setup` and depends on for the per-run flow.

set -euo pipefail

SKILL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SKILL_DIR" && while [ ! -f "$PWD/gradlew" ] && [ "$PWD" != / ]; do cd ..; done; pwd)"
[ -f "$REPO_ROOT/gradlew" ] || { echo "could not locate gradlew from $SKILL_DIR" >&2; exit 1; }

ANDROID_SDK="${ANDROID_HOME:-/home/chris/Android/Sdk}"
export ANDROID_SDK
export ANDROID_HOME="$ANDROID_SDK"
export PATH="$ANDROID_SDK/platform-tools:$ANDROID_SDK/emulator:$ANDROID_SDK/cmdline-tools/latest/bin:$PATH"

# AVD: API 33 google_apis (NOT playstore) — the only image where we got the
# full Magisk + aa4mg + real AA APK recipe to work. See SKILL.md "One-time
# AVD setup" and NOTES.md.
AVD="Medium_Phone"
DHU_BIN="$ANDROID_SDK/extras/google/auto/desktop-head-unit"
SHOTS_DIR="$SKILL_DIR/shots"
mkdir -p "$SHOTS_DIR"

# Files needed by --setup (must be present in the skill dir, downloaded manually).
# The .apkm is the real Android Auto from APKMirror (Cloudflare blocks curl).
# The .zip is the aa4mg Magisk module from sn-00-x/aa4mg releases.
APKM_GLOB=("$SKILL_DIR"/com.google.android.projection.gearhead_*.apkm)
AA4MG_ZIPS=("$SKILL_DIR"/aa4mg-*.zip /tmp/aa4mg.zip "$HOME"/aa4mg-*.zip)

SKIP_EMULATOR=0
SKIP_DEVMODE=0
SETUP=0
STOP=0
for arg in "$@"; do
  case "$arg" in
    --skip-emulator) SKIP_EMULATOR=1 ;;
    --skip-devmode)  SKIP_DEVMODE=1  ;;
    --setup)         SETUP=1 ;;
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

# --- One-time AVD setup (idempotent) -----------------------------------------
# Branches to here when --setup is passed. Performs the per-AVD prep that
# the per-run flow assumes is already in place. Each step checks pre-state
# and skips if already done. Reboots only when something changed.
run_setup() {
  if [ "$SKIP_EMULATOR" -eq 0 ]; then
    echo "==> launching emulator (AVD=$AVD) for setup"
    DISPLAY="${DISPLAY:-:0}" setsid "$ANDROID_SDK/emulator/emulator" -avd "$AVD" \
      -no-boot-anim -no-snapshot -wipe-data \
      -gpu host -accel auto -partition-size 2048 -no-metrics \
      >"$SHOTS_DIR/emulator.log" 2>&1 < /dev/null &
    disown
    "$ADB" wait-for-device
    for i in $(seq 1 180); do
      b=$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
      [ "$b" = "1" ] && { echo "    booted after ~$((i*10))s"; break; }
      sleep 10
    done
    [ "$b" = "1" ] || { echo "emulator did not boot" >&2; exit 1; }
  fi

  # Make sure adb is running as root for module / system dir access.
  "$ADB" root >/dev/null 2>&1 || true
  sleep 1
  "$ADB" wait-for-device
  "$ADB" root >/dev/null 2>&1 || true
  sleep 1

  local changed=0

  # Helpers — magisk --sqlite refuses to run as non-root, so fall back to
  # `su 0 magisk ...` if adb shell is currently running as the shell uid.
  # Output format: "key=NAME|value=VAL" per line, one row per line.
  magisk_setting() {  # magisk_setting KEY → prints "1"/"0"/"" (empty on error)
    local out
    out=$("$ADB" shell "magisk --sqlite 'SELECT value FROM settings WHERE key=\"$1\";'" 2>/dev/null \
          | tr -d '\r' | grep -m1 "^value=" || true)
    out=${out#value=}
    echo "${out:-}"
  }
  magisk_policy() {  # magisk_policy UID → prints "2"/"1"/"0"/"" for that uid
    local out
    out=$("$ADB" shell "magisk --sqlite 'SELECT policy FROM policies WHERE uid=$1;'" 2>/dev/null \
          | tr -d '\r' | grep -m1 "^policy=" || true)
    out=${out#policy=}
    echo "${out:-}"
  }
  package_uid() {  # package_uid PKG → prints uid (numeric) or ""
    local out
    out=$("$ADB" shell "dumpsys package $1" 2>/dev/null \
          | awk '/^[[:space:]]*userId=/{gsub(/\r/,"");sub(/^[[:space:]]*userId=/,"");print;exit}')
    echo "${out:-}"
  }

  # --- 1. Magisk present? (we don't install it; rootAVD must be run by hand)
  if "$ADB" shell "command -v magisk" >/dev/null 2>&1; then
    echo "==> [1/6] Magisk: already installed ($($ADB shell magisk -c 2>/dev/null | tr -d '\r'))"
  else
    echo "==> [1/6] Magisk: NOT INSTALLED." >&2
    echo "    Install it manually — rootAVD is the only path that works on this AVD:" >&2
    echo "      git clone https://github.com/newbit1/rootAVD.git ~/rootAVD" >&2
    echo "      ~/rootAVD/rootAVD.sh $ANDROID_HOME/system-images/android-33/google_apis/x86_64/ramdisk.img" >&2
    echo "    Then re-run: $0 --setup" >&2
    exit 1
  fi

  # --- 2. Zygisk enabled?
  if [ "$(magisk_setting zygisk)" = "1" ]; then
    echo "==> [2/6] Zygisk: already enabled"
  else
    echo "==> [2/6] Zygisk: enabling"
    "$ADB" shell "magisk --sqlite \"REPLACE INTO settings (key,value) VALUES ('zygisk', 1);\"" \
      || { echo "    failed to set zygisk" >&2; exit 1; }
    changed=1
  fi

  # --- 3. aa4mg module installed?
  if "$ADB" shell "test -f /data/adb/modules/aa4mg/module.prop" >/dev/null 2>&1; then
    echo "==> [3/6] aa4mg module: already installed"
  else
    echo "==> [3/6] aa4mg module: installing"
    local zsrc=""
    for z in "${AA4MG_ZIPS[@]}"; do
      if [ -f "$z" ]; then zsrc="$z"; break; fi
    done
    [ -n "$zsrc" ] || { echo "    aa4mg zip not found." >&2
       echo "    Download from https://github.com/sn-00-x/aa4mg/releases and put it in $SKILL_DIR" >&2
       exit 1; }
    echo "    using $zsrc"
    "$ADB" push "$zsrc" /data/local/tmp/aa4mg.zip >/dev/null
    # magisk --install-module: headless install. The customize.sh volume-key
    # prompts all time out on the emulator and default to (NO, NO, NO, YES)
    # which is exactly what we want — see SKILL.md and NOTES.md.
    echo "    (waiting up to 60s for customize.sh to time out its volume-key prompts)"
    "$ADB" shell "magisk --install-module /data/local/tmp/aa4mg.zip" 2>&1 \
      | grep -vE "Volume key could not be detected|Volume (UP|DOWN) \(.\) = (YES|NO) assumed" \
      | tail -8 || true
    # Verify the module landed.
    if ! "$ADB" shell "test -f /data/adb/modules/aa4mg/module.prop" >/dev/null 2>&1; then
      echo "    module install failed — check Magisk logs" >&2; exit 1
    fi
    changed=1
  fi

  # --- 4. aa4mg module disabled? (must disable to avoid stub-vs-real-AA crash)
  if "$ADB" shell "test -f /data/adb/modules/aa4mg/disable" >/dev/null 2>&1; then
    echo "==> [4/6] aa4mg disable flag: already set"
  else
    echo "==> [4/6] aa4mg disable flag: touching"
    "$ADB" shell "touch /data/adb/modules/aa4mg/disable"
    changed=1
  fi

  # --- 5. Real AA APK installed? (com.android.vending installer)
  local aa_ver aa_inst
  aa_ver=$("$ADB" shell dumpsys package com.google.android.projection.gearhead 2>/dev/null \
    | awk '/^[[:space:]]*codePath=\/data\/app\//{r=1;next} r && /versionName=/{gsub(/\r/,"");sub(/^[[:space:]]*versionName=/,"");print;exit}')
  aa_inst=$("$ADB" shell dumpsys package com.google.android.projection.gearhead 2>/dev/null \
    | awk '/^[[:space:]]*codePath=\/data\/app\//{r=1;next} r && /installerPackageName=/{gsub(/\r/,"");sub(/^[[:space:]]*installerPackageName=/,"");print;exit}')
  if [ -n "${aa_ver:-}" ] && [ "${aa_inst:-}" = "com.android.vending" ]; then
    echo "==> [5/6] Real AA APK: already installed ($aa_ver)"
  else
    echo "==> [5/6] Real AA APK: installing (current=$aa_ver installer=${aa_inst:-<none>})"
    local apkm=""
    for f in "${APKM_GLOB[@]}"; do
      if [ -f "$f" ]; then apkm="$f"; break; fi
    done
    [ -n "$apkm" ] || { echo "    .apkm not found in $SKILL_DIR" >&2
       echo "    Download the Android Auto x86_64 .apkm from APKMirror and put it in $SKILL_DIR" >&2
       echo "    (Cloudflare blocks curl, so use a browser.)" >&2
       exit 1; }
    local extract=/tmp/rcd-aa-extract
    rm -rf "$extract" && mkdir -p "$extract"
    unzip -q -o "$apkm" -d "$extract"
    local splits=()
    for s in base split_config.x86_64 split_config.en split_config.xxhdpi; do
      [ -f "$extract/${s}.apk" ] && splits+=("$extract/${s}.apk")
    done
    [ "${#splits[@]}" -ge 2 ] || { echo "    .apkm missing expected splits" >&2; exit 1; }
    "$ADB" push "${splits[@]}" /data/local/tmp/ >/dev/null
    "$ADB" install-multiple -r -i com.android.vending \
      /data/local/tmp/base.apk \
      /data/local/tmp/split_config.x86_64.apk \
      /data/local/tmp/split_config.en.apk \
      /data/local/tmp/split_config.xxhdpi.apk \
      || { echo "    install-multiple failed" >&2; exit 1; }
    echo "    installed."
    # No reboot needed for this step.
  fi

  # --- 6. XLauncher root granted?
  local xlauncher_uid
  xlauncher_uid=$(package_uid com.google.android.projection.gearhead.xunlocked)
  if [ -z "${xlauncher_uid:-}" ]; then
    echo "==> [6/6] XLauncher: not installed (aa4mg customize.sh took the XLauncher=NO branch — fine, head unit server doesn't need it)"
  else
    if [ "$(magisk_policy "$xlauncher_uid")" = "2" ]; then
      echo "==> [6/6] XLauncher root: already granted (uid=$xlauncher_uid)"
    else
      echo "==> [6/6] XLauncher root: granting (uid=$xlauncher_uid)"
      "$ADB" shell "magisk --sqlite \"REPLACE INTO policies (uid,policy,until,logging) VALUES ($xlauncher_uid, 2, 0, 0);\"" \
        || { echo "    failed to grant root to XLauncher" >&2; exit 1; }
      # No reboot needed; Magisk reads policies on demand.
    fi
  fi

  # --- Reboot if any structural change
  if [ "$changed" = "1" ]; then
    echo "==> rebooting AVD to apply changes"
    "$ADB" shell "reboot" >/dev/null 2>&1 || true
    sleep 2
    "$ADB" wait-for-device
    for i in $(seq 1 90); do
      b=$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
      [ "$b" = "1" ] && { echo "    rebooted after ~$((i*5))s"; break; }
      sleep 5
    done
    [ "$b" = "1" ] || { echo "AVD did not come back after reboot" >&2; exit 1; }
    "$ADB" root >/dev/null 2>&1 || true
    sleep 1
  else
    echo "==> nothing changed; no reboot needed"
  fi

  # Re-verify the real AA APK is the active one.
  local new_ver new_inst
  new_ver=$("$ADB" shell dumpsys package com.google.android.projection.gearhead 2>/dev/null \
    | awk '/^[[:space:]]*codePath=\/data\/app\//{r=1;next} r && /versionName=/{gsub(/\r/,"");sub(/^[[:space:]]*versionName=/,"");print;exit}')
  new_inst=$("$ADB" shell dumpsys package com.google.android.projection.gearhead 2>/dev/null \
    | awk '/^[[:space:]]*codePath=\/data\/app\//{r=1;next} r && /installerPackageName=/{gsub(/\r/,"");sub(/^[[:space:]]*installerPackageName=/,"");print;exit}')
  echo
  echo "==> setup complete: AA=$new_ver, installer=$new_inst"
  [ -n "${new_ver:-}" ] && [ "${new_inst:-}" = "com.android.vending" ] \
    || { echo "    WARNING: real AA not detected after setup" >&2; exit 1; }
}

if [ "$SETUP" -eq 1 ]; then
  run_setup
  exit 0
fi

# --- Emulator ----------------------------------------------------------------
if [ "$SKIP_EMULATOR" -eq 0 ]; then
  if ! "$ADB" get-state >/dev/null 2>&1; then
    echo "==> launching emulator (AVD=$AVD, window on DISPLAY=:0)"
    echo "    First boot on a fresh AVD takes ~10-15 min under software rendering."
    echo "    See SKILL.md 'Gotchas' if boot is slow."
    DISPLAY="${DISPLAY:-:0}" setsid "$ANDROID_SDK/emulator/emulator" -avd "$AVD" \
      -no-boot-anim -no-snapshot -wipe-data \
      -gpu host -accel auto -partition-size 2048 -no-metrics \
      >"$SHOTS_DIR/emulator.log" 2>&1 < /dev/null &
    disown
  fi
  echo "==> waiting for boot..."
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

# Confirm the REAL Android Auto APK is installed (not just the system stub).
# `dumpsys package com.google.android.projection.gearhead` reports BOTH the
# real install (codePath=/data/app/...) and the system stub
# (codePath=/product/priv-app/AndroidAutoStubPrebuilt). We want the real one.
AA_VERSION=$("$ADB" shell dumpsys package com.google.android.projection.gearhead 2>/dev/null \
  | awk '
      /^[[:space:]]*codePath=\/data\/app\// { found_real=1; next }
      found_real && /versionName=/ { gsub(/\r/,""); sub(/^[[:space:]]*versionName=/, ""); print; exit }
    ')
AA_INSTALLER=$("$ADB" shell dumpsys package com.google.android.projection.gearhead 2>/dev/null \
  | awk '
      /^[[:space:]]*codePath=\/data\/app\// { found_real=1; next }
      found_real && /installerPackageName=/ { gsub(/\r/,""); sub(/^[[:space:]]*installerPackageName=/, ""); print; exit }
    ')
echo "    AA real versionName: ${AA_VERSION:-<not found>}"
echo "    AA installer:        ${AA_INSTALLER:-<unknown>}"
if [ -z "${AA_VERSION:-}" ] || [ "$AA_INSTALLER" != "com.android.vending" ]; then
  echo "Real Android Auto (com.android.vending installer) is not installed on this AVD." >&2
  echo "Run the one-time AVD setup first — see SKILL.md 'One-time AVD setup' or run:" >&2
  echo "    $0 --setup --skip-emulator" >&2
  exit 1
fi

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
  echo "==> enabling Android Auto developer mode (10 taps on the AA Version row)"
  # Launch AA settings directly (works on both real AA 17.x and XLauncher).
  # The XLauncher variant routes to the same settings activity, so this
  # works regardless of which one is current.
  "$ADB" shell am force-stop com.google.android.projection.gearhead 2>/dev/null || true
  sleep 1
  "$ADB" shell am start -n com.google.android.projection.gearhead/.companion.settings.DefaultSettingsActivity \
    >/dev/null 2>&1
  sleep 2.5

  # Confirm we landed in the AA settings.
  dump_ui /tmp/rcd_aaset.xml
  grep -q 'text="Android Auto settings"' /tmp/rcd_aaset.xml \
    || { echo "did not reach Android Auto settings" >&2; exit 1; }

  # Scroll to the About section so the version number is on screen.
  for i in 1 2 3 4 5 6 7 8; do "$ADB" shell input swipe 540 1900 540 700 250; sleep 0.4; done
  sleep 1
  dump_ui /tmp/rcd_bot.xml
  grep -q 'text="Version"' /tmp/rcd_bot.xml \
    || { echo "could not find the Version row (scrolled past?)" >&2; exit 1; }

  # 10 taps on the Version ROW. In AA 17.x the version number is rendered
  # inside the same row as the "Version" label, not as a separate text node,
  # so any tap on the row works. The 11th tap would dismiss the dialog
  # without accepting — stop at 10, then tap OK.
  VERC=$(python3 "$SKILL_DIR/find_node.py" /tmp/rcd_bot.xml "Version" \
         || python3 - <<'PY'
import re
xml=open('/tmp/rcd_bot.xml').read()
for m in re.finditer(r'<node[^>]*text="(\d+\.\d+\.\d+[^"]*)"[^>]*>',xml):
    bm=re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',m.group(0))
    if bm:
        x1,y1,x2,y2=map(int,bm.groups()); print((x1+x2)//2,(y1+y2)//2); break
PY
)
  echo "    version row @ $VERC"
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
  "$ADB" shell input tap 1027 146   # "More options" overflow (content-desc; top-right)
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
  echo "    Check $SHOTS_DIR/dhu.log. Common causes:" >&2
  echo "    - DHU 2.0 missing runtime libs: 'apt install libc++1 libc++abi1'" >&2
  echo "    - AA too old: install the real APK per SKILL.md step 8" >&2
  echo "    - Head unit server not listening: re-run dev-mode flow" >&2
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
