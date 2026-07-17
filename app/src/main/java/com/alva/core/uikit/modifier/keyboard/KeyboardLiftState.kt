package com.alva.core.uikit.modifier.keyboard

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity

// Состояние клавиатуры платформы: поднята ли она и её высота в пикселях (0f если скрыта).
data class KeyboardLiftState(
    val isKeyboardVisible: Boolean,
    val keyboardHeight: Float,
)

// Реактивный источник состояния клавиатуры на основе WindowInsets.ime (Compose отдаёт IME-инсеты
// покадрово во время анимации показа/скрытия). Порт AlvaBottomSheet читает isKeyboardVisible, чтобы back
// сначала прятал клавиатуру, а уже потом запрашивал закрытие листа.
@Composable
fun rememberKeyboardLiftState(): State<KeyboardLiftState> {
    val ime = WindowInsets.ime
    val density = LocalDensity.current
    return remember(ime, density) {
        derivedStateOf {
            val heightPx = ime.getBottom(density)
            KeyboardLiftState(
                isKeyboardVisible = heightPx > 0,
                keyboardHeight = heightPx.toFloat(),
            )
        }
    }
}
