#!/usr/bin/env bash
# run-car-dhu — enable Android Auto dev mode on the Medium_Phone AVD, start
# the AA head unit server, and connect the Desktop Head Unit (DHU) to it so
# the car surface (RoadTripRadar's CarAppService) renders in a desktop window.
#
# Usage:
#   ./.claude/skills/run-car-dhu/run.sh                  # full pipeline (boot → dev mode → HU server → DHU)
#   ./.claude/skills/run-car-dhu/run.sh --skip-emulator  # AVD already attached
#   ./.claude/skills/run-car-dhu/run.sh --skip-devmode   # AA dev mode + HU server already on; just forward + DHU
#   ./.claude/skills/run-car-dhu/run.sh --setup          # idempotent one-time AVD setup (overlayfs + real AA + role)
#   ./.claude/skills/run-car-dhu/run.sh --stop           # kill the DHU and the emulator we started
#
# THIS IS THE PRIMARY AGENT PATH. See SKILL.md for background on the one-time
# AVD setup (writable /product overlayfs + real AA APK + system-priv-app +
# SYSTEM_AUTOMOTIVE_PROJECTION role) that this script handles via `--setup` and
# depends on for the per-run flow.

set -euo pipefail

SKILL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SKILL_DIR" && while [ ! -f "$PWD/gradlew" ] && [ "$PWD" != / ]; do cd ..; done; pwd)"
[ -f "$REPO_ROOT/gradlew" ] || { echo "could not locate gradlew from $SKILL_DIR" >&2; exit 1; }

ANDROID_SDK="${ANDROID_HOME:-/home/chris/Android/Sdk}"
export ANDROID_SDK
export ANDROID_HOME="$ANDROID_SDK"
export PATH="$ANDROID_SDK/platform-tools:$ANDROID_SDK/emulator:$ANDROID_SDK/cmdline-tools/latest/bin:$PATH"

# AVD: API 35 google_apis (NOT playstore) — userdebug build where `adb root`
# works without Magisk. The setup path uses -writable-system (overlayfs on
# /product) to (a) remove the platform AA stub and (b) install the real AA as
# a system priv-app so it qualifies for the SYSTEM_AUTOMOTIVE_PROJECTION role
# that Android 15's CompanionDeviceManager requires. See SKILL.md and NOTES.md.
AVD="Medium_Phone"
DHU_BIN="$ANDROID_SDK/extras/google/auto/desktop-head-unit"
SHOTS_DIR="$SKILL_DIR/shots"
mkdir -p "$SHOTS_DIR"

# The real Android Auto .apkm bundle (base + x86_64 + en + xxhdpi splits) from
# APKMirror. Cloudflare blocks curl from APKMirror, so it has to be downloaded
# in a browser and dropped into the skill dir.
APKM_GLOB=("$SKILL_DIR"/com.google.android.projection.gearhead_*.apkm)

# The package, the role, and the on-device paths the setup mutates.
AA_PKG="com.google.android.projection.gearhead"
AA_ROLE="android.app.role.SYSTEM_AUTOMOTIVE_PROJECTION"
AA_PRIVAPP_DIR="/product/priv-app/AndroidAuto"
AA_STUB_DIR="/product/priv-app/AndroidAutoStubPrebuilt"
# Runtime (dangerous) permissions AA declares — granted via `pm grant` so AA's
# troubleshooter doesn't tear down the car-client on first DHU connect. `pm
# grant` is idempotent (no-op if already granted).
AA_RUNTIME_PERMS=(
  READ_CALENDAR POST_NOTIFICATIONS READ_CALL_LOG
  ACCESS_FINE_LOCATION NEARBY_WIFI_DEVICES BLUETOOTH_CONNECT ACCESS_COARSE_LOCATION
  READ_PHONE_STATE CALL_PHONE RECORD_AUDIO READ_CONTACTS BLUETOOTH_SCAN
  RECEIVE_SMS SEND_SMS
)

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
  # Bracket trick so the pattern can't match this very command line (which
  # would self-kill the shell with exit 144).
  pkill -f "[d]esktop-head-unit" 2>/dev/null || true
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

