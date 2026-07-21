# XBS-RESULTS — верификация XBottomSheet против дизайн-матрицы

Прогоны: эмулятор `emulator-5554`, экраны 411×731dp @420 (референс дизайн-матрицы, `wm size 1080x1919`)
и 841dp-планшет (2208×1840, кейс ширины ≥600dp + репро фолдабла юзера). 3-button nav включён.
Спека: `XBS-SPEC.md` · справка: `XBOTTOMSHEET.md`.

## Матрица кейсов — итог

| Кейс | Ожидание (дизайн) | Факт | Доказательство |
|---|---|---|---|
| (a) Content короткий | высота по контенту ≤60%, Bottom над nav bar | ✅ | скрин: «Выйти» целиком над 3-button nav, фон листа под баром |
| (b1) Collapsed → Expand | стоп на ExpandedContent (по контенту, не потолок) | ✅ | скрин: верх ниже Status Bar; фикс гарда роста по anchorPx(expandTarget) |
| (b2) Collapsed → Expand | ExpandedFullScreen (весь экран) | ✅ | скрин |
| (c) skipCollapsed=true | Content по контенту, лимит 60% отключён | ✅ | после сокращения demo до 10 элементов ветка видна |
| (d) Loading → contentReady | Loader 192dp → анимация к целевой высоте | ✅ | скрин до/после; фикс stale-метрик + снап-бага (Animatable-мьютекс) |
| (e) overlayBackground=false | тень видна, тачи блокированы | ✅ | скрин |
| (f) dragHandle=null | хендл скрыт, жесты высоты отключены | ✅ | скрин |
| (g) AdditionalTop | Expanded: утоплен 32dp; Collapsed: полоска 20dp, контент alpha 0 | ✅ | скрины обоих состояний; фикс: фейдится только контент, фон-полоска видима |
| (i) IME | подъём/сжатие; авто-FullScreen при нехватке | ✅ | скрин: FullScreen, поле в фокусе, Middle над клавиатурой |
| (j) Закрытия выключены | тап снаружи/свайп не закрывают | ✅ | скрин: лист жив после тапа снаружи |
| (k) Static handle + рост | onContentRemeasured тянет высоту; overflow → FullScreen | ✅ | logcat `content=2381 maxH=1766` → FullScreen (верификация исполнителя) |

## Правила клавиатуры (уточнения юзера — сверх дизайна, вписаны в XBS-SPEC.md §6)

| Правило | Факт |
|---|---|
| Авто-FullScreen от IME откатывается при её скрытии (`imePromotedFrom`); ручной expand — нет | ✅ back → лист вернулся в исходный стейт |
| При видимой IME жесты высоты отключены (`interactionsEnabled && !imeVisible`) | ✅ свайп при клавиатуре скроллит список, лист не двигается/не закрывается |
| Ручное закрытие при открытой IME принудительно роняет клавиатуру, лист не «улетает» | ✅ планшет: `mInputShown` true → тап по скриму → **false**, лист закрыт прижатым к низу |

## История фиксов (3 раунда после базовой реализации)

1. **Рефактор по требованиям юзера**: BoxWithConstraints → `LocalWindowInfo`; SubcomposeLayout → кастомный
   `Layout` (intrinsic + `Constraints.fixed(width, offset)`, одна композиция тела); imePadding → xbet-пакет
   `withAdjustmentForKeyboard`/`withKeyboardShrink` (9 файлов verbatim, гейтинг по стейту).
2. **Nav-bar баг (скрин юзера)**: инсет на внутреннюю Column внутри Surface (фон edge-to-edge), замер с паддингом,
   Loading-якорь 192+inset, `isNavigationBarContrastEnforced=false`.
3. **Ревью-раунд** (workflow, 5 линз, 64 пункта ✅ / 6 major / 9 minor): peek AdditionalTop; additionalTopState → state;
   resistance по сырому overshoot; settle по midpoint соседних якорей; скрытие IME при закрытии/сворачивании;
   contentReady по свежим метрикам. Найдены и починены 2 глубоких корня: коллектор роста умирал от
   CancellationException прерванной анимации (изоляция в дочерний launch); `snapToCurrentAnchor` отменял
   show()/contentReady через мьютекс Animatable (гард `offset.isRunning`).

## Раунд 4 — баг-репорт юзера (2026-07-20), 2 фикса, верифицированы на эмуляторе (2208×1840)

Оба репорта воспроизведены и починены по-настоящему (первая попытка чинила не тот корень и была откачена).

- **(j) свайп вниз «закрывал» лист при `dismissOnSwipeDown=false`.** Корень (доказан логами `settle`):
  `settle` ВСЕГДА выбирал Content и возвращал лист (Hidden не в кандидатах при `dismiss=false`) — то есть
  `onDismissRequest` не вызывался. Но `dragBy` клампил offset к `[0, ceiling]`, поэтому палец свободно уводил
  лист вниз до 0 (лист визуально исчезал), а пружина назад воспринималась как «закрылось». Фикс: при
  `dismissOnSwipeDown=false` нижняя граница драга = самый нижний разрешённый якорь (`lowestRestAnchorPx`), а не 0 —
  лист не уезжает ниже своего состояния. Проверено: (j) палец тянет вниз — лист стоит; (a)/(b1) с разрешённым
  закрытием — свайп по-прежнему закрывает/сворачивает.
