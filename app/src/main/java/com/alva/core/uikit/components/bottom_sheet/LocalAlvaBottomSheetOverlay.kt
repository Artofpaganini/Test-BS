package com.alva.core.uikit.components.bottom_sheet

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf

// Глобальный override scrim из настроек. null = использовать per-route scrim из конфига;
// non-null = глобально навязать (например AlvaBottomSheetScrim.Disabled для non-modal из настроек).
val LocalAlvaBottomSheetScrimOverride: ProvidableCompositionLocal<AlvaBottomSheetScrim?> =
    compositionLocalOf { null }
