package com.onexui.bottomsheet.config

import androidx.compose.runtime.Immutable

/** Конфиг поведеня листа при клавиатуре: режим bottom-слота (Lift / StayUnderKeyboard). */
@Immutable
internal class KeyboardConfig(
    val bottomBehavior: BottomKeyboardBehavior,
)
