package com.onexui.bottomsheet.handle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.onexui.bottomsheet.XBottomSheetDefaults

// Read-only маркер, позиционируется вызывающим (align TopCenter), вёрстку не двигает.
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