# --- Emulator-only safety guards ---------------------------------------------
# This skill issues destructive adb commands (install-multiple, pm uninstall,
# reboot, settings put, UI taps, system-file writes via overlayfs) and forwards
# ports. It must NEVER touch a real phone. Two checks enforce that:
#
#   assert_no_real_devices  — call before any adb command. Refuses if even
#                             one non-emulator (USB / TCP-attached phone) is
#                             connected, regardless of ANDROID_SERIAL, so a
#                             bare `adb` command can never misroute to a phone.
#   assert_single_emulator  — call once the AVD is booted. Requires exactly
#                             one booted emulator (so bare `adb` doesn't hit
#                             "more than one device") AND verifies
#                             ro.kernel.qemu=1, the kernel-level proof that the
#                             target is actually emulated.
# adb devices prints "serial\tstate" rows after a header; emulator serials
# are "emulator-NNNN", everything else (USB serials, host:port TCP) is a real
# or network-attached phone.
assert_no_real_devices() {
  local real=""
  while IFS=$'\t' read -r serial state; do
    case "$state" in device|offline|unauthorized) ;; *) continue ;; esac
    [[ "$serial" == emulator-* ]] || real="$real $serial"
  done < <("$ADB" devices 2>/dev/null | tail -n +2)
  if [ -n "$real" ]; then
    echo "REFUSING TO RUN: non-emulator adb device(s) attached:$real" >&2
    echo "This skill only operates on emulators — it installs packages, toggles" >&2
    echo "system settings, writes system files via overlayfs, and reboots via" >&2
    echo "adb, which must never hit a real phone." >&2
    echo "Disconnect the device(s) (or 'adb disconnect <host:port>') and re-run." >&2
    exit 1
  fi
}

assert_single_emulator() {
  local emu_count=0 serial="" real="" qemu
  while IFS=$'\t' read -r s state; do
    case "$state" in device) ;; *) continue ;; esac
    if [[ "$s" == emulator-* ]]; then
      emu_count=$((emu_count+1)); serial="$s"
    else
      real="$real $s"
    fi
  done < <("$ADB" devices 2>/dev/null | tail -n +2)
  if [ -n "$real" ]; then
    echo "REFUSING TO RUN: non-emulator adb device(s) attached:$real" >&2
    echo "Disconnect them (or 'adb disconnect <host:port>') before continuing." >&2
    exit 1
  fi
  [ "$emu_count" -ge 1 ] || { echo "No booted emulator attached — expected exactly one." >&2; exit 1; }
  [ "$emu_count" -eq 1 ] || {
    echo "Expected exactly one booted emulator, found $emu_count. Bare 'adb'" >&2
    echo "commands would be ambiguous. Stop the extras or set ANDROID_SERIAL to one." >&2
    exit 1; }
  qemu=$("$ADB" shell getprop ro.kernel.qemu 2>/dev/null | tr -d '\r')
  if [ "$qemu" != "1" ]; then
    echo "REFUSING TO RUN: $serial is not qemu-emulated (ro.kernel.qemu=${qemu:-<empty>})." >&2
    echo "This skill only operates on emulators." >&2
    exit 1
  fi
  echo "==> verified emulator-only: $serial (ro.kernel.qemu=1)"
}

# Run the cheap pre-check once, up front, for every mode (setup + per-run).
# (`--stop` already exited above, so teardown is never blocked by this.)
assert_no_real_devices

