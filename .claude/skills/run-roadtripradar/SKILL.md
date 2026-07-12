---
name: run-roadtripradar
description: Build, launch, and drive the RoadTripRadar2 Android app on a headless emulator in this container. Use when the user asks to "run the app," "build and install," "screenshot the app," "test on the emulator," or describes a UI change that needs verification on a running device.
---

# run-roadtripradar

This skill launches the RoadTripRadar2 Android app on a headless Android
emulator in this container, installs the debug APK, opens the app, and
hands the agent a `adb shell` handle to keep driving it. **The driver
script `run.sh` is the primary entry point — do not `npm start`-style
"just build it and hope."**

The app: Kotlin / Jetpack Compose, MVVM, MapLibre map, POI search, weather
radar, route waypoints. A single Gradle module at `app/`.

Paths below are relative to the repo root (`/home/chris/repos/RoadTripRadar2`).

## Prerequisites (already present in this container)

- Android SDK at `/home/chris/Android/Sdk` with emulator, platform-tools,
  build-tools 35+, platform `android-36`, and at least one x86_64 system
  image (the default is `android-30`).
- AVD `Medium_Phone` already created (android-30, x86_64).
- KVM device `/dev/kvm` present and accessible.
- `local.properties` (gitignored) holding `sdk.dir` and the `MAPTILER_API_KEY`
  needed by the map basemap. **Do not copy the key into this skill or
  any committed file.**

If any of those are missing, see *Troubleshooting* below — but they
weren't missing here, so I didn't write install steps for them.

## Build

The debug APK:

```bash
./gradlew :app:assembleDebug
```

Verified: `BUILD SUCCESSFUL` in this container. The wrapper handles
toolchain selection. APK lands at
`app/build/outputs/apk/debug/app-debug.apk` (~74 MB).

The debug build uses `applicationIdSuffix = ".debug"`, so the package
on the emulator is `ca.voiditswarranty.roadtripradar.debug` and the
launchable activity is `ca.voiditswarranty.roadtripradar.MainActivity`.

## Run (agent path) — `run.sh`

The driver script does build → boot emulator → install → launch →
screenshot in one shot:

```bash
.claude/skills/run-roadtripradar/run.sh
```

Reuse the already-installed APK and skip Gradle:

```bash
.claude/skills/run-roadtripradar/run.sh --skip-build
```

Use an already-attached device/emulator and skip the boot:

```bash
.claude/skills/run-roadtripradar/run.sh --skip-emulator
```

Stop the emulator (kills the qemu process we started):

```bash
.claude/skills/run-roadtripradar/run.sh --stop
```

The script ends by printing the `adb` commands you'll use to drive the
app. Output includes the screenshot path
(`.claude/skills/run-roadtripradar/shots/<timestamp>-home.png`).

### Driving the app (after `run.sh`)

`run.sh` puts `adb` on `PATH`. The most useful commands — all of which
I used this session to verify the harness:

```bash
# Tap at screen pixels. 1080x2400 in this AVD.
adb shell input tap 540 1200

# Drag (X1,Y1) → (X2,Y2) over DUR milliseconds.
adb shell input swipe 200 1200 800 1200 300

# Back / Home / Wake.
adb shell input keyevent 4   # KEYCODE_BACK
adb shell input keyevent 3   # KEYCODE_HOME
adb shell input keyevent KEYCODE_WAKEUP

# Relaunch the app (cold start; ~3-4s).
adb shell am start -W -n ca.voiditswarranty.roadtripradar.debug/ca.voiditswarranty.roadtripradar.MainActivity

# Screenshot to stdout (binary-safe; redirect to a .png).
adb exec-out screencap -p > /tmp/shot.png

# Check what's on top.
adb shell dumpsys window | grep mCurrentFocus

# Crash log (filter to the app + framework crashes only).
adb logcat -d -t 200 | grep -iE "roadtripradar|AndroidRuntime|FATAL"
```

When you've captured a screenshot you want to compare against, read it
with the `Read` tool — if it shows the app's UI it's working; if it
shows the launcher/recents, you caught a transition frame (see
*Gotchas*).

## Run (human path)

For a phone or a real device, `./build.sh --dev --install --run` does
build → install on the connected device → launch the main activity.
Useless headless, so I didn't use it. Output is the same APK as above.

