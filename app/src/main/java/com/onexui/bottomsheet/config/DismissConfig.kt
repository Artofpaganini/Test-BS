package com.onexui.bottomsheet.config

import androidx.compose.runtime.Immutable

@Immutable
internal class DismissConfig(
    val onOutsideTap: Boolean,
    val onSwipeDown: Boolean,
    val onBackPress: Boolean,
)
