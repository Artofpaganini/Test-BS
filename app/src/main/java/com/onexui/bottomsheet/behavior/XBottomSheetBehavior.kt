package com.onexui.bottomsheet.behavior

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onexui.bottomsheet.config.BottomKeyboardBehavior
import com.onexui.bottomsheet.config.DismissConfig
import com.onexui.bottomsheet.config.XBottomSheetDsl

@Immutable
internal data class XBottomSheetBehavior(
    val dismiss: DismissConfig,
    val bottomBehaviorWithKeyboard: BottomKeyboardBehavior,
) {
    val flingVelocityThresholdPxPerSec: Float = 400f
    val resistanceMaxPx: Float = 240f
    val predictiveBackMaxShift: Dp = 48.dp
}

internal fun defaultXBottomSheetBehavior(): XBottomSheetBehavior = XBottomSheetBehavior(
    dismiss = DismissConfig(
        isOutsideTapEnabled = true,
        isSwipeDownEnabled = true,
        isBackPressEnabled = false,
    ),
    bottomBehaviorWithKeyboard = BottomKeyboardBehavior.Lift,
)

@XBottomSheetDsl
internal class XBottomSheetBehaviorBuilder(current: XBottomSheetBehavior) {
    var bottomBehaviorWithKeyboard: BottomKeyboardBehavior = current.bottomBehaviorWithKeyboard
    private var dismiss: DismissConfig = current.dismiss

    fun dismiss(configure: DismissBuilder.() -> Unit) {
        dismiss = DismissBuilder(dismiss).apply(configure).build()
    }

    internal fun build(): XBottomSheetBehavior = XBottomSheetBehavior(
        dismiss = dismiss,
        bottomBehaviorWithKeyboard = bottomBehaviorWithKeyboard,
    )
}

@XBottomSheetDsl
internal class DismissBuilder(current: DismissConfig) {
    var isOutsideTapEnabled: Boolean = current.isOutsideTapEnabled
    var isSwipeDownEnabled: Boolean = current.isSwipeDownEnabled
    var isBackPressEnabled: Boolean = current.isBackPressEnabled

    internal fun build(): DismissConfig = DismissConfig(
        isOutsideTapEnabled = isOutsideTapEnabled,
        isSwipeDownEnabled = isSwipeDownEnabled,
        isBackPressEnabled = isBackPressEnabled,
    )
}
