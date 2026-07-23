package com.onexui.bottomsheet.config

@XBottomSheetDsl
internal class KeyboardConfigBuilder {
    var bottomBehavior: BottomKeyboardBehavior = BottomKeyboardBehavior.Lift

    internal fun build(): KeyboardConfig = KeyboardConfig(bottomBehavior = bottomBehavior)
}
