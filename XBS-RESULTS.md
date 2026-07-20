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
