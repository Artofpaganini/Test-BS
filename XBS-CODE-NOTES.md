# XBS-CODE-NOTES — почему код XBottomSheet такой

Выжимка всех пояснений, которые раньше жили комментариями внутри `com/onexui/bottomsheet/`.
Код очищен от комментариев; здесь лежит «что это и зачем». Читать вместе с `XBOTTOMSHEET.md`
(карта и API) и `XBS-SPEC.md` (спека и чеклист).

В коде осталось только одно исключение — KDoc над публичными методами `XBottomSheetState`
(`anchors` · `show` · `expand` · `hide` · `markContentReady`). Всё остальное здесь.

Область: только сам компонент. Не покрыто намеренно:
- `org/xplatform/uikit/compose/modifier/keyboard/**` — verbatim-копия из xbet, стиль не редактируем;
- `com/onexui/demo/**` и `com/zorderlab/**` — демо и лаба, не часть компонента.

---

## 1. Корень

### `XBottomSheet.kt` · функция `XBottomSheet`

Корень листа: связывает стейт, конфиг и слоты (`additionalTop`/`top`/`bottom`/`middle`), рисует
scrim и контейнер, резолвит тема-цвета, вайрит физику/IME/dismiss в стейт одним `SideEffect`'ом
и обрабатывает predictive-back.

| Значение | Зачем |
|---|---|
| `dismissScope` | `onDismissRequest` снаружи — suspend; наружу стейт отдаёт не-suspend `requestDismiss()`, а сама suspend-лямбда исполняется в этом scope |
| `currentOnDismiss` | Не делегируем через `by`: стейту нужен сам `State` — его идентичность стабильна, на ней стоит гард сеттера |
| `containerSize` | Размеры экрана из `LocalWindowInfo.containerSize` (px); при повороте обновляется сам |
| `navBarPx` | Фон Surface остаётся edge-to-edge под баром, контент паддится инсетом внутри (bottom всегда над баром) |
| `loadingSheetHeightPx` | Loading-якорь = 192dp + `navBarPx`: видимая зона Loader'а остаётся 192dp, несмотря на nav-bar-паддинг внутри |
| `scrimColor` · `sheetBackgroundColor` · `handleThemeColor` · `handleStaticColor` | Цвета резолвятся в композиции корня (тема доступна): `Unspecified` -> дефолт. Вниз идут готовые `Color`-примитивы |
| `keyboardState` | Единый источник состояния IME для авто-FullScreen и модификаторов подъёма/сжатия |
| `interactionsEnabledState` | Жесты высоты отключены при `dragHandle == null` / Loading / открытой IME. `derivedStateOf` будит читателей только при смене значения |
| `additionalTopFraction` | Фракция Additional Top (1 = Expanded, 0 = скрыта). `Animatable`, а не `animate*AsState`: `.value` читается в measure -> инвалидация layout, а не композиции |
| `backShift` | Смещение листа за predictive-back-жестом; читается в draw-фазе через `graphicsLayer`, скрим — отдельный узел выше и не двигается |

**Блоки логики**

- `ObserveSheetState(onSheetHidden = …)` — `onSheetHidden` роняет IME: иначе keyboard-lift оффсет
  поднял бы пустой контейнер и лист «улетел» бы вверх.
- `PredictiveBackHandler` — Android 14+: жест двигает лист за прогрессом через `backShift`;
  отмена -> 0, успех -> dismiss + сброс. `enabled = false` (дефолт `onBackPress`) пропускает
  событие хосту/Activity.
- `LaunchedEffect(state, screenHeightPx, statusBarPx)` — поворот/resize: снап высоты к якорю.
  Именно one-shot по размерам, а не `snapshotFlow` — иначе пере-подписал бы коллекторы.
- `SideEffect { … }` — единственная точка вайринга: конфиг закрытия, физика, scope и ссылка на
  suspend `onDismissRequest`, IME-контроллеры в scope. Каждая сущность вписывается своим сеттером
  с собственным гардом — после первого прохода записей нет. `alwaysFullScreenOnIme` = режим
  `StayUnderKeyboard` при наличии bottom-слота; стейт читает live-IME в `shouldPromoteForIme`
  без копий-снапшотов.
- `LaunchedEffect(state) { state.processGestures() }` — единственный потребитель канала жестов
  (drag/settle по порядку), один на весь лист.
- Слоты биндятся к ОДНОМУ scope литералом: detect/place-копии тела получают тот же `sheetScope`;
  `requestDismiss` из detect недостижим, потому что detect не размещается.

### `NativeSheetSpring.kt`

Единая пружина всех анимаций высоты листа: без отскока, средне-мягкая жёсткость — нативное ощущение.

