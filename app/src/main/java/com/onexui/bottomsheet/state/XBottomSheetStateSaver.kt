package com.onexui.bottomsheet.state

import androidx.compose.runtime.saveable.Saver
import com.onexui.bottomsheet.additionaltop.AdditionalTopState

/**
 * Saver стейта листа (инвариант process-death). Формат — append-only список
 * `[tag, isLoading, additionalTopName, …новое в конец]`; чтение `getOrNull(i) as? T ?: default`
 * (forward-tolerance: старый формат читаеться новым кодом). Теги SheetValue заморожены
 * (h/c/col/ec/efs/l/cu: — персистентны, ре-неймы значений их НЕ меняют).
 *
 * Живые поля (peekFraction/anchors/skipCollapsed) НЕ сохраняются: стейт пересоздаётся билдером (начальная
 * вариация), последующие присвоения повторяет код в композиции. Custom(key) при удалённом якоре деградирует к peekFraction.
 */
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

/** Восстановление AdditionalTopState по имени enum; неизвестное имя -> Expanded (forward-tolerance). */
private fun additionalTopFromName(name: String?): AdditionalTopState =
    AdditionalTopState.entries.firstOrNull { entry -> entry.name == name } ?: AdditionalTopState.Expanded

/** Замороженный тег стейта для сохранения (h/c/col/ec/efs/l/cu:key). */
private fun sheetValueTag(value: SheetValue): String = when (value) {
    SheetValue.Hidden -> "h"
    SheetValue.Content -> "c"
    SheetValue.Collapsed -> "col"
    SheetValue.ExpandedContent -> "ec"
    SheetValue.ExpandedFullScreen -> "efs"
    SheetValue.Loading -> "l"
    is SheetValue.Custom -> "cu:${value.key}"
}

/** Восстановление стейта по замороженному тегу; неизвестный тег -> Hidden (forward-tolerance). */
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
