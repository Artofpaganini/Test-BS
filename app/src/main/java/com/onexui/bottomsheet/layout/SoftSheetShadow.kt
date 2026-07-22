package com.onexui.bottomsheet.layout

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

private val ShadowColor = Color.Black.copy(alpha = 0.10f)
private val ShadowElevation = 16.dp

/** Мягкая тень верхнего края листа (для non-overlay режима). clip=false: низ листа уходит за границу экана. */
internal fun Modifier.softSheetShadow(shape: Shape): Modifier = this.shadow(
    elevation = ShadowElevation,
    shape = shape,
    clip = false,
    ambientColor = ShadowColor,
    spotColor = ShadowColor,
)