### `XBottomSheetScope.kt` · интерфейс `XBottomSheetScope`

Receiver слотов (`additionalTop`/`top`/`bottom`/`middle`): безопасное семантическое API для контента.
Внутренности стейта (`offset`, `metrics`, канал жестов) недостижимы; команды высоты
(`show`/`expand`/`hide`) — у хоста, не у контента.

### `XBottomSheetScopeImpl.kt` · класс `XBottomSheetScopeImpl`

Реализация scope поверх стейта: проксирует `sheetValue`/`isFillMode`/`additionalTopState` и
`dismiss`/`hideKeyboard`.

| Член | Зачем |
|---|---|
| `keyboardController`, `focusManager` | Вписываются из корня `SideEffect`'ом, каждый своим сеттером с гардом на идентичность — повторная запись равного значения до поля не доходит |
| `updateKeyboardController(...)` | Вписывает контроллер IME; инстанс стабилен на окно, поэтому гард по идентичности режет все повторы после первого прохода |
| `updateFocusManager(...)` | Вписывает focus-менеджер; гард по идентичности, как у контроллера IME |

---

## 2. Стейт

### `state/XBottomSheetState.kt` · класс `XBottomSheetState`

Стейт-машина высоты листа и единый набор живых поведенческих ручек — одна сущность для
разработчика (канон M3 `SheetState`: поведение живёт в стейте, а не в отдельном config-объекте).
Значения — Compose-state (`mutableStateOf`), не `StateFlow`; вычисляются по метрикам замера и
фактам. Режим wrap/fill определяется замером (`SheetMetrics.isFillMode`).

Живые ручки (`skipCollapsed`/`peekFraction`/`anchors`) меняются прямо в композиции: покоящийся
лист доезжает к новому якорю сам (`onLiveConfigChanged`), присвоение равного значения — no-op.

Вайринг из корня (физика, IME, закрытие) идёт не присваиванием полей, а изолированными
`update*`-сеттерами: у каждой сущности свой сеттер со своим гардом, равное значение до поля
не доходит.

#### Приватные поля

| Поле | Зачем |
|---|---|
| `accumulatedOvershootPx` | Сырой накопленный overshoot за верхним якорем; сопротивление считается именно от него, а не от текущего смещения |
| `anchorTable` | Якорная таблица (rest-якоря + settle-математика); пересчитывается только в `updateMetrics`. `null` — пока нет метрик |
| `imePromotedFrom` | Рест-стейт до авто-промоушена в FullScreen из-за IME: при скрытии клавиатуры откатываемся к нему |
| `dismissScope` | Scope корня, в котором исполняется suspend-закрытие хоста. Вписывается `updateDismissScope` |
| `dismissRequest` | Живая ссылка на suspend-лямбду закрытия хоста (`rememberUpdatedState`). Вписывается `updateDismissRequest` |
| `gestureCommands` | FIFO-канал жестов (drag/settle): единственный вход, потребитель — `processGestures` |

#### Публичные и internal-поля

| Поле | Зачем |
|---|---|
| `skipCollapsed` | Есть ли промежуточный Collapsed-якорь; `false` — открываем сразу в раскрытый стейт. Живая ручка |
| `peekFraction` | Доля высоты экрана для Collapsed-якоря (0..1). Живая ручка |
| `anchors` | Кастомные rest-якоря fill-режима (DSL `"half" at 0.5f`). `private set` — снаружи якорь не создать (конструктор `XSheetAnchor` internal) |
| `currentValue` | Текущий логический стейт высоты; пишется только внутри стейта |
| `isLoading` | Идёт ли загрузка контента (в Middle — Loader вместо контента) |
| `initialLoading` | Стартовое значение `isLoading`, заданное билдером |
| `additionalTopState` | Состояние карточки Additional Top; переключается только внешними факторами |
| `metrics` | Замеренные размеры листа; пишет только `updateMetrics` из measure-фазы |
| `offset` | Текущая высота листа, px. Пишут только suspend-методы стейта (drag -> `snapTo`, анимация -> `animateTo`), читается в measure-фазе измерителя (deferred) — без рекомпозиций |
| `isDragging` | Держит ли палец лист. `private set`: снаружи ставится только через `markDragStarted()` |
| `keyboardState` | Live-состояние IME (xbet `KeyboardLiftState`) — читается напрямую в `shouldPromoteForIme`, без копий-снапшотов |
| `alwaysFullScreenOnIme` | Конфиг-флаг `StayUnderKeyboard` + bottom-слот: bottom уходит под клавиатуру -> лист форсим в FullScreen |
| `dismissOnSwipeDown` | Разрешён ли свайп-вниз-закрытие (settle добавляет Hidden-якорь на 0px) |
| `flingVelocityThresholdPxPerSec`, `resistanceMaxPx` | Физика жестов из config. Старт `0f` не дублирует источник: жесты невозможны до первой композиции корня, где вайринг уже произошёл |
| `isVisible` | Лист не в `Hidden` |
| `isAnimating` | Идёт ли анимация высоты (`offset.isRunning` — snapshot-state, наблюдаемо из `snapshotFlow`) |

