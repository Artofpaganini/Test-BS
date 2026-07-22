package com.onexui.bottomsheet

import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.Dp
import com.onexui.bottomsheet.additionaltop.AdditionalTopState
import com.onexui.bottomsheet.state.SheetValue

/**
 * Receiver слотов (additionalTop/top/bottom/middle): безопасное семантическое API для контена. Внутренности
 * стейта (offset/metrics/канал жестов) недостижимы; команды высоты (show/expand/hide) — у хоста, не у контента.
 */
@Stable
internal interface XBottomSheetScope {
    val sheetValue: SheetValue
    val isFillMode: Boolean
    val loadingSheetHeight: Dp
    var additionalTopState: AdditionalTopState
    fun requestDismiss()
    fun hideKeyboard()
}
