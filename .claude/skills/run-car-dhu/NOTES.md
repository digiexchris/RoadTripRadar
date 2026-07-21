# run-car-dhu — NOTES (battle scars / discovery log)

Scratchpad of what we learned mapping this skill on a real emulator, so the
next agent doesn't repeat the dead ends. The verified path is in `SKILL.md` and
`run.sh`; this file is the *why* behind it.

## The environment

- **DHU is DHU 2.0, and that's the only version.** `sdkmanager "extras;google;auto"`
  installs `desktop-head-unit` v2.0-linux (Build 2022-03-30-438482292) at
  `/home/chris/Android/Sdk/extras/google/auto/`. There is no 2.1 / newer channel.
  `sdkmanager --list` shows `extras;google;auto` as the only package and 2.0 as
  the only version.
- **GPU: Intel UHD Graphics 620 (KBL GT2), Vulkan 1.4.** This is the source of
  most of the friction below.

## The AA-version wall (the one real blocker)

- **DHU 2.0 + AA 5.3 = silent exit.** Against the API-30 `google_apis_playstore`
  image (AA `5.3.801658-preinstall`, ~2021), the DHU connects to the head unit
  server on 5277, prints `[I]: connected.`, then exits ~2s later with no error.
  The protocol handshake never completes.
- The StackOverflow "purely in emulators" answer works on **API 36 / AA v15.8**.
  So the fix is modern AA: the **API 36.1 Play image** (`Play_Phone_36`,
  `system-images;android-36.1;google_apis_playstore;x86_64`) or a sideloaded
  current AA APK on the API-30 image.
- **Disk wall (handed to the user):** the 36.1 image needs a ~7.2 GB userdata
  partition; `emulator` fatal'd with `Available: 7169.40 MB, need 7372.80 MB`
  because /home was 93% full (7.1 GB free). `-partition-size 2048` does NOT shrink
  the 36.1 userdata below its built-in size. The user said they'd free space /
  deal with it. Do not chase emulator booting or disk cleanup in this skill.

## Dev-mode discovery (the verified 10-tap flow)

- **It is 10 taps on the AA version NUMBER, then OK — not 7, not 11.**
  - 7 taps did nothing.
  - 10 taps surfaces the *"Allow development settings?"* toast/dialog.
  - **The 11th tap dismisses the dialog without accepting** — it lands on the
    dialog's neutral area and cancels. Stop at 10, then tap **OK**.
  - User (verbatim): "it is 10 taps till the toast. the 11th dismissed the toast
    without accepting and turning it on."
- **Tap the version NUMBER, not the "Version" label.** They are separate nodes;
  the label row is not tappable for dev mode.
- **Once dev mode is already on, only ONE tap re-surfaces the dialog.** That
  tripped a reset-and-retry; we wipe-data to test the cold path.
- **Dev mode does NOT survive `-wipe-data`.** Fresh image → re-run the 10-tap flow.
- **Phone dev mode is NOT a prerequisite** for AA dev mode. The 10-tap flow is
  entirely inside the AA settings screen.

## What does NOT work (and why)

