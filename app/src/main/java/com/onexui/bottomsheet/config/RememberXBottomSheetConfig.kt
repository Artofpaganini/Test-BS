package com.onexui.bottomsheet.config

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Создаёт и запоминет конфиг листа. remember БЕЗ ключей: configure исполняется ОДИН раз — конфиг статичен на
 * жизнь composable. Динамика цветов темы идёт через Unspecified -> резолв в корне (resolveScrim/…), НЕ через
 * пересоздание. rememberSaveable не нужен: конфиг пересоздаётся из кода после process-death (стейт уже в Saver).
 */
@Composable
internal inline fun rememberXBottomSheetConfig(
    crossinline configure: XBottomSheetConfigBuilder.() -> Unit = {},
): XBottomSheetConfig = remember { XBottomSheetConfigBuilder().apply(configure).build() }
