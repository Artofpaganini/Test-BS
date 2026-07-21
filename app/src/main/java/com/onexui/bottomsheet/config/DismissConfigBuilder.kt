package com.onexui.bottomsheet.config

@XBottomSheetDsl
internal class DismissConfigBuilder {
    var onOutsideTap: Boolean = true
    var onSwipeDown: Boolean = true
    // Дефолт false → back остаётся у хоста (19→21 кейсов 1:1); BackHandler(enabled=false) пропускает диспетчер.
    var onBackPress: Boolean = false
    internal fun build(): DismissConfig =
        DismissConfig(onOutsideTap = onOutsideTap, onSwipeDown = onSwipeDown, onBackPress = onBackPress)
}
