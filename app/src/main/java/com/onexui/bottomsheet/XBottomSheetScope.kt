package com.onexui.bottomsheet

import androidx.compose.runtime.Stable
import com.onexui.bottomsheet.additionaltop.AdditionalTopState
import com.onexui.bottomsheet.state.SheetValue

// Receiver 4 слотов: безопасное семантическое API. offset/metrics/gestureCommands/enqueue* недостижимы из контента;
// команды высоты (show/expand/hide) — у хоста (иначе петля ответственности).
@Stable
internal interface XBottomSheetScope {
    val sheetValue: SheetValue
    val isFillMode: Boolean
    var additionalTopState: AdditionalTopState
    fun requestDismiss()
    fun hideKeyboard()
}
