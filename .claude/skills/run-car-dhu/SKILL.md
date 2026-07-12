---
name: run-car-dhu
description: Test the RoadTripRadar2 car (Android Auto) surface on an emulator using the Desktop Head Unit (DHU). Use when the user asks to "test the car version," "run the DHU," "screenshot the car surface," or verify a CarAppService / Android Auto change on a head unit.
---

# run-car-dhu

Drives the **Android Auto Desktop Head Unit (DHU)** against a `google_apis_playstore`
emulator so the RoadTripRadar **car surface** (the `CarAppService` in
`app/src/main/java/.../car/`) renders in a desktop window.

The driver script `run.sh` is the primary agent path — it enables AA dev mode
on the emulator, starts the AA head unit server, opens the adb tunnel, and
launches the DHU. **Read "The AA-version wall" below before running** — it is the
one hard blocker, and the script is built around it.

Paths are relative to the repo root (`/home/chris/repos/RoadTripRadar2`).

## Prerequisites

- Android SDK at `/home/chris/Android/Sdk` with `platform-tools`, `emulator`,
  `cmdline-tools/latest`, and a `google_apis_playstore` system image.
- The DHU at `$ANDROID_HOME/extras/google/auto/desktop-head-unit`. Install with
  `sdkmanager "extras;google;auto"` (ships **DHU 2.0** — the only version
  available through the SDK; there is no 2.1 to upgrade to).
- A `google_apis_playstore` AVD with a **modern** Android Auto preinstall.
  The API-30 image ships AA **5.3** (too old — see below). The **API 36.1**
  image (`system-images;android-36.1;google_apis_playstore;x86_64`, AVD
  `Play_Phone_36`) ships a current AA and is what `run.sh` uses by default.
  Create it:
  ```bash
  echo no | $ANDROID_HOME/cmdline-tools/latest/bin/avdmanager create avd \
    -n Play_Phone_36 -k "system-images;android-36.1;google_apis_playstore;x86_64" -d pixel_6
  ```
- The RoadTripRadar debug APK installed on the emulator so the car surface
  appears in the AA app list:
  ```bash
  ./gradlew :app:assembleDebug
  $ANDROID_HOME/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
  ```

## Run (agent path) — `run.sh`

Full pipeline (boot → dev mode → head unit server → DHU):

```bash
.claude/skills/run-car-dhu/run.sh
```

Reuse an already-attached emulator and skip the boot:

```bash
.claude/skills/run-car-dhu/run.sh --skip-emulator
```

Assume AA dev mode + the head unit server are already on (just forward + DHU):

```bash
.claude/skills/run-car-dhu/run.sh --skip-devmode
```

Tear it all down:

```bash
.claude/skills/run-car-dhu/run.sh --stop
```

`run.sh` ends by printing whether the DHU stayed alive (handshake completed) or
exited (hit the AA-version wall). The DHU log is at `shots/dhu.log`.

### What `run.sh` actually does (the verified flow)

These are the steps mapped out on a real emulator this session — every one is
adb-driven (no manual tapping):

1. **Boot the API 36.1 Play AVD** (window on `DISPLAY=:0`). First boot under
   software rendering is slow — see Gotchas.
2. **Open phone Settings** (`am start -a android.settings.SETTINGS`, after
   `am force-stop com.android.settings` so it opens at the top).
3. **Settings → Connected devices → Connection preferences → Android Auto.**
4. **Scroll to the About section** so the version number is visible.
5. **Tap the version NUMBER exactly 10 times.** 10 taps surfaces the
   *"Allow development settings?"* dialog. **An 11th tap dismisses it without
   accepting** — stop at 10, then tap **OK**.
6. **Open the overflow** (top-right "More options" ≈ `(1024,143)`) and tap
   **"Start head unit server."** The `gearhead:projection` process starts and
   port 5277 listens (tcp6).
7. **`adb forward tcp:5277 tcp:5277`** then **`desktop-head-unit --adb=5277`**.

