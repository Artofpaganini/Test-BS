package com.alva.core.device.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class SystemBarsInsets(
    val statusBarTop: Dp,
    val navigationBarBottom: Dp,
    val navigationBarLeft: Dp,
    val navigationBarRight: Dp,
    val cutoutTop: Dp,
    val cutoutBottom: Dp,
    val cutoutLeft: Dp,
    val cutoutRight: Dp,
) {
    val safeTop: Dp get() = maxOf(statusBarTop, cutoutTop)
    val safeBottom: Dp get() = maxOf(navigationBarBottom, cutoutBottom)
    val safeLeft: Dp get() = maxOf(navigationBarLeft, cutoutLeft)
    val safeRight: Dp get() = maxOf(navigationBarRight, cutoutRight)

    companion object {
        fun default(): SystemBarsInsets = SystemBarsInsets(
            statusBarTop = 0.dp,
            navigationBarBottom = 0.dp,
            navigationBarLeft = 0.dp,
            navigationBarRight = 0.dp,
            cutoutTop = 0.dp,
            cutoutBottom = 0.dp,
            cutoutLeft = 0.dp,
            cutoutRight = 0.dp,
        )
    }
}
