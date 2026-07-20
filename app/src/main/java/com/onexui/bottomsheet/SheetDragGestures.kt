package com.onexui.bottomsheet

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.exp

// Сопротивление overshoot (§5): max * (1 - exp(-raw/max)). Применяется ТОЛЬКО в Content и ExpandedContent
// (используется в XBottomSheetState.resolveDragTarget). В Collapsed и FullScreen НЕ применяется.
internal fun resistedOvershoot(rawOvershootPx: Float, maxOvershootPx: Float): Float =
    maxOvershootPx * (1f - exp(-rawOvershootPx / maxOvershootPx))

// Драг листа за неподвижные области (хендл / top / bottom): вертикальный жест двигает высоту.
// Скролл-область Middle обслуживается nested-scroll'ом; сюда попадают жесты вне списка. Знак: вниз (delta>0)
// уменьшает высоту (dragBy(-delta)); velocity наследует то же соглашение (вниз>0 → collapse/dismiss).
@Composable
internal fun Modifier.sheetDrag(
    state: XBottomSheetState,
    enabled: Boolean,
    scope: CoroutineScope,
    dismissOnSwipeDown: Boolean,
    onDismissRequest: () -> Unit,
): Modifier {
    val draggableState = rememberDraggableState { delta ->
        scope.launch { state.dragBy(-delta, dismissOnSwipeDown) }
    }
    return this.draggable(
        state = draggableState,
        orientation = Orientation.Vertical,
        enabled = enabled,
        onDragStarted = { state.isDragging = true },
        onDragStopped = { velocity ->
            state.settle(
                velocity = velocity,
                dismissOnSwipeDown = dismissOnSwipeDown,
                onDismissRequest = onDismissRequest,
            )
        },
    )
}
