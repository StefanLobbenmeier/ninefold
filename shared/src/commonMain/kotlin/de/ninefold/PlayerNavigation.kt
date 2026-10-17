package de.ninefold

import androidx.compose.runtime.Composable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

@Composable
internal expect fun PlayerBackHandler(onBack: () -> Unit)

internal fun Modifier.edgeSwipeBack(onBack: () -> Unit) = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (down.position.x < 12 * density) {
            var dx = 0f
            var dy = 0f
            var dragging = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                dx = change.position.x - down.position.x
                dy = change.position.y - down.position.y
                if (dx > viewConfiguration.touchSlop && dx > abs(dy) * 1.5f) dragging = true
                if (dragging) change.consume()
            } while (change.pressed)
            if (dragging && dx > 80 * density && dx > abs(dy) * 1.5f) onBack()
        }
    }
}
