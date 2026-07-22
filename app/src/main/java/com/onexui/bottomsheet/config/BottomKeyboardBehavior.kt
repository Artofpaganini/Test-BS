package com.onexui.bottomsheet.config

/**
 * Поведение bottom-слота при показе клавиатры. Lift — поднимается весь контент. StayUnderKeyboard — top+middle
 * поднимаются, bottom прижат к нижней кромке и уходит ПОД клавиатуру, лист форсированно разворачивается в FullScreen.
 */
internal enum class BottomKeyboardBehavior { Lift, StayUnderKeyboard }