# --- Emulator launch / lifecycle helpers --------------------------------------
# GPU/window mode: defaults to `-gpu host` on DISPLAY=:0 (the user's X session,
# where the emulator window and DHU are visible). Set EMU_HEADLESS=1 to boot
# headless with swiftshader — used when there is no usable X server (e.g. a
# headless agent context missing Qt's libxcb-cursor). adb-driven flows work
# either way; only the visible window differs.
emu_gpu_flags() {
  if [ "${EMU_HEADLESS:-0}" = "1" ]; then
    echo "-no-window -gpu swiftshader_indirect"
  else
    echo "-gpu host"
  fi
}
# A crashed or SIGKILLed emulator leaves a `multiinstance.lock` under its
# ~/.android/avd/<AVD>.avd/ dir. The next launch then FATALs with
# "Running multiple emulators with the same AVD is an experimental feature.
# Please use -read-only flag" — and the check is GLOBAL (any AVD's stale lock
# blocks ALL new launches, not just that AVD's). If no emulator is currently
# attached to adb, every multiinstance.lock is stale; remove them so the
# launch can proceed. Safe: a genuinely-running emulator shows up in
# `adb devices`, so we only clear when nothing is attached.
clear_stale_emulator_locks() {
  if ! "$ADB" devices 2>/dev/null | grep -q '^emulator-'; then
    find "$HOME/.android/avd" -maxdepth 2 -name multiinstance.lock -delete 2>/dev/null || true
  fi
}
# Launch the AVD and block until sys.boot_completed=1.
#
# -writable-system is REQUIRED for the API 35 setup path: it disables verity
# and sets up an overlayfs on /system + /product so we can (a) delete the
# platform AA stub and (b) drop the real AA in as a system priv-app. The
# overlay persists in a scratch partition, but it is only MOUNTED on boots
# launched with -writable-system — so EVERY emulator launch (setup AND
# per-run) must carry the flag, or the system-priv-app AA vanishes and the
# projection role no longer applies. We do NOT pass -wipe-data: that would
# wipe /data (uninstalling the real AA, dropping the role grant and runtime
# perms) and break the idempotent re-run. To start fresh, wipe the AVD
# manually (`emulator -avd Medium_Phone -wipe-data` or the AVD manager) once,
# THEN run `--setup`.
launch_emulator_and_wait() {  # launch_emulator_and_wait [reason]
  local reason="${1:-}"
  [ -n "$reason" ] && reason=" ($reason)"
  echo "==> launching emulator (AVD=$AVD)${reason} on DISPLAY=${DISPLAY:-:0}"
  clear_stale_emulator_locks
  DISPLAY="${DISPLAY:-:0}" setsid "$ANDROID_SDK/emulator/emulator" -avd "$AVD" \
    -no-boot-anim -no-snapshot -no-snapshot-load -no-snapshot-save \
    -writable-system \
    $(emu_gpu_flags) -accel auto -partition-size 2048 -no-metrics \
    >"$SHOTS_DIR/emulator.log" 2>&1 < /dev/null &
  disown
  "$ADB" wait-for-device
  local b=""
  for i in $(seq 1 180); do
    b=$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
    [ "$b" = "1" ] && { echo "    booted after ~$((i*10))s"; break; }
    sleep 10
  done
  [ "$b" = "1" ] || { echo "emulator did not boot (see $SHOTS_DIR/emulator.log)" >&2; exit 1; }
}

stop_emulator() {
  echo "==> stopping emulator"
  "$ADB" emu kill 2>/dev/null || true
  for _ in $(seq 1 30); do
    "$ADB" get-state >/dev/null 2>&1 || break
    sleep 1
  done
  sleep 2
}

# Reboot the AVD and block until it's back up, then re-root adb. Used by the
# setup steps that need PMS to re-scan /product (after deleting the stub, after
# dropping the priv-app AA) so the package flags update.
reboot_and_wait() {
  echo "==> rebooting AVD to apply changes"
  "$ADB" shell "reboot" >/dev/null 2>&1 || true
  sleep 2
  "$ADB" wait-for-device
  local b=""
  for i in $(seq 1 90); do
    b=$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
    [ "$b" = "1" ] && { echo "    rebooted after ~$((i*5))s"; break; }
    sleep 5
  done
  [ "$b" = "1" ] || { echo "AVD did not come back after reboot" >&2; exit 1; }
  "$ADB" root >/dev/null 2>&1 || true
  sleep 1
}

