package com.alva.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.autoSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.savedstate.serialization.SavedStateConfiguration
import com.alva.core.navigation.route.NavRoute
import com.alva.core.navigation.saver.enumNameSaver
import com.alva.core.navigation.scene.bottom_sheet.AlvaBottomSheetSceneRetentionStore
import com.alva.core.navigation.scene.bottom_sheet.rememberAlvaBottomSheetSceneRetentionEvictionDecorator
import com.alva.core.navigation.tab.AlvaTabCommand
import kotlin.jvm.JvmName
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Навигатор с несколькими независимыми back-stack'ами — по одному на каждую вкладку (tab).
 * Используется в scaffold'ах с BottomBar / NavigationRail, где переключение вкладки
 * НЕ должно сбрасывать историю внутри других вкладок.
 *
 * Реализован по **Google Navigation 3 Pattern B** (`NavigationState + Navigator`
 * из `android/nav3-recipes`). Хранит `Map<TabKey, NavBackStack<NavKey>>`:
 * каждая вкладка имеет свой [NavBackStack] (обёртка над `SnapshotStateList<NavKey>` от
 * Google, интегрированная с Compose snapshots и сериализацией).
 *
 * Пример сценария: в `Home` юзер ушёл в `ChildDetails → Feeding`, переключился на `Stats`,
 * потом вернулся на `Home` — видит `Feeding` (стек Home сохранился в памяти).
 *
 * Создаётся через [rememberMultiBackStackNavigator] с DSL-билдером [MultiBackStackScope].
 *
 * ## Поведение
 *
 * - [selectTab] — при тапе на другой таб переключает активный; при повторном тапе на уже активный
 *   таб (re-select) НЕ сбрасывает стек напрямую, а шлёт [AlvaTabCommand.ScrollToTop] в шину
 *   [commands] (двухступенчатый reselect: видимый экран реагирует первым). См. KDoc [selectTab].
 * - [popBack] — двухуровневый ("exit through home", как у Google):
 *   1) Pop верхнего элемента в активном стеке, если стек не пустой.
 *   2) Иначе, если активный таб не [defaultTab] — переход на [defaultTab].
 *   3) Иначе вернуть `false` (приложение может выйти).
 * - [navigate] / [replaceAllInCurrentTab] работают с активным стеком.
 * - [resetTabToRoot] — ручной сброс любого таба (например кнопкой "домой").
 *
 * ## Пример использования
 * ```
 * enum class AlvaTab { Home, Stats, Profile }
 *
 * @Composable
 * fun AlvaMainScaffold() {
 *     val navigator = rememberMultiBackStackNavigator<AlvaTab> {
 *         tab(AlvaTab.Home, HomeRoute)
 *         tab(AlvaTab.Stats, StatsRoute)
 *         tab(AlvaTab.Profile, ProfileRoute)
 *         defaultTab(AlvaTab.Home)
 *     }
 *
 *     Scaffold(
 *         bottomBar = {
 *             AlvaNavigationBar(
 *                 tabs = navigator.tabs,
 *                 selected = navigator.currentTab,
 *                 onTabClick = navigator::selectTab,
 *             )
 *         },
 *     ) { padding ->
 *         NavDisplay(
 *             backStack = navigator.activeBackStack,
 *             entries = navigator.toDecoratedEntries(entryProvider = koinEntryProvider()),
 *             onBack = { navigator.popBack() },
 *             modifier = Modifier.padding(padding).fillMaxSize(),
 *         )
 *     }
 * }
 * ```
 *
 * ## Process-death persistence
 *
 * Есть две перегрузки [rememberMultiBackStackNavigator]:
 * - Без [SavedStateConfiguration] — стеки хранятся только в composition, теряются после kill'а процесса.
 * - С [SavedStateConfiguration] — каждый стек сохраняется через [rememberNavBackStack],
 *   активная вкладка — через [rememberSaveable]. Требует polymorphic `SerializersModule` для [NavKey].
 *
 * ## Совместимость
 *
 * Работает в commonMain (Android + iOS) без платформенных actual/expect. Полагается на KMP-совместимый
 * `androidx.navigation3` и `androidx.savedstate` runtime.
 *
 * @param TabKey тип идентификатора вкладки. Обычно `enum class` в клиентском коде.
 */
