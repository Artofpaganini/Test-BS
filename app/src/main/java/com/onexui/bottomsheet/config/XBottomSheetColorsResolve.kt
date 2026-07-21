package com.onexui.bottomsheet.config

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import com.onexui.bottomsheet.XBottomSheetDefaults

// Резолв ТОЛЬКО в композиции корня (тема доступна): Unspecified → дефолт из XBottomSheetDefaults (единый источник
// дефолтов; цвета в токенах не дублируются). Темазависимые дефолты читаются в val до takeOrElse (безусловный
// composable-read, не в inline-лямбде).
@Composable
internal fun XBottomSheetColors.resolveScrim(): Color = scrim.takeOrElse { XBottomSheetDefaults.ScrimColor }

@Composable
internal fun XBottomSheetColors.resolveHandleStatic(): Color = handleStatic.takeOrElse { XBottomSheetDefaults.HandleStatic }

@Composable
internal fun XBottomSheetColors.resolveSheetBackground(): Color {
    val default = XBottomSheetDefaults.SheetBackground
    return sheetBackground.takeOrElse { default }
}

@Composable
internal fun XBottomSheetColors.resolveHandleTheme(): Color {
    val default = XBottomSheetDefaults.HandleTheme
    return handleTheme.takeOrElse { default }
}
