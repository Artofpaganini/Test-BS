package com.onexui.bottomsheet.config

import androidx.compose.runtime.Immutable

@Immutable
internal data class DismissConfig(
    val isOutsideTapEnabled: Boolean,
    val isSwipeDownEnabled: Boolean,
    val isBackPressEnabled: Boolean,
)