After the DHU window appears, tap the RoadTripRadar entry in the AA app list to
load the car surface. Screenshot the DHU window with `xwd`/`import` (see the
script's final output).

## The AA-version wall (read this)

**DHU 2.0 connects to the head unit server ("connected") then exits silently
when the emulator's Android Auto is too old.** The API-30 Play image ships
**AA 5.3** (~2021); DHU 2.0's protocol handshake never completes against it and
the process exits ~2s after "connected" with no error in the log.

The fix is a **modern Android Auto**. Two ways:

- **Use the API 36.1 Play image** (`Play_Phone_36`, default in `run.sh`). It
  ships a current AA — the path the [StackOverflow "purely in emulators"
  answer](https://stackoverflow.com/questions/76482834/can-we-test-android-auto-purely-in-emulators-2023)
  confirms works (API 36, AA v15.8, DHU 2.0).
- **Sideload a current AA APK** onto the API-30 image (`adb install -r` over the
  preinstall — signatures match Google's so it takes). Keeps the fast ~10s boot.

What does **not** work, and why we ended up at the version wall:

- `adb shell settings put secure android_auto_dev_mode 1` — flips the secure
  flag, but AA keeps dev-mode state internally; the flag alone does **not**
  surface the "Start head unit server" overflow entry. The 10-tap dialog is
  required.
- `adb shell am startservice …/DeveloperHeadUnitNetworkService` — the service is
  not exported; shell (uid 2000) can't start it ("Requires permission not
  exported from uid 10101"). And `google_apis_playstore` images are **production
  builds** — `adb root` is refused ("adbd cannot run as root in production
  builds"), so you can't escalate to start it. The UI tap is the only way.
- "Developer settings → Application Mode → Developer" (an old StackOverflow
  fix) — **does not exist on AA 5.3.** The "Developer settings" overflow item
  on 5.3 navigates back to the system Connection preferences screen, not an AA
  developer page. Don't chase it.
- DHU `--usb` — that's the Android Open Accessory path for a **physical USB
  phone**, not an emulator (no real USB accessory). The emulator must use
  `--adb=5277` + the head unit server. A manual `--usb` flow against a phone
  does not transfer to the emulator.

## Test

The car surface has its own unit tests (Robolectric, `TestCarContext`):

```bash
./gradlew :app:testDebugUnitTest --tests "ca.voiditswarranty.roadtripradar.car.*"
```

The DHU is for visual/integration verification of the running car surface.

## Gotchas (real, all hit this session)

- **API 36.1 first boot is slow and disk-hungry.** The 36.1 image needs a
  ~7.2 GB userdata partition and first-booted under software rendering took
  well over 5 min (we killed it before it finished). If `emulator` logs
  `FATAL | Not enough space to create userdata partition`, free ≥7.2 GB on the
  filesystem holding `~/.android/avd/` (the API-30 AVD's userdata, gradle caches,
  and `app/build/` are the easy wins — all regenerable). `-partition-size 2048`
  does **not** shrink the 36.1 userdata below its built-in size.
- **`-gpu host` silently falls back to software on Intel UHD 620.** The emulator
  distrusts the Mesa driver ("Your GPU cannot be used for hardware rendering")
  and drops to `lavapipe`/`swangle`. The window is laggy and GNOME shows an
  **"Application is not responding"** popover. `-gpu angle_indirect` is
  rejected as "not valid" on this emulator build. Mitigation: drive input via
  `adb shell input tap` (as `run.sh` does) — injection bypasses the window
  manager's ping entirely, so ANR doesn't break the flow. Click "Wait" on the
  popover if you're tapping manually.
- **`am start -a android.settings.SETTINGS` resumes the existing Settings task
  at its scroll position**, it does not scroll to the top. `run.sh`
  `am force-stop`s Settings first so "Connected devices" is at the top.
- **The version number is the tap target, not the "Version" label.** The label
  row and the value row are separate nodes; tapping the label does nothing.
- **10 taps, then OK — not 11.** The 11th tap lands on the dialog's neutral
  area and dismisses it without enabling dev mode. If dev mode is already on,
  a single tap re-surfaces the dialog.
- **Dev mode does not survive `-wipe-data`.** A fresh-wipe image has
  `android_auto_dev_mode = null`; re-run the 10-tap flow.
- **The DHU log is full of ALSA/Jack noise** ("cannot find card '0'", "jack
  server is not running"). Ignore it — there's no audio device; it's harmless.
  The real signal is `[I]: connected.` followed by either the car surface or
  a silent exit.
- **No `~/.android/headunit.ini` config.** The DHU logs
  `[E]: Could not load configuration from '~/.android/headunit.ini'` and uses
  defaults. Not required for the basic flow.

## Troubleshooting

| Symptom | Fix |
|---|---|
| DHU prints `connected.` then exits ~2s later | AA too old (the version wall). Use the API 36.1 image (`Play_Phone_36`) or sideload a modern AA APK. |
| `FATAL: Not enough space to create userdata partition` | Free ≥7.2 GB where `~/.android/avd/` lives; the 36.1 userdata is fixed-size. |
| Emulator "offline" for many minutes | API 36.1 first boot is slow under software rendering. Give it 10-15 min, or use the API-30 image + a sideloaded modern AA for fast iteration. |
| `desktop-head-unit: command not found` | `sdkmanager "extras;google;auto"`; binary lands in `$ANDROID_HOME/extras/google/auto/`. |
| Overflow shows only "Help & feedback" / "Send feedback" | Dev mode not enabled. Run the 10-tap flow (`run.sh` without `--skip-devmode`). |
| `am startservice` → "not exported from uid 10101" | Expected — the HU server service is not startable from shell. Use the UI "Start head unit server" tap. |
| `adb root` → "cannot run as root in production builds" | Expected — `google_apis_playstore` is a production image. No root; use the UI path. |
| GNOME "not responding" popover while tapping | Software rendering lag. Tap via `adb shell input tap`, or click "Wait". |
| DHU window is black | The AA launcher is up but nothing selected; tap the RoadTripRadar entry in the AA app list. Confirm the debug APK is installed. |

## What's in this skill

```
.claude/skills/run-car-dhu/
  SKILL.md        ← this file
  run.sh          ← primary driver (boot → dev mode → HU server → forward → DHU)
  find_node.py    ← uiautomator-dump → center-coords helper used by run.sh
  NOTES.md        ← battle scars / discovery log
  shots/          ← emulator + DHU logs and screenshots land here
```