package com.onexui.bottomsheet.state

import androidx.compose.runtime.saveable.Saver
import com.onexui.bottomsheet.additionaltop.AdditionalTopState

internal fun xBottomSheetStateSaver(builder: XBottomSheetStateBuilder): Saver<XBottomSheetState, List<Any>> = Saver(
    save = { state ->
        listOf(sheetValueTag(state.currentValue), state.isLoading, state.additionalTopState.name)
    },
    restore = { saved ->
        val tag = saved.getOrNull(0) as? String ?: "h"
        val loading = saved.getOrNull(1) as? Boolean ?: false
        val topName = saved.getOrNull(2) as? String
        builder.buildState().apply {
            restore(
                value = sheetValueFromTag(tag),
                isLoadingSaved = loading,
                additionalTop = additionalTopFromName(topName),
            )
        }
    },
)

private fun additionalTopFromName(name: String?): AdditionalTopState =
    AdditionalTopState.entries.firstOrNull { entry -> entry.name == name } ?: AdditionalTopState.Expanded

private fun sheetValueTag(value: SheetValue): String = when (value) {
    SheetValue.Hidden -> "h"
    SheetValue.Content -> "c"
    SheetValue.Collapsed -> "col"
    SheetValue.ExpandedContent -> "ec"
    SheetValue.ExpandedFullScreen -> "efs"
    SheetValue.Loading -> "l"
    is SheetValue.Custom -> "cu:${value.key}"
}

private fun sheetValueFromTag(tag: String): SheetValue = when {
    tag == "h" -> SheetValue.Hidden
    tag == "c" -> SheetValue.Content
    tag == "col" -> SheetValue.Collapsed
    tag == "ec" -> SheetValue.ExpandedContent
    tag == "efs" -> SheetValue.ExpandedFullScreen
    tag == "l" -> SheetValue.Loading
    tag.startsWith("cu:") -> SheetValue.Custom(tag.removePrefix("cu:"))
    else -> SheetValue.Hidden
}