- **`adb shell settings put secure android_auto_dev_mode 1`** — sets the secure
  flag, but AA keeps dev-mode state internally; the flag alone does NOT surface
  the "Start head unit server" overflow item. The 10-tap dialog is mandatory.
  (Web-searched for a shell path; the consensus is there isn't a clean one.)
- **`adb shell am startservice …/DeveloperHeadUnitNetworkService`** — the
  service is **not exported**. Shell (uid 2000) gets "Requires permission not
  exported from uid 10101".
- **`adb root`** — refused: "adbd cannot run as root in production builds."
  `google_apis_playstore` images are production builds (unlike `google_apis`).
  So there's no escalation path to start the non-exported service. **The UI tap
  flow is the only way** on a Play image.
- **"Developer settings → Application Mode → Developer"** (an old StackOverflow
  fix for older AA) — **does not exist on AA 5.3.** The "Developer settings"
  overflow item on 5.3 navigates back to the *system* Connection preferences
  screen, not an AA developer page. Don't chase it. (User caught me drifting
  there: "You appeared to be scrolling down in the main settings menu and
  tapping on about, rather than going into the connected devices menu.")
- **DHU `--usb`** — Android Open Accessory path for a **physical USB phone**.
  The emulator has no real USB accessory, so `--usb` does not apply to it.
  Emulator must use `--adb=5277` + the AA head unit server. The user's manual
  success was `--usb` against a phone; that does not transfer to the emulator.
- **Issue Tracker #171130740** (DHU exits after "connected") — same symptom;
  the fix there is dev mode + HU server + sometimes "Exit App", all of which
  `run.sh` does. The remaining piece is just AA being new enough.

## Settings navigation gotchas

- **`am start -a android.settings.SETTINGS` resumes the existing Settings task
  at its scroll position** — it does NOT scroll to the top. `run.sh`
  `am force-stop com.android.settings` first so "Connected devices" is at the
  top. Without the force-stop, hardcoded top-of-list coords land on the wrong
  row.
- **Verify each screen's text before tapping.** A couple of times an empty
  coord from a failed `find_node` (see below) made `adb shell input tap` error
  out ("Argument expected after tap") and the screen drifted to the app
  drawer. Always grep the dump for the expected text first.

## The find_node.py detour

- The first helper was an inline `read CX CY < <(python3 ...)` process
  substitution inside a heredoc. It returned empty coords every time → every
  `adb shell input tap` errored → the screen drifted. Process-substitution with
  heredocs inside `adb shell` is fragile; don't do it inline.
- Fix: `find_node.py` is a real file, called as `C=$(python3 find_node.py ...)`.
  It matches on `text=` by default, supports `--desc` (for content-desc nodes
  like "More options") and `--last` (for the second of two matches), prints
  `CX CY`, exits 0/1. `run.sh`'s `tap_text` wraps it.
- The earlier throwaway `/tmp/find_text.py` had a syntax bug
  (`matches.append(((x1+x2)//2, (y1+y2)//2)))` — extra paren). `find_node.py` is
  the corrected, enhanced version. `/tmp/find_text.py` is NOT part of the skill.

## GPU / ANR saga

- **`-gpu host`** → "Your GPU cannot be used for hardware rendering" → silent
  fallback to software (lavapipe/swangle). The emulator distrusts the Intel
  UHD 620 Mesa driver.
- **`-gpu angle_indirect`** → "not valid" on emulator 36.6.11 → fallback to
  software again.
- So the emulator is **always software-rendering** here → the window is laggy
  and GNOME shows an **"Application is not responding"** popover (~5s WM ping
  timeout) that gets in the way of manual tapping.
- **No clean gsettings toggle for the ANR popover** — it's hardcoded in
  gnome-shell's window manager. (There are dconf keys for *crash* dialogs, not
  for the WM ping ANR.)
- **The fix that works: drive input via `adb shell input tap`.** Injection goes
  straight into the guest input pipeline and does NOT need the emulator window
  to be responsive to GNOME, so the ANR popover doesn't break the flow. This is
  why `run.sh` is entirely adb-driven. If tapping manually, just click "Wait" on
  the popover.

## Verified coordinates (API-30 Play image, 1080x2400)

For reference / sanity-checking; `run.sh` finds most of these dynamically via
`find_node.py` so they don't go stale, but the overflow is a content-desc and
hardcoded.

- Settings → **Connected devices**: (383, 889)
- → **Connection preferences**: (430, 440)
- → **Android Auto**: found dynamically (text match)
- AA version number (e.g. "5.3.801658-preinstall"): found dynamically
  ("preinstall" or `--last "5."` / digit-dot fallback)
- **"More options" overflow** (content-desc, top-right): (1024, 143) — hardcoded
- **OK** on the dev-mode dialog: found dynamically ("OK")
- **Start head unit server** (overflow item, dev-mode only): found dynamically

