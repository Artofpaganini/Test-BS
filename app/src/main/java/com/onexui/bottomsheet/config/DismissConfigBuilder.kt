package com.onexui.bottomsheet.config

@XBottomSheetDsl
internal class DismissConfigBuilder {
    var onOutsideTap: Boolean = true
    var onSwipeDown: Boolean = true

    var onBackPress: Boolean = false

    internal fun build(): DismissConfig =
        DismissConfig(onOutsideTap = onOutsideTap, onSwipeDown = onSwipeDown, onBackPress = onBackPress)
}
