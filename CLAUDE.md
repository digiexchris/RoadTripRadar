# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Debug build (faster iteration, uses .debug applicationId)
./build.sh --dev

# Release build (unsigned without signing config)
./build.sh

# Build and install on connected device
./build.sh --dev --install --run

# Gradle directly (compilation check only)
./gradlew :app:compileDebugKotlin

# Unit tests (MockWebServer-based HTTP tests, POI grid tests)
./gradlew test

# Instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest
```

The devcontainer provides Java 21, Gradle, and ADB. The maki icon git submodule must be initialized (`git submodule update --init`) or builds fail.

## Architecture

Single-module Android app (Kotlin, Jetpack Compose) using MVVM without a DI framework.

**Single-Activity, single-screen design**: `MainActivity` → `RoadTripRadarApp()` composable → `MapScreen`. No Navigation Component; all UI is overlays on a map (bottom sheets, side drawer, popups).

**State management**: `MapViewModel` (~1500 lines) holds all UI state as Compose `mutableStateOf`/`mutableStateListOf` properties. No StateFlow or LiveData. ViewModel is constructed via `MapViewModelFactory` with `PreferencesRepository`, `CustomThemeRepository`, and `OpenMeteoRepository`.

**Data layer**: Plain Kotlin repository classes using coroutines + `Dispatchers.IO` and `java.net.URL` for network calls. Persistence is `SharedPreferences` only (JSON-encoded lists for waypoints, etc.). No Room/database. `PreferencesRepository` manages migrations (currently at schema version 11).

**Mapping**: MapLibre Compose (`maplibre-compose`) renders the map. POI symbols use maki SVG icons (git submodule); built-in dark styles ship as MapLibre style JSON. See **Data Flows** below for the build-time sync and theme resolution.

**Key files**:
- `app/src/main/java/.../viewmodel/MapViewModel.kt` — central ViewModel (weather, POIs, waypoints, search, settings, tutorials, themes, changelogs)
- `app/src/main/java/.../ui/MapScreen.kt` — main composable screen
- `app/src/main/java/.../data/PreferencesRepository.kt` — SharedPreferences wrapper with migration logic
- `app/src/main/assets/changelog.json` — drives in-app "What's New" sheet and release notes

## Data Flows

**POIs**: POIs come from the Geofabrik **Postpass** SQL API (`https://postpass.geofabrik.de/api/0.2/interpreter`, not Overpass). `PostpassRepository` issues SQL queries against `postpass_pointpolygon` and parses results into MapLibre SpatialK `Feature<Point, JsonObject>` carrying `name`/`iconName`/`categoryLabel` properties. `MapViewModel` chunks the viewport into ~25 km `PoiGridCell`s (`PoiViewportChunks`), runs a single-worker pipeline (`searchVisibleArea`/`onCameraSettled` → enqueue → `singleWorkerLoop` → `fetchCell`) that caches results in `cellCache` and merges them into the `nearbyPoiFeatures` state field with sliding-window eviction. `NearbyPoiLayers` in `MapLayers.kt` feeds that `FeatureCollection` into a clustered GeoJSON source and renders unclustered POIs via a `SymbolLayer` whose `iconImage` is a `switch` on `feature["iconName"]` — each icon a maki SVG (mapped per category in `POI_CATEGORIES`, rasterized to a 64px circular bitmap by `loadMakiIcon`).

**Weather** (two independent paths): `OpenMeteoRepository.fetchCurrent()` calls Open-Meteo `/v1/forecast` and returns an `OpenMeteoSnapshot`; `MapViewModel.startLocalWeatherPolling()` polls it on a `while(true)` coroutine — 10 min on success, 60 s on failure — only while `localWeatherAnchor` is set, which `MapScreen` updates from the GPS `userPosition`. There is no caching: each poll overwrites the `openMeteoSnapshot` `mutableStateOf`. `WeatherWidget` (gated on `windEnabled`) renders temp/trend and a bearing-corrected wind arrow. Separately, `WeatherRepository` polls the RainViewer API every 60 s (only when `weatherActive`) to feed `WeatherTimeline`/`WeatherLegend` radar overlays.

