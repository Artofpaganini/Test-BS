package com.onexui.bottomsheet.handle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import com.onexui.bottomsheet.theme.XTheme

@Composable
internal fun DragHandle(
    style: DragHandleStyle,
    topPadding: Dp,
    size: DpSize,
    modifier: Modifier = Modifier,
) {
    val color = when (style) {
        is DragHandleStyle.Theme -> style.color.takeOrElse { XTheme.colors.separator }
        DragHandleStyle.Static -> StaticHandleColor
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

private const val STATIC_HANDLE_ALPHA = 0.40f
private val StaticHandleColor: Color = Color.White.copy(alpha = STATIC_HANDLE_ALPHA)