@Stable
class MultiBackStackNavigator<TabKey : Any> internal constructor(
    private val stacks: Map<TabKey, NavBackStack<NavKey>>,
    private val defaultTab: TabKey,
    initialTab: TabKey,
    private val onTabChange: (TabKey) -> Unit = {},
) : AlvaNav {

    private val _currentTab: MutableState<TabKey> = mutableStateOf(initialTab)

    /**
     * Текущая активная вкладка. Хранится во внутреннем `MutableState` — можно наблюдать из Compose.
     * Меняется только через `selectTab` и `popBack`.
     */
    var currentTab: TabKey
        get() = _currentTab.value
        private set(value) {
            _currentTab.value = value
            onTabChange(value)
        }

    /** Все зарегистрированные вкладки в порядке объявления в DSL. Для отрисовки BottomBar. */
    val tabs: Set<TabKey>
        get() = stacks.keys

    /**
     * Те же вкладки, что и [tabs], но как индексируемый [List] в порядке объявления в DSL.
     * Используется для переключения по позиции в таб-баре через [selectTabAt].
     */
    val orderedTabs: List<TabKey>
        get() = stacks.keys.toList()

    private val _commands: MutableSharedFlow<Pair<TabKey, AlvaTabCommand>> =
        MutableSharedFlow(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    /**
     * Шина команд активному табу (см. [AlvaTabCommand], напр. [AlvaTabCommand.ScrollToTop]).
     * Каждая эмиссия — пара `активныйTab -> команда`. Подписка экранов — через
     * [com.alva.core.navigation.tab.AlvaTabCommandHandler], который фильтрует по своему tabKey.
     *
     * `replay = 0` (намеренно): команда доставляется только уже подписанным коллекторам активного
     * таба. Команда, отправленная до того как экран успел подписаться (например лениво-композящийся
     * таб ещё не смонтирован), — теряется. Это семантика current-subscriber: шина адресует текущему
     * живому подписчику, а не воспроизводит историю. Если нужна гарантированная доставка
     * «отложенному» экрану — это уже не задача этой шины.
     */
    val commands: SharedFlow<Pair<TabKey, AlvaTabCommand>> = _commands.asSharedFlow()

    /**
     * [NavBackStack] активной вкладки. Передаётся в `NavDisplay(backStack = ...)`.
     * При смене [currentTab] геттер автоматически отдаст стек другой вкладки — `NavDisplay` перерисуется.
     */
    override val activeBackStack: NavBackStack<NavKey>
        get() = stacks.getValue(currentTab)

    /**
     * Переключить активную вкладку.
     * - Если [tab] не зарегистрирован — игнорируется.
     * - Если [tab] уже активен (re-select) — НЕ сбрасывает стек напрямую, а отправляет
     *   [AlvaTabCommand.ScrollToTop] в шину [commands] активного таба (двухступенчатый reselect).
     *   Видимый экран реагирует первым через [com.alva.core.navigation.tab.AlvaTabCommandHandler]:
     *   если контент уже прокручен наверх — экран сам зовёт [resetTabToRoot] для возврата к корню;
     *   иначе — просто скроллится вверх, не теряя историю стека.
     * - Иначе — смена активной вкладки без изменения стеков.
     *
     * Это смена прежнего контракта: раньше re-select безусловно звал [resetTabToRoot].
     * [resetTabToRoot] остаётся публичным для ручного сброса (кнопка «домой», таб без UI-скролла).
     */
    fun selectTab(tab: TabKey) {
        when {
            stacks.containsKey(tab).not() -> Unit
            tab == currentTab -> sendCommand(AlvaTabCommand.ScrollToTop)
            else -> currentTab = tab
        }
    }

    /**
     * Переключить активную вкладку по её позиции [index] в порядке регистрации (см. [orderedTabs]).
     * Индекс вне диапазона игнорируется без падения. Делегирует в [selectTab], поэтому повторный
     * выбор уже активной позиции тоже даёт двухступенчатый reselect.
     */
    fun selectTabAt(index: Int) {
        val tab: TabKey = orderedTabs.getOrNull(index) ?: return
        selectTab(tab)
    }

    /**
     * Отправить [command] активному табу. Доставится только подписчику с совпадающим tabKey
     * (см. [com.alva.core.navigation.tab.AlvaTabCommandHandler]). При переполнении буфера старая
     * команда отбрасывается ([BufferOverflow.DROP_OLDEST]).
     *
     * Из-за `replay = 0` команда, отправленная до подписки экрана активного таба, теряется —
     * намеренно, семантика current-subscriber (подробнее см. [commands]).
     */
    fun sendCommand(command: AlvaTabCommand) {
        _commands.tryEmit(currentTab to command)
    }

    /** Добавить [route] на вершину стека активной вкладки. Эквивалент push. */
    override fun navigate(route: NavRoute) {
        stacks.getValue(currentTab).add(route)
    }

    /**
     * Очистить стек активной вкладки и положить [route] как единственный элемент.
     * Пример использования: после успешного onboarding'а сбросить историю вкладки на главный экран.
     */
    fun replaceAllInCurrentTab(route: NavRoute) {
        val stack: NavBackStack<NavKey> = stacks.getValue(currentTab)
        Snapshot.withMutableSnapshot {
            stack.clear()
            stack.add(route)
        }
    }

    /**
     * Сбросить указанный [tab] до единственного (корневого) route.
     * Корневой route — тот что был зарегистрирован в DSL через `tab(key, startRoute)`.
     * Не меняет [currentTab].
     */
    fun resetTabToRoot(tab: TabKey) {
        val stack: NavBackStack<NavKey> = stacks.getValue(tab)
        Snapshot.withMutableSnapshot {
            while (stack.size > 1) {
                stack.removeAt(stack.lastIndex)
            }
        }
    }

    /**
     * Обработать системную кнопку "назад". Возвращает `true`, если back был успешно обработан
     * (стек сокращён или переключили таб), `false` — если возвращаться некуда (можно закрывать приложение).
     *
     * Последовательность (паттерн "exit through home" из `android/nav3-recipes`):
     * 1. Если в активном стеке больше одного элемента — pop верхнего.
     * 2. Иначе, если активный таб не [defaultTab] — переключение на [defaultTab].
     * 3. Иначе `false`.
     */
    override fun tryPopBack(): Boolean = when {
        activeBackStack.size > 1 -> {
            activeBackStack.removeAt(activeBackStack.lastIndex)
            true
        }
        currentTab != defaultTab -> {
            currentTab = defaultTab
            true
        }
        else -> false
    }

    override fun replaceAllInActiveStack(route: NavRoute) {
        replaceAllInCurrentTab(route)
    }

    /**
     * Собирает список [NavEntry] для передачи в `NavDisplay(entries = ...)` активного таба.
     *
     * Каждый таб оборачивается в свой `rememberDecoratedNavEntries` с отдельным
     * [SaveableStateHolderNavEntryDecorator] — это гарантирует сохранение UI-состояния
     * внутри каждого таба при переключении между ними. Возвращается только список элементов
     * активного таба; остальные табы остаются «живыми», но не рендерятся.
     *
     * Паттерн взят из рецепта `multiple-backstacks` в Google `nav3-recipes`. Отличие: этот навигатор
     * не держит в стеке пару `[start, current]` — логика "exit through home" реализована
     * внутри [popBack].
     *
     * Пример передачи в [androidx.navigation3.ui.NavDisplay]:
     * ```
     * NavDisplay(
     *     backStack = navigator.activeBackStack,
     *     entries = navigator.toDecoratedEntries(entryProvider = koinEntryProvider()),
     *     onBack = { navigator.popBack() },
     *     ...
     * )
     * ```
     */
    @Composable
    fun toDecoratedEntries(
        entryProvider: (NavKey) -> NavEntry<NavKey>,
        bottomSheetSceneRetention: AlvaBottomSheetSceneRetentionStore? = null,
    ): List<NavEntry<NavKey>> {
        val perTab: Map<TabKey, List<NavEntry<NavKey>>> = stacks.mapValues { entry ->
            key(entry.key) {
                val decorators: List<NavEntryDecorator<NavKey>> = listOf(
                    rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
                    rememberViewModelStoreNavEntryDecorator(),
                    // Сбрасывает сохранённый анкер листа при окончательном закрытии таб-BS (см. retention store).
                    rememberAlvaBottomSheetSceneRetentionEvictionDecorator<NavKey>(bottomSheetSceneRetention),
//                    alvaInsetsNavEntryDecorator(),
                )
                rememberDecoratedNavEntries(
                    backStack = entry.value,
                    entryDecorators = decorators,
                    entryProvider = entryProvider,
                )
            }
        }
        return perTab[currentTab].orEmpty()
    }

    /**
     * Декорирует и возвращает [NavEntry] ВСЕХ табов разом — для pager / keep-alive хоста,
     * который держит несколько табов отрисованными одновременно (а не только активный).
     *
     * В отличие от [toDecoratedEntries] (возвращает только активный таб) отдаёт полную карту
     * `tabKey -> entries`. Каждый таб декорируется в собственном `key(tabKey)`-скоупе с отдельными
     * [SaveableStateHolderNavEntryDecorator] и ViewModelStore-декораторами, поэтому UI-состояние
     * и `ViewModel`'и каждого таба переживают переключение между табами.
     *
     * Не вызывай вместе с [toDecoratedEntries] в одной композиции для тех же стеков — это создаст
     * дублирующие декораторы для одного backStack. Хост выбирает один из двух API.
     */
    @Composable
    fun toDecoratedEntriesByTab(
        entryProvider: (NavKey) -> NavEntry<NavKey>,
    ): Map<TabKey, List<NavEntry<NavKey>>> =
        stacks.mapValues { entry ->
            key(entry.key) {
                val decorators: List<NavEntryDecorator<NavKey>> = listOf(
                    rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
                    rememberViewModelStoreNavEntryDecorator(),
                )
                rememberDecoratedNavEntries(
                    backStack = entry.value,
                    entryDecorators = decorators,
                    entryProvider = entryProvider,
                )
            }
        }

}

/**
 * DSL-билдер для конфигурации [MultiBackStackNavigator].
 * Используется только внутри лямбды [rememberMultiBackStackNavigator].
 *
 * Обязательные вызовы:
 * - как минимум один [tab] — зарегистрировать вкладку с корневым route.
 * - [defaultTab] — указать вкладку, на которую будет авто-возврат при системном back и
 *   которая активна при старте (если не указана явно через [initialTab]).
 *
 * Опциональные вызовы:
 * - [initialTab] — стартовая активная вкладка. По умолчанию равна [defaultTab].
 * - [onTabSelected] — хук, вызываемый при смене активной вкладки.
 *
 * Пример:
 * ```
 * rememberMultiBackStackNavigator<AlvaTab> {
 *     tab(AlvaTab.Home, HomeRoute)
 *     tab(AlvaTab.Stats, StatsRoute)
 *     tab(AlvaTab.Profile, ProfileRoute)
 *     defaultTab(AlvaTab.Home)
 *     initialTab(AlvaTab.Stats)    // опционально — стартуем со Stats, но back ведёт на Home
 *     onTabSelected { tab -> analytics.track(tab) }  // опционально
 * }
 * ```
 *
 * Валидация конфигурации выполняется внутри [resolveConfig] и бросит [IllegalStateException] если:
 * - не вызван [defaultTab],
 * - [defaultTab] / [initialTab] указывает на незарегистрированную вкладку.
 */
class MultiBackStackScope<TabKey : Any> internal constructor() {

    internal val entries: LinkedHashMap<TabKey, NavRoute> = linkedMapOf()
    private var defaultTabRef: TabKey? = null
    private var initialTabRef: TabKey? = null
    private var onTabSelectedRef: ((TabKey) -> Unit)? = null

    /**
     * Зарегистрировать вкладку [key] с её корневым [startRoute].
     * Порядок регистрации сохраняется — по нему отрисовывается BottomBar через [MultiBackStackNavigator.tabs].
     */
    fun tab(key: TabKey, startRoute: NavRoute) {
        entries[key] = startRoute
    }

    /**
     * Указать вкладку, на которую системная кнопка "назад" будет возвращать из других табов.
     * Обязательный вызов. Должна быть одной из зарегистрированных через [tab].
     */
    fun defaultTab(tab: TabKey) {
        defaultTabRef = tab
    }

    /**
     * Стартовая активная вкладка. Опционально. Если не указана — равна [defaultTab].
     * Должна быть одной из зарегистрированных через [tab].
     */
    fun initialTab(tab: TabKey) {
        initialTabRef = tab
    }

    /**
     * Опциональный хук, вызываемый при смене активного таба — через [MultiBackStackNavigator.selectTab]
     * или через [MultiBackStackNavigator.popBack] при возврате на [defaultTab].
     * Используй для аналитики, синхронизации с DataStore или любого side-effect,
     * который должен реагировать на переключение вкладок.
     * Вызывается после внутреннего сохранения состояния — никогда не блокирует composition.
     */
    fun onTabSelected(callback: (TabKey) -> Unit) {
        onTabSelectedRef = callback
    }

    internal fun resolveConfig(): ResolvedConfig<TabKey> {
        val default: TabKey = checkNotNull(defaultTabRef) {
            "MultiBackStackScope: defaultTab(...) must be set"
        }
        check(entries.containsKey(default)) {
            "MultiBackStackScope: defaultTab must be one of tabs registered via tab(...)"
        }
        val initial: TabKey = initialTabRef ?: default
        check(entries.containsKey(initial)) {
            "MultiBackStackScope: initialTab must be one of tabs registered via tab(...)"
        }
        return ResolvedConfig(
            entries = entries.toMap(),
            defaultTab = default,
            initialTab = initial,
            onTabSelected = onTabSelectedRef,
        )
    }
}

internal data class ResolvedConfig<TabKey : Any>(
    val entries: Map<TabKey, NavRoute>,
    val defaultTab: TabKey,
    val initialTab: TabKey,
    val onTabSelected: ((TabKey) -> Unit)? = null,
)

/**
 * Создать [MultiBackStackNavigator] БЕЗ сохранения после process death.
 * Каждый стек создаётся через `remember { NavBackStack(startRoute) }`. При kill'е процесса
 * всё сбрасывается к начальному состоянию.
 *
 * Пример:
 * ```
 * val navigator = rememberMultiBackStackNavigator<AlvaTab> {
 *     tab(AlvaTab.Home, HomeRoute)
 *     tab(AlvaTab.Stats, StatsRoute)
 *     tab(AlvaTab.Profile, ProfileRoute)
 *     defaultTab(AlvaTab.Home)
 * }
 * ```
 *
 * Для persistence — использовать перегрузку с [SavedStateConfiguration].
 *
 * @param TabKey тип идентификатора вкладки (обычно `enum class`).
 * @param builder DSL-блок конфигурации: [MultiBackStackScope.tab], [MultiBackStackScope.defaultTab],
 *   опционально [MultiBackStackScope.initialTab], [MultiBackStackScope.onTabSelected].
 */
@Composable
fun <TabKey : Any> rememberMultiBackStackNavigator(
    builder: MultiBackStackScope<TabKey>.() -> Unit,
): MultiBackStackNavigator<TabKey> {
    val config: ResolvedConfig<TabKey> = remember {
        MultiBackStackScope<TabKey>().apply(builder).resolveConfig()
    }
    val stacks: Map<TabKey, NavBackStack<NavKey>> = config.entries.mapValues { entry ->
        key(entry.key) {
            remember { NavBackStack<NavKey>(entry.value) }
        }
    }
    return remember(stacks) {
        MultiBackStackNavigator(
            stacks = stacks,
            defaultTab = config.defaultTab,
            initialTab = config.initialTab,
            onTabChange = { newTab -> config.onTabSelected?.invoke(newTab) },
        )
    }
}

/**
 * Создать [MultiBackStackNavigator] С сохранением после process death.
 *
 * Каждый стек создаётся через [rememberNavBackStack] (переживает kill процесса через
 * [rememberSaveable] + `NavBackStackSerializer`). Активная вкладка — через [rememberSaveable]
 * с указанным [currentTabSaver].
 *
 * Требования:
 * - Все [NavRoute] должны быть `@Serializable`.
 * - [configuration] должен содержать `SerializersModule` с регистрацией всех subtypes [NavKey]
 *   через `polymorphic(NavKey::class) { subclass(...) }`. Без этого persistence не сработает.
 * - [currentTabSaver] — saver для [TabKey]. По умолчанию [autoSaver], который работает для
 *   примитивов / enum / Parcelable / Serializable. Для кастомных типов — передать явно.
 *
 * Пример:
 * ```
 * val navConfig = SavedStateConfiguration {
 *     serializersModule = defaultAlvaSerializersModule()
 * }
 *
 * val navigator = rememberMultiBackStackNavigator<AlvaTab>(configuration = navConfig) {
 *     tab(AlvaTab.Home, HomeRoute)
 *     tab(AlvaTab.Stats, StatsRoute)
 *     tab(AlvaTab.Profile, ProfileRoute)
 *     defaultTab(AlvaTab.Home)
 * }
 * ```
 *
 * @param configuration конфигурация Kotlin serialization c polymorphic [NavKey]-модулем.
 * @param currentTabSaver saver для сохранения активной вкладки (по умолчанию [autoSaver]).
 * @param builder DSL-блок конфигурации табов и корневых route'ов.
 */
@Composable
fun <TabKey : Any> rememberMultiBackStackNavigator(
    configuration: SavedStateConfiguration,
    currentTabSaver: Saver<TabKey, out Any> = autoSaver(),
    builder: MultiBackStackScope<TabKey>.() -> Unit,
): MultiBackStackNavigator<TabKey> {
    val config: ResolvedConfig<TabKey> = remember {
        MultiBackStackScope<TabKey>().apply(builder).resolveConfig()
    }
    val stacks: Map<TabKey, NavBackStack<NavKey>> = config.entries.mapValues { entry ->
        key(entry.key) {
            rememberNavBackStack(configuration, entry.value)
        }
    }
    val savedTab: MutableState<TabKey> = rememberSaveable(stateSaver = currentTabSaver) {
        mutableStateOf(config.initialTab)
    }
    return remember(stacks, savedTab) {
        MultiBackStackNavigator(
            stacks = stacks,
            defaultTab = config.defaultTab,
            initialTab = savedTab.value,
            onTabChange = { newTab ->
                savedTab.value = newTab
                config.onTabSelected?.invoke(newTab)
            },
        )
    }
}

/**
 * Создать [MultiBackStackNavigator] С сохранением после process death для [TabKey : Enum].
 *
 * Convenience-перегрузка для случая, когда тип вкладки — `enum class`. Автоматически использует
 * [enumNameSaver] для сохранения активной вкладки (имя через [Enum.name], восстановление через
 * `enumValueOf<TabKey>`). Не требует явного указания [currentTabSaver].
 *
 * Пример:
 * ```
 * enum class AlvaTab { Home, Stats, Profile }
 *
 * val navigator = rememberMultiBackStackNavigator<AlvaTab>(configuration = navConfig) {
 *     tab(AlvaTab.Home, HomeRoute)
 *     tab(AlvaTab.Stats, StatsRoute)
 *     tab(AlvaTab.Profile, ProfileRoute)
 *     defaultTab(AlvaTab.Home)
 * }
 * ```
 *
 * @param configuration конфигурация Kotlin serialization c polymorphic [NavKey]-модулем.
 * @param builder DSL-блок конфигурации табов и корневых route'ов.
 */
@JvmName("rememberMultiBackStackNavigatorEnum")
@Composable
inline fun <reified TabKey : Enum<TabKey>> rememberMultiBackStackNavigator(
    configuration: SavedStateConfiguration,
    noinline builder: MultiBackStackScope<TabKey>.() -> Unit,
): MultiBackStackNavigator<TabKey> = rememberMultiBackStackNavigator(
    configuration = configuration,
    currentTabSaver = enumNameSaver<TabKey>(),
    builder = builder,
)
