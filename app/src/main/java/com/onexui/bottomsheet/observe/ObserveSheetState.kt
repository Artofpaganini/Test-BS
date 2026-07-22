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

// Единая точка реактивных snapshotFlow-наблюдателей листа. Четыре дочерних коллектора (каждый переживает отмену
// соседа):
//   1. Рост/уменьшение контента → высота следует. Каждый пересчёт — в отдельном launch: его offset.animateTo
//      прерывается конкурирующей анимацией (show/settle) CancellationException'ом, без launch это убило бы
//      коллектор (после первого прерывания рост перестал бы отслеживаться).
//   2. Клавиатура → авто-FullScreen при нехватке места (или всегда, для StayUnderKeyboard) / откат при скрытии.
//   3. Hidden → onSheetHidden (принудительный дроп IME).
//   4. Живые поля стейта (skipCollapsed/peekFraction/anchors) → пере-резолв метрик+якорей и доводка высоты у
//      покоящегося листа. Тот же приём отдельного launch, что и #1 (конкурирующая анимация не убивает коллектор).
// snapToCurrentAnchor (поворот) — НЕ здесь: one-shot по размерам экрана, не snapshotFlow.
@Composable
internal fun ObserveSheetState(
    state: XBottomSheetState,
    keyboardState: State<KeyboardLiftState>,
    onSheetHidden: () -> Unit,
) {
    LaunchedEffect(state, keyboardState) {
        launch {
            snapshotFlow { state.metrics?.contentHeightPx }
                .filterNotNull()
                .collect { launch { state.onContentRemeasured() } }
        }
        launch {
            // Ключ (lift, isLoading): при снятии Loading под открытой IME (keyboardState не менялся) промоушен
            // переоценивается заново — иначе Collapsed под видимой IME увёл бы верх листа за статус-бар (без
            // авто-FullScreen). onImeShown идемпотентен (гард по стейтам); высоту/флаг стейт читает live из
            // keyboardState (см. XBottomSheetState.shouldPromoteForIme).
            snapshotFlow { keyboardState.value to state.isLoading }
                .distinctUntilChanged()
                .collect { (lift, _) ->
                    if (lift.isKeyboardVisible) state.onImeShown() else state.onImeHidden()
                }
        }
        launch {
            // drop(1): пропускаем стартовую эмиссию, иначе скрытый на старте лист сразу дёрнул бы onSheetHidden
            // (дроп чужого фокуса/IME). Реагируем только на переход visible → hidden.
            snapshotFlow { state.isVisible }
                .drop(1)
                .collect { visible -> if (!visible) onSheetHidden() }
        }
        launch {
            // drop(1): стартовую вариацию (её задал билдер) не пере-резолвим — лист ещё не показан. Реагируем
            // на живые смены. Отдельный launch на эмиссию — как в #1.
            snapshotFlow { Triple(state.skipCollapsed, state.peekFraction, state.anchors) }
                .drop(1)
                .distinctUntilChanged()
                .collect { launch { state.onLiveConfigChanged() } }
        }
    }
}