**Themes**: Two parallel systems that stay consistent with each other. The MapLibre style is the `MapStyle` enum (`LIBERTY`, `DARK`, `COLOR_DARK`, `CUSTOM_LIGHT`, `CUSTOM_DARK`, `AUTO`). `MapStyle.resolveToConcrete(context)` (`data/MapStyleResolution.kt`) collapses `AUTO` to a concrete style based on the system dark/light setting (`UI_MODE_NIGHT_MASK`) — system light → `LIBERTY` (or custom light), system dark → `COLOR_DARK` (or custom dark); any explicitly chosen style is used as-is. The Material3 Compose theme (`ui/theme/Theme.kt`'s `RoadTripRadarTheme`) derives its dark/light from `MapStyle.isDarkForAppTheme(context)`, which follows the *resolved* map style — so the UI chrome always matches the map, and both track the system theme under `AUTO`. Built-in dark styles ship as MapLibre v8 JSON in `assets/` (`dark.json`, `dark_small_roads.json`, addressed via `asset://`); user-imported custom themes are validated, rewritten to OpenFreeMap sources, and persisted as `custom_light.json`/`custom_dark.json` in `filesDir` by `CustomThemeRepository` (no DB). `MapScreen` keys the `MaplibreMap` on `(mapStyleUri, customThemeVersion)` so imports/deletes reload the map, and passes `mapOverlaysDark` to overlay composables.

**Preferences & migrations**: All state persists in one `SharedPreferences` file ("map_prefs"); `PreferencesRepository` exposes typed Kotlin properties over string/boolean/float/int keys plus a JSON-serialized `waypoints` list. Schema evolution is an imperative `migrate()` chain run from the repo's `init` block: a `prefs_version` int tracks the schema, and each step is an independent `if (prefs.getInt("prefs_version", 0) < N) { ...transform keys... putInt("prefs_version", N).apply() }` block that runs once and stamps its version. To bump the schema, append a new `if (... < N)` block at the bottom of `migrate()` and increment `PrefsDefaults.PREFS_VERSION` in `Models.kt` (currently 11).

**Maki build sync**: The maki submodule (`libs/maki`) is synced into assets by the `syncMakiIcons` Gradle `Sync` task in `app/build.gradle.kts`, which copies `libs/maki/icons/*.svg` verbatim into `app/src/main/assets/maki/` (no format conversion — SVGs are rasterized at runtime via androidsvg). A guard task `checkMakiIcons` fails the build with a `GradleException` if the destination is empty (prompting `git submodule update --init`); `preBuild` depends on it, so the sync runs before every build.

## Coding Conventions

**i18n is mandatory**: All user-facing text must come from `strings.xml` resources. Use `stringResource(R.string.key)` in composables and `appContext.getString(R.string.key)` in ViewModels. Exempt: log tags, internal layer/asset identifiers, JSON keys, preference keys, test fixtures, `contentDescription = null` when adjacent `Text` provides accessibility. When adding strings, update both `values/strings.xml` and `values-en-rUS/strings.xml`; other locales are managed via Crowdin.

**Dependency preference**: Prefer Android SDK / AndroidX / first-party libraries over third-party alternatives, even at the cost of more code.

**External dependency docs**: Use official documentation for libraries (developer.android.com, library GitHub docs), not local JAR/AAR or Gradle cache sources.

**Icons — prefer the maki pack**: When a change needs a map/UI icon, first check the maki icon pack (`libs/maki/icons/*.svg`, the Mapbox Maki git submodule, ~215 SVGs) for a suitable existing icon before introducing a new asset. Maki icons are synced into app assets at build time and are already wired into the MapLibre rendering pipeline, so reusing one avoids adding/maintaining a separate icon. Only add a custom SVG if no maki icon fits the concept.

**Kotlin code style**: `official` (set in `gradle.properties`).

## Changelog System

User-facing release notes are authored in `app/src/main/assets/changelog.json`. Between releases, the top entry has `"versionName": "next"` — append bullets to its `items` array. The CI release workflow stamps it with the real version, generates Fastlane changelogs, and creates the GitHub Release. After a release, insert a new `"next"` entry at `releases[0]` before adding more bullets. A `next` entry uses the placeholder shape `{"versionName": "next", "versionCode": 99, "showInApp": true, "items": [...]}` — the CI release workflow overwrites `versionName` and `versionCode` with the real values when stamping the release.

## Reproducible Builds

Release builds use deterministic ZIP ordering and suppress file timestamps. Baseline profile generation is disabled. APK signing uses `apksigner` from build-tools 34.0.0 (not 35+) to maintain F-Droid reproducible build compatibility.

## Known Quirks

- Range rings (dashed LineLayer with dasharray) do not render on the Android emulator — verify on physical hardware.
- The debug variant includes a "Preview What's New" button in Help & Info for testing changelog upgrades.