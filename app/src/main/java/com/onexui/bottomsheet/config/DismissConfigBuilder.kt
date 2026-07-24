package com.onexui.bottomsheet.config

@XBottomSheetDsl
internal class DismissConfigBuilder {
    var isOutsideTapEnabled: Boolean = true
    var isSwipeDownEnabled: Boolean = true

    var isBackPressEnabled: Boolean = false

    internal fun build(): DismissConfig =
        DismissConfig(isOutsideTapEnabled = isOutsideTapEnabled, isSwipeDownEnabled = isSwipeDownEnabled, isBackPressEnabled = isBackPressEnabled)
}
