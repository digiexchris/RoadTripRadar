package ca.voiditswarranty.roadtripradar.harness

import ca.voiditswarranty.roadtripradar.data.InsertPosition
import ca.voiditswarranty.roadtripradar.data.Waypoint
import ca.voiditswarranty.roadtripradar.data.WaypointSource
import ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.maplibre.spatialk.geojson.Position
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Method dispatch table for the harness WebSocket API.
 *
 * Each handler is a suspend function `(vm, params) -> JsonElement`. Reads may
 * run on any thread (Compose snapshot reads are thread-safe). Writes are
 * dispatched to the main thread via [runOnMainBlockingWithResult] so all
 * `MapViewModel` mutations happen on the main thread, matching Compose's
 * threading contract.
 *
 * Methods are namespaced: `route.*` and `location.*`. See the locked spec
 * in the implementation plan for the full method catalogue.
 *
 * JSON keys and internal identifiers are exempt from the i18n rule per CLAUDE.md.
 */
class HarnessMethods(private val json: Json) {

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /** Helper: build a [JsonObject] from a mutable map, allowing nullable [JsonElement] values. */
    private fun jsonObject(build: MutableMap<String, JsonElement>.() -> Unit): JsonObject {
        val map = mutableMapOf<String, JsonElement>()
        map.build()
        return JsonObject(map)
    }

    /** Put a nullable [JsonElement] into the map, using [JsonNull] for null values. */
    private fun MutableMap<String, JsonElement>.putJson(key: String, value: JsonElement?) {
        put(key, value ?: JsonNull)
    }

    /** Convenience: put a [Boolean] as a [JsonPrimitive]. */
    private fun MutableMap<String, JsonElement>.put(key: String, value: Boolean) {
        put(key, JsonPrimitive(value))
    }

    /** Convenience: put an [Int] as a [JsonPrimitive]. */
    private fun MutableMap<String, JsonElement>.put(key: String, value: Int) {
        put(key, JsonPrimitive(value))
    }

    /** Convenience: put a [Double] as a [JsonPrimitive]. */
    private fun MutableMap<String, JsonElement>.put(key: String, value: Double) {
        put(key, JsonPrimitive(value))
    }

    /** Dispatch a parsed [HarnessRequest]. Returns a result element or throws a [HarnessError]. */
    suspend fun dispatch(vm: MapViewModel, request: HarnessRequest): JsonElement {
        val handler = handlers[request.method]
            ?: throw HarnessError(request.id, code = 404, message = "unknown method: ${request.method}")
        val params = request.params ?: JsonObject(emptyMap())
        return handler(vm, params)
    }

