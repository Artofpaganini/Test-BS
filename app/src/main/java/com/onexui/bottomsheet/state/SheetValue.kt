package com.onexui.bottomsheet.state

import com.onexui.bottomsheet.anchor.AnchorState

sealed interface SheetValue {
    data object Hidden : SheetValue
    data object Content : SheetValue
    data object Collapsed : SheetValue
    data object ExpandedContent : SheetValue
    data object ExpandedFullScreen : SheetValue
    data object Loading : SheetValue
    data class Custom(val anchor: AnchorState) : SheetValue
}
