package com.onexui.bottomsheet.config

import androidx.compose.runtime.Immutable

/** Способы закрытя листа: тап вне листа, свайп вниз, системный back. */
@Immutable
internal class DismissConfig(
    val onOutsideTap: Boolean,
    val onSwipeDown: Boolean,
    val onBackPress: Boolean,
)
