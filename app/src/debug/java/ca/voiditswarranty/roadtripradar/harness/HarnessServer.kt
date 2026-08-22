package ca.voiditswarranty.roadtripradar.harness

import ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel
import fi.iki.elonen.NanoWSD
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD.WebSocket
import fi.iki.elonen.NanoWSD.WebSocketFrame
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The dev-only test harness server. Extends [NanoWSD] so it handles both plain
 * HTTP requests (for `GET /health` liveness probing) and WebSocket upgrades
 * (for the bidirectional harness API). Binds to `127.0.0.1:port` so it is only
 * reachable via `adb forward` from the host — never exposed on a network.
 *
 * One [HarnessServer] instance is created per Activity session by
 * [HarnessBootstrap] and torn down in the same composable's `onDispose`.
 *
 * JSON keys and internal identifiers are exempt from the i18n rule per CLAUDE.md.
 */
class HarnessServer(
    private val vm: MapViewModel,
    port: Int,
) : NanoWSD("127.0.0.1", port) {

    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }
    private val methods = HarnessMethods(json)

    /** Live WebSocket sessions, used to broadcast [HarnessEvent]s. */
    private val sessions = CopyOnWriteArrayList<WebSocket>()
    private val eventEmitter = HarnessEventEmitter(vm, json, ::broadcast)

    override fun serveHttp(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        // Only /health is handled here for plain HTTP. WebSocket upgrades are
        // handled by NanoWSD.serve() before this is reached.
        val uri = session.uri
        if (uri == "/health") {
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, "ok\n")
        }
        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "not found: $uri\n")
    }

    override fun openWebSocket(handshake: NanoHTTPD.IHTTPSession): WebSocket {
        lateinit var ws: HarnessWebSocket
        ws = HarnessWebSocket(
            handshake = handshake,
            vm = vm,
            methods = methods,
            json = json,
            onCloseCallback = { sessionClosed(ws) },
        )
        sessions.add(ws)
        if (sessions.size == 1) eventEmitter.start()
        return ws
    }

    /** Broadcast a serialized event JSON element to all live sessions. */
    private fun broadcast(element: JsonElement) {
        val text = json.encodeToString(JsonElement.serializer(), element)
        for (ws in sessions) {
            try {
                ws.send(text)
            } catch (_: Exception) {
                // Socket is closed; it will be removed in onClose.
            }
        }
    }

    /** Removes a closed session from the live set and stops the emitter when the last disconnects. */
    internal fun sessionClosed(ws: WebSocket) {
        sessions.remove(ws)
        if (sessions.isEmpty()) eventEmitter.stop()
    }

    override fun stop() {
        super.stop()
        eventEmitter.shutdown()
        sessions.clear()
    }
}