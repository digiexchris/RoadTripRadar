# run-roadtripradar skill — build/launch notes

Scratchpad of what worked and what didn't, driving the RoadTripRadar2 Android app
in this headless Linux container. Becomes the Gotchas/Troubleshooting sections
of SKILL.md.

NOTE: `local.properties` carries a `MAPTILER_API_KEY`. That file is gitignored
and the key must NEVER be copied into any committed artifact (SKILL.md, NOTES.md,
driver). The skill references it by name only.

## Environment (discovered)
- SDK: `/home/chris/Android/Sdk` (emulator bin, platform-tools, build-tools
  35/36/36.1, platforms android-36/36.1, system-images
  android-28/30/35-ext15/36/36.1).
- `/usr/lib/android-sdk` has only platform-tools. System `adb` is at
  `/usr/bin/adb` and works.
- KVM: `/dev/kvm` present and accessible → hardware accel, ~30s cold boot.
- AVDs already exist: `Medium_Phone` (android-30, x86_64) and `Medium_Phone_2`.
  Use `Medium_Phone` — image is at `system-images/android-30/default/x86_64/`.
- `ANDROID_HOME` not set in shell; must export it. `emulator` not on PATH.
  `sdkmanager` NOT installed (cmdline-tools absent) — not needed since the
  required system-image is already on disk.
- `gradle` not on PATH → use `./gradlew` wrapper.
- `local.properties`: `sdk.dir=/home/chris/Android/Sdk` + `MAPTILER_API_KEY`
  (map tiles need this; already present in the gitignored file).
- No device attached at start (`adb devices` empty).

## Build
- `./gradlew :app:assembleDebug` → debug APK. BUILD SUCCESSFUL in this container.
- Debug applicationId: `ca.voiditswarranty.roadtripradar.debug` (the `.debug`
  suffix comes from `applicationIdSuffix = ".debug"` in `app/build.gradle.kts`).
- Launchable activity: `ca.voiditswarranty.roadtripradar.MainActivity`.
- APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Emulator boot (the working incantation)
- `-no-window -no-audio -no-boot-anim -no-snapshot -wipe-data` is the safe
  headless baseline. `-no-snapshot` + `-wipe-data` guarantees a clean boot
  (a stale snapshot from a different host can hang the boot).
- `-gpu swiftshader_indirect` for software GL (no GPU context in the
  container). With `-no-window` this is the only sane choice.
- `-accel auto` lets it pick KVM (present).
- `-no-metrics` suppresses Google's anonymous-usage beacon (and any
  network roundtrip that could slow boot in restricted networks).
- Cold boot with KVM: ~32s. Without KVM, expect several minutes.
- DO NOT pass `-no-boot-announce` — that flag was removed; the emulator
  exits with "unknown option" and the boot never starts. (Caught this
  the hard way during this build.)

## Install + launch
- `adb install -r -g app-debug.apk` — `-r` reinstall, `-g` auto-grant all
  runtime permissions (the app asks for location, so this saves a click).
- `adb shell am start -W -n ca.voiditswarranty.roadtripradar.debug/ca.voiditswarranty.roadtripradar.MainActivity`
  → cold-start ~3.7s, `Status: ok`.
- Animations off + `svc power stayon` are essential for stable
  screenshot timing; otherwise the first screencap can race the splash
  transition and show a black/launcher-shaped frame.

## Driving the running app
- `adb shell input tap X Y` (pixels in screen coords; the emulator is
  1080x2400 here).
- `adb shell input swipe X1 Y1 X2 Y2 DUR_MS`.
- `adb shell input keyevent 4` is KEYCODE_BACK.
- `adb exec-out screencap -p > out.png` for screenshots (binary-safe;
  `adb shell screencap` + `adb pull` adds a host-side corruption risk
  if the file isn't cleanly terminated).
- A 2-second sleep after a tap is enough for the next paint on the
  Compose surfaces here; vary by what you tapped.
- `adb logcat -d -t 200 | grep -iE "roadtripradar|AndroidRuntime|FATAL"`
  is the first thing to check when a tap seems to do nothing.

## Gotchas
- **No internet in the container for tile servers.** The basemap will be
  blank/light (the app shows a `MapStyle` background tint instead of
  tiles). The UI is still composed and tappable; the recenter/FAB/waypoint
  controls are all visible. Don't conclude "the app is broken" from a
  blank map — the tile load fails silently, and the rest of the app
  still works.
- **The screenshot Read tool sometimes shows the image as "..." or empty.**
  The PNG file itself is correct (use `file`/`stat` to verify). The
  display is a model-side rendering issue. Trust the file size and the
  pixel sample; don't loop re-shooting.
- **After re-foregrounding an already-running app, the next screencap
  may catch a Recents/launcher transition frame** (mostly black +
  light center stack). Wait 5+s and shoot again, or go Home → relaunch
  for a clean capture.
- **KVM is required for fast boots.** `/dev/kvm` is present in this
  container; if it's missing on a different host, boot times balloon
  to several minutes and some images may not boot at all.
- **The gitignored `local.properties` holds the MapTiler key.** The
  driver script reads `ANDROID_HOME` from the env or `local.properties`'s
  `sdk.dir` line; it never reads the key. Don't add a step that does.

## Verification
- After install + launch, `mCurrentFocus` should report
  `ca.voiditswarranty.roadtripradar.debug/ca.voiditswarranty.roadtripradar.MainActivity`.
  If it shows a different package, the launch didn't land on the app
  (maybe a different APK was already on the emulator).
- A tap on the map area should change the screenshot (drag-pan, marker
  re-center, etc). I observed 45.69% of pixels change after one tap at
  (900, 2050) — the FAB/utility area in the lower-right.
- `adb logcat -d -t 200 | grep -iE "FATAL|AndroidRuntime"` should be empty
  for the app's UID.