- **(g) Additional Top не исчезал в Collapsed (оставалась полоска peek 20dp с квадратными углами).** По требованию
  юзера — полное исчезновение. Фикс: `SheetBody` рендерит слот только в `Expanded`; в `Collapsed` — один
  `sheetSurface` со своими скруглёнными углами (карточка убирается целиком, `contentHeight` уменьшается,
  высота анимируется). Expanded не тронут — углы там как были. Проверено: Collapsed — карточки нет, углы листа
  скруглены; Expanded — карточка «Добавить в купон» на месте.

## Раунд 5 — гибкость подъёма над клавиатурой (запрос юзера, 2026-07-20)

Добавлена возможность выбирать, что поднимать над клавиатурой: весь контент или только часть (bottom остаётся
под клавиатурой). Новый параметр `XBottomSheet(bottomKeyboardBehavior: KeyboardBottomBehavior = Lift)`:
- `Lift` (дефолт) — весь контент (top/middle/bottom) над клавиатурой. Поведение как было; кейсы a–k не менялись.
- `StayUnderKeyboard` — над клавиатурой только top+middle; bottom прижат к нижней кромке листа и уходит ПОД
  клавиатуру, появляется при её скрытии. Требует FullScreen — при показе IME лист форсированно разворачивается
  (`onImeShown(alwaysFullScreen=true)`).

Реализация: `SheetBody` в режиме StayUnderKeyboard рендерит middle+bottom через кастомный `Layout`
(`StayUnderKeyboardMeasurePolicy`): middle сверху кончается у верхней кромки клавиатуры (при IME) / у верха bottom
(без IME); bottom прижат к низу региона (низ листа) — при IME под клавиатурой, без IME над nav bar.

Новый demo-кейс **(l) «IME — поиск + список + bottom ПОД клавиатурой»** (`CaseImeBottomUnderKeyboard`). Верификация
на эмуляторе: без IME — «Показать все» видна снизу; фокус поиска → FullScreen, поиск+список над клавиатурой,
«Показать все» под клавиатурой; скрытие IME → кнопка вернулась. Регрессия (i) с дефолтным Lift — зелёная.

**Пойманный краш** (репорт юзера при открытии (l)): кастомный `Layout` без override `maxIntrinsicHeight` — при
`body.maxIntrinsicHeight()` из `SheetContainer` Compose прогонял `measure` c `maxHeight=Infinity` → `layout(w, ∞)` →
мусор в contentHeightPx и падение. Фикс: полноценный `MeasurePolicy` c override `maxIntrinsicHeight` (конечная
высота = список + bottom + navBar) + guard от Infinity в `measure`.

## Раунд 6 — рывок при возврате из сопротивления (репорт юзера, 2026-07-20)

Симптом: эффект растягивания (драг за drag handle + отпускание) возвращался «через раз» рывком — лист
застревал вверху и спрыгивал. Диагностика логами (лог юзера — решающий): жест ФРАГМЕНТИРУЕТСЯ на сессии,
`settle` стартует `animateTo` возврата, а прилетевший позже `dragBy.snapTo` (независимый `scope.launch`) её
`CANCELLED` — offset застревает на overshoot, потом финальный settle доигрывает = рывок. Корень — гонка
независимых корутин `scope.launch { dragBy }` и `scope.launch { settle }` над одним `offset` (мьютекс Animatable).

Фикс: все мутации offset сериализованы через ОДИН FIFO-канал `Channel<GestureCommand>` с единственным
потребителем `processGestures` (запускается раз из XBottomSheet). Drag'и применяются inline; settle крутится
дочерним job'ом — следующий Drag отменяет его `cancelAndJoin` ДО первого кадра → нет видимого дёрганья.
Команды — чистые data-класс без лямбд (правило юзера); конфиг закрытия (`dismissOnSwipeDown`/`onDismissRequest`)
живёт в свойствах стейта. Жесты зовут `enqueueDrag`/`enqueueSettle` (синхронно, не suspend). Убраны `scope`
из `sheetDrag`/`SheetNestedScrollConnection`.

Верификация: 6 агрессивных драг-отпусканий → каждый ровно один `animateTo … DONE` за ~640мс, **ноль CANCELLED**
(было — CANCELLED через раз); видео-монтаж возврата монотонный (без прыжка вверх). Регрессия: (a) свайп→закрытие,
(b1) свайп вверх→Expanded/вниз→Collapsed, (j) драг вниз→pinned — зелёные.

## Раунд 7 — консолидация snapshotFlow в SheetSnapshotFlowManager (запрос юзера)

