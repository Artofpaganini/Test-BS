package com.alva.core.device

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import com.alva.core.device.model.SystemBarsInsets
import com.alva.core.device.model.WindowSizeClass

// Заглушка core:device для лаборатории z-order. В реальном Alva DeviceState приходит из Koin-провайдера
// поверх единого pipeline (fold-сигнал + insets + конфигурация). Здесь порт AlvaBottomSheet читает лишь три
// поля — системные insets, класс ширины окна и ширину экрана, — поэтому держим их напрямую из Compose,
// без Koin и полного device-модуля.
@Immutable
data class DeviceStateStub(
    val systemBarsInsets: SystemBarsInsets,
    val widthSizeClass: WindowSizeClass,
    val screenWidthDp: Dp,
)

@Composable
fun rememberDeviceStateStub(): DeviceStateStub {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val systemBars = WindowInsets.systemBars
    val displayCutout = WindowInsets.displayCutout
    val containerSize = LocalWindowInfo.current.containerSize
    val statusBarTop = with(density) { systemBars.getTop(density).toDp() }
    val navigationBarBottom = with(density) { systemBars.getBottom(density).toDp() }
    val navigationBarLeft = with(density) { systemBars.getLeft(density, layoutDirection).toDp() }
    val navigationBarRight = with(density) { systemBars.getRight(density, layoutDirection).toDp() }
    val cutoutTop = with(density) { displayCutout.getTop(density).toDp() }
    val cutoutBottom = with(density) { displayCutout.getBottom(density).toDp() }
    val cutoutLeft = with(density) { displayCutout.getLeft(density, layoutDirection).toDp() }
    val cutoutRight = with(density) { displayCutout.getRight(density, layoutDirection).toDp() }
    val screenWidthDp = with(density) { containerSize.width.toDp() }
    return remember(
        statusBarTop,
        navigationBarBottom,
        navigationBarLeft,
        navigationBarRight,
        cutoutTop,
        cutoutBottom,
        cutoutLeft,
        cutoutRight,
        screenWidthDp,
    ) {
        DeviceStateStub(
            systemBarsInsets = SystemBarsInsets(
                statusBarTop = statusBarTop,
                navigationBarBottom = navigationBarBottom,
                navigationBarLeft = navigationBarLeft,
                navigationBarRight = navigationBarRight,
                cutoutTop = cutoutTop,
                cutoutBottom = cutoutBottom,
                cutoutLeft = cutoutLeft,
                cutoutRight = cutoutRight,
            ),
            widthSizeClass = WindowSizeClass.fromWidthDp(screenWidthDp.value.toInt()),
            screenWidthDp = screenWidthDp,
        )
    }
}
