package com.onexui.bottomsheet.config

// StayUnderKeyboard: над клавиатурой поднимаются top+middle, bottom прижат к нижней кромке и уходит ПОД
// клавиатуру; при показе IME лист форсированно разворачивается в FullScreen. Lift: поднимается весь контент.
internal enum class BottomKeyboardBehavior { Lift, StayUnderKeyboard }