#### Публичные методы (KDoc остался в коде)

| Метод | Что делает |
|---|---|
| `anchors(configure)` | Меняет набор якорей той же DSL-грамматикой, что и билдер (`"half" at 0.5f`) |
| `show()` | Открывает лист (если ещё не открыт): в Loading — к Loader-якорю, иначе к `openTarget` по замеру. Уже открытый (в т.ч. восстановленный из Saver) не переоткрывает: анимация к `openTarget` сбросила бы стейт, высоту снапнет `snapToCurrentAnchor` |
| `expand()` | Раскрывает лист из Collapsed в `expandTarget`. Сбрасывает `imePromotedFrom` — ручной разворот не откатывается при скрытии IME |
| `hide()` | Закрывает лист (анимация к Hidden) |
| `markContentReady()` | Снимает Loading и доводит лист до рест-стейта по РЕАЛЬНОМУ контенту |

Детали `markContentReady()`:
- Закрыли во время Loading -> снимаем флаг и выходим, иначе `openTarget` поднял бы закрытый лист.
- `openTarget` считается по реальному контенту, не по Loader-метрикам: ждём первый ре-замер
  (инстанс метрик отличается по ссылке). На таймаут stale-метрики не берём — ждём реальный
  ре-замер без лимита (measure может быть заморожен на фоне).
- `hide()` мог прилететь за время ожидания ре-замера — проверяем видимость повторно.
- Если клавиатура уже открыта, применяется тот же авто-FullScreen, что и в `onImeShown`: иначе
  не-FullScreen лист поднялся бы на полную высоту IME и верх уехал бы за экран.
- Иначе сбрасываем промоушен: скрытие IME не должно откатывать к Loading.

#### Стандартные `update*`-сеттеры

Общий контракт: вписываются из корня в `SideEffect`, у каждого свой гард. Гард на равенство
(`==`) для значений и на идентичность (`===`) для ссылок. Смысл — после первого прохода
композиции записей в поля нет вообще: ни мусора в снапшот, ни аллокаций на кадр.

| Сеттер | Что вписывает | Тип гарда |
|---|---|---|
| `updateDismissOnSwipeDown(value)` | Разрешение свайп-вниз-закрытия из `config.dismiss.onSwipeDown` | `==` |
| `updateFlingVelocityThreshold(value)` | Порог fling-скорости выбора якоря | `==` |
| `updateResistanceMax(value)` | Предел сопротивления overshoot | `==` |
| `updateAlwaysFullScreenOnIme(value)` | Флаг безусловного FullScreen при IME (режим `StayUnderKeyboard` с bottom-слотом) | `==` |
| `updateKeyboardState(value)` | Live-ссылку на состояние IME. `rememberKeyboardLiftState` стабилен по идентичности | `===` |
| `updateDismissScope(value)` | Scope, в котором исполняется suspend-закрытие | `===` |
| `updateDismissRequest(value)` | Живую ссылку на лямбду закрытия. Именно `State`, а не лямбда: иначе новый инстанс на каждую рекомпозицию и гард никогда не сработает | `===` |

#### Internal-методы

