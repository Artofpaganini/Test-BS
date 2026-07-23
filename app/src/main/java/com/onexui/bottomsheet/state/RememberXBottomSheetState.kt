package com.onexui.bottomsheet.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable

@Composable
internal inline fun rememberXBottomSheetState(
    crossinline configure: XBottomSheetStateBuilder.() -> Unit = {},
): XBottomSheetState {
    val builder = remember { XBottomSheetStateBuilder().apply(configure) }
    return rememberSaveable(saver = xBottomSheetStateSaver(builder)) { builder.buildState() }
}
