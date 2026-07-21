package com.onexui.bottomsheet.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable

@Composable
internal fun rememberXBottomSheetState(
    configure: XBottomSheetStateConfigBuilder.() -> Unit = {},
): XBottomSheetState {
    val config = XBottomSheetStateConfigBuilder().apply(configure).build()
    return rememberSaveable(config, saver = xBottomSheetStateSaver(config)) {
        XBottomSheetState(config)
    }
}
