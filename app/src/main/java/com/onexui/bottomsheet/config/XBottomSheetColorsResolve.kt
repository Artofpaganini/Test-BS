package com.onexui.bottomsheet.config

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import com.onexui.bottomsheet.theme.XTheme

/**
 * Резолв цвета scrim: Unspecified -> дефолт спеки. Как и остальные resolve*, зовётся ТОЛЬКО в композицие корня,
 * где доступна тема.
 */
@Composable
internal fun XBottomSheetColors.resolveScrim(): Color = scrim.takeOrElse { specScrim }

/** Резолв цвета статичного хендла: Unspecified -> дефолт спеки. */
@Composable
internal fun XBottomSheetColors.resolveHandleStatic(): Color = handleStatic.takeOrElse { specHandleStatic }

/** Резолв фона листа: Unspecified -> фон темы. Дефолт читается в val до takeOrElse (безусловный composable-read). */
@Composable
internal fun XBottomSheetColors.resolveSheetBackground(): Color {
    val default = XTheme.colors.backgroundContent
    return sheetBackground.takeOrElse { default }
}

/** Резолв тема-хендла: Unspecified -> цвет сепаратора темы. Дефолт читается в val до takeOrElse. */
@Composable
internal fun XBottomSheetColors.resolveHandleTheme(): Color {
    val default = XTheme.colors.separator
    return handleTheme.takeOrElse { default }
}
