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

// Связка скролла Middle с высотой листа. Вверх в Collapsed — сначала разворачиваем лист, затем скроллим; вниз —
// сначала доскролл к началу, затем тянем лист. Мутации offset — только через FIFO-канал (enqueueDrag/Settle).
// enabledState — тот же derivedStateOf, что гейтит sheetDrag; при видимой IME он false (nested-scroll двигает
// только список).
internal class SheetNestedScrollConnection(
    private val state: XBottomSheetState,
    private val enabledState: State<Boolean>,
) : NestedScrollConnection {

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (!enabledState.value || source != NestedScrollSource.UserInput) return Offset.Zero
        val delta = available.y
        val metrics = state.metrics ?: return Offset.Zero
        // Вверх (delta<0) в Collapsed растим лист до якоря expandTarget (не до потолка), иначе жест утащил бы
        // мимо ExpandedContent к Status Bar.
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
        // Вниз (delta>0) — список уже в начале, остаток тянет лист вниз.
        return if (delta > 0f) {
            state.enqueueDrag(-delta)
            Offset(x = 0f, y = delta)
        } else {
            Offset.Zero
        }
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        // Гейт по isDragging: раз драг стартовал, settle обязан завершиться, даже если доступность сменилась в
        // момент броска (напр. всплыла IME). Не начатый драг сюда не даёт isDragging=true → вернём Zero.
        if (!state.isDragging) return Velocity.Zero
        // Лист уже на rest-якоре, палец дальше скроллил список → НЕ съедаем скорость (инерция списка продолжится);
        // settle(0) лишь сбросит isDragging/overshoot, но лист уже на якоре — не двинет.
        if (state.isOffsetAtRestAnchor()) {
            state.enqueueSettle(0f)
            return Velocity.Zero
        }
        state.enqueueSettle(available.y)
        return available.copy(x = 0f)
    }
}
