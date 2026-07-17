package com.alva.core.navigation.saver

import androidx.compose.runtime.saveable.Saver

/**
 * [Saver], который сохраняет значение [Enum] по его строке [Enum.name].
 *
 * Работает в commonMain без платформенных Parcel/Bundle — имя сохраняется как обычная
 * [String] и восстанавливается через [enumValueOf]. Подходит для сохранения ключа активного таба
 * в [androidx.compose.runtime.saveable.rememberSaveable] после process death на всех платформах.
 *
 * Пример:
 * ```
 * enum class AlvaTab { Home, Stats, Profile }
 *
 * val currentTabState = rememberSaveable(stateSaver = enumNameSaver<AlvaTab>()) {
 *     mutableStateOf(AlvaTab.Home)
 * }
 * ```
 */
inline fun <reified E : Enum<E>> enumNameSaver(): Saver<E, String> = Saver(
    save = { value -> value.name },
    restore = { name -> enumValueOf<E>(name) },
)
