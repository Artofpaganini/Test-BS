# XBottomSheet — справка для CLI-агента

Кастомный Compose Bottom Sheet по дизайн-спеке 1XUI Core Android · Bottom Sheet v2.5.
Прежде чем менять код — прочитай `XBS-SPEC.md` (полная спека + живой чеклист реализации,
источник правды). Итоги верификации — `XBS-RESULTS.md` (появляется после финального прогона).

## Где что лежит

```
app/src/main/java/
├── com/onexui/bottomsheet/            # компонент
│   ├── XBottomSheet.kt                # корневой composable: scrim, ширина, кастомный Layout-измеритель, слои
│   ├── XBottomSheetConfig.kt          # DSL-конфиг листа: XBottomSheetConfig + dismiss/keyboard, @DslMarker
│   ├── XBottomSheetState.kt           # стейт-машина, публичное API (+ DSL-конфиг стейта), resistance, settle
│   ├── SheetAnchors.kt                # SheetValue (6), SheetMetrics, openTarget/expandTarget/anchorPx
│   ├── SheetNestedScroll.kt           # связка скролла Middle с высотой
│   ├── SheetDragGestures.kt           # драг за контент
│   ├── ObserveSheetState.kt    # ВСЕ реактивные snapshotFlow-наблюдатели в одном месте:
│   │                                  #   рост контента · IME авто-FullScreen/откат · Hidden→дроп клавиатуры
│   ├── DragHandle.kt                  # DragHandleStyle.Theme / .Static, read-only
│   ├── AdditionalTop.kt               # sticky-слой: Expanded (утоплен 32dp) / Collapsed (peek 20dp)
│   ├── XBottomSheetDefaults.kt        # ВШИТЫЕ токены (менять только по спеке)
│   ├── theme/XTheme.kt                # стаб темы (в 1XUI заменяется реальной)
│   └── presets/                       # Title, BodyText, MenuCell, 1Button, SearchField, Loader, Spacing
├── org/xplatform/uikit/compose/modifier/keyboard/  # КОПИЯ из xbet (verbatim, не редактировать стиль)
│   ├── adjustment/  # Modifier.withAdjustmentForKeyboard — подъём контента над IME (вместо imePadding)
│   ├── shrink/      # Modifier.withKeyboardShrink — сжатие скролл-секции на высоту IME
│   └── lift/        # KeyboardLiftState — источник высоты/видимости IME
└── com/onexui/demo/                   # XbsDemoActivity + XbsDemoScreen (19 кейсов a..s: матрица стейтов,
                                       #   IME l/s (bottom под/над клавиатурой), Lazy-контент m/n/o/p/q,
                                       #   кастомный якорь r). Вход в приложение — com.zorderlab.LauncherActivity
                                       #   (единый LAUNCHER: развилка «Z-Order лаба» / «XBS Demo»)
```

Не путать: в этом же модуле живут `com.alva.*` (порт AlvaBottomSheet) и `com.zorderlab.*`
(z-order-лаба, кнопки 1–8) — это ДРУГИЕ эксперименты, к XBottomSheet отношения не имеют, не трогать.

## API (контракт §3 спеки + расширения юзера, раунды 4–7)

```kotlin
val sheet = rememberXBottomSheetState {          // DSL-конфиг стейта (дефолты — в билдере)
    skipCollapsed = false
    initialLoading = false
    peekFraction = XBottomSheetDefaults.PeekFraction   // высота Collapsed, дефолт 2/3 экрана (юзер; спека давала 0.60)
    anchors { anchor("half", 0.5f) }             // свои промежуточные якоря (fill-режим), опц.
}

XBottomSheet(
    state = sheet,
    onDismissRequest = { scope.launch { sheet.hide() } }, // единственная точка закрытия
    config = xBottomSheetConfig {                 // DSL-конфиг листа (@Immutable data, value-equality → skip рекомпозиции)
        overlayBackground = true                  // false → затемнения нет, видна тень; тачи под листом блокированы ВСЕГДА
        dragHandle = DragHandleStyle.Theme        // null → хендл скрыт И жесты высоты отключены
        additionalTopCornerRadius = 0.dp          // верхние скругления карточки Additional Top
        dismiss {                                 // группа флагов закрытия
            onOutsideTap = true
            onSwipeDown = true                    // false → лист не тянется ниже своего якоря (floor = нижний якорь)
        }
        keyboard {                                // поведение bottom при клавиатуре
            bottomBehavior = BottomKeyboardBehavior.Lift  // StayUnderKeyboard → bottom уходит ПОД клавиатуру
        }
    },
    additionalTop = { ... },         // sticky-слой; состояние: state.additionalTopState (плавная анимация fraction)
    top = { ... },                   // sticky, вне скролла
    bottom = { ... },                // прибит к низу, вне скролла
) { /* middle: контент САМ предоставляет скролл — LazyColumn/LazyGrid/Column+verticalScroll */ }

// config опционален: config = XBottomSheetConfig.Default — если всё дефолтное.
// Управление: sheet.show() / sheet.expand() / sheet.hide() / sheet.markContentReady()
// Состояние ВЫЧИСЛЯЕТСЯ (state.currentValue read-only). Переживает ротацию/process-death (rememberSaveable+Saver).
```

