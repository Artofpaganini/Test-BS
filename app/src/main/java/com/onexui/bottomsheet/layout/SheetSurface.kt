package com.onexui.bottomsheet.layout

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.onexui.bottomsheet.XBottomSheetDefaults

@Composable
internal fun SheetSurface(
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = XBottomSheetDefaults.Shape,
        color = XBottomSheetDefaults.SheetBackground,
    ) {
        content()
    }
}