| Метод | Зачем |
|---|---|
| `restore(...)` | Восстановление из Saver. `offset` снапнется к якорю с приходом метрик (`snapToCurrentAnchor`) |
| `requestDismiss()` | Не-suspend запрос закрытия: скрим/settle/back/scope зовут синхронно, suspend-лямбда хоста уходит в его scope |
| `onContentRemeasured()` | Контент вырос/подгрузился: высота следует за текущим якорем. `skipCollapsed` + `Content`, заполнивший экран (`isFillMode`) -> авто-FullScreen. В Loading — no-op (высоту ведут `show`/`markContentReady`) |
| `onLiveConfigChanged()` | Живые поля сменились в композиции: пере-резолвим метрики и якорную таблицу. Покоящийся лист (виден, не грузится, палец не тянет) доводим к новому якорю сразу, не дожидаясь жеста. Закрытый лист и Loading оставляем как есть. `copy()` обязателен: `updateMetrics` при неизменных размерах рано выходит и держал бы старый `peekFraction`/`anchors` |
| `onImeShown()` | IME показалась: при нехватке места промоутим в FullScreen, запоминая рест-стейт для отката |
| `onImeHidden()` | IME скрылась: откат из авто-FullScreen к рест-стейту, запомненному при промоушене |
| `snapToCurrentAnchor()` | Снап высоты к якорю текущего стейта без анимации (поворот/resize). Поверх бегущей анимации не снапает: `snapTo` прервал бы её из-за мьютекса `Animatable` |
| `updateMetrics(...)` | Пересобирает метрики и якорную таблицу по свежему замеру. Зовётся из measure на КАЖДОМ пассе (кадре драга/settle), поэтому сравнивает примитивы до аллокации — равный `SheetMetrics` не пишем, иначе снапшот грязнится каждый layout-пасс |
| `markDragStarted()` | Помечает старт живого драга; повторный старт до settle до поля не доходит |
| `enqueueDrag(delta)` | Кладёт drag-дельту в FIFO-канал жестов и помечает лист тянущимся |
| `enqueueSettle(velocity)` | Кладёт settle (по скорости отпускания) в FIFO-канал жестов |
| `processGestures()` | Единственный потребитель FIFO-канала: применяет drag/settle по порядку. Сериализация против гонки drag↔settle — иначе поздний `dragBy.snapTo` отменил бы settle-анимацию возврата |
| `isOffsetAtRestAnchor()` | Стоит ли лист на rest-якоре: `onPreFling` не съедает инерцию списка, если лист уже дорос до якоря |

#### Приватные методы

| Метод | Зачем |
|---|---|
| `awaitMetrics()` | Ждёт первые метрики, если замер ещё не приходил |
| `awaitContentMetrics(loaderMetrics)` | Ждёт первый инстанс метрик, отличный от Loader-метрик по ссылке — то есть ре-замер по реальному контенту |
| `resolveRestTargetAfterConfigChange(table)` | Цель для доводки после смены живых полей. Текущее значение валидно -> оно же. `Custom(key)` с исчезнувшим из `anchors` ключом -> ближайший существующий rest-якорь (`settleTarget` при v=0, без dismiss), а НЕ закрытие листа |
| `shouldPromoteForIme(value, measured)` | Нужно ли форсировать FullScreen при видимой IME. Не-FullScreen лист поднимается `withAdjustmentForKeyboard` на ПОЛНУЮ высоту клавиатуры (безусловный подъём), поэтому «якорь + IME выше потолка» означает, что верх уехал бы за экран. В FullScreen подъёма нет — Middle сжимается. Loading промоутим всегда: подъём швырнул бы Loader на полную высоту IME мгновенно, а FullScreen сжимает его покадрово вместе с клавиатурой |
| `dragBy(delta)` | Живой драг высоты. Обычный ход ограничен верхним rest-якорем и floor снизу; overshoot над ним — с сопротивлением (wrap -> к потолку; fill/FullScreen -> rubber-band в зону статус-бара, spring назад). Overshoot определяется по позиции, не по `currentValue`, поэтому срабатывает и при драге из Collapsed. Порог `atTop` берёт базой `maxHeight`, остальные — верхний rest-якорь |
| `settle(velocity)` | Довод к якорю после отпускания: по скорости или ближайшему из таблицы; Hidden -> свайп-закрытие, если разрешено |
| `animateTo(target)` | Меняет логический стейт и анимирует высоту к его якорю пружиной `NativeSheetSpring` |

#### Константы

| Имя | Зачем |
|---|---|
| `REMEASURE_TIMEOUT_MS` | Мягкий лимит ожидания ре-замера в `markContentReady` перед переходом на безлимитное ожидание |
| `OVERSHOOT_ENTER_EPS_PX` | Допуск «у верхнего якоря» — гасит float-дрожание на границе перед началом сопротивления |

### `state/SheetValue.kt`

Логический стейт высоты листа: `Hidden` — закрыт; `Content` — wrap по контенту; `Collapsed` —
peek-якорь; `ExpandedContent` — контент целиком в пределах экрана; `ExpandedFullScreen` — во весь
экран; `Loading` — Loader-якорь; `Custom` — кастомный якорь по ключу (fill-режим).

### `state/SheetMetrics.kt`

Замеренные размеры листа, из которых считаются якоря. `contentHeightPx` — замер контента при
ограниченной высоте: короткий -> натуральная высота, ленивый/overflow -> `maxHeight`.

| Член | Зачем |
|---|---|
| `maxHeightPx` | Потолок любого стейта: экран минус статус-бар (в покое лист под него не заходит) |
| `peekPx` | Высота Collapsed-якоря по `peekFraction`, зажата в `0..maxHeightPx` |
| `isFillMode` | Заполнил ли контент экран: fill-режим с фикс-якорями vs wrap по контенту |
| `customAnchorPx(key)` | Высота (px) кастомного якоря по ключу; неизвестный ключ -> `peekFraction` |

