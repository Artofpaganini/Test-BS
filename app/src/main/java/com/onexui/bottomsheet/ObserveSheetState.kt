package com.onexui.bottomsheet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.xplatform.uikit.compose.modifier.keyboard.lift.KeyboardLiftState
import kotlin.math.roundToInt

// Единая точка всех РЕАКТИВНЫХ snapshotFlow-наблюдателей листа — вместо разрозненных LaunchedEffect'ов по
// XBottomSheet. Один LaunchedEffect держит три дочерних коллектора (каждый переживает отмену соседа):
//   1. Рост/уменьшение контента → высота следует за контентом. Каждый пересчёт — в отдельном launch: его
//      offset.animateTo прерывается конкурирующей анимацией (show/settle) CancellationException'ом, и без
//      launch это отменило бы сам коллектор (он умер бы после первого прерывания).
//   2. Клавиатура (§6): показ IME → авто-FullScreen при нехватке места (alwaysFullScreen — всегда, для
//      StayUnderKeyboard); скрытие IME → откат авто-промоушена. onImeShown/onImeHidden идемпотентны.
//   3. Лист ушёл в Hidden → onSheetHidden (принудительный дроп клавиатуры, §6 усиление юзера).
// snapToCurrentAnchor (поворот/resize) НЕ здесь: это one-shot по screenHeightPx/statusBarPx, не snapshotFlow —
// иначе смена размеров пере-подписывала бы все коллекторы.
@Composable
internal fun ObserveSheetState(
    state: XBottomSheetState,
    keyboardState: State<KeyboardLiftState>,
    alwaysFullScreenOnIme: Boolean,
    onSheetHidden: () -> Unit,
) {
    LaunchedEffect(state, keyboardState, alwaysFullScreenOnIme) {
        launch {
            snapshotFlow { state.metrics?.contentHeightPx }
                .filterNotNull()
                .collect { launch { state.onContentRemeasured() } }
        }
        launch {
            snapshotFlow { keyboardState.value }
                .distinctUntilChanged()
                .collect { lift ->
                    if (lift.isKeyboardVisible) {
                        state.onImeShown(lift.keyboardHeight.roundToInt(), alwaysFullScreenOnIme)
                    } else {
                        state.onImeHidden()
                    }
                }
        }
        launch {
            // drop(1): пропускаем стартовую эмиссию — иначе смонтированный СКРЫТЫЙ лист сразу дёрнул бы
            // onSheetHidden (дроп чужого фокуса/IME). Реагируем ТОЛЬКО на реальный переход visible → hidden.
            snapshotFlow { state.isVisible }
                .drop(1)
                .collect { visible -> if (!visible) onSheetHidden() }
        }
    }
}
