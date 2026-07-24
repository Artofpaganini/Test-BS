package com.onexui.bottomsheet.gesture

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.onexui.bottomsheet.state.XBottomSheetState

@Composable
internal fun Modifier.sheetDrag(
    state: XBottomSheetState,
    isEnabled: Boolean,
): Modifier {
    val draggableState = rememberDraggableState { delta ->
        state.enqueueDrag(-delta)
    }
    return this.draggable(
        state = draggableState,
        orientation = Orientation.Vertical,
        enabled = isEnabled,
        onDragStarted = { state.markDragStarted() },
        onDragStopped = { velocity -> state.enqueueSettle(velocity) },
    )
}
