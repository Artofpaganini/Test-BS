package com.onexui.bottomsheet.presets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.onexui.bottomsheet.XBottomSheetDefaults

// Пресет загрузки для Middle. Lottie внутри БШ запрещён (§9), скелетон компонентом не поддерживается —
// простой индикатор прогресса на высоту LoadingSheetHeight (192dp).
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
