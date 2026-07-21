assert_no_real_devices() {
  local real=""
  while IFS=$'\t' read -r serial state; do
    case "$state" in device|offline|unauthorized) ;; *) continue ;; esac
    [[ "$serial" == emulator-* ]] || real="$real $serial"
  done < <("$ADB" devices 2>/dev/null | tail -n +2)
  if [ -n "$real" ]; then
    echo "REFUSING TO RUN: non-emulator adb device(s) attached:$real" >&2
    echo "This skill only operates on emulators — it installs packages, toggles" >&2
    echo "system settings, and reboots via adb, which must never hit a real phone." >&2
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


run_case() {
  local desc="$1"; shift
  ADB="$1"; shift
  echo "--- $desc"
  ( assert_no_real_devices ) && echo "  no_real: PASS(proceed)"
}