### `state/SheetAnchorTargets.kt`

| Функция | Зачем |
|---|---|
| `openTarget(skipCollapsed)` | Стейт, в который лист открывается при `show`, по замеру и `skipCollapsed` |
| `expandTarget()` | Стейт раскрытия из Collapsed: контент в пределах экрана -> `ExpandedContent`, иначе `ExpandedFullScreen` |
| `anchorPx(value, skipCollapsed)` | Высота (px) якоря для заданного стейта по текущим метрикам |

### `state/SheetAnchorTable.kt` · класс `SheetAnchorTable`

Якорная математика листа (settle/floor/ceiling/isAtRest). `restEntries` — rest-якоря,
отсортированы по высоте и distinct по px, БЕЗ Hidden: `dismissOnSwipeDown` мутабелен, поэтому
Hidden добавляется в момент запроса settle. Строится через `SheetMetrics.toAnchorTable`.

| Член | Зачем |
|---|---|
| `settleTarget(...)` | Выбор якоря при отпускании: fling -> якорь по направлению, иначе ближайший. `Hidden(0)` — только при `isDismissAllowed` и отсутствии rest-якоря на 0px. `null` — если кандидатов нет (метрики без якорей) |
| `isAtRestAnchor(offset)` | Стоит ли `offset` на rest-якоре (без Hidden): `onPreFling` не съедает инерцию списка, если лист дорос до якоря |
| `AnchorEntry` | Пара «стейт -> высота якоря в px» |
| `ANCHOR_EPS` | Допуск сравнения позиции с якорем в пикселях |

### `state/SheetAnchorTableFactory.kt`

Собирает якорную таблицу из метрик (rest-якоря, без Hidden). fill -> `Collapsed(peek)` /
`ExpandedFullScreen` + кастомные; wrap -> `Content` либо `Collapsed` + `ExpandedContent` по
контенту. Финал — `distinctBy` + `sortedBy` по px.

### `state/XSheetAnchor.kt` · класс `XSheetAnchor`

Кастомный rest-якорь fill-режима: `heightFraction` — доля высоты экрана (0..1), то есть высота
листа снизу. `internal constructor` — якоря создаёт только `XSheetAnchorsBuilder` (DSL).

Равенство по ссылке считается достаточным: якоря живут списком в одном snapshot-поле стейта,
наружу не копируются; смена набора = новый список новых инстансов, подхватывается re-settle.

### `state/XSheetAnchorsBuilder.kt`

DSL-набор кастомных rest-якорей: `"half" at 0.5f` (доля высоты экрана 0..1).
Якоря действуют только в fill-режиме (контент ≥ экрана); в wrap-режиме игнорируются.

### `state/XBottomSheetStateBuilder.kt`

DSL-билдер начальной вариации стейта: исполняется ОДИН раз (`rememberXBottomSheetState`), дальше
поведение меняется прямо на живых полях стейта. `anchorsBuilder` — `internal` для доступа из
inline-групп; при переносе в public API xbet станет `@PublishedApi internal`.

### `state/RememberXBottomSheetState.kt`

Создаёт и запоминает стейт листа. Билдер в `remember` без ключей: `configure` исполняется ОДИН
раз и задаёт начальную вариацию; динамику (`peekFraction`/`anchors`/`skipCollapsed`) даёт не
пересоздание, а живые поля стейта. `rememberSaveable` без ключей: после process-death живые поля
восстанавливаются кодом (билдер + присвоения), в Saver их нет. `crossinline` — `configure`
зовётся из `remember`-лямбды.

### `state/XBottomSheetStateSaver.kt`

Saver стейта листа — инвариант process-death.

- Формат append-only: `[tag, isLoading, additionalTopName, …новое в конец]`.
- Чтение `getOrNull(i) as? T ?: default` — forward-tolerance: старый формат читается новым кодом.
- Теги `SheetValue` заморожены (`h`/`c`/`col`/`ec`/`efs`/`l`/`cu:`) — они персистентны,
  ре-неймы значений их НЕ меняют.
- Живые поля (`peekFraction`/`anchors`/`skipCollapsed`) НЕ сохраняются: стейт пересоздаётся
  билдером (начальная вариация), последующие присвоения повторяет код в композиции.
- `Custom(key)` при удалённом якоре деградирует к `peekFraction`.
- Неизвестное имя `AdditionalTopState` -> `Expanded`; неизвестный тег -> `Hidden`.

### `state/GestureCommand.kt`