# Ensure /product is writable via the -writable-system overlay. `adb remount`
# on a fresh -writable-system boot sets up the overlay but says "reboot for
# changes to take effect" — /product is NOT writable until that reboot lands.
# So: remount, probe by touching a file in /product; if it fails, reboot and
# remount again. Idempotent — a 2nd run with the overlay already live probes
# successfully and returns immediately. Errors out with a clear hint if the
# emulator was launched WITHOUT -writable-system (remount can't help then).
ensure_overlay_rw() {
  "$ADB" root >/dev/null 2>&1 || true
  "$ADB" wait-for-device
  sleep 1
  "$ADB" root >/dev/null 2>&1 || true
  sleep 1
  "$ADB" remount >/dev/null 2>&1 || true
  if "$ADB" shell "touch /product/.rwtest 2>/dev/null" >/dev/null 2>&1 \
     && "$ADB" shell "rm -f /product/.rwtest" >/dev/null 2>&1; then
    echo "    /product is writable (overlay active)"
    return 0
  fi
  echo "    /product not writable after remount — rebooting to activate the overlay"
  reboot_and_wait
  "$ADB" root >/dev/null 2>&1 || true; sleep 1
  "$ADB" remount >/dev/null 2>&1 || true
  if "$ADB" shell "touch /product/.rwtest 2>/dev/null" >/dev/null 2>&1 \
     && "$ADB" shell "rm -f /product/.rwtest" >/dev/null 2>&1; then
    echo "    /product is writable after reboot"
    return 0
  fi
  echo "    FAILED to make /product writable." >&2
  echo "    Was the emulator launched with -writable-system? This script adds it" >&2
  echo "    automatically when IT launches the AVD; if you booted the AVD yourself," >&2
  echo "    relaunch with:  emulator -avd $AVD -writable-system -no-snapshot" >&2
  exit 1
}

# --- UI navigation helpers (used by the dev-mode flow)
# Dump the on-screen UI hierarchy to a host file, find a node by text, tap it.
dump_ui() { "$ADB" shell uiautomator dump >/dev/null 2>&1; "$ADB" pull /sdcard/window_dump.xml "$1" >/dev/null 2>&1; }
tap_text() {  # tap_text <file> <text-substring>
  dump_ui "$1"
  local c; c=$(python3 "$SKILL_DIR/find_node.py" "$1" "$2") || { echo "    [$2] NOT FOUND" >&2; return 1; }
  echo "    [$2] @ $c -> tap"
  "$ADB" shell input tap $c
}
tap_desc() {  # tap_desc <file> <content-desc-substring>
  dump_ui "$1"
  local c; c=$(python3 "$SKILL_DIR/find_node.py" "$1" "$2" --desc) || { echo "    [desc:$2] NOT FOUND" >&2; return 1; }
  echo "    [desc:$2] @ $c -> tap"
  "$ADB" shell input tap $c
}

# Read the REAL (in /data/app) AA install's versionName + installerPackageName.
# `dumpsys package` reports BOTH the real install (codePath=/data/app/...) and
# any system stub (codePath=/product/...). We want the real one. Empty if the
# real AA isn't installed.
aa_real_version() {
  "$ADB" shell dumpsys package "$AA_PKG" 2>/dev/null \
    | awk '
        /^[[:space:]]*codePath=\/data\/app\// { found_real=1; next }
        found_real && /versionName=/ { gsub(/\r/,""); sub(/^[[:space:]]*versionName=/,""); print; exit }
      '
}
aa_real_installer() {
  "$ADB" shell dumpsys package "$AA_PKG" 2>/dev/null \
    | awk '
        /^[[:space:]]*codePath=\/data\/app\// { found_real=1; next }
        found_real && /installerPackageName=/ { gsub(/\r/,""); sub(/^[[:space:]]*installerPackageName=/,""); print; exit }
      '
}

