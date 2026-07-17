# XBottomSheet — справка для CLI-агента

Кастомный Compose Bottom Sheet по дизайн-спеке 1XUI Core Android · Bottom Sheet v2.5.
Прежде чем менять код — прочитай `XBS-SPEC.md` (полная спека + живой чеклист реализации,
источник правды). Итоги верификации — `XBS-RESULTS.md` (появляется после финального прогона).

## Где что лежит

```
app/src/main/java/
├── com/onexui/bottomsheet/            # компонент
│   ├── XBottomSheet.kt                # корневой composable: scrim, ширина, кастомный Layout-измеритель, слои
│   ├── XBottomSheetState.kt           # стейт-машина, публичное API, resistance, settle
│   ├── SheetAnchors.kt                # SheetValue (6), SheetMetrics, openTarget/expandTarget/anchorPx
│   ├── SheetNestedScroll.kt           # связка скролла Middle с высотой
│   ├── SheetDragGestures.kt           # драг за контент
│   ├── SheetKeyboard.kt               # IME-эффекты (авто-FullScreen, откат, дроп клавиатуры)
│   ├── DragHandle.kt                  # DragHandleStyle.Theme / .Static, read-only
│   ├── AdditionalTop.kt               # sticky-слой: Expanded (утоплен 32dp) / Collapsed (peek 20dp)
│   ├── XBottomSheetDefaults.kt        # ВШИТЫЕ токены (менять только по спеке)
│   ├── theme/XTheme.kt                # стаб темы (в 1XUI заменяется реальной)
│   └── presets/                       # Title, BodyText, MenuCell, 1Button, SearchField, Loader, Spacing
├── org/xplatform/uikit/compose/modifier/keyboard/  # КОПИЯ из xbet (verbatim, не редактировать стиль)
│   ├── adjustment/  # Modifier.withAdjustmentForKeyboard — подъём контента над IME (вместо imePadding)
│   ├── shrink/      # Modifier.withKeyboardShrink — сжатие скролл-секции на высоту IME
│   └── lift/        # KeyboardLiftState — источник высоты/видимости IME
└── com/onexui/demo/                   # XbsDemoActivity (LAUNCHER «XBS Demo») + XbsDemoScreen (11 кейсов)
```

Не путать: в этом же модуле живут `com.alva.*` (порт AlvaBottomSheet) и `com.zorderlab.*`
(z-order-лаба, кнопки 1–8) — это ДРУГИЕ эксперименты, к XBottomSheet отношения не имеют, не трогать.

## API (контракт §3 спеки)

```kotlin
val sheet = rememberXBottomSheetState(skipCollapsed = false, initialLoading = false)

XBottomSheet(
    state = sheet,
    onDismissRequest = { scope.launch { sheet.hide() } }, // единственная точка закрытия
    overlayBackground = true,        // false → затемнения нет, видна тень; тачи под листом блокированы ВСЕГДА
    dragHandle = DragHandleStyle.Theme, // null → хендл скрыт И жесты высоты отключены
    dismissOnOutsideTap = true,
    dismissOnSwipeDown = true,       // игнорируется при dragHandle == null
    additionalTop = { ... },         // sticky-слой над листом; состояние: state.additionalTopState
    top = { ... },                   // sticky, вне скролла
    bottom = { ... },                // прибит к низу, вне скролла
) { /* middle — единственная скроллируемая секция */ }

// Управление: sheet.show() / sheet.expand() / sheet.hide() / sheet.contentReady()
// Состояние ВЫЧИСЛЯЕТСЯ (state.currentValue read-only): факты — skipCollapsed, isLoading, контент.
```

## Стейт-машина (кратко)

`Hidden · Content · Collapsed · ExpandedContent · ExpandedFullScreen · Loading`

