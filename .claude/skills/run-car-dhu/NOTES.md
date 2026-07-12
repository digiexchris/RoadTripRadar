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