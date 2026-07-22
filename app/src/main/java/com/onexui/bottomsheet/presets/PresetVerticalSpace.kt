package com.onexui.bottomsheet.presets

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/** Пресет: вертикальный отступ заданной высоы между частями тела. */
@Composable
internal fun PresetVerticalSpace(height: Dp) {
    Spacer(modifier = Modifier.height(height))
}
