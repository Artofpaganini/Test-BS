package com.onexui.bottomsheet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import org.xplatform.uikit.compose.modifier.keyboard.lift.KeyboardLiftState
import kotlin.math.roundToInt

// Реакция листа на клавиатуру (§6). Подъём над IME и сжатие Middle делают xbet-модификаторы
// (withAdjustmentForKeyboard / withKeyboardShrink) на основе KeyboardLiftState. Здесь — логика авто-разворота:
// при показе IME, если места на подъём не хватает (§6), стейт уходит в ExpandedFullScreen; при СКРЫТИИ IME
// авто-промоушен откатывается (уточнение юзера, §6). onImeShown/onImeHidden идемпотентны, серия событий IME
// безопасна.
@Composable
internal fun SheetKeyboardAutoFullScreenEffect(
    state: XBottomSheetState,
    keyboardState: State<KeyboardLiftState>,
) {
    LaunchedEffect(state, keyboardState) {
        snapshotFlow { keyboardState.value }
            .distinctUntilChanged()
            .collect { lift ->
                if (lift.isKeyboardVisible) {
                    state.onImeShown(lift.keyboardHeight.roundToInt())
                } else {
                    state.onImeHidden()
                }
            }
    }
}
