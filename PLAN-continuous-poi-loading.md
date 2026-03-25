# Plan: Continuous Cell-Based POI Loading (Option C)

User activates POI loading manually via "Search Visible Area" (from the category picker or "Refresh POIs" button). This triggers the cell pipeline for the current viewport. Once active, a camera-settle watcher continuously extends coverage as the user pans/zooms, fetching new cells incrementally. The pipeline stops when the user clears POIs or disables all categories.

A single merged GeoJSON source is used for rendering (preserving cross-cell clustering). Per-cell fetching is the data pipeline underneath. Failed cells auto-retry after 30s cooldown via normal pipeline flow. Cached data remains visible even when zoomed below the fetch threshold.

## Steps

### 1. Fix the flash
**File:** `MapLayers.kt` — `NearbyPoiLayers`, line with `key(categoriesVersion, fc.features.size)`

Change `key(categoriesVersion, fc.features.size)` → `key(categoriesVersion)`. The `rememberGeoJsonSource` already handles in-place `setData()` updates via its internal `LaunchedEffect`; including `fc.features.size` in the key forces full source+layer teardown/recreation on every data change, causing the visible flicker.

### 2. Add cell cache state
**File:** `MapViewModel.kt` — near existing POI state fields

- `cellCache: MutableMap<String, CachedCell>` — maps cell ID → `CachedCell(cellId, features: List<Feature>, fetchedAtMs: Long)`
- `failedCells: MutableMap<String, Long>` — maps cell ID → failure timestamp (epoch ms). Cells here are treated as "not populated" after 30s cooldown.
- `poiPipelineActive: Boolean` — `false` on startup. Set to `true` by `searchVisibleArea()`, set to `false` by `clearNearbyPois()` and when all categories disabled. **This gates the camera-settle watcher.** No automatic loading on app start.
- Replace `isLoadingPois: Boolean` with `cellsLoadingTotal: Int` and `cellsLoadingComplete: Int` (both default 0).
- `isLoadingPois` becomes a derived property: `get() = cellsLoadingTotal > 0 && cellsLoadingComplete < cellsLoadingTotal`
- Remove `showPoiFetchError: Boolean` — no longer needed (no modal dialog).
- Add `interCellDelayMs: Long = 250` mutable field for adaptive backoff.

### 3. Add `rebuildMergedFeatures()` helper
**File:** `MapViewModel.kt`

- Concatenates all `cellCache.values.flatMap { it.features }` into a single `FeatureCollection<Point, JsonObject>`
- Assigns to `nearbyPoiFeatures`
- Recomputes `poiLoadBounds` as the union bounding box of all cached cells (min/max of all cell bounds)
- Recomputes `poiFetchRegion` = same union bbox (for the dotted outline)
- Called after each cell completes (success or eviction)

### 4. Add `fetchCellPipeline(cells, cameraCenter)` coroutine
**File:** `MapViewModel.kt`

