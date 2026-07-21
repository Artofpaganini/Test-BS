package com.onexui.bottomsheet.state

import androidx.compose.runtime.Immutable

@Immutable
internal data class XBottomSheetStateConfig(
    val skipCollapsed: Boolean,
    val initialLoading: Boolean,
    val peekFraction: Float,
    val anchors: List<XSheetAnchor>,
)
