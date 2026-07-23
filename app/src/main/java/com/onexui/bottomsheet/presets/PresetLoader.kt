package com.onexui.bottomsheet.presets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.onexui.bottomsheet.XBottomSheetScope

@Composable
internal fun XBottomSheetScope.PresetLoader(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(loadingSheetHeight),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}
