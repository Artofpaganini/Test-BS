package com.onexui.bottomsheet

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.onexui.bottomsheet.theme.XTheme

// Вшитые токены геометрии и цвета: цвета в API не принимаются, а берутся отсюда.
internal object XBottomSheetDefaults {
    val CornerRadius = 20.dp
    val MaxWidth = 512.dp
    val WideScreenThreshold = 600.dp
    const val PeekFraction: Float = 2f / 3f
    val DragHandleSize = DpSize(36.dp, 4.dp)
    val DragHandleTopPadding = 8.dp
    val AdditionalTopOverlap = 32.dp
    val LoadingSheetHeight = 192.dp
    // Дистанция разгона альфы скрима по высоте листа.
    val ScrimFadeDistance = 120.dp
    val ScrimColor = Color.Black.copy(alpha = 0.40f)
    val HandleStatic = Color.White.copy(alpha = 0.40f)

    val Shape: Shape = RoundedCornerShape(
        topStart = CornerRadius,
        topEnd = CornerRadius,
        bottomStart = 0.dp,
        bottomEnd = 0.dp,
    )

    val SheetBackground: Color
        @Composable get() = XTheme.colors.backgroundContent

    val HandleTheme: Color
        @Composable get() = XTheme.colors.separator

    // Вшитая физика жестов (§9, НЕ публичный конфиг; менять только с полным прогоном 21 кейса). px намеренно —
    // перевод в dp менял бы поведение на не-базовой плотности.
    const val FlingVelocityThresholdPxPerSec = 400f
    const val ResistanceMaxPx = 240f
}
