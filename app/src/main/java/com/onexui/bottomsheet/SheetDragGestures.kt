package com.onexui.bottomsheet

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlin.math.exp

// Сопротивление overshoot (§5): max * (1 - exp(-raw/max)). Применяется при перетяге ВВЕРХ над верхним якорем —
// в Content и ExpandedContent (тянет к потолку) И в ExpandedFullScreen (rubber-band в зону статус-бара, spring
// назад). В Collapsed НЕ применяется. См. XBottomSheetState.dragBy.
internal fun resistedOvershoot(accumulatedOvershootPx: Float, maxOvershootPx: Float): Float =
    maxOvershootPx * (1f - exp(-accumulatedOvershootPx / maxOvershootPx))

// Драг листа за неподвижные области (хендл / top / bottom): вертикальный жест двигает высоту.
// Скролл-область Middle обслуживается nested-scroll'ом; сюда попадают жесты вне списка. Знак: вниз (delta>0)
// уменьшает высоту (enqueueDrag(-delta)); velocity наследует то же соглашение (вниз>0 → collapse/dismiss).
// Драг и settle кладутся в FIFO-канал стейта (enqueueDrag/enqueueSettle) — один потребитель применяет их по
// порядку, поэтому хвостовой drag не может отменить settle-анимацию (см. XBottomSheetState.processGestures).
@Composable
internal fun Modifier.sheetDrag(
    state: XBottomSheetState,
    enabled: Boolean,
): Modifier {
    val draggableState = rememberDraggableState { delta ->
        state.enqueueDrag(-delta)
    }
    return this.draggable(
        state = draggableState,
        orientation = Orientation.Vertical,
        enabled = enabled,
        onDragStarted = { state.isDragging = true },
        onDragStopped = { velocity -> state.enqueueSettle(velocity) },
    )
}
