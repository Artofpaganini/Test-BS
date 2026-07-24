package com.onexui.bottomsheet.layout

import androidx.compose.runtime.Immutable

@Immutable
internal data class SheetInsets(
    val screenHeightPx: Int,
    val statusBarPx: Int,
    val navBarPx: Int,
    val loadingSheetHeightPx: Int,
)