# --- One-time AVD setup (idempotent) -----------------------------------------
# The API 35 path: no Magisk, no aa4mg module, no ramdisk patching. `adb root`
# works directly on the google_apis userdebug image. The six steps:
#   1. writable /product via -writable-system overlayfs (adb root + remount)
#   2. remove the platform AA stub from /product/priv-app + reboot so PMS
#      deregisters it (else the real APK rejects with INSTALL_FAILED_UPDATE_INCOMPATIBLE)
#   3. install the real AA APK (base + x86_64 + en + xxhdpi splits) with the
#      `-i com.android.vending` installer trick so AA enables full features
#   4. grant AA's runtime (dangerous) permissions so its troubleshooter doesn't
#      tear down the car-client on first DHU connect
#   5. copy the real AA's base.apk into /product/priv-app/AndroidAuto/ + reboot
#      so PMS flags it SYSTEM/PRIVILEGED — a user-installed (/data/app) AA does
#      NOT qualify for the role; a system one does
#   6. grant the SYSTEM_AUTOMOTIVE_PROJECTION role (set-bypassing-role-
#      qualification + add-role-holder). This is the permission that Android
#      15's CompanionDeviceManager checks ("Failed to register vehicle with
#      CDM" / "No 0p checker" without it); it's protection level internal|role
#      and is granted ONLY to the role holder, which must be a system app.
run_setup() {
  if [ "$SKIP_EMULATOR" -eq 0 ]; then
    launch_emulator_and_wait "for setup"
  fi
  # Confirm the attached device is exactly one qemu emulator before any adb.
  assert_single_emulator

  echo "==> [1/6] writable /product via overlayfs (adb root + remount)"
  ensure_overlay_rw

  echo "==> [2/6] remove the platform AA stub + let PMS deregister it"
  if "$ADB" shell "test -d $AA_STUB_DIR" >/dev/null 2>&1; then
    echo "    removing $AA_STUB_DIR"
    "$ADB" shell "rm -rf $AA_STUB_DIR" \
      || { echo "    rm of stub dir failed" >&2; exit 1; }
    # Belt-and-suspenders: deregister for user 0 in case pm still has it cached.
    "$ADB" shell "pm uninstall -k --user 0 $AA_PKG" >/dev/null 2>&1 || true
    echo "    rebooting so PMS re-scans /product and drops the stub registration"
    reboot_and_wait
    ensure_overlay_rw
  else
    echo "    stub dir already gone"
  fi

  echo "==> [3/6] install the real Android Auto APK (installer=com.android.vending)"
  local aa_ver aa_inst
  aa_ver=$(aa_real_version)
  aa_inst=$(aa_real_installer)
  if [ -n "${aa_ver:-}" ] && [ "${aa_inst:-}" = "com.android.vending" ]; then
    echo "    already installed ($aa_ver)"
  else
    echo "    installing (current=${aa_ver:-<none>} installer=${aa_inst:-<none>})"
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
    # -i com.android.vending marks it Play-Store-installed so AA enables full
    # features; -r allows replacing a prior install.
    "$ADB" install-multiple -r -i com.android.vending "${splits[@]}" \
      || { echo "    install-multiple failed" >&2
           echo "    If INSTALL_FAILED_UPDATE_INCOMPATIBLE: the platform stub is still" >&2
           echo "    registered. Reboot the AVD and re-run --setup (step 2 removes it)." >&2
           exit 1; }
    echo "    installed."
  fi

  echo "==> [4/6] grant AA runtime (dangerous) permissions"
  local granted=0 skipped=0
  for p in "${AA_RUNTIME_PERMS[@]}"; do
    if "$ADB" shell "pm grant $AA_PKG android.permission.$p" >/dev/null 2>&1; then
      granted=$((granted+1))
    else
      skipped=$((skipped+1))  # already granted, or not declared by this AA build
    fi
  done
  echo "    granted $granted, skipped $skipped (already granted / not declared)"

  echo "==> [5/6] make AA a system priv-app (so it qualifies for the projection role)"
  # A user-installed AA in /data/app is NOT a system app, so `cmd role
  # add-role-holder` rejects it with "Package does not qualify for the role".
  # Copying its base.apk into /product/priv-app (via the live overlay) and
  # rebooting makes PMS flag it SYSTEM/PRIVILEGED — the /data/app install with
  # its splits stays the running update, but the package gains the SYSTEM flag.
  local priv_base="$AA_PRIVAPP_DIR/base.apk"
  if "$ADB" shell "test -f $priv_base" >/dev/null 2>&1; then
    echo "    already present at $priv_base"
  else
    local base_path
    base_path=$("$ADB" shell "pm path $AA_PKG" 2>/dev/null | tr -d '\r' \
                | grep -m1 "base.apk" | sed 's/^package://')
    [ -n "$base_path" ] || { echo "    could not find installed base.apk via 'pm path'" >&2; exit 1; }
    echo "    copying $base_path -> $priv_base"
    ensure_overlay_rw
    "$ADB" shell "mkdir -p $AA_PRIVAPP_DIR && cp $base_path $priv_base && chmod 644 $priv_base && restorecon $priv_base" \
      || { echo "    failed to place base.apk in $AA_PRIVAPP_DIR" >&2; exit 1; }
    echo "    rebooting so PMS flags $AA_PKG as SYSTEM/PRIVILEGED"
    reboot_and_wait
    ensure_overlay_rw
  fi
  # Verify the SYSTEM flag landed.
  if ! "$ADB" shell "dumpsys package $AA_PKG" 2>/dev/null | tr -d '\r' \
        | grep -qE 'flags=\[.*SYSTEM'; then
    echo "    WARNING: $AA_PKG not flagged SYSTEM after reboot — role grant below may fail" >&2
  fi

  echo "==> [6/6] grant the SYSTEM_AUTOMOTIVE_PROJECTION role"
  # The role is what actually grants REQUEST_COMPANION_PROFILE_AUTOMOTIVE_PROJECTION
  # (protection level internal|role). set-bypassing-role-qualification lets us add
  # a holder without the usual app-qualification checks; add-role-holder is
  # idempotent-ish (we verify the holder list afterward).
  "$ADB" shell "cmd role set-bypassing-role-qualification true" >/dev/null 2>&1 || true
  local holders
  holders=$("$ADB" shell "cmd role get-role-holders $AA_ROLE" 2>/dev/null | tr -d '\r')
  if printf '%s\n' "$holders" | grep -q "$AA_PKG"; then
    echo "    role already held by $AA_PKG"
  else
    "$ADB" shell "cmd role add-role-holder $AA_ROLE $AA_PKG" \
      || { echo "    add-role-holder failed (is $AA_PKG flagged SYSTEM?)" >&2; exit 1; }
    holders=$("$ADB" shell "cmd role get-role-holders $AA_ROLE" 2>/dev/null | tr -d '\r')
    printf '%s\n' "$holders" | grep -q "$AA_PKG" \
      || { echo "    role holder not set: '$holders'" >&2; exit 1; }
    echo "    role granted to $AA_PKG"
  fi
  # Confirm the internal|role permission is now actually granted.
  if "$ADB" shell "dumpsys package $AA_PKG" 2>/dev/null | tr -d '\r' \
        | grep -q "REQUEST_COMPANION_PROFILE_AUTOMOTIVE_PROJECTION: granted=true"; then
    echo "    REQUEST_COMPANION_PROFILE_AUTOMOTIVE_PROJECTION: granted=true"
  else
    echo "    WARNING: REQUEST_COMPANION_PROFILE_AUTOMOTIVE_PROJECTION not granted=true" >&2
  fi

  echo
  echo "==> setup complete: AA=$(aa_real_version), installer=$(aa_real_installer)"
  echo
  echo "    ONE MANUAL STEP: launch Android Auto on the emulator once and complete"
  echo "    its first-launch setup (consent / safety / the permission prompts it"
  echo "    surfaces). The runtime perms above are pre-granted, but AA still runs"
  echo "    its own onboarding the first time it's opened, and the head unit server"
  echo "    connects reliably only after that. Then run the per-run flow:"
  echo "        $0 --skip-emulator"
}

