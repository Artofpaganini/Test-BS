package com.onexui.bottomsheet.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable

// Билдер в remember{} без ключей: configure исполняется ОДИН раз — задаёт начальную вариацию стейта (канон
// rememberXBottomSheetConfig). Динамику (peekFraction/anchors/skipCollapsed) даёт не пересоздание, а живые поля
// стейта. rememberSaveable без ключей: после process-death живые поля восстанавливаются кодом (билдер + присвоения
// в композиции), в Saver их нет.
@Composable
internal inline fun rememberXBottomSheetState(
    crossinline configure: XBottomSheetStateBuilder.() -> Unit = {},
): XBottomSheetState {
    val builder = remember { XBottomSheetStateBuilder().apply(configure) }
    return rememberSaveable(saver = xBottomSheetStateSaver(builder)) { builder.buildState() }
}