## Head unit server

- After "Start head unit server", `com.google.android.projection.gearhead:projection`
  runs and **port 5277 listens (tcp6)**. Confirm with
  `adb shell 'cat /proc/net/tcp6'` or just `adb forward tcp:5277 tcp:5277`.
- `adb forward tcp:5277 tcp:5277` then `desktop-head-unit --adb=5277`.
- DHU log noise to ignore: `ALSA: ... cannot find card '0'`, `Jack: ... server
  is not running`, `[E]: Could not load configuration from '~/.android/headunit.ini'`.
  None of these affect the connection; they're audio-device and missing-config
  noise. The real signal is `[I]: connected.` followed by either the car surface
  or a silent exit.

## Open items for the user

1. Free ≥7.2 GB where `~/.android/avd/` lives so the 36.1 image can create its
   userdata partition (or sideload a modern AA APK onto the API-30 image and skip
   the 36.1 image entirely).
2. Boot `Play_Phone_36` (slow first boot under software rendering — 10-15 min,
   give it time) OR sideload modern AA + run `run.sh --skip-emulator`.
3. Once DHU stays alive past `connected.`, tap the RoadTripRadar entry in the AA
   app list (debug package `ca.voiditswarranty.roadtripradar.debug` must be
   installed) to load the car surface.

---

# Addendum (2026-07-12): the path that actually works on this machine

The original SKILL.md (API 36.1 Play image + the 10-tap dev-mode flow) does
not work on this machine. The downloaded "Play" image was actually a stripped
AOSP build with no GApps, so the `gearhead` package is just a stub that
deeplinks to the Play Store and bounces back. Switching to a `google_apis`
(non-Play) image doesn't help — same stub problem, just no Play Store to
deeplink to.

The fix is a community-documented root + module + APK swap path. Full
reference: https://github.com/Rikj000/Android-Auto-Ultimate-Dev-Unit
(a community-maintained guide for testing AA car apps in emulators).

## Validated AVD setup (what works on this box)

- **AVD**: `Medium_Phone` (Android Studio's "Medium Phone" device profile,
  AVD id `Medium_Phone`, dir `~/.android/avd/Medium_Phone.avd`).
- **System image**: `system-images;android-33;google_apis;x86_64`, revision 17,
  build `TE1A.240213.009`. **`userdebug` build with `ro.debuggable=1` and
  `ro.secure=1`**, which is critical — `google_apis` images are debuggable
  so `adb root` works, unlike `google_apis_playstore` which is production.
- **API 33, x86_64, Google APIs (NOT playstore).** This is exactly what the
  Rikj000 guide recommends.
- **17.1 real AA APK** (`com.google.android.projection.gearhead` v17.1.662418)
  installed from a user-supplied `.apkm` bundle, base + x86_64 + en + xxhdpi
  splits, via `adb install-multiple -i com.android.vending -r`. The
  `-i com.android.vending` flag is the "install as Play Store" trick — AA
  checks its installer identity and only enables full features when it
  thinks it came from the Play Store.

## Step-by-step (the validated recipe)

1. **Root with Magisk** (now automated by `run.sh --setup` via the bundled
   `rootAVD` submodule at `.claude/skills/run-car-dhu/rootAVD`; this is the
   manual equivalent for reference):
   ```bash
   # submodule must be initialized: git submodule update --init --recursive
   .claude/skills/run-car-dhu/rootAVD/rootAVD.sh \
     system-images/android-33/google_apis/x86_64/ramdisk.img
   ```
   Run with the AVD **offline** so rootAVD uses its bundled `Magisk.zip`
   instead of popping an interactive Magisk-version menu. The script
   patches `ramdisk.img` with Magisk init, installs `Magisk.apk`, stamps a
   `ramdisk.img.backup`, and shuts down the AVD. Manually re-launch the AVD
   after the script ends. First launch on a new AVD boots in ~20s with a
   saved snapshot, ~10-15 min on a true cold boot.

