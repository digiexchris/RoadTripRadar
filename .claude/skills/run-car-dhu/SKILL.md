---
name: run-car-dhu
description: Test the RoadTripRadar car (Android Auto) surface on an emulator using the Desktop Head Unit (DHU). Use when the user asks to "test the car version," "run the DHU," "screenshot the car surface," or verify a CarAppService / Android Auto change on a head unit.
---

# run-car-dhu

Drives the **Android Auto Desktop Head Unit (DHU)** against a `google_apis`
emulator so the RoadTripRadar **car surface** (the `CarAppService` in
`app/src/main/java/.../car/`) renders in a desktop window.

> **Heads up — this skill is set up for the API 33 / Magisk / aa4mg path that
> actually works on this box.** The original "API 36.1 Play image" path
> documented in earlier revisions of this file **does not work**: the
> downloadable system image is a stripped AOSP build with no GApps, and
> DHU 2.0's "AA-version wall" means even a real Play image with old AA
> silently exits. The recipe below is the only one that has been validated
> end-to-end on this machine. See `NOTES.md` for the full history of
> what we tried and why.

The driver script `run.sh` is the primary agent path — it enables AA dev mode
on the emulator, starts the AA head unit server, opens the adb tunnel, and
launches the DHU.

Paths are relative to the repo root (`/home/chris/repos/RoadTripRadar`).

## TL;DR (validated, 2026-07-12)

A one-time AVD setup followed by the per-run flow:

```bash
# One-time, idempotent — re-run on any AVD that needs (re)installing.
.claude/skills/run-car-dhu/run.sh --setup --skip-emulator

# Every subsequent run.
.claude/skills/run-car-dhu/run.sh --skip-emulator
```

`--setup` auto-detects which of the six setup steps (Magisk, Zygisk, aa4mg,
disable flag, real AA APK, XLauncher root) are already done and skips them.
`[I]: connected.` in `shots/dhu.log` is the DHU success signal.

## Prerequisites

- Android SDK at `/home/chris/Android/Sdk` with `platform-tools`, `emulator`,
  `cmdline-tools/latest`, and **DHU 2.0** at
  `$ANDROID_HOME/extras/google/auto/desktop-head-unit`. Install with
  `sdkmanager "extras;google;auto"`. On Debian you also need
  `apt install libc++1 libc++abi1` for DHU's runtime libs.
- An AVD named `Medium_Phone` with the
  `system-images;android-33;google_apis;x86_64` image. `google_apis`
  (NOT `google_apis_playstore`) is required — the Play image is a
  production build and `adb root` is refused. `google_apis` is
  `userdebug` so root works.
