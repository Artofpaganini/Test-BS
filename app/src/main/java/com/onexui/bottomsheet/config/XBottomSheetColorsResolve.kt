package com.onexui.bottomsheet.config

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import com.onexui.bottomsheet.theme.XTheme

@Composable
internal fun XBottomSheetColors.resolveScrim(): Color = scrim.takeOrElse { specScrim }

@Composable
internal fun XBottomSheetColors.resolveSheetBackground(): Color {
    val default = XTheme.colors.backgroundContent
    return sheetBackground.takeOrElse { default }
}