    /** Runs `block` on the main thread and returns its result. Used for all writes. */
    private fun <T> runOnMainBlockingWithResult(block: () -> T): T {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            return block()
        }
        val resultHolder = arrayOfNulls<Any>(1)
        val errorHolder = arrayOfNulls<Throwable>(1)
        val done = CountDownLatch(1)
        mainHandler.post {
            try {
                resultHolder[0] = block()
            } catch (t: Throwable) {
                errorHolder[0] = t
            } finally {
                done.countDown()
            }
        }
        if (!done.await(5, TimeUnit.SECONDS)) {
            throw HarnessError("main-dispatch", code = 500, message = "timed out waiting for main-thread dispatch")
        }
        errorHolder[0]?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return resultHolder[0] as T
    }

    private val handlers: Map<String, suspend (MapViewModel, JsonElement) -> JsonElement> = buildMap {
        // ---------- route.* (read) ----------

        put("route.getWaypoints") { vm, _ ->
            json.encodeToJsonElement(ListSerializer(Waypoint.serializer()), vm.waypoints.toList())
        }

        put("route.getActiveWaypoint") { vm, _ ->
            val active = vm.activeWaypoint
            jsonObject {
                putJson("waypoint", active?.let { json.encodeToJsonElement(Waypoint.serializer(), it) })
            }
        }

        put("route.get") { vm, _ ->
            jsonObject {
                put("waypoints", json.encodeToJsonElement(ListSerializer(Waypoint.serializer()), vm.waypoints.toList()))
                putJson("activeWaypointId", vm.activeWaypointId?.let { JsonPrimitive(it) })
                put("autoAdvance", jsonObject {
                    put("enabled", vm.autoAdvanceEnabled)
                    put("thresholdMeters", vm.autoAdvanceThresholdMeters)
                })
            }
        }

        // ---------- route.* (write) ----------

        put("route.addWaypoint") { vm, params ->
            val p = json.decodeFromJsonElement<AddWaypointParams>(params)
            val at = p.at.toInsertPosition()
            val newId = runOnMainBlockingWithResult {
                vm.addWaypoint(
                    position = Position(latitude = p.lat, longitude = p.lon),
                    name = p.name,
                    subtitle = p.subtitle,
                    source = p.source ?: WaypointSource.DROPPED_PIN,
                    at = at,
                    iconName = p.iconName,
                )
            }
            val created = runOnMainBlockingWithResult { vm.waypoints.firstOrNull { it.id == newId } }
                ?: throw HarnessError("route.addWaypoint", code = 500, message = "created waypoint $newId not found in waypoints list after add")
            jsonObject {
                put("waypoint", json.encodeToJsonElement(Waypoint.serializer(), created))
            }
        }

        put("route.removeWaypoint") { vm, params ->
            val p = json.decodeFromJsonElement<RemoveWaypointParams>(params)
            val existed = vm.waypoints.any { it.id == p.id }
            if (!existed) throw HarnessError("route.removeWaypoint", code = 404, message = "waypoint not found: id=${p.id}")
            runOnMainBlockingWithResult { vm.removeWaypoint(p.id) }
            jsonObject { put("removed", true) }
        }

        put("route.moveWaypoint") { vm, params ->
            val p = json.decodeFromJsonElement<MoveWaypointParams>(params)
            if (p.fromIndex !in vm.waypoints.indices) {
                throw HarnessError("route.moveWaypoint", code = 400, message = "fromIndex ${p.fromIndex} out of range 0..${vm.waypoints.lastIndex}")
            }
            runOnMainBlockingWithResult { vm.moveWaypoint(p.fromIndex, p.toIndex) }
            jsonObject { put("ok", true) }
        }

        put("route.setActiveWaypoint") { vm, params ->
            val p = json.decodeFromJsonElement<SetActiveWaypointParams>(params)
            if (!vm.waypoints.any { it.id == p.id }) {
                throw HarnessError("route.setActiveWaypoint", code = 404, message = "waypoint not found: id=${p.id}")
            }
            runOnMainBlockingWithResult { vm.setActiveWaypoint(p.id) }
            jsonObject { put("ok", true) }
        }

        put("route.advanceActiveWaypoint") { vm, _ ->
            runOnMainBlockingWithResult { vm.advanceActiveWaypoint() }
            jsonObject {
                put("ok", true)
                putJson("activeWaypointId", vm.activeWaypointId?.let { JsonPrimitive(it) })
            }
        }

        put("route.regressActiveWaypoint") { vm, _ ->
            runOnMainBlockingWithResult { vm.regressActiveWaypoint() }
            jsonObject {
                put("ok", true)
                putJson("activeWaypointId", vm.activeWaypointId?.let { JsonPrimitive(it) })
            }
        }

        put("route.clear") { vm, _ ->
            runOnMainBlockingWithResult { vm.clearRoute() }
            jsonObject { put("ok", true) }
        }

        put("route.setAutoAdvanceEnabled") { vm, params ->
            val p = json.decodeFromJsonElement<SetAutoAdvanceEnabledParams>(params)
            runOnMainBlockingWithResult { vm.updateAutoAdvanceEnabled(p.enabled) }
            jsonObject { put("ok", true) }
        }

        put("route.setAutoAdvanceThreshold") { vm, params ->
            val p = json.decodeFromJsonElement<SetAutoAdvanceThresholdParams>(params)
            runOnMainBlockingWithResult { vm.updateAutoAdvanceThreshold(p.meters) }
            jsonObject {
                put("ok", true)
                put("thresholdMeters", vm.autoAdvanceThresholdMeters)
            }
        }

        // ---------- location.* (read-only — setting location is via `adb emu geo fix`) ----------

        put("location.get") { vm, _ ->
            jsonObject {
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
        }
    }

    // ---------- Param DTOs ----------

    @Serializable
    data class AddWaypointParams(
        val lat: Double,
        val lon: Double,
        val name: String? = null,
        val subtitle: String? = null,
        val source: WaypointSource? = null,
        val iconName: String? = null,
        val at: AtSpec,
    ) {
        @Serializable
        data class AtSpec(
            val type: String,
            val i: Int? = null,
            val id: String? = null,
        ) {
            fun toInsertPosition(): InsertPosition = when (type) {
                "start" -> InsertPosition.Start
                "end" -> InsertPosition.End
                "beforeLast" -> InsertPosition.BeforeLast
                "index" -> InsertPosition.Index(
                    i ?: throw HarnessError("route.addWaypoint", code = 400, message = "at.type=index requires at.i"),
                )
                "replaceId" -> InsertPosition.ReplaceId(
                    id ?: throw HarnessError("route.addWaypoint", code = 400, message = "at.type=replaceId requires at.id"),
                )
                else -> throw HarnessError("route.addWaypoint", code = 400, message = "unknown at.type: $type")
            }
        }
    }

    @Serializable
    data class RemoveWaypointParams(val id: String)

    @Serializable
    data class MoveWaypointParams(val fromIndex: Int, val toIndex: Int)

    @Serializable
    data class SetActiveWaypointParams(val id: String)

    @Serializable
    data class SetAutoAdvanceEnabledParams(val enabled: Boolean)

    @Serializable
    data class SetAutoAdvanceThresholdParams(val meters: Int)
}