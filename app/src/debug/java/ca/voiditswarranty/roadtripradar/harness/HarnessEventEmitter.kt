package ca.voiditswarranty.roadtripradar.harness

import androidx.compose.runtime.snapshotFlow
import ca.voiditswarranty.roadtripradar.data.Waypoint
import ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement

/**
 * Emits unsolicited [HarnessEvent]s to all live WebSocket sessions when
 * observable ViewModel state changes.
 *
 * Two event streams:
 *  - `route.changed` — emitted on any change to `waypoints`, `activeWaypointId`,
 *    `autoAdvanceEnabled`, or `autoAdvanceThresholdMeters`. Debounced ~50 ms so
 *    a batch of writes from the harness doesn't spam the socket.
 *  - `location.changed` — emitted on `userPosition` / accuracy / bearing / speed
 *    change. Throttled to ≤ 4 Hz (250 ms min interval) so driving doesn't flood.
 *
 * The emitter is refcounted: [start] increments a refcount and starts the
 * underlying coroutine jobs only when the count goes 0 → 1; [stop] decrements
 * and cancels the jobs when the count returns to 0. This lets multiple WS
 * sessions share a single emitter.
 *
 * Events are broadcast to all live sessions via the [HarnessServer.broadcast]
 * callback supplied at construction.
 */
class HarnessEventEmitter(
    private val vm: MapViewModel,
    private val json: Json,
    private val broadcast: (JsonElement) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var routeJob: Job? = null
    private var locationJob: Job? = null
    private var refCount = 0

    /** Helper: build a [JsonObject] from a mutable map, allowing nullable [JsonElement] values. */
    private fun jsonObject(build: MutableMap<String, JsonElement>.() -> Unit): JsonObject {
        val map = mutableMapOf<String, JsonElement>()
        map.build()
        return JsonObject(map)
    }

    private fun MutableMap<String, JsonElement>.putJson(key: String, value: JsonElement?) {
        put(key, value ?: JsonNull)
    }

    private fun MutableMap<String, JsonElement>.put(key: String, value: Boolean) {
        put(key, JsonPrimitive(value))
    }

    private fun MutableMap<String, JsonElement>.put(key: String, value: Int) {
        put(key, JsonPrimitive(value))
    }

    private fun MutableMap<String, JsonElement>.put(key: String, value: Double) {
        put(key, JsonPrimitive(value))
    }

    private fun routeSnapshot(): JsonElement = jsonObject {
        put("waypoints", json.encodeToJsonElement(ListSerializer(Waypoint.serializer()), vm.waypoints.toList()))
        putJson("activeWaypointId", vm.activeWaypointId?.let { JsonPrimitive(it) })
        put("autoAdvance", jsonObject {
            put("enabled", vm.autoAdvanceEnabled)
            put("thresholdMeters", vm.autoAdvanceThresholdMeters)
        })
    }

    private fun locationSnapshot(): JsonElement = jsonObject {
        val pos = vm.userPosition
        putJson("position", pos?.let {
            jsonObject {
                put("lat", it.latitude)
                put("lon", it.longitude)
            }
        })
        putJson("accuracy", vm.userPositionAccuracy?.let { JsonPrimitive(it) })
        putJson("bearing", vm.userPositionBearing?.let { JsonPrimitive(it) })
        putJson("speed", vm.userPositionSpeed?.let { JsonPrimitive(it) })
    }

    @Synchronized
    fun start() {
        if (refCount++ > 0) return
        routeJob = scope.launch {
            var lastEmitted: JsonElement? = null
            snapshotFlow { routeSnapshot() }
                .distinctUntilChanged()
                .collect {
                    if (it == lastEmitted) return@collect
                    // Coalesce rapid bursts (e.g. a batch of addWaypoint calls).
                    delay(50)
                    // Re-read after the delay so we send the latest state, not the
                    // snapshot that triggered the coalescing window.
                    val latest = routeSnapshot()
                    if (latest == lastEmitted) return@collect
                    lastEmitted = latest
                    val event = HarnessEvent(event = "route.changed", data = latest)
                    broadcast(json.encodeToJsonElement(HarnessEvent.serializer(), event))
                }
        }
        locationJob = scope.launch {
            // Throttle to ≤ 4 Hz: only emit if at least 250 ms has passed since
            // the last emission. We use a simple time-gate inside the collector.
            var lastEmitTimeMs = 0L
            snapshotFlow { locationSnapshot() }
                .distinctUntilChanged()
                .collect {
                    val now = System.currentTimeMillis()
                    val elapsed = now - lastEmitTimeMs
                    if (elapsed < 250) {
                        delay(250 - elapsed)
                    }
                    lastEmitTimeMs = System.currentTimeMillis()
                    val event = HarnessEvent(event = "location.changed", data = locationSnapshot())
                    broadcast(json.encodeToJsonElement(HarnessEvent.serializer(), event))
                }
        }
    }

    @Synchronized
    fun stop() {
        if (refCount == 0) return
        if (--refCount > 0) return
        routeJob?.cancel(); routeJob = null
        locationJob?.cancel(); locationJob = null
    }

    fun shutdown() {
        scope.cancel()
    }
}