- Открытие: контент ≤ 60% экрана → `Content` (высота по контенту); больше → `Collapsed` (фикс 60%).
- `skipCollapsed=true`: сразу `Content` без лимита 60% (потолок — Status Bar); контент выше экрана → `ExpandedFullScreen`.
- Жест вверх из Collapsed: влезает → `ExpandedContent`, нет → `ExpandedFullScreen`.
- Жест вниз с начала списка: Expanded* → Collapsed; Content/Collapsed → закрытие (если dismissOnSwipeDown).
- `Loading`: высота 192dp (+inset), Middle = Loader; закрыть можно, развернуть нельзя; `contentReady()` анимирует к цели.
- Рост контента → высота следует за контентом; при skipCollapsed и overflow → авто-FullScreen.
- Лист никогда не заходит под Status Bar. Тап вне области/кнопка закрывают из любого стейта.

## Правила клавиатуры (§6 + уточнения юзера — ОБЯЗАТЕЛЬНЫ)

1. Клавиатура всегда ПОД листом. Подъём — `withAdjustmentForKeyboard` (НЕ imePadding), сжатие Middle — `withKeyboardShrink`; гейтинг: adjustment при !FullScreen, shrink при FullScreen.
2. Не хватает места → авто-переход в `ExpandedFullScreen`; в FullScreen стейт неизменен.
3. **[юзер]** Авто-разворот от клавиатуры откатывается при её скрытии (`imePromotedFrom`); ручной expand — не откатывается.
4. **[юзер]** При видимой клавиатуре жесты высоты ОТКЛЮЧЕНЫ (свайп/драг не сворачивают и не закрывают лист; nested-scroll двигает только список).
5. **[юзер]** Ручное закрытие (тап снаружи/кнопка/hide()) при открытой клавиатуре сначала ПРИНУДИТЕЛЬНО скрывает IME (clearFocus+hide), потом анимирует закрытие — лист не «улетает» от keyboard-lift оффсета.

## Инсеты (edge-to-edge)

Фон Surface тянется ПОД navigation bar; контент паддится `windowInsetsPadding(WindowInsets.navigationBars)`
ВНУТРИ Surface (Bottom-слот всегда над баром). Замер контента идёт С паддингом. Loading-якорь = 192dp + navBarInset
(видимая зона Loader'а = 192). В Activity: `enableEdgeToEdge()` + `isNavigationBarContrastEnforced = false`.

## Технические решения (зафиксированы, не переделывать без причины)

- **Без BoxWithConstraints / SubcomposeLayout** (требование юзера): ширина — `LocalWindowInfo.containerSize`
  (<600dp → 100%, ≥600dp → 512dp центр); замер — кастомный `Layout` с одним ребёнком:
  `maxIntrinsicHeight(width)` для contentHeight (unclamped), `measure(Constraints.fixed(width, offset))` для плейсмента.
  Композиция тела — одна. SubcomposeLayout легален ТОЛЬКО если в слот заедет intrinsics-несовместимый контент (Lazy) — фиксировать причину в XBS-SPEC.md.
- Compose-state (`mutableStateOf`), НЕ StateFlow.
- Resistance: только Content/ExpandedContent, `max * (1 − exp(−raw/max))` от СЫРОГО аккумулированного overshoot.
- Settle: ближайший якорь по midpoint между соседними якорями + учёт скорости.
- Отклонения от спеки помечены в XBS-SPEC.md словом «ПЕРЕСМОТРЕНО» — сверяйся с ними, а не с исходным текстом спеки.

## Жёсткие запреты (спека §9)

Цвета/высота НЕ в API (вшиты/вычисляются) · скелетон не поддерживается · RTL лист не зеркалится ·
Lottie внутри БШ запрещён · отступы внутри частей не менять (только пресет Spacing) · один БШ на экран.

## Сборка / прогон

```bash
cd /Users/Victor/work/bs-zorder-lab
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.zorderlab/com.onexui.demo.XbsDemoActivity   # экран с 11 кейсами a..k
# эталонный экран дизайн-матрицы (411×731dp @420): adb shell wm size 1080x1919
# 3-button nav: adb shell cmd overlay enable com.android.internal.systemui.navbar.threebutton
```

Тесты НЕ писать (правило проекта). KDoc к файлам не генерировать; комментарии на русском.
