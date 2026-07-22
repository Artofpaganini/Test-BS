package com.onexui.bottomsheet

import androidx.compose.runtime.Stable
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.unit.Dp
import com.onexui.bottomsheet.additionaltop.AdditionalTopState
import com.onexui.bottomsheet.state.SheetValue
import com.onexui.bottomsheet.state.XBottomSheetState

// keyboardController/focusManager пишутся из корня SideEffect'ом (тот же приём, что onDismissRequest в стейте).
@Stable
internal class XBottomSheetScopeImpl(
    private val state: XBottomSheetState,
    override val loadingSheetHeight: Dp,
) : XBottomSheetScope {
    internal var keyboardController: SoftwareKeyboardController? = null
    internal var focusManager: FocusManager? = null

    override val sheetValue: SheetValue get() = state.currentValue
    override val isFillMode: Boolean get() = state.metrics?.isFillMode ?: false
    override var additionalTopState: AdditionalTopState
        get() = state.additionalTopState
        set(value) {
            state.additionalTopState = value
        }

    override fun requestDismiss() = state.onDismissRequest()

    override fun hideKeyboard() {
        keyboardController?.hide()
        focusManager?.clearFocus(force = true)
    }
}
