package com.alva.core.uikit.components.bottom_sheet.config

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.alva.core.uikit.components.bottom_sheet.AlvaBottomSheetScrim

@DslMarker
annotation class AlvaBottomSheetConfigDsl

@AlvaBottomSheetConfigDsl
class AlvaBottomSheetConfigBuilder {
    var shape: Shape = RoundedCornerShape(topStart = DEFAULT_CORNER_RADIUS_DP.dp, topEnd = DEFAULT_CORNER_RADIUS_DP.dp)
    var containerColor: Color = Color.Unspecified
    var scrim: AlvaBottomSheetScrim = AlvaBottomSheetScrim.Enabled()
    var draggable: Boolean = true
    var dismissOnScrimTap: Boolean = true
    var positionalThresholdFraction: Float = DEFAULT_POSITIONAL_THRESHOLD
    var adaptiveWidthFraction: Float = DEFAULT_ADAPTIVE_WIDTH_FRACTION

    fun build(): AlvaBottomSheetConfig = AlvaBottomSheetConfig(
        shape = shape,
        containerColor = containerColor,
        scrim = scrim,
        draggable = draggable,
        dismissOnScrimTap = dismissOnScrimTap,
        positionalThresholdFraction = positionalThresholdFraction,
        adaptiveWidthFraction = adaptiveWidthFraction,
    )

    private companion object {
        const val DEFAULT_POSITIONAL_THRESHOLD = 0.5f
        const val DEFAULT_ADAPTIVE_WIDTH_FRACTION = 0.75f
        const val DEFAULT_CORNER_RADIUS_DP = 28
    }
}

@AlvaBottomSheetConfigDsl
inline fun alvaBottomSheetConfig(
    receiver: AlvaBottomSheetConfigBuilder.() -> Unit = {},
): AlvaBottomSheetConfig = AlvaBottomSheetConfigBuilder().apply(receiver).build()

@Composable
fun rememberAlvaBottomSheetConfig(
    receiver: AlvaBottomSheetConfigBuilder.() -> Unit = {},
): AlvaBottomSheetConfig {
    val themeContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
    return remember(themeContainerColor) {
        alvaBottomSheetConfig {
            containerColor = themeContainerColor
            receiver()
        }
    }
}
