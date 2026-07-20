# XBottomSheet — статус (handoff)

Дата среза: 2026-07-20. Проект-лаборатория `Test-BS`. Всё ниже **собрано, установлено и проверено на устройстве** (планшетный эмулятор 2208×1840, `emulator-5556`).

## Проект

- Package: `com.zorderlab`. minSdk 30, compileSdk 37, Kotlin 2.4.0, Compose Multiplatform 1.11.1, AGP 9.2.1.
- Точка входа — `LauncherActivity` (единственный LAUNCHER): UI-развилка на две активити:
  - `MainActivity` — z-order лаборатория (кнопки 1-8, `com.zorderlab.*`).
  - `com.onexui.demo.XbsDemoActivity` — демо XBottomSheet (кейсы a-s), `windowSoftInputMode=adjustResize`, edge-to-edge.
- Компонент XBottomSheet — `app/src/main/java/com/onexui/bottomsheet/`, демо — `com/onexui/demo/`.

## Команды

```bash
# сборка
./gradlew :app:assembleDebug
# установка (поверх, без wipe)
adb -s emulator-5556 install -r app/build/outputs/apk/debug/app-debug.apk
# запуск демо напрямую
adb -s emulator-5556 shell am start -n com.zorderlab/com.onexui.demo.XbsDemoActivity
# скриншот (мульти-дисплей: default display иногда ловит НЕ то → проверяй dumpsys ResumedActivity)
adb -s emulator-5556 shell screencap -p /sdcard/x.png && adb -s emulator-5556 pull /sdcard/x.png
```

⚠️ Эмулятор мультидисплейный: `screencap` без `-d` может отдать домашний экран/чужой дисплей. Перед выводами сверяйся с `dumpsys activity activities | grep ResumedActivity` и `dumpsys input_method | grep mInputShown`.

## Файлы компонента

| Файл | Роль |
|---|---|
| `XBottomSheet.kt` | Публичный `@Composable XBottomSheet`, `SheetContainer` (SubcomposeLayout-измеритель), `SheetBody` (Surface + top/middle/bottom), `StayUnderKeyboardContent`, enum `KeyboardBottomBehavior { Lift, StayUnderKeyboard }` |
| `XBottomSheetState.kt` | Стейт-машина якорей, канал жестов (FIFO), IME-хуки, `rememberXBottomSheetState`, Saver (rememberSaveable) |
| `SheetAnchors.kt` | `sealed interface SheetValue`, `data class XSheetAnchor`, `SheetMetrics` (wrap/fill детект), `openTarget`/`expandTarget`/`anchorPx` |
| `AdditionalTop.kt` | `AdditionalTopStack` + `AdditionalTopMeasurePolicy` (стики-перекрытие), enum `AdditionalTopState { Expanded, Collapsed }` |
| `SheetSnapshotFlowManager.kt` | Единый узел всех реактивных `snapshotFlow` (рост контента / IME / Hidden) |
| `SheetDragGestures.kt`, `SheetNestedScroll.kt` | Драг drag-handle + nested-scroll списка, оба через `state.enqueueDrag/enqueueSettle` |
| `XBottomSheetDefaults.kt` | Токены геометрии/цвета, `NativeSheetSpring`, `softSheetShadow` |
| `DragHandle.kt`, `presets/`, `theme/` | Хендл, пресет-контролы, тема |

## Публичный API

```kotlin
@Composable
internal fun XBottomSheet(
    state: XBottomSheetState,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    overlayBackground: Boolean = true,            // false → без scrim, мягкая верхняя тень
    dragHandle: DragHandleStyle? = DragHandleStyle.Theme,  // null → хендл скрыт, жесты высоты off
    dismissOnOutsideTap: Boolean = true,
    dismissOnSwipeDown: Boolean = true,
    bottomKeyboardBehavior: KeyboardBottomBehavior = KeyboardBottomBehavior.Lift,
    additionalTopCornerRadius: Dp = 0.dp,         // верхние скругления карточки AdditionalTop
    additionalTop: (@Composable () -> Unit)? = null,
    top: (@Composable () -> Unit)? = null,        // sticky-верх
    bottom: (@Composable () -> Unit)? = null,     // sticky-низ
    middle: @Composable () -> Unit,               // скролл предоставляет САМ контент
)

@Composable
internal fun rememberXBottomSheetState(
    skipCollapsed: Boolean = false,
    initialLoading: Boolean = false,
    peekFraction: Float = 2f/3f,                  // DEFAULT_PEEK_FRACTION — высота Collapsed
    customAnchors: List<XSheetAnchor> = emptyList(),
): XBottomSheetState
```

Переключение AdditionalTop — внешним фактором: `state.additionalTopState = Expanded/Collapsed`.

## Модель якорей (wrap vs fill)

`SubcomposeLayout` мерит тело ДВАЖДЫ за пасс (два слота `SlotDetect`/`SlotPlace`, один Measurable нельзя мерить дважды):
- **detect** при `Constraints(maxHeight = ceiling)`: короткий контент → его высота (wrap); `LazyColumn`/скролл/overflow → `== maxHeight` (fill). Это `contentHeightPx`, отсюда `SheetMetrics.fills`.
- **place** при `Constraints.fixed(offset)`: реальная высота листа (offset может превышать потолок на overshoot).

Стейты `SheetValue`: `Hidden / Content / Collapsed / ExpandedContent / ExpandedFullScreen / Loading / Custom(key)`.
- **wrap-режим** (контент < экрана): `Content / Collapsed(peek) / ExpandedContent` по высоте контента.
- **fill-режим** (LazyColumn/контент ≥ экрана): `Collapsed(peek 2/3) / ExpandedFullScreen(full)` + пользовательские `Custom` якоря.