## Стейт-машина (кратко)

`Hidden · Content · Collapsed · ExpandedContent · ExpandedFullScreen · Loading · Custom(key)` — sealed interface.

Режим определяется ЗАМЕРОМ контента (`SheetMetrics.isFillMode`):
- **wrap** (контент < экрана): Content / Collapsed(peek) / ExpandedContent — по высоте контента, как в спеке.
- **fill** (LazyColumn или контент ≥ экрана): Collapsed(peek) / ExpandedFullScreen + якоря `Custom(key)` —
  свайп останавливается на ближайшем (peek → custom → full).

- Открытие: контент ≤ peek → `Content`; больше → `Collapsed` (peek = `peekFraction`, дефолт 2/3 экрана).
- `skipCollapsed=true`: сразу `Content` без лимита peek (потолок — Status Bar); fills → `ExpandedFullScreen`.
- Жест вверх из Collapsed: влезает → `ExpandedContent`, нет/fills → `ExpandedFullScreen`.
- Жест вниз с начала списка: Expanded* → Collapsed; Content/Collapsed → закрытие (если dismissOnSwipeDown).
- `Loading`: высота 192dp (+inset), Middle = Loader; закрыть можно, развернуть нельзя; `markContentReady()` ждёт
  ре-замер реального контента (не Loader'а) и анимирует к цели.
- Рост контента → высота следует за контентом; при skipCollapsed и overflow → авто-FullScreen.
- Лист не стоит под Status Bar (rubber-band overshoot из FullScreen может временно заходить — spring назад).
- Тап вне области/кнопка закрывают из любого стейта. Жесты сериализованы FIFO-каналом
  (`enqueueDrag`/`enqueueSettle` → единственный потребитель `processGestures`) — гонок drag↔settle нет.

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

- **Без BoxWithConstraints**; ширина — `LocalWindowInfo.containerSize` (<600dp → 100%, ≥600dp → 512dp центр).
- **Замер — SubcomposeLayout с двумя слотами** (легализован юзером под Lazy-контент — intrinsics с LazyColumn
  не работают): слот DETECT (невидимый) меряется при `maxHeight = ceiling` → contentHeightPx (короткий контент
  wrap'ится < max; Lazy/скролл заполняет == max → fill-режим); слот PLACE (видимый) меряется при
  `Constraints.fixed(width, offset)`. Тело в каждом слоте композируется один раз.
- **Middle не оборачивается в свой verticalScroll** — скролл предоставляет контент слота
  (LazyColumn/LazyGrid/LazyRow/Column+verticalScroll — все проверены demo-кейсами m/n/o/p/q).
- **AdditionalTop** — измеряемый стек (`AdditionalTopStack(additionalTopContent, sheetContent)` + MeasurePolicy):
  видимая высота карточки = fraction × (natural − overlap), Collapsed = полное скрытие (peek 20dp из спеки отменён
  юзером). Анимация — через `Animatable` (НЕ animate*AsState; драйвится snapshotFlow, `.value` читается в measure →
  инвалидация layout, а не композиции). Верхние углы карточки — `additionalTopCornerRadius` (в `XBottomSheetConfig`).
- **StayUnderKeyboard** — кастомный `StayUnderKeyboardMeasurePolicy` (middle кончается у верха IME, bottom прижат
  к низу и уходит под клавиатуру) с override `maxIntrinsicHeight` (без него — краш на Infinity-замере).
- **Все реактивные наблюдатели — в `ObserveSheetState`** (рост контента / IME show+hide / Hidden→дроп IME);
  `snapToCurrentAnchor` (поворот) — отдельный one-shot LaunchedEffect по размерам экрана.
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
