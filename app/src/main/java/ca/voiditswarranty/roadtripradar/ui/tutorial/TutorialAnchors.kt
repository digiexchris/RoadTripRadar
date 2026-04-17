package ca.voiditswarranty.roadtripradar.ui.tutorial

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot

/**
 * Holds the current on-screen bounds (in the coordinate space of the root layout that
 * also hosts the [TutorialOverlay]) of every registered anchor. Populated by
 * [Modifier.tutorialAnchor] via `onGloballyPositioned`.
 */
class TutorialAnchorsState {
    private val bounds = mutableStateMapOf<String, Rect>()

    fun register(id: String, rect: Rect) {
        bounds[id] = rect
    }

    fun unregister(id: String) {
        bounds.remove(id)
    }

    fun get(id: String?): Rect? = id?.let { bounds[it] }
}

val LocalTutorialAnchors = compositionLocalOf<TutorialAnchorsState?> { null }

@Composable
fun rememberTutorialAnchorsState(): TutorialAnchorsState = remember { TutorialAnchorsState() }

/**
 * Reports this node's bounds to the nearest [LocalTutorialAnchors] under the given [id].
 * Safe to use when no provider is present (no-op).
 */
fun Modifier.tutorialAnchor(id: String): Modifier = composed {
    val state = LocalTutorialAnchors.current ?: return@composed this
    var lastRect by remember(id) { mutableStateOf<Rect?>(null) }
    DisposableEffect(state, id) {
        onDispose { state.unregister(id) }
    }
    this.onGloballyPositioned { coords ->
        val pos = coords.positionInRoot()
        val size = coords.size
        val rect = Rect(
            left = pos.x,
            top = pos.y,
            right = pos.x + size.width,
            bottom = pos.y + size.height,
        )
        if (rect != lastRect) {
            lastRect = rect
            state.register(id, rect)
        }
    }
}
