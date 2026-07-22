package com.onexui.bottomsheet.config

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import com.onexui.bottomsheet.theme.XTheme

// Резолв ТОЛЬКО в композиции корня (тема доступна): Unspecified → дефолт спеки (specScrim/specHandleStatic) или темы.
// Темазависимые дефолты читаются в val до takeOrElse (безусловный composable-read, не в inline-лямбде).
@Composable
internal fun XBottomSheetColors.resolveScrim(): Color = scrim.takeOrElse { specScrim }

@Composable
internal fun XBottomSheetColors.resolveHandleStatic(): Color = handleStatic.takeOrElse { specHandleStatic }

@Composable
internal fun XBottomSheetColors.resolveSheetBackground(): Color {
    val default = XTheme.colors.backgroundContent
    return sheetBackground.takeOrElse { default }
}

@Composable
internal fun XBottomSheetColors.resolveHandleTheme(): Color {
    val default = XTheme.colors.separator
    return handleTheme.takeOrElse { default }
}
