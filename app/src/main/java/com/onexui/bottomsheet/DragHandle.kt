package com.onexui.bottomsheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// Стиль Drag Handle: Theme (темазависимый сепаратор) или Static (белый alpha .40, вне темы —
// для Additional Top / статичных фонов). null в API листа = хендл скрыт и жесты высоты отключены.
internal enum class DragHandleStyle { Theme, Static }

// Хендл 36×4dp, r360, отступ сверху 8dp. Read-only: нажатий не поддерживает (только визуальный маркер),
// рисуется поверх контента и вёрстку не двигает — позиционируется вызывающим (align TopCenter).
@Composable
internal fun DragHandle(
    style: DragHandleStyle,
    modifier: Modifier = Modifier,
) {
    val color = when (style) {
        DragHandleStyle.Theme -> XBottomSheetDefaults.HandleTheme
        DragHandleStyle.Static -> XBottomSheetDefaults.HandleStatic
    }
    Box(
        modifier = modifier
            .padding(top = XBottomSheetDefaults.DragHandleTopPadding)
            .size(
                width = XBottomSheetDefaults.DragHandleSize.width,
                height = XBottomSheetDefaults.DragHandleSize.height,
            )
            .background(color = color, shape = CircleShape),
    )
}
