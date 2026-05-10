# Multi-Waypoint Route Editor — Implementation Plan

## Goal

Replace the single-POI "destination" model with an ordered list of waypoints. Users can build a route from either end (destination-first backtracking, or origin-first forward planning), reorder it, and follow it via a nav widget that points to the *active* waypoint with optional auto-advance.

## Resolved design decisions

- **Data model:** flat `List<Waypoint>`. No privileged "destination" slot — `list.last()` is just the last stop. Current location is implicit and never in the list.
- **Active waypoint:** stored as a stable `id` (UUID), not an index. Survives inserts/deletes/reorders.
- **Auto-advance:** when within configurable threshold of the active waypoint, advance to next. Default 100 m. Slider range 25 m – 500 m. Stored in meters; displayed as meters or yards based on `useMetric`.
- **Auto-advance arrival (final waypoint reached):** silent. No toast/haptic/notification.
- **Marker labels:** plain number badge (`#1`, `#2`, …) for `DROPPED_PIN` and `SEARCH` sources; reuse the Nearby Place icon for `NEARBY_PLACE`. Active waypoint gets a colored ring/border on top of whichever base.
- **Naming:** unnamed waypoints display as "Waypoint" + number for deduplication. Named ones (search/Nearby Places) also show their number as prefix.
- **Route line:** active leg solid green 4dp / 0.8 opacity (today's style); past legs solid grey faded; future legs dashed green at lower opacity.
- **Drag-to-reorder library:** `androidx.recyclerview.widget.ItemTouchHelper` via `AndroidView` bridge. Chose this over `sh.calvin.reorderable` (third-party, single-maintainer) and DIY Compose because the View bridge is reusable for future drag/swipe surfaces in this app.
- **Manual active-waypoint controls:** prev/next chevron pill on the nav widget (discoverable); long-press zoom-in advances, long-press zoom-out regresses (power-user shortcut).
- **Wording:** remove "destination" from user-facing strings — it implies a special slot the model no longer has.

## Data model

New `data/Waypoint.kt`:

```kotlin
data class Waypoint(
    val id: String,            // UUID, stable across reorders
    val position: Position,
    val name: String?,         // null for manual drops; set from search/nearby
    val subtitle: String?,
    val source: WaypointSource // DROPPED_PIN, SEARCH, NEARBY_PLACE
)

enum class WaypointSource { DROPPED_PIN, SEARCH, NEARBY_PLACE }
```

Display labels are *computed*, not stored:
- Map marker: `"${index+1}"`
- Lists/slideouts: `"${index+1}. ${name ?: stringRes(R.string.waypoint_unnamed)}"`

Re-numbering happens automatically because numbers are positional.

## Persistence (`PreferencesRepository.kt`)

Replace the `poi_*` scalars (`poi_lat`, `poi_lon`, `poi_name`, `poi_subtitle`) with:

| Key | Type | Default |
| --- | --- | --- |
| `waypoints` | JSON-encoded `List<Waypoint>` | `[]` |
| `active_waypoint_id` | `String?` | `null` |
| `auto_advance_enabled` | `Boolean` | `true` |
| `auto_advance_threshold_m` | `Int` (meters) | `100` |

**Migration:** add a `prefs_version < 11` block in `migrate()` that:
1. If `poi_lat`/`poi_lon` are present, build a one-element `waypoints` list with a fresh UUID, `name = poi_name`, `subtitle = poi_subtitle`, `source = DROPPED_PIN` (since old data has no source distinction). Set `active_waypoint_id` to that id.
2. Remove the four `poi_*` keys.
3. Bump `prefs_version` to 11 (and `PrefsDefaults.PREFS_VERSION` to match).

Use `kotlinx.serialization` for the JSON encoding (verify it's already on the classpath; if not, the persistence layer can use a hand-rolled JSON writer over `org.json` since the Waypoint shape is trivial).

## ViewModel (`MapViewModel.kt`)

Replace the `poiPosition` / `poiName` / `poiSubtitle` fields (lines ~148-156) with:

```kotlin
val waypoints: SnapshotStateList<Waypoint> = mutableStateListOf<Waypoint>().apply {
    addAll(prefsRepo.waypoints)
}
var activeWaypointId by mutableStateOf<String?>(prefsRepo.activeWaypointId)
    private set
var autoAdvanceEnabled by mutableStateOf(prefsRepo.autoAdvanceEnabled)
    private set
var autoAdvanceThresholdMeters by mutableStateOf(prefsRepo.autoAdvanceThresholdMeters)
    private set
```

Derived helpers:
- `val activeIndex: Int? get() = activeWaypointId?.let { id -> waypoints.indexOfFirst { it.id == id }.takeIf { it >= 0 } }`
- `val activeWaypoint: Waypoint? get() = activeIndex?.let { waypoints[it] }`

New ops:
- `addWaypoint(position, name?, subtitle?, source, insertAt: InsertPosition)` where `InsertPosition` is `START`, `BEFORE_LAST`, `END`, `Index(i)`, or `ReplaceId(id)`.
- `removeWaypoint(id)`, `moveWaypoint(fromIndex, toIndex)`, `renameWaypoint(id, name)`, `clearRoute()`.
- `setActiveWaypoint(id)`, `advanceActiveWaypoint()`, `regressActiveWaypoint()`.
- After each mutation, persist the full list and active id back through `prefsRepo`.

Auto-advance hook in the existing user-location update path: when `autoAdvanceEnabled` && `userPosition.distanceTo(activeWaypoint.position) < threshold` && a next waypoint exists, advance. No-op silently at the final waypoint.

Migrate existing call-sites:
- `setPoiFromLongPress(position)` → `addWaypoint(position, name = null, …, source = DROPPED_PIN, insertAt = START)`. (`START` matches the backtracking workflow.) UI semantics for a fresh empty route: same as today, becomes the only stop.
- `setPoiFromSearch(position, name)` → `addWaypoint(..., source = SEARCH, insertAt = START)` initially. Once the multi-stop slideout lands (PR #2), the slideout dictates the position.
- `clearPoi()` → `clearRoute()`.
- `tappedPoi` / `TappedPoiInfo` flow unchanged for now; the slideout re-mapping happens in PR #2.

## Slideout actions (`TappedPoiPopup.kt`)

Dynamic action set based on `waypoints.size`:

| Size | Primary actions | Overflow |
| --- | --- | --- |
| 0 | Add waypoint | — |
| 1 | Insert before #1 · Add after #1 | Replace #1 |
| 2+ | Add to start · Insert before final stop · Add to end | Replace last · Replace specific stop ▸ |

Each label shows the adjacent waypoint name in secondary text where useful (e.g. "Add to start / before #1 Tim Hortons"). Falls back to the index alone when the adjacent stop is unnamed.

## Map markers and route line

**Markers** (extend the current POI marker layer to render one per waypoint):
- Base: number badge for `DROPPED_PIN` / `SEARCH`; Nearby-Place icon (reused from `NearbyPoiLayers`) for `NEARBY_PLACE`.
- Active waypoint: colored ring/border overlay on top of the base.

**Route line** (`MapLayers.kt`): replace the single-segment LineString with one GeoJSON source carrying multiple features distinguished by a `leg` property:
- `active`: `(prevAnchor → list[activeIndex])` where `prevAnchor = userPosition` if `activeIndex == 0`, else `list[activeIndex-1]`. Solid green, 4dp, 0.8 opacity.
- `past`: legs strictly before the active leg. Solid grey faded.
- `future`: legs strictly after the active leg. Dashed green at lower opacity.

## Stops-list editor

New `RouteEditorSheet` Composable, opened from a map-overlay chip ("N stops • #1 [first name]") near the existing top controls. Modal bottom sheet contents:

- Reorderable list using `RecyclerView` + `ItemTouchHelper` wrapped in `AndroidView`. Drag handle on each row.
- Each row: index badge, name (or "Waypoint"), subtitle, "Active" radio/check, delete button.
- Tap a row to recenter the map on it (no state change beyond camera).
- Footer: "Clear route" button with confirm dialog.
- Renaming: tap-to-edit on the name field. Optional — can be a follow-up.

## Nav widget (`NavWidget.kt`, `MapScreen.kt`)

- Bearing source becomes `activeWaypoint.position` instead of `poiPosition`.
- Label: index + name (`"#2 Tim Hortons"`, or `"Waypoint 2"` for unnamed).
- Inline prev / next chevron pill attached to the widget. Disabled-state when at first/last.
- The chevrons are the visible, discoverable manual override for the active waypoint.

## Zoom-button long-press (`MapFabs.kt`)

Add `onLongClick` to both buttons via `combinedClickable`:
- Long-press zoom-in → `advanceActiveWaypoint()`
- Long-press zoom-out → `regressActiveWaypoint()`
- Light haptic feedback on activation.
- Tap behavior unchanged.

Power-user shortcut. Discoverable primary control is the chevron pill.

## Settings (`MapDrawerSettingsContent.kt`)

Add to the existing settings sheet (boolean/slider patterns already present):
- "Auto-advance to next waypoint" toggle (default on).
- "Advance when within" continuous integer slider, range **25 m – 500 m**, default **100 m**. Display in meters or yards depending on `useMetric`. Stored in meters always.

## Tutorial (`TutorialModels.kt` + `TutorialAnchors.kt` + `strings.xml`)

New anchors and steps:
- Route editor chip — explains tapping it opens the stops editor.
- Nav widget — update existing step to reference "the active waypoint" and the chevrons. Add a new step for the chevron control if it has its own anchor.
- Long-press zoom — new step (one combined or one per button) explaining the power-user shortcut.

Replace any existing user-facing strings that reference "destination" with neutral wording. Crowdin will pick up new/changed `tutorial_*` keys on next sync; mark removed keys for deletion in `crowdin.yml`.

## Strings to add (`values/strings.xml`)

- `waypoint_unnamed` ("Waypoint")
- `route_editor_chip_format` ("%1$d stops")
- `action_add_waypoint`, `action_add_to_start`, `action_insert_before_final`, `action_add_to_end`, `action_replace_last`, `action_replace_specific`, `action_set_active`, `action_clear_route`
- `settings_auto_advance_toggle`, `settings_auto_advance_threshold`
- `tutorial_route_editor_title` / `_body`, `tutorial_long_press_zoom_title` / `_body`
- Update `tutorial_nav_widget_*` body to reference active waypoint instead of destination

## PR sequence

Each step lands independently; the app stays working between steps.

1. **[done] Data model + persistence migration + single-waypoint behaviour preserved.** Converted `poi_*` to `List<Waypoint>` of size 0 or 1 internally; UI still treats it as the only stop. No user-visible change. New `Waypoint` data class (`@Serializable`, lat/lon doubles + `WaypointSource` enum). `PrefsDefaults.PREFS_VERSION` → 11 with migration converting old keys. `MapViewModel` exposes `waypoints` / `activeWaypointId` / `autoAdvanceEnabled` / `autoAdvanceThresholdMeters` plus computed back-compat aliases (`poiPosition` / `poiName` / `poiSubtitle`) so existing UI code keeps working.
2. **[done] Multi-waypoint slideout actions + numbered map markers.** Users can build a list. Route line still draws only to the active waypoint (back-compat alias). New `InsertPosition` sealed type, `addWaypoint(... at: InsertPosition)`, `addWaypointFromTapped`, `setActiveWaypoint`, `removeWaypoint`, `showWaypointPopup` ops. Slideout adapts: size 0 keeps existing 2-button layout (Center + Navigate Here); size 1+ shows a vertical action stack with adjacency labels and an overflow menu (Replace #1 / Replace last). Numbered markers via new `WaypointMarkersLayer`; active waypoint gets a dark-green 3.5dp border, inactive gets a white 2dp border. Tapping any marker opens the navigation-target popup for that specific waypoint, with a Remove button that drops only that stop.
3. **[done] Multi-segment route line + active-waypoint nav widget + chevron control.** Replaced `PoiRouteLineLayer` with `WaypointRouteLineLayer` rendering three styles: past legs solid grey at 0.4 opacity, active leg solid green 4dp 0.8 opacity (today's style), future legs dashed green at 0.5 opacity. NavWidget still receives distance/bearing through `vm.poiPosition` (back-compat alias resolves to active waypoint), but its label is now `"#N Name"` / `"Waypoint N"` formatted via the shared `waypointDisplayLabel` helper. Added `vm.advanceActiveWaypoint()` / `regressActiveWaypoint()` and a chevron pill (prev/next icon buttons) flanking the NavWidget when `waypoints.size >= 2`, with disabled state at first/last.
4. **[done] Stops-list editor sheet** (drag-to-reorder, delete, clear). Added `androidx.recyclerview` dependency. New `RouteEditorSheet` Composable wraps a programmatically-built `RecyclerView` + `ItemTouchHelper` inside an `AndroidView`. Each row shows index, name, subtitle, an active-state toggle, and a remove button; long-press anywhere on the row starts drag. Drag commits via `vm.moveWaypoint(from, to)` on `clearView`. Footer "Clear route" with confirm `AlertDialog`. Sheet is opened from a new `RouteChip` in `MapOverlay` (visible only when `vm.waypoints.isNotEmpty()`). `vm.clearPoi()` renamed to `vm.clearRoute()`; `ActionsDrawer` updated accordingly.
5. **[done] Auto-advance + settings + long-press zoom.** Added `vm.maybeAutoAdvance(userPos)` (called from `MapScreen`'s `LaunchedEffect(userPosition)`); silently advances the active waypoint when within `autoAdvanceThresholdMeters`. New "Auto-advance to next waypoint" toggle (Switch) + slider (25–500 m, displayed as `m` or `yd` per `useMetric`) in `MapDrawerSettingsContent`. Zoom-in/zoom-out FABs converted from `LargeFloatingActionButton` to `Surface + Box + combinedClickable` (matching the weather FAB pattern); long-press fires `vm.advanceActiveWaypoint()` / `regressActiveWaypoint()` with `HapticFeedbackType.LongPress`.
6. **[done] Tutorial updates + string cleanup.** Added `TutorialAnchors.ROUTE_CHIP` + a new MAP-tutorial step ("Edit your route") inserted after the drop-pin step. Updated drop-pin / zoom-in / zoom-out tutorial bodies to reference waypoints and the long-press shortcuts. No "destination" wording was left to remove — the user-facing label was eliminated in earlier PRs by switching to "Waypoint N" / "#N Name" formatting throughout. `crowdin.yml` needed no changes (only string additions and value rewrites; no key removals).

GPX import is a separate follow-up — once 1–4 are in, importing reduces to `waypoints.addAll(...)`.