2. **Magisk → Settings → Zygisk** (toggle ON) → "Reboot to apply changes".
   On the next boot, `adb shell "su 0 id"` returns `uid=0(root)`.

3. **Grant root to adb shell** (the per-app toggle in Magisk's Superuser
   tab — toggle ON for `com.android.shell`). Without this, `adb shell su -c`
   always returns "Permission denied" even with Magisk installed.

4. **Install `aa4mg` Magisk module** (replaces the system AA stub):
   - Download `aa4mg-vX.Y.Z.zip` from
     https://github.com/sn-00-x/aa4mg/releases
   - Push to `/sdcard/Download/` on the AVD.
   - Magisk app → Modules tab → "Install from storage" → pick the zip.
   - The module's `customize.sh` runs 4 prompts with volume keys:
     "Install Fake Google Maps/Search/Speech Services stub?" and
     "Install Android Auto - XLauncher Unlocked?". The 3 stub prompts
     default to NO after 20s timeout (volume keys can't be sent on the
     emulator). The XLauncher Unlocked prompt defaults to YES after 10s
     and that's what we want. Net result: XLauncher is installed.

5. **Reboot** (`adb shell "su -c reboot"`) to load the module. The system
   `gearhead` package is now provided by `AndroidAutoStubPrebuilt.apk` in
   `/data/adb/modules/aa4mg/system/product/priv-app/`. The XLauncher
   (`com.google.android.projection.gearhead.xunlocked`) appears in the
   app drawer.

6. **Grant root to XLauncher** (Magisk Superuser tab → toggle ON for
   `com.google.android.projection.gearhead.xunlocked`). XLauncher's
   "Developer Settings" entry requires root; without granting, tapping
   it bounces back to the launcher.

7. **Install the real AA APK over the stub** (the key swap):
   - Get the latest Android Auto x86_64 release from APKMirror
     (currently v17.1.662418, ~25 MB `.apkm` bundle, includes base +
     split_config.x86_64 + per-locale + per-density splits).
   - Extract the bundle (it's a regular zip):
     ```bash
     unzip *.apkm -d /tmp/aa-extract
     ```
   - Push the base + x86_64 + en + xxhdpi splits to the AVD:
     ```bash
     adb push /tmp/aa-extract/base.apk /data/local/tmp/aa-base.apk
     adb push /tmp/aa-extract/split_config.x86_64.apk /data/local/tmp/aa-x86_64.apk
     adb push /tmp/aa-extract/split_config.en.apk /data/local/tmp/aa-en.apk
     adb push /tmp/aa-extract/split_config.xxhdpi.apk /data/local/tmp/aa-xxhdpi.apk
     ```
   - Install as "from Play Store" via `adb install-multiple -i com.android.vending`:
     ```bash
     adb install-multiple -r -i com.android.vending \
       /tmp/aa-extract/base.apk \
       /tmp/aa-extract/split_config.x86_64.apk \
       /tmp/aa-extract/split_config.en.apk \
       /tmp/aa-extract/split_config.xxhdpi.apk
     ```
     Result: `Success`. AA's `installerPackageName` is now
     `com.android.vending`, and `versionName=17.1.662418-release` (the real
     AA, not the stub).

8. **Disable the aa4mg stub** (it would otherwise serve the old stub APK
   and crash the new AA at startup with `ClassNotFoundException`):
   ```bash
   adb shell "su -c touch /data/adb/modules/aa4mg/disable"
   adb shell "su -c reboot"
   ```
   The `disable` flag tells Magisk to skip mounting this module on next
   boot. The system then uses the new AA APK installed at `/data/app/...`
   instead of the stub at `/product/priv-app/...`.

9. **Enable AA developer mode** (the 10-tap dance, now on real AA 17.x):
   - Launch AA settings: `adb shell am start -n com.google.android.projection.gearhead/.companion.settings.DefaultSettingsActivity`
   - Scroll to the bottom (5-6 swipes) to find the "Version" row.
   - **The Version row IS the tap target** — in AA 17.x, the version number
     is rendered inside the same row as the "Version" label, not as a
     separate text node. Tapping anywhere on the row 10 times works.
   - Stop at 10, then tap **OK** on the "Allow development settings?" dialog.
   - Open the overflow (top-right "More options" at `(1027, 146)`) and tap
     **"Start head unit server"**. The overflow also shows
     "Developer settings" and "Quit developer mode" as new entries —
     proof dev mode is on.

10. **Verify head unit server is up**:
    ```bash
    adb shell "ps -A | grep gearhead:projection"  # process exists
    adb shell "netstat -tlnp | grep 5277"          # tcp6 listening
    adb logcat -d | grep "GH.DHUService"           # "Network server running on port 5277"
    ```

11. **Forward + DHU**:
    ```bash
    adb forward tcp:5277 tcp:5277
    $ANDROID_HOME/extras/google/auto/desktop-head-unit --adb=5277
    ```
    DHU prints `[I]: connected.` — protocol handshake complete. The Qt
    window then opens. In a headless environment the window exits shortly
    after, but `[I]: connected.` is the success signal.

## Why the original SKILL.md path doesn't work on this machine

- The downloaded `system-images;android-36.1;google_apis_playstore;x86_64`
  is actually a dev-keys stripped build with no GApps — 88 packages, no
  `com.google.android.projection.gearhead`, no Play Store. The system
  image's `build.display.id` reads `BE4B.251210.005 dev-keys` (debug
  build, not the production image the tag suggests). Replacing with a
  current `google_apis_playstore` image is the "version wall" fix in
  the original SKILL.md, but we couldn't get sdkmanager to download
  cleanly (mismatched SDK installs at `/opt/android-sdk` vs
  `/home/chris/Android/Sdk`).
