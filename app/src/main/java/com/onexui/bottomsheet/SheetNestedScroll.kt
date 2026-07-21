package com.onexui.bottomsheet

import androidx.compose.runtime.State
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

// Связка скролла Middle с высотой листа (§5).
// Жест вверх: в Collapsed — СНАЧАЛА разворачиваем лист (растим высоту), ЗАТЕМ скроллим список.
// Жест вниз: пока список не в начале — доскролл к началу (лист не трогаем); в начале — тянем лист вниз.
// Мутации offset идут только через FIFO-канал стейта (enqueueDrag/enqueueSettle) — один потребитель применяет
// команды по порядку, без гонок drag↔settle (см. XBottomSheetState.processGestures).
// enabledState — ТОТ ЖЕ derivedStateOf, что гейтит sheetDrag (единый источник доступности жестов): читается в
// колбэках (не снапшот-наблюдатели → ничего не инвалидирует). state — единственная конструктор-зависимость →
// connection один инстанс на лист (remember(state), не пересоздаётся на show/hide IME и конец Loading).
// §6 п.4: при видимой IME доступность=false, nested-scroll двигает только список.
internal class SheetNestedScrollConnection(
    private val state: XBottomSheetState,
    private val enabledState: State<Boolean>,
) : NestedScrollConnection {

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (!enabledState.value || source != NestedScrollSource.UserInput) return Offset.Zero
        val delta = available.y
        val metrics = state.metrics ?: return Offset.Zero
        // Вверх (delta<0) в Collapsed растим лист ДО якоря expandTarget (ExpandedContent/FullScreen), не до
        // потолка — иначе в b1 (контент ≤ экрана) жест утаскивал бы лист мимо ExpandedContent к Status Bar.
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
        // Гейт ТОЛЬКО по isDragging: раз драг стартовал — взаимодействие было доступно, и долёт-settle обязан
        // завершиться, даже если доступность сменилась в момент броска (напр. IME всплыла). Не начатый драг
        // (доступность была false в onPreScroll/onPostScroll) сюда не даёт isDragging=true → вернём Zero.
        if (!state.isDragging) return Velocity.Zero
        // Составной жест: лист уже дорос до rest-якоря, палец дальше скроллил список → НЕ съедаем скорость, чтобы
        // инерция списка (fling) продолжилась. settle(0) сбросит isDragging/overshoot, но лист уже на якоре — не двинет.
        if (state.isOffsetAtRestAnchor()) {
            state.enqueueSettle(0f)
            return Velocity.Zero
        }
        state.enqueueSettle(available.y)
        return available.copy(x = 0f)
    }
}
