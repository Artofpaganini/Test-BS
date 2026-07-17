package com.alva.core.uikit.components.bottom_sheet

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * CompositionLocal для доступа к [AlvaBottomSheetStateHolder] из Composable-функций
 * внутри bottom-sheet route (entry.Content() в Nav3 OverlayScene).
 *
 * Предоставляется через [androidx.compose.runtime.CompositionLocalProvider] в
 * AlvaBottomSheetScene — caller вызывает `LocalAlvaBottomSheetStateHolder.current`
 * для программного управления листом (expand, hide, и т.д.).
 *
 * Используется [staticCompositionLocalOf] потому что значение не меняется в течение
 * жизни одного BS — это исключает повторные рекомпозиции при чтении.
 */
val LocalAlvaBottomSheetStateHolder: ProvidableCompositionLocal<AlvaBottomSheetStateHolder> =
    staticCompositionLocalOf {
        error("LocalAlvaBottomSheetStateHolder is not provided. Are you inside an AlvaBottomSheet route?")
    }
