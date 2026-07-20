package ca.voiditswarranty.roadtripradar.harness

import ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.IOException

/**
 * Per-connection WebSocket handler for the harness API.
 *
 * One [HarnessWebSocket] is created per upgraded WebSocket connection by
 * [HarnessServer.openWebSocket]. It owns a coroutine scope for running the
 * suspend method handlers. On `onMessage`: parse the text frame as a
 * [HarnessRequest], dispatch it via [HarnessMethods.dispatch], and send back
 * either a [HarnessResponse] or an [HarnessError] matched by `id`.
 *
 * JSON keys and internal identifiers are exempt from the i18n rule per CLAUDE.md.
 */
class HarnessWebSocket(
    handshake: NanoHTTPD.IHTTPSession,
    private val vm: MapViewModel,
    private val methods: HarnessMethods,
    private val json: Json,
    private val onCloseCallback: () -> Unit,
) : NanoWSD.WebSocket(handshake) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onOpen() {
        // No-op — the session is request/response; no handshake payload.
    }

    override fun onClose(code: NanoWSD.WebSocketFrame.CloseCode, reason: String, initiatedByRemote: Boolean) {
        scope.cancel()
        onCloseCallback()
    }

    override fun onMessage(message: NanoWSD.WebSocketFrame) {
        val raw = message.textPayload
        val request = try {
            json.decodeFromString(HarnessRequest.serializer(), raw)
        } catch (e: Exception) {
            sendError("parse", code = 400, message = "failed to parse request JSON: ${e.message} (raw=$raw)")
            return
        }
        scope.launch {
            try {
                val result = methods.dispatch(vm, request)
                sendResponse(request.id, result)
            } catch (e: HarnessError) {
                sendError(request.id, code = e.code, message = e.message ?: "(no message)")
            } catch (e: Exception) {
                sendError(request.id, code = 500, message = "internal error: ${e::class.java.simpleName}: ${e.message ?: "(no message)"}")
            }
        }
    }

    override fun onPong(pong: NanoWSD.WebSocketFrame) {
        // No-op
    }

    override fun onException(exception: IOException) {
        // Swallow — the socket is dead; the server will clean up via onClose.
    }

    private fun sendResponse(id: String, result: JsonElement) {
        val response = HarnessResponse(id = id, result = result)
        val text = json.encodeToString(HarnessResponse.serializer(), response)
        try {
            send(text)
        } catch (_: Exception) {
            // Socket is closed; drop the response.
        }
    }

    private fun sendError(id: String, code: Int, message: String) {
        val error = HarnessErrorEnvelope(id = id, code = code, message = message)
        val text = json.encodeToString(HarnessErrorEnvelope.serializer(), error)
        try {
            send(text)
        } catch (_: Exception) {
            // Socket is closed; drop the error.
        }
    }
}