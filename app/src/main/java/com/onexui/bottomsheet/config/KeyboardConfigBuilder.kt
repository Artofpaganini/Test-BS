package com.onexui.bottomsheet.config

/** DSL-билдер конфига поведения листа при клавиатуе. */
@XBottomSheetDsl
internal class KeyboardConfigBuilder {
    var bottomBehavior: BottomKeyboardBehavior = BottomKeyboardBehavior.Lift
    internal fun build(): KeyboardConfig = KeyboardConfig(bottomBehavior = bottomBehavior)
}