Команда FIFO-канала жестов: `Drag` (живой сдвиг высоты) или `Settle` (довод к якорю по скорости
отпускания). Намеренно `data class` без лямбд — конфиг закрытия живёт в свойствах стейта, не в
командах канала.

---

## 3. Жесты

### `gesture/SheetDragGestures.kt`

Драг листа за неподвижные области (хендл / top / bottom); Middle тянется через nested-scroll.
Знак: вниз (`delta > 0`) уменьшает высоту, поэтому в канал уходит `-delta`. Драг и settle
кладутся в FIFO-канал стейта.

### `gesture/SheetNestedScrollConnection.kt`

Связка скролла Middle с высотой листа. Вверх в Collapsed — сначала растим лист, затем скроллим;
вниз — сначала доскролл к началу, затем тянем лист. Мутации `offset` — только через FIFO-канал.
`enabledState` — тот же `derivedStateOf`, что гейтит `sheetDrag`; при видимой IME он `false`,
и nested-scroll двигает только список.

| Место | Зачем |
|---|---|
| `onPreScroll` | Вверх (`delta < 0`) в Collapsed растим лист до `expandTarget`, а не до потолка — иначе жест утащил бы мимо `ExpandedContent` |
| `onPostScroll` | Вниз (`delta > 0`): список уже в начале, остаток тянет лист вниз |
| `onPreFling` — гейт по `isDragging` | Раз драг стартовал, settle обязан завершиться, даже если доступность сменилась (всплыла IME) |
| `onPreFling` — проверка rest-якоря | Лист уже на rest-якоре -> НЕ съедаем скорость (инерция списка продолжится); `settle(0)` лишь сбросит `isDragging`/overshoot |

### `gesture/ResistedOvershoot.kt`

Экспоненциальное сопротивление overshoot: чем дальше тянут за `maxOvershoot`, тем меньше реальный
ход (rubber-band). Нулевой предел отсекается до деления: `0/0` дало бы `NaN` и уронило бы `offset`
в `NaN`.

---

## 4. Раскладка

### `layout/SheetContainer.kt`

Контейнер листа: `SubcomposeLayout` мерит тело ДВАЖДЫ — detect (wrap контента -> `contentHeightPx`)
и place (fixed по `offset` -> реальная высота). `withAdjustmentForKeyboard` (подъём над IME)
вешается только вне FullScreen: в FullScreen лист у потолка, там Middle сжимается вместо подъёма.
Карточка Additional Top — отдельный слой над листом.

| Место | Зачем |
|---|---|
| `sheetBodySlot(fillHeight)` | `fillHeight`: detect wrap'ит тело (замер контента); place заполняет `offset` (фон на всю высоту листа) |
| `detectBody` / `placeBody` | Слот-лямбды объявлены в композиции, а не в measure: стабильная идентичность -> `SubcomposeLayout` не пере-сетит контент на кадр драга |
| `additionalTopBody` | Карточка Additional Top — отдельный слот над листом с клипом верхних углов: её протрузия не входит в detect и в высоту тела, фракция не дёргает контент |
| Два слота в measure | Один `Measurable` нельзя мерить дважды за пасс, поэтому detect (невидимый, при `maxHeight`) и place (при `fixed(offset)`) — разные слоты |
| Высота карточки | Видимая высота = `fraction × (natural − overlap)`. Фракция читается ЗДЕСЬ, в measure — синхронно с `sheetHeight` |
| Порядок размещения | Карточка кладётся раньше surface -> surface поверх, её низ утоплен на `overlap` |

### `layout/SheetBody.kt`

Тело листа: Surface со скруглёнными верхними углами и слотами `top` (sticky) / `middle` (scroll) /
`bottom` (sticky). DragHandle рисуется поверх (TopCenter) и вёрстку не двигает.

| Место | Зачем |
|---|---|
| `isBottomUnderKeyboardMode` | `StayUnderKeyboard` активен только при наличии bottom-слота |
| `sizeModifier` | `fillHeight` (place): тело заполняет `offset` — фон на всю высоту, нет дыры снизу. `!fillHeight` (detect): wrap по контенту |
| `weight(1f, fill = false)` | Middle без нашего `verticalScroll` (скролл даёт контент): короткий wrap'ится, ленивый заполняет |
| Ветка `StayUnderKeyboardContent` | bottom прижат к нижней кромке и уходит ПОД клавиатуру; top остаётся над регионом |
| `pointerInput { detectTapGestures {} }` | No-op гаситель тапов: тап по телу не проваливается на scrim; драги проходят, потому что `detectTapGestures` отменяется по slop |

### `layout/SheetSurface.kt`

Фон-подложка тела листа: Material `Surface` с заданными формой и цветом.

### `layout/SheetInsets.kt`

