package com.onexui.bottomsheet

import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.Dp
import com.onexui.bottomsheet.additionaltop.AdditionalTopState
import com.onexui.bottomsheet.state.SheetValue

@Stable
internal interface XBottomSheetScope {
    val sheetValue: SheetValue
    val isFillMode: Boolean
    val loadingSheetHeight: Dp
    var additionalTopState: AdditionalTopState
    fun requestDismiss()
    fun hideKeyboard()
}
