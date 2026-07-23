package com.onexui.bottomsheet.handle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize

@Composable
internal fun DragHandle(
    style: DragHandleStyle,
    themeColor: Color,
    staticColor: Color,
    topPadding: Dp,
    size: DpSize,
    modifier: Modifier = Modifier,
) {
    val color = when (style) {
        DragHandleStyle.Theme -> themeColor
        DragHandleStyle.Static -> staticColor
    }
    Box(
        modifier = modifier
            .padding(top = topPadding)
            .size(
                width = size.width,
                height = size.height,
            )
            .background(color = color, shape = CircleShape),
    )
}
