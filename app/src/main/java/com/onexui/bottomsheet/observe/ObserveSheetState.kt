package com.onexui.bottomsheet.observe

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.snapshotFlow
import com.onexui.bottomsheet.state.XBottomSheetState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.xplatform.uikit.compose.modifier.keyboard.lift.KeyboardLiftState

/**
 * Единая точка реактивных snapshotFlow-наблюдателей листа. Четыре дочерних коллектора (каждый переживает отмену соседа):
 * 1. Рост/уменьшение контена -> высота следует (каждый пересчёт в отдельном launch, см. ниже).
 * 2. Клавиатура -> авто-FullScreen при нехватке места (или всегда для StayUnderKeyboard) / откат при скрытии.
 * 3. Hidden -> onSheetHidden (принудительный дроп IME).
 * 4. Живые поля (skipCollapsed/peekFraction/anchors) -> пере-резолв метрик+якорей и доводка высоты у покоящегося листа.
 *
 * snapToCurrentAnchor (поворот) — НЕ здесь: one-shot по размерам экрана, не snapshotFlow.
 */
@Composable
internal fun ObserveSheetState(
    state: XBottomSheetState,
    keyboardState: State<KeyboardLiftState>,
    onSheetHidden: () -> Unit,
) {
    LaunchedEffect(state, keyboardState) {
        launch {
            // Отдельный launch на эмиссию: конкурирующая анимация (show/settle) прервёт animateTo, не убив коллектор.
            snapshotFlow { state.metrics?.contentHeightPx }
                .filterNotNull()
                .collect { launch { state.onContentRemeasured() } }
        }
        launch {
            // Ключ (lift, isLoading): при снятии Loading под открытой IME промоушен переоценивается заново (иначе Collapsed увёл бы верх за статус-бар).
            // onImeShown идемпотентен (гард по стейтам).
            snapshotFlow { keyboardState.value to state.isLoading }
                .distinctUntilChanged()
                .collect { (lift, _) ->
                    if (lift.isKeyboardVisible) state.onImeShown() else state.onImeHidden()
                }
        }
        launch {
            // drop(1): стартовую эмиссию пропускаем (скрытый на старте лист не должен дёргать onSheetHidden); ловим переход visible -> hidden.
            snapshotFlow { state.isVisible }
                .drop(1)
                .collect { visible -> if (!visible) onSheetHidden() }
        }
        launch {
            // drop(1): стартовую вариацию (от билдера) не пере-резолвим; отдельный launch на эмиссию — как в #1.
            snapshotFlow { Triple(state.skipCollapsed, state.peekFraction, state.anchors) }
                .drop(1)
                .distinctUntilChanged()
                .collect { launch { state.onLiveConfigChanged() } }
        }
    }
}