if [ "$SETUP" -eq 1 ]; then
  run_setup
  exit 0
fi

# --- Emulator ----------------------------------------------------------------
if [ "$SKIP_EMULATOR" -eq 0 ]; then
  if ! "$ADB" get-state >/dev/null 2>&1; then
    echo "==> launching emulator (AVD=$AVD, window on DISPLAY=${DISPLAY:-:0})"
    echo "    First boot on a fresh AVD takes ~10-15 min under software rendering."
    echo "    See SKILL.md 'Gotchas' if boot is slow."
    clear_stale_emulator_locks
    launch_emulator_and_wait
  else
    echo "==> waiting for boot..."
    "$ADB" wait-for-device
    for i in $(seq 1 180); do
      b=$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
      [ "$b" = "1" ] && { echo "    booted after ~$((i*10))s"; break; }
      sleep 10
    done
    [ "${b:-}" = "1" ] || { echo "emulator did not report boot_completed; see $SHOTS_DIR/emulator.log" >&2; exit 1; }
  fi
fi

# Per-run flow: confirm the attached device is exactly one qemu emulator
# before any `adb shell` / install / forward lands on it.
assert_single_emulator

# Stable display + unlock.
"$ADB" shell settings put global window_animation_scale 0
"$ADB" shell settings put global transition_animation_scale 0
"$ADB" shell settings put global animator_duration_scale 0
"$ADB" shell svc power stayon true
"$ADB" shell input keyevent KEYCODE_WAKEUP 2>/dev/null || true
"$ADB" shell input swipe 540 1800 540 400 200 2>/dev/null || true

