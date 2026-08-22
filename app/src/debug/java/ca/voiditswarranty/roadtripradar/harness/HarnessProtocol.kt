package ca.voiditswarranty.roadtripradar.harness

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Wire protocol envelopes for the dev-only test harness WebSocket.
 *
 * One JSON object per WebSocket message. Three envelope kinds:
 *
 *  - [HarnessRequest]     — client → server. `id` is client-chosen and echoed
 *    in the matching [HarnessResponse] / [HarnessError].
 *  - [HarnessResponse] / [HarnessError] — server → client, matched to a
 *    request by `id`.
 *  - [HarnessEvent]      — server → client, unsolicited (no `id`). Sent whenever
 *    observable ViewModel state changes (throttled/debounced by [HarnessEventEmitter]).
 *
 * Methods are namespaced (`route.get`, `location.get`, …). Errors use HTTP-style
 * integer `code`s with verbose `message`s for harness-side debugging.
 *
 * JSON keys and internal identifiers are exempt from the i18n rule per CLAUDE.md.
 */
@Serializable
data class HarnessRequest(
    val id: String,
    val type: String = "request",
    val method: String,
    val params: JsonElement? = null,
)

@Serializable
data class HarnessResponse(
    val id: String,
    val type: String = "response",
    val result: JsonElement,
)

@Serializable
data class HarnessErrorEnvelope(
    val id: String,
    val type: String = "error",
    val code: Int,
    val message: String,
)

@Serializable
data class HarnessEvent(
    val type: String = "event",
    val event: String,
    val data: JsonElement,
)

/**
 * Thrown by method handlers to signal a non-200 outcome. Caught by the
 * WebSocket session, serialized to a [HarnessErrorEnvelope], and sent back to
 * the client matched by `id`.
 *
 * Extends [Exception] so it's a regular [Throwable] and can be thrown from
 * suspend functions without a `@Throws` annotation. The [id] is the request
 * id (or a synthetic id for errors detected before parsing completes).
 */
class HarnessError(
    val id: String,
    val code: Int,
    message: String,
) : Exception(message)