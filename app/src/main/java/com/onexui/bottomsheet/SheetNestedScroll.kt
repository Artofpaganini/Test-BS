package com.onexui.bottomsheet

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// Связка скролла Middle с высотой листа (§5).
// Жест вверх: в Collapsed — СНАЧАЛА разворачиваем лист (растим высоту), ЗАТЕМ скроллим список.
// Жест вниз: пока список не в начале — доскролл к началу (лист не трогаем); в начале — тянем лист вниз.
// Мутации offset идут только через suspend-методы стейта (dragBy/settle), запускаемые на scope листа.
internal class SheetNestedScrollConnection(
    private val state: XBottomSheetState,
    private val scope: CoroutineScope,
    private val interactionsEnabled: Boolean,
    private val dismissOnSwipeDown: Boolean,
    private val onDismissRequest: () -> Unit,
) : NestedScrollConnection {

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (!interactionsEnabled || source != NestedScrollSource.UserInput) return Offset.Zero
        val delta = available.y
        val metrics = state.metrics ?: return Offset.Zero
        // Вверх (delta<0) в Collapsed растим лист ДО якоря expandTarget (ExpandedContent/FullScreen), не до
        // потолка — иначе в b1 (контент ≤ экрана) жест утаскивал бы лист мимо ExpandedContent к Status Bar.
        val expandAnchor = metrics.anchorPx(metrics.expandTarget(), state.skipCollapsed)
        return if (delta < 0f && state.currentValue == SheetValue.Collapsed &&
            state.offset.value < expandAnchor
        ) {
            state.isDragging = true
            scope.launch { state.dragBy(-delta) }
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
        if (!interactionsEnabled || source != NestedScrollSource.UserInput) return Offset.Zero
        val delta = available.y
        // Вниз (delta>0) — список уже в начале, остаток тянет лист вниз.
        return if (delta > 0f) {
            state.isDragging = true
            scope.launch { state.dragBy(-delta) }
            Offset(x = 0f, y = delta)
        } else {
            Offset.Zero
        }
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (!interactionsEnabled || !state.isDragging) return Velocity.Zero
        state.settle(
            velocity = available.y,
            dismissOnSwipeDown = dismissOnSwipeDown,
            onDismissRequest = onDismissRequest,
        )
        return available.copy(x = 0f)
    }
}