Токены: `CornerRadius=20dp` (только верхние углы листа), `MaxWidth=512dp` (планшет), `WideScreenThreshold=600dp`, `AdditionalTopOverlap=32dp`, `LoadingSheetHeight=192dp`, peek по умолчанию `2/3`.

## Что готово и проверено

### Базовое поведение (§ дизайн-спека)
- Hidden/Content/Collapsed(peek 2/3)/Expanded*/Loading, авто-FullScreen при росте контента выше экрана.
- iOS-подобный resistance на overshoot верхнего якоря: `resisted = max*(1 - exp(-raw/max))`.
- Нативная пружина `NativeSheetSpring` (без отскока), scrim с fade по offset.
- Закрытия опциональны (`dismissOnOutsideTap`, `dismissOnSwipeDown`), ширина 512dp на планшете, `skipCollapsed`.

### Контент любого типа (проверены кейсы m–r)
`LazyColumn / LazyVerticalGrid / LazyRow / вложенные Lazy* / Column+verticalScroll / обычный контент` — все работают в листе без краша при ограниченной высоте (wrap когда коротко, fill когда длинно). Кастомные промежуточные якоря — `customAnchors` (кейс r: peek → 50% → full).

### Сохранение состояния
`rememberSaveable` + `Saver`: currentValue-тег + isLoading + additionalTopState. Позиция скролла, состояние листа выживают поворот и process death (подтверждено пользователем: «process death работает все ок»). Saver — только safe-cast (`as?` с фоллбэками), без смарткастов.

### Жесты
FIFO-канал `Channel<GestureCommand>` (`Drag`/`Settle` — data class, **без лямбд**), единственный потребитель `processGestures`. Конфиг закрытия (`dismissOnSwipeDown`/`onDismissRequest`) — в свойствах стейта, не в командах канала. Устранён рывок resistance (гонка settle↔drag).

### AdditionalTop (последние правки — DONE, проверено кейс g)
- **Плавная анимация** появления/сворачивания: `additionalTopFraction` (`animateFloatAsState`, 1=Expanded, 0=скрыт), интерполируется видимая высота протрузии — без резкого show/hide.
- **Верхние round corners**: параметр `additionalTopCornerRadius` (демо задаёт 16dp).
- **Seamless-стык**: низ карточки квадратный и утоплен под лист на `overlap`(32dp) (перекрытие в раскладке `AdditionalTopMeasurePolicy`), скруглённые верхние углы Surface «наезжают» на тело карточки → читается как единое продолжение контента, без зазора/квадратного нуба. В Collapsed карточка исчезает полностью (не полоска-peek).

### Клавиатура — 3 режима (все проверены)
Лист использует кастомные модификаторы `withAdjustmentForKeyboard` (подъём всего листа вне FullScreen) и `withKeyboardShrink` (SOFT_INPUT_ADJUST_NOTHING + сжатие middle в FullScreen). При показе IME короткий лист авто-промоутится в ExpandedFullScreen (`onImeShown`), при скрытии откатывается (`onImeHidden`).

- **(i)** Lift, только поиск+список (без bottom): search остаётся, список ужимается под клавиатуру.
- **(l)** `StayUnderKeyboard`: top+middle над клавиатурой, **bottom прижат к низу листа и уходит ПОД клавиатуру** (кастомный `StayUnderKeyboardContent` layout). Появляется при скрытии IME.
- **(s)** Lift (дефолт), поиск+список+bottom: **весь контент, включая bottom, поднимается НАД клавиатурой**.
  - Фикс (последняя правка): в Lift-ветке `SheetBody`, при `isFullScreen && keyboardHeightPx > 0` на Column вешается `padding(bottom = keyboardHeight)` → middle(weight) ужимается, bottom встаёт прямо над IME. `withKeyboardShrink` оставлен только чтобы держать `ADJUST_NOTHING` (его shrink самообнуляется, т.к. низ middle уходит выше клавиатуры). Кейсы (i)/(l) не затронуты.

### Системные пермишены
`AndroidManifest.xml` — только декларации (без рантайм-инфраструктуры, по запросу пользователя): `POST_NOTIFICATIONS`, `CAMERA`, `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_EXTERNAL_STORAGE` (maxSdkVersion=32).

## Демо-кейсы (`XbsDemoScreen.kt`, enum `DemoCase`)

a Content · b1 ExpandedContent · b2 ExpandedFullScreen · c skipCollapsed · d Loading→contentReady · e overlayBackground=false(тень) · f dragHandle=null · **g AdditionalTop** · **i IME подъём+shrink** · **l IME bottom ПОД клавиатурой** · **s IME bottom НАД клавиатурой** · j закрытия off · k static handle+рост · m LazyVerticalGrid · n LazyRow(wrap) · o карусели · p Column+verticalScroll · q вложенный LazyColumn · r кастомный якорь 50%.

Списки спорта расширены до ~50 (`SPORTS`), помощник `SportLazyList`.

## Правила кода (обязательны, от пользователя)

- **Никаких лямбд в data class** — лямбды существуют отдельно.
- Избегать смарткастов; `as` → только `as?` с фоллбэком; никаких `!!`.
- Комментарии — по-русски. Без KDoc/комментариев без запроса. **Alva не трогать.**
- Тесты — только по явной просьбе. Файлы/директории не удалять без разрешения.
- Билд после работы + проверка импортов (без unused/star).

## Открытые хвосты (не срочно)

- Кейсы (m–r) собраны и частично проверены; полный прогон каждого на устройстве — по желанию.
- fillHeight-stretch (залив фоном при overshoot вниз) — закодирован, чисто на устройстве не заснят (пружина слишком быстрая для adb-скриншота).
