package com.onexui.bottomsheet.presets

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Пресет отступов (§9): отступы внутри составных частей менять запрещено, задаём только пресетом Spacing.
// Дизайнерская рекомендация: бок 16, верх 20, низ 8.
internal object PresetSpacing {
    val Horizontal = 16.dp
    val Top = 20.dp
    val Bottom = 8.dp
    val ItemGap = 12.dp
}

@Composable
internal fun PresetVerticalSpace(height: Dp) {
    Spacer(modifier = Modifier.height(height))
}