Пиксельные инсеты и размеры экрана, передаваемые в контейнер листа (замер якорей и паддингов).

### `layout/SheetScrim.kt`

Полноэкранный scrim: тачи под листом гасятся ВСЕГДА (hit-таргет), затемнение опционально.
Тап вне листа -> закрытие при `dismissOnOutsideTap`; тапы по самому листу сюда не долетают
(гасит тело). Alpha разгоняется по `offset` листа.

### `layout/LiftContent.kt`

Lift-режим клавиатуры: над IME едет ВЕСЬ контент. Вне FullScreen подъём делает
`withAdjustmentForKeyboard` (весь лист, в `SheetContainer`); в FullScreen лист у потолка, поэтому
низ поджимается: middle ужимается `withKeyboardShrink`, bottom встаёт над клавиатурой.
Нижний inset читается в measure-фазе — реакция на IME без рекомпозиции корня.

### `layout/StayUnderKeyboardContent.kt`

Middle + Bottom в режиме `StayUnderKeyboard`: middle кончается у верхней кромки IME (или у верха
bottom без IME), bottom прижат к нижней кромке региона и уходит ПОД клавиатуру при IME.
Политика — один инстанс (`remember` без ключей); IME/navBar читаются в measure как `State`,
реакция без пересоздания. Middle сам предоставляет скролл (`LazyColumn`/`verticalScroll`).

### `layout/StayUnderKeyboardMeasurePolicy.kt`

MeasurePolicy режима `StayUnderKeyboard`: раскладывает middle (скролл) и bottom (sticky).
При IME middle кончается у верхней кромки клавиатуры, bottom уходит под неё; без IME bottom стоит
над nav bar. IME/navBar читаются в measure-фазе (`State`) — реакция без рекомпозиции.

| Место | Зачем |
|---|---|
| Гард `maxHeight == Infinity` | Натуральная раскладка на всякий случай; обычно перехватывает override `maxIntrinsicHeight` |
| `reservePx` | Резерв под middle: при IME — высота клавиатуры (middle кончается у её верха); без IME — высота bottom + nav bar |
| `bottomY` | Без IME — над nav bar; при IME — прижат к низу экрана, то есть уходит под клавиатуру |
| `maxIntrinsicHeight` | Натуральная высота = middle + bottom + nav bar. Без override замер с Infinity дал бы мусор в `contentHeightPx` и краш |

### `layout/SoftSheetShadow.kt`

Мягкая тень верхнего края листа (для non-overlay режима). `clip = false`: низ листа уходит за
границу экрана.

---

## 5. Реактивность

### `observe/ObserveSheetState.kt`

Единая точка реактивных `snapshotFlow`-наблюдателей листа. Четыре дочерних коллектора, каждый
переживает отмену соседа. `snapToCurrentAnchor` (поворот) — НЕ здесь: это one-shot по размерам
экрана, а не `snapshotFlow`.

| Коллектор | Зачем |
|---|---|
| Рост/уменьшение контента | Высота следует за контентом. Каждый пересчёт в отдельном `launch`: конкурирующая анимация (`show`/`settle`) прервёт `animateTo`, но не убьёт коллектор |
| Клавиатура | Авто-FullScreen при нехватке места (или всегда для `StayUnderKeyboard`), откат при скрытии. Ключ `(lift, isLoading)`: при снятии Loading под открытой IME промоушен переоценивается заново, иначе Collapsed увёл бы верх за статус-бар. `onImeShown` идемпотентен — гард по стейтам |
| Hidden | `onSheetHidden` -> принудительный дроп IME. `drop(1)`: стартовую эмиссию пропускаем, чтобы скрытый на старте лист не дёргал колбэк; ловим переход visible -> hidden |
| Живые поля | Пере-резолв метрик и якорей + доводка высоты у покоящегося листа. `drop(1)`: стартовую вариацию от билдера не пере-резолвим. Отдельный `launch` на эмиссию — как у коллектора роста |

---

## 6. Конфиг

### `config/XBottomSheetConfig.kt` · класс `XBottomSheetConfig`

Immutable-конфиг листа: визуал (форма/цвета/хендл), поведение закрытия/клавиатуры и вшитая физика
жестов. Собирается билдером; дефолт — `XBottomSheetConfigDefault`, стабильная идентичность держит
skip рекомпозиции.

| Поле | Зачем |
|---|---|
| `scrimFadeDistance` | Дистанция разгона альфы скрима по высоте листа |
| `predictiveBackMaxShift` | Максимальный визуальный сдвиг листа за predictive-back-жестом (Android 14+) |
| `flingVelocityThresholdPxPerSec`, `resistanceMaxPx` | Вшитая физика жестов, НЕ публичный конфиг; менять только с полным прогоном всех demo-кейсов. Значения в px намеренно — перевод в dp менял бы поведение на не-базовой плотности |

