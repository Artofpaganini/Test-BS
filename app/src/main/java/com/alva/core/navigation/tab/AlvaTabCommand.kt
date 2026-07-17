package com.alva.core.navigation.tab

import androidx.compose.runtime.Stable

/**
 * Маркер команды, адресованной активному табу через шину команд
 * [com.alva.core.navigation.MultiBackStackNavigator.commands].
 *
 * Контракт намеренно открыт: фичи реализуют собственные команды под свои сценарии
 * (например, обновить ленту, открыть поиск). Доставка идёт строго активному табу —
 * подписка через [AlvaTabCommandHandler].
 */
@Stable
interface AlvaTabCommand {

    /**
     * Стандартная команда: попросить видимый экран активного таба прокрутиться к началу.
     * Отправляется при повторном тапе на уже активный таб (re-select). Экран сам решает,
     * что делать: если уже прокручен наверх — вернуть стек к корню через
     * [com.alva.core.navigation.MultiBackStackNavigator.resetTabToRoot].
     */
    data object ScrollToTop : AlvaTabCommand
}
