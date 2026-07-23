package com.onexui.bottomsheet

import androidx.compose.runtime.Stable
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.unit.Dp
import com.onexui.bottomsheet.additionaltop.AdditionalTopState
import com.onexui.bottomsheet.state.SheetValue
import com.onexui.bottomsheet.state.XBottomSheetState

@Stable
internal class XBottomSheetScopeImpl(
    private val state: XBottomSheetState,
    override val loadingSheetHeight: Dp,
) : XBottomSheetScope {
    private var keyboardController: SoftwareKeyboardController? = null
    private var focusManager: FocusManager? = null

    override val sheetValue: SheetValue get() = state.currentValue
    override val isFillMode: Boolean get() = state.metrics?.isFillMode ?: false
    override var additionalTopState: AdditionalTopState
        get() = state.additionalTopState
        set(value) {
            state.additionalTopState = value
        }

    override fun requestDismiss() = state.requestDismiss()

    override fun hideKeyboard() {
        keyboardController?.hide()
        focusManager?.clearFocus(force = true)
    }

    internal fun updateKeyboardController(keyboardController: SoftwareKeyboardController?) {
        if (this.keyboardController === keyboardController) return
        this.keyboardController = keyboardController
    }

    internal fun updateFocusManager(focusManager: FocusManager?) {
        if (this.focusManager === focusManager) return
        this.focusManager = focusManager
    }
}
