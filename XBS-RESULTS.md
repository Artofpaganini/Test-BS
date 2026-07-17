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

## Отклонения от буквы спеки (помечены ПЕРЕСМОТРЕНО в XBS-SPEC.md)

- Измеритель: кастомный Layout вместо SubcomposeLayout (требование юзера; Subcompose легален для Lazy-слотов).
- Клавиатура: xbet-модификаторы вместо imePadding (требование юзера).
- ShadowSoft: elevation 16dp вместо y=−4/blur=32 (точный blur-API требует API 31, minSdk 30).
- Loading-якорь: 192dp + navBarInset (edge-to-edge; видимая зона Loader'а — ровно 192).
- internal-видимость API — снять при переносе в 1XUI Core.
