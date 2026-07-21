---
name: run-car-dhu
description: Test the RoadTripRadar car (Android Auto) surface on an emulator using the Desktop Head Unit (DHU). Use when the user asks to "test the car version," "run the DHU," "screenshot the car surface," or verify a CarAppService / Android Auto change on a head unit.
---

# run-car-dhu

Drives the **Android Auto Desktop Head Unit (DHU)** against a `google_apis`
emulator so the RoadTripRadar **car surface** (the `CarAppService` in
`app/src/main/java/.../car/`) renders in a desktop window.

> **Heads up — this skill is set up for the API 35 / `google_apis`
> (userdebug) / `-writable-system` overlayfs path that actually works on
> this box.** The earlier "API 33 / Magisk / aa4mg" path documented in
> prior revisions worked but is now superseded: API 35 lets us `adb root`
> directly (no Magisk), and the writable `/product` overlay lets us both
> delete the platform AA stub and install the real AA as a **system
> priv-app** — which is the only way to satisfy Android 15's
> `SYSTEM_AUTOMOTIVE_PROJECTION` role requirement that the API 33 path
> never hit. See `NOTES.md` for the full history of what we tried and why.

The driver script `run.sh` is the primary agent path — it enables AA dev mode
on the emulator, starts the AA head unit server, opens the adb tunnel, and
launches the DHU.

Paths are relative to the repo root (`/home/chris/repos/RoadTripRadar`).

## TL;DR (validated, 2026-07-20)

A one-time AVD setup followed by the per-run flow:

```bash
# One-time, idempotent — re-run on any AVD that needs (re)installing.
.claude/skills/run-car-dhu/run.sh --setup --skip-emulator

# ONE MANUAL STEP after --setup: open Android Auto on the AVD once and
# finish its first-launch onboarding/consent. The runtime perms are
# pre-granted by --setup, but AA still runs its own first-launch flow, and
# the head unit server connects reliably only after it completes.

# Every subsequent run.
.claude/skills/run-car-dhu/run.sh --skip-emulator
```

`--setup` auto-detects which of the six setup steps (overlayfs, stub removal,
real AA APK, runtime perms, system priv-app, role grant) are already done
and skips them. `[I]: connected.` in `shots/dhu.log` is the DHU success
signal.

## Prerequisites

- **Emulator only.** `run.sh` refuses to run if any non-emulator adb device
  (a USB- or TCP-attached phone) is connected, and after boot it requires
  exactly one emulator whose `ro.kernel.qemu=1`. This prevents the
  destructive adb commands (package install, `pm uninstall`, system-file
  writes via overlayfs, `reboot`, `settings put`, UI taps) from ever
  hitting a real phone. Disconnect real devices before invoking it.
- Android SDK at `/home/chris/Android/Sdk` with `platform-tools`, `emulator`,
  `cmdline-tools/latest`, and **DHU 2.0** at
  `$ANDROID_HOME/extras/google/auto/desktop-head-unit`. Install with
  `sdkmanager "extras;google;auto"`. On Debian you also need
  `apt install libc++1 libc++abi1` for DHU's runtime libs.
- An AVD named `Medium_Phone` with the
  `system-images;android-35;google_apis;x86_64` image. `google_apis`
  (NOT `google_apis_playstore`) is required — the Play image is a
  production build and `adb root` is refused. `google_apis` is
  `userdebug` so root works without Magisk.
- The latest Android Auto APKM bundle from APKMirror
  (`com.google.android.projection.gearhead`, x86_64 split). Cloudflare
  blocks `curl` from APKMirror, so the APKM has to be downloaded in a
  browser and dropped into `.claude/skills/run-car-dhu/`.
- The emulator **must be launched with `-writable-system`** so the
  `/product` overlay (which holds the deleted stub + the system-priv-app
  AA) is mounted. `run.sh` adds this flag whenever it launches the AVD;
  if you boot the AVD yourself, add `-writable-system -no-snapshot` or
  the system-priv-app AA vanishes and the projection role no longer
  applies.
