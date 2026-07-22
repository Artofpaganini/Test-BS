package com.onexui.bottomsheet.config

/** DSL-билдер способв закрытия листа. */
@XBottomSheetDsl
internal class DismissConfigBuilder {
    var onOutsideTap: Boolean = true
    var onSwipeDown: Boolean = true

    // Дефолт false -> back остаётся у хоста; BackHandler(enabled=false) пропускает событие диспетчеру.
    var onBackPress: Boolean = false
    internal fun build(): DismissConfig =
        DismissConfig(onOutsideTap = onOutsideTap, onSwipeDown = onSwipeDown, onBackPress = onBackPress)
}
