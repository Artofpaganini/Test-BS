package com.onexui.bottomsheet.gesture

import androidx.compose.runtime.State
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import com.onexui.bottomsheet.state.SheetValue
import com.onexui.bottomsheet.state.XBottomSheetState
import com.onexui.bottomsheet.state.anchorPx
import com.onexui.bottomsheet.state.expandTarget

internal class SheetNestedScrollConnection(
    private val state: XBottomSheetState,
    private val enabledState: State<Boolean>,
) : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (!enabledState.value || source != NestedScrollSource.UserInput) return Offset.Zero
        val delta = available.y
        val metrics = state.metrics ?: return Offset.Zero
        val expandAnchor = metrics.anchorPx(metrics.expandTarget(), state.skipCollapsed)
        return if (delta < 0f && state.currentValue == SheetValue.Collapsed &&
            state.offset.value < expandAnchor
        ) {
            state.enqueueDrag(-delta)
            Offset(x = 0f, y = delta)
        } else {
            Offset.Zero
        }
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (!enabledState.value || source != NestedScrollSource.UserInput) return Offset.Zero
        val delta = available.y
        return if (delta > 0f) {
            state.enqueueDrag(-delta)
            Offset(x = 0f, y = delta)
        } else {
            Offset.Zero
        }
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (!state.isDragging) return Velocity.Zero
        if (state.isOffsetAtRestAnchor()) {
            state.enqueueSettle(0f)
            return Velocity.Zero
        }
        state.enqueueSettle(available.y)
        return available.copy(x = 0f)
    }
}