- Sort `cells` by distance from `cameraCenter` (nearest first)
- Set `cellsLoadingTotal = cells.size`, `cellsLoadingComplete = 0`
- For each cell:
  - Check `networkStatus.connected` — if offline, break out of loop (remaining cells will be picked up on next camera-settle when connectivity returns)
  - Call `overpassRepo.fetchPoisForTile(cell.bounds, enabledPoiCategories)` inside a `try/catch`
  - **On success**: insert into `cellCache` with current timestamp, remove from `failedCells` if present, call `rebuildMergedFeatures()`, increment `cellsLoadingComplete`
  - **On failure**: record `failedCells[cell.id] = System.currentTimeMillis()`, increment `cellsLoadingComplete`, check response: if 429 or 5xx → double `interCellDelayMs` (capped at 2000ms) for the remainder of this batch. **Continue to next cell** (don't abort).
  - `delay(interCellDelayMs)` between cells
- When batch finishes: reset `cellsLoadingTotal = 0`, `cellsLoadingComplete = 0`

### 5. Add `cellsNeedingFetch(loadBounds)` helper
**File:** `MapViewModel.kt`

- Call `PoiViewportChunks.worldGridCellsIntersecting(loadBounds)` to get all cells in viewport
- Filter out cells already in `cellCache`
- Filter out cells in `failedCells` where `System.currentTimeMillis() - timestamp < 30_000` (cooldown not expired)
- Cells in `failedCells` where cooldown HAS expired → treat as eligible (include them)
- Return remaining cells

### 6. Modify `searchVisibleArea()`
**File:** `MapViewModel.kt`

- Keep early returns for empty categories / no camera
- Compute `loadBounds` via `gridCellsForManualLoad()` (existing)
- **Clear** `cellCache` and `failedCells` (fresh start)
- Set `poiPipelineActive = true`
- Reset `interCellDelayMs = 250`
- Cancel any existing `poiFetchJob`
- Compute missing cells (all of them, since cache just cleared)
- Launch `poiFetchJob = viewModelScope.launch { fetchCellPipeline(missingCells, cameraCenter) }`
- Increment `poiCategoriesVersion`

### 7. Add camera-settle watcher
**File:** `MapScreen.kt`

- `snapshotFlow { cameraState.position }` → `debounce(500ms)` → collect
- **Gate**: only proceed if `vm.poiPipelineActive && !vm.isLoadingPois`
- **Zoom gate**: only proceed if `zoom >= 9`
- **Trigger condition**: check if any edge of the current viewport has reached the edge of the union bbox of cached cells (`poiLoadBounds`). If the viewport is fully contained within `poiLoadBounds`, skip. This means we only fetch when the user has panned near the boundary, not on every minor pan.
- Compute new `loadBounds` via `gridCellsForManualLoad(cam.lat, cam.lon, cam.zoom)`
- Apply clamp to `MAX_POI_LOAD_EXTENT_KM`
- Run eviction: remove cells from `cellCache` that are outside `padBounds(loadBounds, 1.5)` **AND** older than 60 seconds, then `rebuildMergedFeatures()`
- Compute `cellsNeedingFetch(loadBounds)`
- If non-empty, reset `interCellDelayMs = 250`, cancel existing `poiFetchJob`, launch new pipeline

### 8. Modify `clearNearbyPois()`
**File:** `MapViewModel.kt`

- Clear `cellCache`, `failedCells`
- Set `poiPipelineActive = false`
- Reset `cellsLoadingTotal = 0`, `cellsLoadingComplete = 0`
- Keep existing: cancel job, clear features/bounds/region

### 9. Handle category changes
**File:** `MapViewModel.kt` — `togglePoiCategory` / `resetPoiCategories`

- If `poiPipelineActive`:
  - Clear `cellCache` and `failedCells` (category set changed, old data is stale)
  - Immediately re-trigger pipeline for current viewport cells
- If all categories disabled: set `poiPipelineActive = false`, clear everything

### 10. Update loading indicator UI
**File:** `MapScreen.kt`

Replace the existing `CircularProgressIndicator` block with a progress chip:
- Show when `vm.cellsLoadingTotal > 0 && vm.cellsLoadingComplete < vm.cellsLoadingTotal`
- Text: `"Loading areas (${vm.cellsLoadingComplete}/${vm.cellsLoadingTotal})"`
- Subtle chip/pill near the bottom-center of the map (where the spinner currently is)
- Remove the `AlertDialog` for fetch errors entirely — failed cells auto-retry silently after 30s

### 11. Update "Refresh POIs" button
**File:** `MapOverlay.kt`

- **Show condition**: `vm.poiLoadBounds != null` (same as before — shown once POIs have ever been loaded)
- **On click**: calls `searchVisibleArea()` as before (which clears cache and re-fetches)
- No change from current behavior — it's the user's manual "re-do everything" button
- Remove `showPoiFetchError` from the condition (that state variable is removed)

### 12. Keep existing features visible below zoom 9
The camera-settle watcher only *fetches* at zoom ≥ 9. The `NearbyPoiLayers` composable continues rendering `nearbyPoiFeatures` at all zoom levels. No change needed — MapLibre clustering naturally handles density at low zoom.

### 13. Remove stale code
Delete `showPoiFetchError`, `dismissPoiFetchError()`, `fetchPoisForRegion()`, `loadPoisForCategory()`, `loadedNearbyPoiCategory`. These are replaced by the cell pipeline. Keep `searchVisibleArea()` as the entry point.

## Key Decisions

- **No "Refresh" button tied to failures** — failed cells silently become eligible after 30s cooldown
- **POI loading is user-initiated only** — "Search Visible Area" or "Refresh POIs" activates the pipeline, no auto-start
- **Modal error dialog removed entirely** — silent retry is sufficient
- **Adaptive inter-cell delay**: 250ms default, doubles on 429/5xx per batch, resets on next batch
- **Union bbox of cached cells** used for both `poiLoadBounds` outline and camera-settle trigger threshold

## Verification

- Build: `gradlew.bat compileDebugKotlin` — no errors
- Manual test 1: Open app, select categories, tap "Search Visible Area" → cells load progressively, chip shows "Loading areas (2/6)" etc., POIs appear incrementally without flash
- Manual test 2: Pan outside loaded region → camera-settle triggers new cells, existing POIs remain visible
- Manual test 3: Airplane mode → pipeline stops gracefully, no error dialogs, loaded POIs remain on screen. Re-enable network → next camera-settle resumes fetching
- Manual test 4: Simulate slow/failing Postpass → failed cells recorded, after 30s they become eligible again and load on next pipeline run. Inter-cell delay backs off.
- Manual test 5: Zoom out below 9 → POIs remain visible, no new fetches. Zoom back in → pipeline resumes.
- Manual test 6: Change categories while POIs visible → cache clears, re-fetches with new categories
- Manual test 7: "Refresh POIs" button → full reset, re-fetches all visible cells