# Confirm the REAL Android Auto APK is installed (not just the system stub).
echo "    AA real versionName: $(aa_real_version || true)"
echo "    AA installer:        $(aa_real_installer || true)"
if [ -z "$(aa_real_version)" ] || [ "$(aa_real_installer)" != "com.android.vending" ]; then
  echo "Real Android Auto (com.android.vending installer) is not installed on this AVD." >&2
  echo "Run the one-time AVD setup first — see SKILL.md 'One-time AVD setup' or run:" >&2
  echo "    $0 --setup --skip-emulator" >&2
  exit 1
fi

# --- Dev mode + head unit server ---------------------------------------------
# (dump_ui / tap_text / tap_desc are defined above with the setup helpers.)

if [ "$SKIP_DEVMODE" -eq 0 ]; then
  echo "==> enabling Android Auto developer mode (10 taps on the AA Version row)"
  # Launch AA settings directly.
  "$ADB" shell am force-stop com.google.android.projection.gearhead 2>/dev/null || true
  sleep 1
  # Surface the am start result instead of hiding it — if the activity class
  # is wrong for this AA version (or AA is in a broken state), we want to see
  # "Error: Activity class {...} does not exist" rather than fail opaquely.
  rcd_start_out=$("$ADB" shell am start -n com.google.android.projection.gearhead/.companion.settings.DefaultSettingsActivity 2>&1 || true)
  if printf '%s\n' "$rcd_start_out" | grep -qiE 'Error|Exception|does not exist|Warning'; then
    echo "    am start did not launch cleanly:" >&2
    printf '%s\n' "$rcd_start_out" | sed 's/^/      /' >&2
  fi
  sleep 2.5

  # AA 17.x may show a first-launch safety/consent/onboarding screen before
  # the actual settings list. Tap through common forward buttons until we
  # land on a screen whose hierarchy contains the settings title or the
  # Version row. This is a bounded loop: a few rounds, then bail with a
  # diagnostic dump so we can see what AA actually rendered.
  rcd_aaset=/tmp/rcd_aaset.xml
  rcd_reached=0
  rcd_round=0
  while [ "$rcd_round" -lt 8 ]; do
    rcd_round=$((rcd_round + 1))
    dump_ui "$rcd_aaset"
    # We've landed in AA settings if we see either the (older) toolbar title,
    # a bottom-of-list Version/About row, OR any of the 17.1 settings rows
    # that appear at the top of the list — 17.1 dropped the toolbar title and
    # opens straight to the list ("Connection", "Startup", "Customize
    # launcher", ...). The Version row is further down, reached by the
    # scroll loop below, not by this check.
    if grep -qE 'text="Android Auto settings"|text="Version"|text="About Android Auto"|text="Connection"|text="Connect a vehicle"|text="Startup"|text="Customize launcher"' "$rcd_aaset"; then
      rcd_reached=1
      break
    fi
    # Try a forward/consent button. Tap the first one found, then re-dump.
    rcd_tapped=0
    for rcd_label in "Continue" "Get started" "Got it" "Accept" "I agree" "Agree" "OK" "Allow" "Start" "Next" "Begin"; do
      if grep -q "text=\"$rcd_label\"" "$rcd_aaset"; then
        tap_text "$rcd_aaset" "$rcd_label" || true
        rcd_tapped=1
        sleep 1.5
        break
      fi
    done
    # Nothing tappable found this round — either we're already on settings
    # (caught above) or we're stuck on a screen we don't recognize. Stop.
    [ "$rcd_tapped" = "1" ] || break
  done

  if [ "$rcd_reached" = "0" ]; then
    echo "did not reach Android Auto settings" >&2
    echo "    (after $rcd_round round(s) of tapping forward buttons)" >&2
    echo "    Visible text on screen:" >&2
    { grep -oE 'text="[^"]+"' "$rcd_aaset" 2>/dev/null || true; } \
      | grep -vE 'text=""' | head -25 | sed 's/^/      /' >&2
    echo "    am start output was:" >&2
    printf '%s\n' "$rcd_start_out" | sed 's/^/      /' >&2
    echo "    If the screen above is an AA onboarding/consent page, complete AA's" >&2
    echo "    first-launch setup manually (see the 'ONE MANUAL STEP' note from --setup)," >&2
    echo "    then re-run. Or tell me the exact button label to tap." >&2
    exit 1
  fi

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
  # The overflow is a content-desc node ("More options") whose y depends on the
  # AA version / screen density, so find it dynamically instead of hardcoding.
  echo "==> starting the Android Auto head unit server"
  dump_ui /tmp/rcd_ovf_entry.xml
  OVC=$(python3 "$SKILL_DIR/find_node.py" /tmp/rcd_ovf_entry.xml "More options" --desc || true)
  if [ -n "$OVC" ]; then
    echo "    More options @ $OVC -> tap"
    "$ADB" shell input tap $OVC
  else
    # Fallback to the historically-verified top-right coord.
    echo "    (overflow not found by desc; tapping 1027 215)"
    "$ADB" shell input tap 1027 215
  fi
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
  echo "    - AA too old: install the real APK per SKILL.md" >&2
  echo "    - Head unit server not listening: re-run dev-mode flow" >&2
  echo "    - AA first-launch setup not completed: open AA on the AVD once," >&2
  echo "      finish its onboarding/consent, then re-run" >&2
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