## Test

```bash
./gradlew test                                       # unit tests (Robolectric, SDK 33)
./gradlew :app:assembleDebug                         # the APK build this skill drives
./build.sh --dev                                     # the project's canonical build entry
```

For the Android-specific e2e (requires the emulator from this skill):

```bash
./gradlew connectedAndroidTest
```

## Gotchas (real, all hit this session)

- **`-no-boot-announce` is not a valid flag in emulator 36.x.** Passing
  it makes the emulator exit with `unknown option: -no-boot-announce`
  and the boot never starts. The driver script does not use it. If you
  copy-paste a `-no-window` invocation from older docs, drop that flag.
- **The container has no real internet to MapTiler/OpenFreeMap.** The
  basemap will be a blank tinted rectangle (the app's `MapStyle`
  background) instead of tiles. The rest of the UI is composed and
  tappable — recenter, FAB, waypoint controls all work. Don't
  conclude "the app is broken" from a blank map.
- **Re-foregrounding a running app can produce a launcher-shaped
  screencap.** When you go `am start` on an already-running app, the
  first screencap may catch the Recents/launcher transition frame
  (mostly black with a light center stack). Wait 5+s and shoot
  again, or Home → relaunch for a clean capture.
- **The Read tool may show the image as `...` or empty.** The PNG file
  is correct — verify with `file`/`stat`/`md5sum`. The model-side
  renderer sometimes fails on large PNGs. Trust the file size and
  pixel sample, not whether the inline image displays.
- **Don't read `local.properties` for the `MAPTILER_API_KEY` from
  anything that gets committed.** The key is in the gitignored
  `local.properties`; this skill's `run.sh` never copies it. If you
  need to share a screenshot of a fully-mapped view, the basemap
  still won't load in this container.
- **Cold boot is ~30s with KVM; multi-minute without.** `/dev/kvm` is
  present in this container. If you move this skill to a host without
  KVM (CI without nested virt), expect several-minute boots and some
  images may not boot at all.
- **The `Medium_Phone` AVD is the one that works here.** `Medium_Phone_2`
  exists but points at the same image; `run.sh` uses `Medium_Phone`.

## Troubleshooting

| Symptom | Fix |
|---|---|
| `emulator: command not found` | `export ANDROID_HOME=/home/chris/Android/Sdk; export PATH=$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools:$PATH` |
| `adb: no devices/emulators found` after boot | `adb wait-for-device`; emulator takes 30s+ to register |
| Boot loops on `qemu: could not access KVM kernel module` | `/dev/kvm` missing or no permission. In this container it is present; on a different host fix the device or fall back to software TCG (`-accel tcg` — much slower) |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | The previous APK was signed with a different key. `adb uninstall ca.voiditswarranty.roadtripradar.debug` then re-run `run.sh` |
| `am start` shows `Status: -1` / `Error type 3` | Wrong component name. Debug suffix is `.debug`; full name is `ca.voiditswarranty.roadtripradar.debug/ca.voiditswarranty.roadtripradar.MainActivity` |
| Screenshot is all black | Caught the launcher transition. `adb shell input keyevent 3` (Home) then `am start` the app, wait 5s, screencap again |
| Screenshot is solid-color | App crashed; `adb logcat -d -t 300 \| grep -iE "FATAL\|AndroidRuntime\|roadtripradar"` |
| `gradle` not found | Use the wrapper: `./gradlew …` (not `gradle …`) |
| `./gradlew: Permission denied` | `chmod +x ./gradlew` |
| Tiles don't load in the map | Expected — the container can't reach the tile servers. Verify other UI works (FAB tap, recenter), then move on |

## What's in this skill

```
.claude/skills/run-roadtripradar/
  SKILL.md                       ← this file
  run.sh                         ← primary driver (build + boot + install + launch + screenshot)
  NOTES.md                       ← scratchpad that became the Gotchas section
  screenshot-01-home.png         ← a real screenshot of the launched app (1080x2400, 6,299 colors)
  screenshot-01-home-thumb.png   ← 540x1200 thumbnail
  screenshot-02-after-tap.png    ← shot after a tap (45.69% of pixels changed)
  shots/                         ← subsequent runs land here with timestamps
```