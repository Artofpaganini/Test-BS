package com.onexui.bottomsheet.presets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.onexui.bottomsheet.XBottomSheetDefaults

// Loader для Middle: простой индикатор (Lottie в БШ запрещён, скелетон не поддерживается).
@Composable
internal fun PresetLoader(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(XBottomSheetDefaults.LoadingSheetHeight),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}