- The `google_apis` (non-Play) API 37.1 image (downloaded as
  `x86_64-ps16k-37.1_r06.zip`) HAS GApps but ships `gearhead 1.8` —
  the **stub** AA that deeplinks to Play Store and bounces back. No
  way to make it functional without a Google sign-in (which we
  chose not to do for privacy).
- The community `aa4mg` + real-APK-sideload path works around both
  problems: it gives us a full non-stub AA 17.1 without needing a
  Google account or a Play Store image.

## How this maps to the existing `run.sh`

`run.sh --setup` automates steps 1-8 (Magisk via the bundled `rootAVD`
submodule, Zygisk + immediate reboot, aa4mg module + disable, real AA APK,
XLauncher root) — it inspects the booted AVD at each step and only acts if
the step isn't already done. The default `run.sh` (no `--setup`) does
steps 9-11 (the dev-mode tap flow + DHU launch) and assumes steps 1-8 are
already in place. So: run `./.claude/skills/run-car-dhu/run.sh --setup` once
on a fresh AVD, then `./.claude/skills/run-car-dhu/run.sh` (or `--skip-emulator`
if the AVD is already booted) for the per-run DHU flow.

`AVD="Medium_Phone"` (the API 33 + google_apis AVD) is set in `run.sh`.

## Files saved to the skill

- `shots/avd-aa-settings.png` — screenshot of the AVD window after
  AA dev mode is enabled (Settings → About → Version row visible).
- `shots/dhu.log` and `shots/dhu2.log` — DHU logs showing
  `[I]: connected.` (the success signal). DHU exits shortly after in
  this headless environment but the handshake completed.
---

# Addendum (2026-07-20): the API 35 path (supersedes Magisk/aa4mg)

The API 33 / Magisk / aa4mg recipe above works, but moving to **API 35
`google_apis` (userdebug)** gave a cleaner path that needs no Magisk, no
aa4mg module, no ramdisk patching — and fixed a blocker the API 33 path
never hit. This is now what `run.sh --setup` implements.

## Why API 35, and why it changed the setup