- The RoadTripRadar debug APK installed on the emulator so the car surface
  appears in the AA app list:
  ```bash
  ./gradlew :app:assembleDebug
  $ANDROID_HOME/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
  ```

## One-time AVD setup (`--setup`)

Run once after creating the AVD. Re-run any time (it's idempotent). To start
from a truly clean slate, wipe the AVD manually first
(`emulator -avd Medium_Phone -wipe-data` or the AVD manager) — `run.sh`
itself never passes `-wipe-data`, since that would wipe the real AA, the
runtime-permission grants, and the role holder that the setup installs.

```bash
.claude/skills/run-car-dhu/run.sh --setup
```

`--setup` walks six steps. Each one checks pre-state and is skipped if
already done. No Magisk, no aa4mg module, no ramdisk patching — `adb root`
works directly on the `google_apis` userdebug image.

1. **Writable `/product` via overlayfs** (`adb root` + `adb remount`). The
   emulator must be launched with `-writable-system` (run.sh adds it
   automatically). `adb remount` on a fresh boot sets up the overlay but
   says "reboot to take effect" — the script probes `/product`
   writability and reboots + re-remounts only if needed. Idempotent: a
   2nd run with the overlay already live returns immediately.
2. **Remove the platform AA stub** (`rm -rf
   /product/priv-app/AndroidAutoStubPrebuilt`) + `pm uninstall --user 0` +
   reboot. The stub is platform-signed; a public AA APK (Google release
   key) can't `install -r` over it
   (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). The reboot lets PMS re-scan
   `/product`, find the stub gone (the deletion persists in the overlay),
   and deregister it — only then will the real AA install fresh.
3. **Real AA APK installed** (`adb install-multiple -i com.android.vending`
   over the splits extracted from the APKM). The `-i com.android.vending`
   flag is the trick: AA checks its installer identity and only enables
   full features when it thinks it came from the Play Store.
4. **Runtime (dangerous) permissions granted** (`pm grant` for each of
   AA's 14 dangerous perms). Without these, AA's troubleshooter tears down
   the car-client the moment the DHU connects, and the DHU exits ~2s after
   `connected.`. `pm grant` is idempotent.
5. **AA made a system priv-app** (copy the real AA's `base.apk` into
   `/product/priv-app/AndroidAuto/` via the live overlay + reboot). The
   `/data/app` install with its splits stays the running update, but the
   package gains the `SYSTEM`/`PRIVILEGED` flags. This is the crux of the
   API 35 path: a user-installed AA does NOT qualify for the projection
   role (`cmd role add-role-holder` rejects it with "Package does not
   qualify for the role"); a system one does.
6. **`SYSTEM_AUTOMOTIVE_PROJECTION` role granted**
   (`cmd role set-bypassing-role-qualification true` +
   `cmd role add-role-holder`). The role is the only thing that grants
   `REQUEST_COMPANION_PROFILE_AUTOMOTIVE_PROJECTION` (protection level
   `internal|role`) — Android 15's CompanionDeviceManager checks it when
   the `:car` process registers the vehicle, and without it the `:car`
   process crashes with "Failed to register vehicle with CDM" (the "No 0p
   checker" failure). This step is the difference between the working
   API 35 path and every prior dead end.

`--setup` reboots after steps 2 and 5 (PMS re-scan of `/product`). If
everything was already done, no reboot is needed. After it finishes, **open
Android Auto on the AVD once and complete its first-launch
onboarding/consent** — the runtime perms are pre-granted, but AA still runs
its own first-launch flow, and the head unit server connects reliably
only after it.

## Run (agent path) — `run.sh`

Full pipeline (boot → dev mode → head unit server → DHU), assuming
one-time setup is already done:

```bash
.claude/skills/run-car-dhu/run.sh --skip-emulator
```

(`--skip-emulator` because the AVD is already booted. Drop it to also
boot the AVD.)

The script then:
1. Opens AA settings via `am start` → scrolls to the Version row →
   taps it 10 times → taps **OK** on the "Allow development settings?"
   dialog.
2. Opens the overflow (top-right "More options" at `(1027, 146)`) →
   taps **"Start head unit server."**
3. Verifies `gearhead:projection` is running and `5277` is listening.
4. Runs `adb forward tcp:5277 tcp:5277` and launches
   `desktop-head-unit --adb=5277`.
5. Watches for `[I]: connected.` — that's the protocol handshake
   success signal. The Qt window will open on a normal desktop.

After the DHU window appears, tap the RoadTripRadar entry in the AA
app list to load the car surface.

Other flags:
- `run.sh --skip-devmode` — assume dev mode + HU server are already
  running; just forward + launch DHU.
- `run.sh --stop` — kill the DHU and the emulator.

## What `run.sh` actually does (the verified flow)

Steps 1-8 above are one-time. The script does the per-run flow:

1. **Open AA settings** (`am start -n com.google.android.projection.gearhead/.companion.settings.DefaultSettingsActivity`).
2. **Scroll to the About section** so the Version row is visible.
3. **Tap the Version row exactly 10 times.** In AA 17.x, the
   version number is rendered inside the same row as the "Version"
   label, not as a separate text node — tapping anywhere on the row
   10 times works. **Stop at 10.** The 11th tap lands on the
   "Allow development settings?" dialog's neutral area and dismisses
   it without accepting.
4. **Tap OK** on the "Allow development settings?" dialog.
5. **Open the overflow** (top-right "More options", found dynamically by
   content-desc — falls back to `(1027,215)`) and tap **"Start head unit
   server."** The `gearhead:projection` process starts and port 5277
   listens (tcp6).
6. **`adb forward tcp:5277 tcp:5277`** then
   **`desktop-head-unit --adb=5277`**.

After the DHU window appears, tap the RoadTripRadar entry in the AA
app list to load the car surface. Screenshot the DHU window with
`xwd`/`import`:

```bash
DISPLAY=${DISPLAY:-:0} xwd -root -silent | convert xwd:- shots/dhu-window.png
```

## Gotchas (real, all hit this session)

- **The emulator must launch with `-writable-system`.** The overlay
  that holds the deleted stub and the system-priv-app AA is only mounted
  on `-writable-system` boots. `run.sh` adds the flag when it launches
  the AVD; if you boot the AVD yourself without it, the priv-app AA
  vanishes and the projection role no longer applies. `run.sh` never
  passes `-wipe-data` (that would wipe the real AA + role + perms);
  wipe manually only when you want a clean slate.
- **AA dev mode does not survive `-wipe-data`.** A fresh-wipe AVD has
  `android_auto_dev_mode = null`; re-run the 10-tap flow.
- **The Version row IS the tap target, not a separate value.** In
  AA 17.x, the version number is rendered inside the same row as the
  "Version" label, not as a separate text node. Tapping anywhere on
  the row 10 times works.
- **10 taps, then OK — not 11.** The 11th tap lands on the dialog's
  neutral area and dismisses it without enabling dev mode. If dev mode
  is already on, a single tap re-surfaces the dialog.
- **The platform AA stub blocks the real APK.** The
  `AndroidAutoStubPrebuilt` in `/product/priv-app` is platform-signed;
  the public AA APK (Google release key) rejects over it with
  `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Step 2 of `--setup` deletes the
  stub (via the overlay) and reboots so PMS deregisters it before the
  real install. If the install fails with that error, the stub wasn't
  gone yet — reboot and re-run `--setup`.
- **A user-installed AA does NOT qualify for the projection role.**
  `cmd role add-role-holder` rejects `/data/app` AA with "Package does
  not qualify for the role." Step 5 of `--setup` copies `base.apk` into
  `/product/priv-app/AndroidAuto/` + reboots so PMS flags it
  `SYSTEM`/`PRIVILEGED`; only then does the role grant (step 6) succeed.
  Without the role, `REQUEST_COMPANION_PROFILE_AUTOMOTIVE_PROJECTION`
  (protection level `internal|role`) is not granted, and the `:car`
  process crashes on DHU connect with "Failed to register vehicle with
  CDM" (the "No 0p checker" failure).
- **Runtime perms must be `pm grant`-ed before the first DHU connect.**
  AA's troubleshooter tears down the car-client for ungranted dangerous
  permissions and the DHU exits ~2s after `connected.`. Step 4 grants
  all 14.
- **`adb install-multiple -i com.android.vending` is required.** AA
  checks its installer identity and only enables full features when
  it thinks it came from the Play Store. Plain `adb install` leaves AA
  thinking it was side-loaded and the car surface may not load.
- **The DHU log is full of ALSA/Jack noise** ("cannot find card '0'",
  "jack server is not running"). Ignore it — there's no audio device;
  it's harmless. The real signal is `[I]: connected.` followed by
  either the car surface or a silent exit.
- **No `~/.android/headunit.ini` config.** The DHU logs
  `[E]: Could not load configuration from '~/.android/headunit.ini'`
  and uses defaults. Not required for the basic flow.
- **Cloudflare blocks `curl` from APKMirror.** You must download the
  `.apkm` bundle in a browser, save it under
  `.claude/skills/run-car-dhu/`, and `unzip` it locally.
- **DHU 2.0 won't link on Debian without `libc++1 libc++abi1`.** The
  binary ships without those runtime libs; install via apt before
  launching.

## Test

The car surface has its own unit tests (Robolectric, `TestCarContext`):

```bash
./gradlew :app:testDebugUnitTest --tests "ca.voiditswarranty.roadtripradar.car.*"
```

The DHU is for visual/integration verification of the running car
surface.

## Troubleshooting

| Symptom | Fix |
|---|---|
| DHU won't start — `error while loading shared libraries: libc++.so.1` | `apt install libc++1 libc++abi1` |
| DHU prints `connected.` then exits ~2s later | Runtime perms not granted (step 4), or AA first-launch setup not completed. Re-run `--setup`, then open AA on the AVD once and finish its onboarding. |
| `:car` process crashes — "Failed to register vehicle with CDM" | The `SYSTEM_AUTOMOTIVE_PROJECTION` role isn't granted. Run `--setup` (steps 5+6); confirm the AVD booted with `-writable-system` so the priv-app AA is present. |
| AA app shows "Sign in with Google" instead of settings | Stub AA still active: real APK not installed, or the stub wasn't removed. Run `run.sh --setup --skip-emulator` (step 2 removes it). |
| `adb install-multiple` → `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | The platform stub is still registered. Step 2 removes it + reboots; reboot the AVD and re-run `--setup`. |
| `cmd role add-role-holder` → "Package does not qualify for the role" | AA isn't flagged SYSTEM. Step 5 (priv-app + reboot) must land first; confirm the AVD booted with `-writable-system`. |
| `adb remount` → "Device must be bootloader unlocked" / verity still on | The AVD was NOT launched with `-writable-system`. Relaunch: `emulator -avd Medium_Phone -writable-system -no-snapshot`. |
| `adb root` → "cannot run as root in production builds" | You used `google_apis_playstore`. Use `google_apis` (API 35, not Play). |
| The Version row doesn't surface a dialog after 10 taps | Dev mode is already on; a single tap re-surfaces the dialog. |
| `pm dump … versionName` shows `1.8` or `1.0-stub` | The real AA APK install failed. Re-run `--setup` with the `.apkm` in the skill dir and the `-i com.android.vending` flag. |

## What's in this skill

```
.claude/skills/run-car-dhu/
  SKILL.md        ← this file
  run.sh          ← primary driver (dev mode → HU server → forward → DHU)
  find_node.py    ← uiautomator-dump → center-coords helper used by run.sh
  NOTES.md        ← battle scars / discovery log
  shots/          ← emulator + DHU logs and screenshots land here
```