### `config/XBottomSheetConfigDefault.kt`

Дефолт `config`-параметра `XBottomSheet`: статичный immutable-инстанс со стабильной
идентичностью -> referential equality держит skip рекомпозиции. Тема не нужна: цвета
`Unspecified`, резолв в корне.

### `config/XBottomSheetConfigBuilder.kt`

DSL-билдер конфига листа: скалярные поля + вложенные группы
(`additionalTop`/`dismiss`/`keyboard`/`colors`). Билдеры групп — `internal` для доступа из
inline-групп; при переносе в public API xbet станут `@PublishedApi internal`. Повторные вызовы
групп мёржатся (last-write-wins): переиспользуется один билдер группы.

### `config/XBottomSheetColors.kt` · `config/XBottomSheetColorsResolve.kt` · `config/XBottomSheetColorsBuilder.kt`

Цвета листа (scrim/фон/хендл). Каждый конфигурируем; `Unspecified` -> дефолт спеки или темы,
резолв в композиции корня, где доступна тема. `specScrim`/`specHandleStatic` — тема-независимые
дефолты спеки. В билдере невыставленный цвет остаётся `Unspecified`.

Функции `resolve*` зовутся ТОЛЬКО в композиции корня. У `resolveSheetBackground` и
`resolveHandleTheme` дефолт читается в `val` до `takeOrElse` — безусловный composable-read.

### `config/DismissConfig.kt` · `config/DismissConfigBuilder.kt`

Способы закрытия листа: тап вне листа, свайп вниз, системный back. Дефолт `onBackPress = false` —
back остаётся у хоста; `BackHandler(enabled = false)` пропускает событие диспетчеру.

### `config/KeyboardConfig.kt` · `config/KeyboardConfigBuilder.kt` · `config/BottomKeyboardBehavior.kt`

Конфиг поведения листа при клавиатуре: режим bottom-слота.
`Lift` — поднимается весь контент. `StayUnderKeyboard` — top+middle поднимаются, bottom прижат к
нижней кромке и уходит ПОД клавиатуру, лист форсированно разворачивается в FullScreen.

### `config/AdditionalTopConfig.kt` · `config/AdditionalTopConfigBuilder.kt`

Конфиг карточки Additional Top: радиус скругления верхних углов.

### `config/RememberXBottomSheetConfig.kt`

Создаёт и запоминает конфиг листа. `remember` БЕЗ ключей: `configure` исполняется ОДИН раз —
конфиг статичен на жизнь composable. Динамика цветов темы идёт через `Unspecified` -> резолв в
корне, НЕ через пересоздание. `rememberSaveable` не нужен: конфиг пересоздаётся из кода после
process-death, стейт уже в Saver.

### `config/XBottomSheetDsl.kt`

DSL-маркер: запрещает неявный доступ к внешнему receiver'у во вложенных билдер-блоках листа.

---

## 7. Мелочи

### `handle/DragHandle.kt` · `handle/DragHandleStyle.kt`

Read-only маркер-«пилюля» перетаскивания: позиционируется вызывающим (align TopCenter), вёрстку
не двигает. Стиль: `Theme` — цвет сепаратора темы; `Static` — белый alpha .40, не зависит от темы
(для статичных фонов).

### `additionaltop/AdditionalTopState.kt`

Состояние карточки Additional Top. Переключается только внешними факторами (кнопка/логика экрана),
не жестами.

### `theme/XTheme.kt`

Стаб-палитра темы листа: фон и цвет сепаратора (= Drag Handle в стиле `Theme`). `XTheme` — точка
доступа к палитре (light/dark по системной теме). В 1XUI заменяется реальной темой.

### `presets/`

| Пресет | Что это |
|---|---|
| `PresetTitle` | Заголовок листа (жирный `titleLarge`, отступы из `PresetSpacing`) |
| `PresetBodyText` | Абзац второстепенного текста в теле листа (отступы из `PresetSpacing`) |
| `PresetMenuCell` | Строка меню с опциональным цветным маркером слева |
| `PresetSearchField` | Однострочное поле поиска в теле листа |
| `PresetSingleButton` | Одиночная кнопка на всю ширину внизу листа |
| `PresetLoader` | Loader для Middle: простой индикатор (Lottie в листе запрещён, скелетон не поддерживается) |
| `PresetVerticalSpace` | Вертикальный отступ заданной высоты между частями тела |
| `PresetSpacing` | Единые отступы пресетов тела листа (бок 16, верх 20, низ 8, зазор 12) |
