# Local Development

## Prerequisites

- [Docker](https://docs.docker.com/get-docker/)
- [VS Code](https://code.visualstudio.com/) or [Cursor](https://cursor.com/) with the **Dev Containers** extension installed
- An Android device with USB debugging enabled (or wireless debugging for ADB over TCP/IP)

## Getting Started

1. Open the project in VS Code or Cursor.
2. When prompted, select **Reopen in Container** (or run the command palette action `Dev Containers: Reopen in Container`).
3. Wait for the container to build. The first build downloads the Android SDK and takes several minutes.

## Building

Run the build script from the project root inside the devcontainer:

```bash
# Release APK + AAB (unsigned if signing is not configured)
./build.sh

# Debug APK only (faster local iteration; uses applicationId .debug)
./build.sh --dev

# Build and install on a connected device
./build.sh --install

# Debug build, install, and launch
./build.sh --dev --install --run
```

This produces:
- `app/build/outputs/apk/release/RoadTripRadar.apk`
- `app/build/outputs/bundle/release/RoadTripRadar.aab`

The build uses the same toolchain as CI (Java 21, Gradle wrapper, build-tools 34.0.0).

## Signing (Optional)

Without signing configuration, `build.sh` produces unsigned artifacts and prints a warning. To enable signing (matching the CI flow with `apksigner`/`jarsigner`):

1. Place your `keystore.jks` in the project root (this file is gitignored).
2. Copy the template:
   ```bash
   cp .devcontainer/signing.env.example .devcontainer/signing.env
   ```
3. Edit `.devcontainer/signing.env` and fill in your credentials:
   ```bash
   KEYSTORE_PATH=./keystore.jks
   KEYSTORE_PASSWORD=your_password
   KEY_ALIAS=your_alias
   KEY_PASSWORD=your_key_password
   ```

`The `signing.env` file is gitignored and will not be committed. Once configured, `build.sh` will automatically sign both the APK and AAB.

## Connecting a Device

A device connection is required for `./build.sh --install`. Once connected, the ADB session persists until the container is stopped or the device disconnects.

### USB

1. Connect your phone via USB to the host machine.
2. On the phone, enable **USB debugging** in Developer Options.
3. The devcontainer runs in privileged mode, so the device is passed through automatically.
4. Verify the connection:
   ```bash
   adb devices
   ```
5. If prompted on the phone, approve the USB debugging authorization.

### ADB over TCP/IP (Wireless)

#### Android 11+ (Wireless Debugging)

1. On the phone, go to **Settings > Developer Options > Wireless debugging** and enable it.
2. Tap **Pair device with pairing code** and note the IP, port, and pairing code.
3. In the devcontainer terminal:
   ```bash
   adb pair <phone-ip>:<pairing-port>
   ```
   Enter the pairing code when prompted.
4. After pairing, connect using the address shown on the Wireless debugging screen (this is a different port than the pairing port):
   ```bash
   adb connect <phone-ip>:<port>
   ```
5. Verify:
   ```bash
   adb devices
   ```

#### Android 10 and below

1. Connect the phone via USB first and ensure it appears in `adb devices`.
2. Switch ADB to TCP/IP mode:
   ```bash
   adb tcpip 5555
   ```
3. Disconnect the USB cable, then connect wirelessly:
   ```bash
   adb connect <phone-ip>:5555
   ```
4. Verify:
   ```bash
   adb devices
   ```

## Testing the in-app What's New changelog

The first time the app stores `last_seen_changelog_version_code`, it matches the current `versionCode` and **does not** show the sheet (avoids dumping history on fresh installs).

**Debug build (simplest):** install the **debug** variant, open **Actions → Help & Info**, scroll below the version line, tap **Preview What's New (debug)**. That sets last-seen to `versionCode - 1` and runs the same logic as an upgrade, so the sheet appears if `changelog.json` contains at least one release with `versionCode` strictly greater than that value and less than or equal to your current `versionCode`.

**Without the button:** clear app data (or uninstall) then install again — that only tests the “first launch, no sheet” path. To mimic an upgrade manually, you’d need to edit `SharedPreferences` `map_prefs` / key `last_seen_changelog_version_code` to an integer **below** your `BuildConfig.VERSION_CODE` while `changelog.json` still lists a release for your current code.

**Local “real” upgrade simulation:** temporarily bump `versionCode` in `app/build.gradle.kts` and add a matching `{ "versionName", "versionCode", "items" }` block in `app/src/main/assets/changelog.json`, install, then use **Preview What's New** after setting last-seen lower (or install once, then lower last-seen via debug button which uses `versionCode - 1`).

## Version Behavior

The version displayed in the app is resolved from git at build time:

| Build context | Version shown |
|---|---|
| Tagged commit (CI release) | Tag name, e.g. `1.3.1` |
| Local dev build | Branch name, e.g. `1.4.0-rc` |

`versionCode` is only incremented by the CI release workflow. Local builds reuse the last released value.

## Changelog system

User-facing release notes are driven from a single pending entry in `app/src/main/assets/changelog.json`. The release workflow stamps it with the real version during dispatch and feeds the in-app **What's New** sheet, the GitHub Release body, and the Fastlane/Play Store changelog from the same source.

### Files

- `app/src/main/assets/changelog.json` — bundled with the app; powers the in-app changelog sheets. Newest release is always at `releases[0]`. Between releases the top entry is a staging entry with `"versionName": "next"` where new bullets are added.
- `fastlane/metadata/android/<locale>/changelogs/next.txt` — staging file(s) for the Play Store changelog, renamed to `<versionCode>.txt` during release.
- `changelog/pending.json` — legacy staging file for a previous workflow; not currently consumed by `.github/workflows/prepare-release.yml` (kept for reference / tooling).

### Adding entries between releases

1. Edit `app/src/main/assets/changelog.json` and append user-facing bullets to the `items` array of the `releases[0]` entry (the one with `"versionName": "next"`). Keep entries short and user-readable — they appear verbatim in the What's New sheet and the GitHub Release body.
2. Optionally mirror the English bullets into `fastlane/metadata/android/en-US/changelogs/next.txt` (one line per bullet, ≤ 500 chars total per Play Store rules) if the Play Store changelog should differ from the aggregated bullets. If not updated here, the release workflow writes this file from `changelog.json` automatically.

### Hiding a release from the in-app UI

Each release entry in `changelog.json` accepts an optional `"showInApp"` boolean (defaults to `true`). Set it to `false` to exclude that entry from both in-app views:

- The post-update **What's New** sheet skips hidden entries entirely. If every release between the user's last-seen `versionCode` and the current one is hidden, the sheet does not appear on launch.
- The **Help & Info → Changelog** sheet omits hidden entries from the full history.

`showInApp = false` has no effect on Fastlane/Play Store changelog files or GitHub Release notes — those are still built from the raw `items`. Use this for purely internal/rebuild releases where the user-facing content is unchanged.

### In-app sort order

Both the What's New sheet and the Help & Info changelog sheet display releases with the highest `versionCode` at the top.

### What the Release workflow does

`.github/workflows/prepare-release.yml` (triggered via **Actions → Release → Run workflow**, with a `version` input like `1.12.0`):

1. **Validates** `releases[0].versionName == "next"` and that `items` is non-empty. If not, the workflow fails (e.g. "already released?").
2. **Computes `versionCode`** as `(# of existing v* tags) + 1`. There is no placeholder convention for `versionCode` in the `"next"` entry — whatever integer is there gets overwritten unconditionally. Only `versionName` is matched against the `"next"` sentinel.
3. **Bumps** `versionCode` / `versionName` in `app/build.gradle.kts`.
4. **Stamps** `releases[0]` in `changelog.json` with the computed `versionName` and `versionCode`, replacing `"next"` in place.
5. **Renames** each `fastlane/metadata/android/*/changelogs/next.txt` to `<versionCode>.txt`.
6. Writes `release_notes.md` (the GitHub Release body) from the bulleted items.
7. Commits, tags `v<versionName>`, builds a signed APK + AAB, and creates the GitHub Release.

After a release is cut, the top entry is the released one, so the next time you want to add changelog items you must first insert a new `{"versionName": "next", "versionCode": <any>, "items": [...] }` block at `releases[0]` before appending bullets. The `versionCode` value in that block is irrelevant — pick anything (e.g. the current build's `versionCode`), the workflow will overwrite it.

### Rebuild-only mode

If `rebuild_only` is set when dispatching the workflow, it skips the validate/bump/stamp/commit/tag steps, reads the existing `versionCode` from `build.gradle.kts`, checks out the existing `v<version>` tag, rebuilds, and replaces the APK/AAB assets on the existing GitHub Release. Use this only for re-signing or rebuilding an already-tagged release.

## Testing CI Workflows Locally

[`act`](https://github.com/nektos/act) is installed in the devcontainer for running GitHub Actions workflows locally:

```bash
# List available workflows
act -l

# Dry run the PR build workflow
act pull_request --dryrun
```