- `~/rootAVD/` (clone of https://github.com/newbit1/rootAVD) for the
  one-time ramdisk patch that installs Magisk. `rootAVD.sh` shuts down
  the AVD at the end, so it can't be safely wrapped in `--setup`.
- The `aa4mg` Magisk module (download zip from
  https://github.com/sn-00-x/aa4mg/releases, drop into
  `.claude/skills/run-car-dhu/`) and the latest Android Auto APKM
  bundle from APKMirror (`com.google.android.projection.gearhead`,
  x86_64 split). Cloudflare blocks `curl` from APKMirror, so the APKM
  has to be downloaded in a browser and dropped into
  `.claude/skills/run-car-dhu/`.
- The RoadTripRadar debug APK installed on the emulator so the car surface
  appears in the AA app list:
  ```bash
  ./gradlew :app:assembleDebug
  $ANDROID_HOME/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
  ```

## One-time AVD setup (`--setup`)

Run once after creating the AVD. Re-run any time (it's idempotent) — for
example, after a `-wipe-data`, or on a fresh AVD.

```bash
.claude/skills/run-car-dhu/run.sh --setup --skip-emulator
```

`--setup` walks six steps. Each one checks pre-state and is skipped if
already done. Only Magisk install is left manual (because `rootAVD.sh`
shuts down the AVD — see step 1):

1. **Magisk installed** (manual). Run `rootAVD` once:
   ```bash
   git clone https://github.com/newbit1/rootAVD.git ~/rootAVD
   ~/rootAVD/rootAVD.sh $ANDROID_HOME/system-images/android-33/google_apis/x86_64/ramdisk.img
   ```
   Patches `ramdisk.img`, installs `Magisk.apk`, and shuts down the
   AVD. Re-launch it manually. `--setup` checks for the `magisk`
   binary and refuses to continue if missing.
2. **Zygisk enabled** (`magisk --sqlite` toggle + reboot if needed).
3. **`aa4mg` Magisk module installed** (`magisk --install-module`).
   The module's `customize.sh` runs 4 volume-key prompts; on the
   emulator all 4 time out and default to (NO, NO, NO, YES) — the
   combination we want.
4. **aa4mg module disabled** (touch `/data/adb/modules/aa4mg/disable`
   + reboot). Without this, the system serves the old stub APK and
   crashes the new AA at startup with `ClassNotFoundException`.
5. **Real AA APK installed** (`adb install-multiple -i com.android.vending`
   over the splits extracted from the APKM). The `-i com.android.vending`
   flag is the trick: AA checks its installer identity and only enables
   full features when it thinks it came from the Play Store.
6. **XLauncher root granted** (if XLauncher is present — i.e. the
   aa4mg XLauncher=YES branch ran). Magisk policy entry for the
   XLauncher uid.

`--setup` only reboots if something structural changed (steps 2, 3,
or 4). If everything was already done, it prints "nothing changed;
no reboot needed" and exits.

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
5. **Open the overflow** (top-right "More options" at `(1027,146)`)
   and tap **"Start head unit server."** The `gearhead:projection`
   process starts and port 5277 listens (tcp6).
6. **`adb forward tcp:5277 tcp:5277`** then
   **`desktop-head-unit --adb=5277`**.

After the DHU window appears, tap the RoadTripRadar entry in the AA
app list to load the car surface. Screenshot the DHU window with
`xwd`/`import`:

```bash
DISPLAY=${DISPLAY:-:0} xwd -root -silent | convert xwd:- shots/dhu-window.png
```

## Gotchas (real, all hit this session)

- **AA dev mode does not survive `-wipe-data`.** A fresh-wipe AVD has
  `android_auto_dev_mode = null`; re-run the 10-tap flow.
- **The Version row IS the tap target, not a separate value.** In
  AA 17.x, the version number is rendered inside the same row as the
  "Version" label, not as a separate text node. Tapping anywhere on
  the row 10 times works.
- **10 taps, then OK — not 11.** The 11th tap lands on the dialog's
  neutral area and dismisses it without enabling dev mode. If dev mode
  is already on, a single tap re-surfaces the dialog.
- **The aa4mg stub must be disabled** after the real AA is installed,
  otherwise the system serves the old stub APK and crashes the new AA
  at startup with `ClassNotFoundException: SharedPreferencesProvider`.
  Touch `/data/adb/modules/aa4mg/disable` and reboot.
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
- **Volume-key prompts on `aa4mg` install can't be sent on the
  emulator.** They time out and default to NO for the 3 stub prompts
  and YES for the XLauncher prompt — which is the combination we
  want.
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
| DHU prints `connected.` then exits ~2s later | AA too old. The 10-tap on the API-33 image with aa4mg + real APK gives AA 17.x which works. |
| AA app shows "Sign in with Google" instead of settings | Stub AA: real APK not installed, or `aa4mg/disable` flag missing. Run `run.sh --setup --skip-emulator`. |
| AA crashes immediately on launch with `ClassNotFoundException: SharedPreferencesProvider` | The aa4mg stub is still active. `run.sh --setup --skip-emulator` will touch `/data/adb/modules/aa4mg/disable` and reboot. |
| `adb root` → "cannot run as root in production builds" | You used `google_apis_playstore`. Use `google_apis` (API 33, not Play). |
| The Version row doesn't surface a dialog after 10 taps | Dev mode is already on; a single tap re-surfaces the dialog. |
| "Volume key could not be detected" during `aa4mg` install | Expected on emulator. The prompts time out to the defaults (NO for stubs, YES for XLauncher) which is what we want. |
| `pm dump … versionName` shows `1.8` or `1.0-stub` | The real AA APK install failed. Re-run step 8 with all four split apks and the `-i com.android.vending` flag. |

## What's in this skill

```
.claude/skills/run-car-dhu/
  SKILL.md        ← this file
  run.sh          ← primary driver (dev mode → HU server → forward → DHU)
  find_node.py    ← uiautomator-dump → center-coords helper used by run.sh
  NOTES.md        ← battle scars / discovery log
  shots/          ← emulator + DHU logs and screenshots land here
```