- **`adb root` works directly.** The `google_apis` (not playstore) image
  is `userdebug`; `adb root` drops the shell to uid 0 with no prompt.
  No Magisk, no `su 0` "Permission denied" (which on a Magisk AVD is a
  hidden grant prompt the agent can't see — the original reason the
  fresh-AVD detour happened).
- **`-writable-system` overlayfs replaces Magisk module overlays.**
  Boot with `-writable-system`, `adb root`, `adb remount` → overlayfs on
  /system + /product ("Verity disabled; overlayfs enabled"), live after a
  reboot, persisted in a scratch partition. This is how we both delete the
  platform AA stub AND install the real AA as a system priv-app. The
  overlay is only mounted on `-writable-system` boots, so EVERY launch
  (setup + per-run) must carry the flag.
- **The real blocker: Android 15's CDM role requirement.** This is the
  thing the API 33 path didn't hit and is the crux of the whole detour.
  The `:car` process crashes on DHU connect:
  `FATAL EXCEPTION: CarService: IllegalStateException: Failed to register
  vehicle with CDM` caused by `SecurityException: ...
  REQUEST_COMPANION_PROFILE_AUTOMOTIVE_PROJECTION to associate with a
  device with SYSTEM_AUTOMOTIVE_PROJECTION profile`. That permission has
  protection level **`internal|role`** — granted ONLY to the holder of
  `android.app.role.SYSTEM_AUTOMOTIVE_PROJECTION`.
  - `pm grant` sets `granted=true` but is **ineffective** (it's a role
    permission, not a normal grant).
  - `cmd role add-role-holder` rejects a user-installed (in `/data/app`)
    AA with **"Package does not qualify for the role"** — the role
    requires a **system app**.
  - `cmd role set-bypassing-role-qualification true` alone does NOT bypass
    on this build.
  - **Fix:** copy the real AA's `base.apk` into
    `/product/priv-app/AndroidAuto/` (via the live overlay) + reboot →
    PMS flags it `SYSTEM`/`PRIVILEGED` (the `/data/app` install with its
    splits stays the running `UPDATED_SYSTEM_APP`) → now `add-role-holder`
    succeeds → the permission lands `granted=true, flags=[GRANTED_BY_ROLE]`
    → CDM check passes → `:car` no longer crashes.
  This is the "No 0p checker" / "0p first-launch car-setup" failure, and
  it's specific to Android 15 enforcing the CDM automotive-projection
  role. API 33's older platform didn't gate it.

## The two earlier disconnect causes (also fixed)

Before the CDM crash surfaced, the DHU connected then self-exited ~2s
after `connected.` for two other reasons, both on the API 35 image:

1. **Background Activity Launch (BAL)** blocked AA's
   `com.google.android.gms.carsetup.START_DUPLEX` / `FirstActivityImpl`
   ("0p" first-launch car-setup) activity
   (`callingUidHasAnyVisibleWindow: false`, result 102 BAL_BLOCK).
   Foregrounding AA works around it; the per-run flow's `am start` on
   the settings activity avoids the BAL path.
2. **AA's troubleshooter tore down the car-client** because the 14
   dangerous runtime permissions weren't granted. Fix: `pm grant` them
   all in `--setup` step 4 → `TROUBLESHOOTER_ISSUE_RESOLVED`.

## One manual step the agent can't do

After `--setup`, the user must **open Android Auto on the AVD once and
complete its first-launch onboarding/consent** (the safety notices,
permission prompts). The runtime perms are pre-granted, but AA still
runs its own first-launch flow, and the head unit server connects
reliably only after it. In this session the DHU only stayed connected
after the user did that first-launch setup on the phone.

## What this obsoletes

- The `rootAVD` submodule, `aa4mg` module, Zygisk, XLauncher, the 10-tap
  dev-mode-doesn't-survive-wipe dance, and the `aa4mg/disable` flag —
  none of those are on the API 35 path. `run.sh` no longer references
  them. The submodule is left in the tree (untouched — it's a
  third-party repo we don't edit) but is unused by the current script.
