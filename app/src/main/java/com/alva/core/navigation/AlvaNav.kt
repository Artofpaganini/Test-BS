package com.alva.core.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.snapshots.Snapshot
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.alva.core.navigation.route.NavRoute

/**
 * Минимальный общий контракт навигации, от которого может зависеть любой экран Alva,
 * независимо от того, использует ли хост-оболочка одиночный линейный стек ([AlvaNavigator])
 * или стеки по одному на каждый таб ([MultiBackStackNavigator]).
 *
 * Контракт открыт для расширения — feature-модули могут реализовать собственные навигаторы
 * (например, wizard-flow со специфичной back-логикой) и подкладывать их через [LocalAlvaNav].
 * Декораторы вроде [com.alva.core.navigation.guard.GuardedNavigator] оборачивают любую реализацию
 * через паттерн `by delegate`.
 *
 * Поверхность намеренно минимальна — всё, что специфично для оболочки (переключение табов,
 * `replaceAll` всего приложения), живёт на конкретном типе.
 */
@Stable
interface AlvaNav {

    /**
     * Текущий отображаемый [NavBackStack]. Для [AlvaNavigator] — это его единственный стек;
     * для [MultiBackStackNavigator] — стек активного таба.
     */
    val activeBackStack: NavBackStack<NavKey>

    /** Положить [route] на вершину активного стека. */
    fun navigate(route: NavRoute)

    /**
     * Атомарно положить все [routes] на вершину активного стека по порядку: сначала parent'ы
     * синтетического back-стека, затем видимый target — последним. Используется при входе по deep
     * link для корректного Up-навигейшна. Реализация по умолчанию добавляет весь стек в одном
     * мутирующем снапшоте — без поэлементного пересчёта scene и мелькания parent-экрана.
     */
    fun navigateAll(routes: List<NavRoute>) {
        Snapshot.withMutableSnapshot {
            routes.forEach { route -> activeBackStack.add(route) }
        }
    }

    /**
     * Попытаться снять верхний элемент активного стека. Возвращает `true`, если back обработан,
     * `false` — если снимать больше нечего (вызывающий код может закрыть приложение).
     */
    fun tryPopBack(): Boolean

    /**
     * Back без ожидания результата. Вызывает [tryPopBack] и игнорирует результат.
     * Передаётся в `NavDisplay(onBack = { navigator.popBack() })`.
     */
    fun popBack() {
        tryPopBack()
    }

    /**
     * Атомарно заменить содержимое активного стека на [route]. Для [AlvaNavigator]
     * эквивалентно `replaceAll`. Для [MultiBackStackNavigator] эквивалентно
     * `replaceAllInCurrentTab` — не затрагивает другие табы.
     */
    fun replaceAllInActiveStack(route: NavRoute)
}
