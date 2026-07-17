package com.onexui.bottomsheet

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.onexui.bottomsheet.theme.XTheme

// Вшитые токены геометрии и цвета XBottomSheet (§2 спеки). Красить составные части нельзя —
// цвета в API не принимаются, а берутся отсюда.
internal object XBottomSheetDefaults {
    val CornerRadius = 20.dp
    val MaxWidth = 512.dp
    val WideScreenThreshold = 600.dp
    const val CollapsedFraction = 0.60f
    val DragHandleSize = DpSize(36.dp, 4.dp)
    val DragHandleTopPadding = 8.dp
    val AdditionalTopOverlap = 32.dp
    val AdditionalTopPeek = 20.dp
    val LoadingSheetHeight = 192.dp
    val ScrimColor = Color.Black.copy(alpha = 0.40f)
    val HandleStatic = Color.White.copy(alpha = 0.40f)

    // Радиус только верхних углов: 20 20 0 0.
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
}

// Имитация нативной анимации листа: мягкая пружина без отскока.
internal val NativeSheetSpring: AnimationSpec<Float> = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

// Мягкая верхняя тень Shadow Soft (0 -4 32 rgba(0,0,0,.1)). Видна ТОЛЬКО при overlayBackground = false.
// В Compose нет готового «верхнего мягкого» токена, поэтому используем elevation-тень по форме листа
// без клипа — она мягко очерчивает верхний край (низ листа уходит за нижнюю границу экрана).
private val ShadowColor = Color.Black.copy(alpha = 0.10f)
private val ShadowElevation = 16.dp

internal fun Modifier.softSheetShadow(shape: Shape): Modifier = this.shadow(
    elevation = ShadowElevation,
    shape = shape,
    clip = false,
    ambientColor = ShadowColor,
    spotColor = ShadowColor,
)