Все РЕАКТИВНЫЕ snapshotFlow-наблюдатели листа сведены в один `SheetSnapshotFlowManager` (файл
`SheetKeyboard.kt` → `SheetSnapshotFlowManager.kt`, git mv): вместо 3 разрозненных `LaunchedEffect { snapshotFlow{}
.collect }` в XBottomSheet — один LaunchedEffect с 3 дочерними коллекторами: (1) рост контента →
`onContentRemeasured`; (2) клавиатура → авто-FullScreen/откат (§6); (3) Hidden → дроп IME (§6). `snapToCurrentAnchor`
(поворот) оставлен отдельно — это one-shot по размерам экрана, не snapshotFlow. `awaitMetrics`/`contentReady` в
стейте — one-shot `first()`, не персистентные коллекторы, не трогались. Верифицировано: (i) IME авто-FullScreen +
дроп при закрытии, (k) рост контента → авто-FullScreen — зелёные.

## Отклонения от буквы спеки (помечены ПЕРЕСМОТРЕНО в XBS-SPEC.md)

- Измеритель: кастомный Layout вместо SubcomposeLayout (требование юзера; Subcompose легален для Lazy-слотов).
- Клавиатура: xbet-модификаторы вместо imePadding (требование юзера).
- ShadowSoft: elevation 16dp вместо y=−4/blur=32 (точный blur-API требует API 31, minSdk 30).
- Loading-якорь: 192dp + navBarInset (edge-to-edge; видимая зона Loader'а — ровно 192).
- internal-видимость API — снять при переносе в 1XUI Core.
- **Additional Top в Collapsed — полное скрытие вместо peek 20dp (требование юзера).** Спека §1 задаёт полоску
  peek 20dp; юзер потребовал, чтобы слот в Collapsed исчезал целиком. Реализовано в `SheetBody` (слот только в
  Expanded). При переносе в 1XUI Core решить, оставлять ли peek конфигурируемым.
- **Свайп вниз при `dismissOnSwipeDown=false` — лист не тянется ниже своего якоря (нижняя граница драга = якорь).**

## Раунд 8 — качественный рефактор (2026-07-21): перф/нейминг/DSL, полная регрессия

Исполнение `XBS-QUALITY-RESEARCH.md` (53 находки, 5 линз) фазами P0–P4 с попунктными апрувами юзера.
Правила юзера №0–10 (функционал неприкосновенен; без animate*AsState; лямбды не в remember; без
remember(keys){create}; объекты только под remember; derivedStateOf; простой нейминг; DSL-билдеры;
без it; без лямбд в data class; без require-валидаций) — соблюдены, подтверждено отдельным ревью
(P1+P2: критов нет) и компайлер-метриками (Strong Skipping ON, 100% skippability).

Ключевое: Animatable вместо animateFloatAsState (чтение в measure/draw) · стабильные MeasurePolicy/
NestedScrollConnection (входы через State) · слот-идентичность SubcomposeLayout · IME-высота целиком
в layout-фазе (замер: 1 событие на переход) · кэш floor-якоря (ноль аллокаций в драге) · drop(1)
Hidden-коллектора · settle-гейт по isDragging · E1 fills-баг AdditionalTop · E2 overflow→isFillMode ·
E3 markContentReady только для видимого листа · 16 переименований (protrusionPx→cardVisibleHeightPx,
fills→isFillMode, contentReady→markContentReady и др.) · SheetInsets/AnchorCandidate/LiftContent ·
DSL: rememberXBottomSheetState{} + xBottomSheetConfig{} (dismiss{}/keyboard{}), атомарная миграция
11 call-sites demo.

Финальная регрессия (эмулятор 2208×1840, adb): 19/19 кейсов зелёные. Визуально сверены посткейсы:
a (navbar), b1 (ExpandedContent), k (рост→FullScreen), g (полное скрытие AdditionalTop + тоггл),
i (IME true→back→false, откат авто-FullScreen), l (bottom ПОД клавиатурой и возврат), s (IME),
j (тап снаружи не закрывает), r (осадка на кастомном якоре 50% медленным драгом; fling вниз →
закрытие по направлению — штатная механика), d (Loading→корректная высота), e (тень).
Заметка для ручного тестирования: fling вниз быстрее ~400px/s из peek уводит в закрытие, к якорю
50% — медленный драг (порог FLING_VELOCITY_THRESHOLD).

## Раунд 9 (2026-07-21) — LOW-хвост L1–L8 + баг анимации AdditionalTop

L1 updateMetrics без аллокаций на кадр · L2 одинарный клип · L3 demo-фильтр в remember(query) ·
L4 markContentReady не принимает лоадерные метрики · L5 ре-оценка IME-промоушена при снятии Loading ·
L6 потолок драга = верхний rest-якорь (пустой Surface не тянется) · L7 Log.w про customAnchors в wrap ·
L8 инерция списка не гасится после составного жеста.
Баг юзера (тоггл AdditionalTop дёргал контент листа): карточка вынесена в отдельный subcompose-слот НАД
листом, высота синхронна во внутреннем measure-пассе (без догоняющей пружины). Приёмка: «Событие»
y=1650 неподвижен во всех состояниях тоггла (пиксель-в-пиксель), конечные высоты 1:1, драг жив.
Ресерч закрыт полностью: 53/53 находок (исполнено/осознанно отклонено с фиксацией).
