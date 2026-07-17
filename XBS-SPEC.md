# XBS-SPEC — XBottomSheet (1XUI Core Android · Bottom Sheet v2.5 · Jetpack Compose)

Источник правды: claude.ai/design проект `09a495d4-7a61-4c14-b1d3-93f87b0b9cd2`
(«БШ - Кодовая база.dc.html» + «БШ - Экран и логика.dc.html»). Этот файл — полный перенос правил,
живой документ реализации (preserve-task-context).

## 1. Анатомия

`Overlay Background · Sheet (Drag Handle · Additional Top · Top · Middle · Bottom)`.
Все цвета вшиты — красить составные части нельзя.

- **Status Bar** системный 24dp; БШ никогда не заходит под него.
- **Drag Handle** — 36×4dp, r360, отступ сверху 8dp; поверх контента, вёрстку не двигает; read-only
  (нажатий не поддерживает). Если взаимодействие с высотой отключено — хендл скрывается (и свайп-жест недоступен).
  Стили: `Theme` (основной, `XTheme.colors.separator` #D8DDE9) · `Static` (`White(alpha 0.40)`, не темазависимый —
  для Additional Top / статичных фонов).
- **Additional Top** — опционален; sticky. Состояния (переключаются ТОЛЬКО внешними факторами):
  `Expanded` — раскрыт, утоплен на 32dp под основную часть; `Collapsed` — видна полоска 20dp, контент в Alpha 0.
  Кейс: «Добавить в купон» / «Отслеживать».
- **Top** — опционален; sticky (закреплён у верха БШ, вне скролла).
- **Middle** — единственная скроллируемая область.
- **Bottom** — опционален; прибит к нижнему краю, скролл не влияет.
- **Overlay Background** rgba(0,0,0,.4); опционален. Блокировка тачей под БШ — ВСЕГДА, даже без затемнения.
- **Тень** `Shadow Soft` 0 −4 32 rgba(0,0,0,.1) — видна ТОЛЬКО при `overlayBackground = false`.
- Лист: радиус 20 20 0 0, фон `XTheme.colors.backgroundContent` (#FFFFFF в светлой) — вшит.

## 2. Вшитые токены

```kotlin
object XBottomSheetDefaults {
    val CornerRadius            = 20.dp                    // только верхние углы
    val MaxWidth                = 512.dp                   // на экранах ≥ 600dp
    val WideScreenThreshold     = 600.dp
    const val CollapsedFraction = 0.60f                    // высота Collapsed = 60% экрана
    val DragHandleSize          = DpSize(36.dp, 4.dp)      // r = 360, отступ сверху 8dp
    val DragHandleTopPadding    = 8.dp
    val AdditionalTopOverlap    = 32.dp                    // Expanded: утоплен под основную часть
    val AdditionalTopPeek       = 20.dp                    // Collapsed: видимая полоска
    val LoadingSheetHeight      = 192.dp                   // Middle = пресет Loader
    val ScrimColor              = Color.Black.copy(alpha = 0.40f)
    val ShadowSoft              = Shadow(y = (-4).dp, blur = 32.dp, color = Color.Black.copy(0.10f))
    val SheetBackground         get() = XTheme.colors.backgroundContent   // #FFFFFF в светлой
    val HandleTheme             get() = XTheme.colors.separator           // #D8DDE9, темазависимый
    val HandleStatic            = Color.White.copy(alpha = 0.40f)         // не зависит от темы
}
```

## 3. Контракт API

```kotlin
@Composable
fun XBottomSheet(
    state: XBottomSheetState,                        // rememberXBottomSheetState(skipCollapsed = …)
    onDismissRequest: () -> Unit,                    // единственная точка закрытия
    modifier: Modifier = Modifier,
    overlayBackground: Boolean = true,               // затемнение; тень видна только при false
    dragHandle: DragHandleStyle? = DragHandleStyle.Theme,  // null = скрыт → жесты высоты отключены
    dismissOnOutsideTap: Boolean = true,             // все способы закрытия опциональны
    dismissOnSwipeDown: Boolean = true,              // игнорируется, если dragHandle == null
    additionalTop: (@Composable () -> Unit)? = null, // sticky-слой над листом
    top:    (@Composable () -> Unit)? = null,        // sticky сверху
    bottom: (@Composable () -> Unit)? = null,        // прибит снизу
    middle: @Composable ColumnScope.() -> Unit,      // единственная скроллируемая секция
)

enum class DragHandleStyle { Theme, Static }

@Composable
fun rememberXBottomSheetState(
    skipCollapsed: Boolean = false,
    initialLoading: Boolean = false,
): XBottomSheetState
```

Принципы: состояние ВЫЧИСЛЯЕТСЯ, а не задаётся (разработчик управляет фактами: skipCollapsed, isLoading,
контент); цвета вшиты (API не принимает цветов); один скролл (Middle); один БШ на экран (хост закрывает
предыдущий при открытии нового). Слоты зеркалят Figma: x/Additional Top, x/Top, x/Bottom, x/Middle.
Отсутствие слота = выключенная секция. `dragHandle = null` ⇒ полностью отключено взаимодействие с высотой.

## 4. Стейт-машина и якоря

```kotlin
enum class SheetValue { Hidden, Content, Collapsed, ExpandedContent, ExpandedFullScreen, Loading }

internal data class SheetMetrics(
    val screenHeightPx: Int,
    val statusBarPx: Int,
    val contentHeightPx: Int,   // additionalTop + top + middle(по контенту) + bottom
) {
    val maxHeightPx get() = screenHeightPx - statusBarPx                // потолок любого стейта
    val collapsedPx get() = (screenHeightPx * CollapsedFraction).roundToInt()
}

internal fun SheetMetrics.openTarget(skipCollapsed: Boolean): SheetValue = when {
    skipCollapsed -> if (contentHeightPx <= maxHeightPx) SheetValue.Content
                     else SheetValue.ExpandedFullScreen
    contentHeightPx <= collapsedPx -> SheetValue.Content
    else -> SheetValue.Collapsed
}

internal fun SheetMetrics.expandTarget(): SheetValue =
    if (contentHeightPx <= maxHeightPx) SheetValue.ExpandedContent
    else SheetValue.ExpandedFullScreen

internal fun SheetMetrics.anchorPx(value: SheetValue): Int = when (value) {
    SheetValue.Hidden             -> 0
    SheetValue.Loading            -> loadingSheetHeightPx                 // 192dp
    SheetValue.Content            -> minOf(contentHeightPx, collapsedPx)  // при skipCollapsed — maxHeightPx
    SheetValue.Collapsed          -> collapsedPx
    SheetValue.ExpandedContent    -> minOf(contentHeightPx, maxHeightPx)
    SheetValue.ExpandedFullScreen -> maxHeightPx
}
```

```kotlin
@Stable
class XBottomSheetState internal constructor(val skipCollapsed: Boolean, initialLoading: Boolean) {
    var currentValue by mutableStateOf(SheetValue.Hidden); private set
    var isLoading by mutableStateOf(initialLoading)         // Loading = Content + SkipCollapsed=true, Middle = Loader
    internal var metrics: SheetMetrics? = null
    internal val offset = Animatable(0f)                    // текущая высота листа, px

    val isVisible get() = currentValue != SheetValue.Hidden

    suspend fun show()   = animateTo(if (isLoading) SheetValue.Loading else metrics!!.openTarget(skipCollapsed))
    suspend fun expand() { if (currentValue == SheetValue.Collapsed) animateTo(metrics!!.expandTarget()) }
    suspend fun hide()   = animateTo(SheetValue.Hidden)

    // Данные загрузились: снять Loading и анимировать высоту к целевому значению
    suspend fun contentReady() { isLoading = false; animateTo(metrics!!.openTarget(skipCollapsed)) }

    // Контент вырос/уменьшился (например, после подгрузки страницы списка)
    internal suspend fun onContentRemeasured() {
        if (skipCollapsed && currentValue == SheetValue.Content
            && metrics!!.contentHeightPx > metrics!!.maxHeightPx) animateTo(SheetValue.ExpandedFullScreen)
        else offset.animateTo(metrics!!.anchorPx(currentValue).toFloat())  // высота следует за контентом
    }

    private suspend fun animateTo(target: SheetValue) {
        currentValue = target
        offset.animateTo(metrics!!.anchorPx(target).toFloat(), NativeSheetSpring)  // имитация нативной анимации
    }
}
```

NB: `var … by mutableStateOf` — Compose state (НЕ StateFlow; правило `.update{}` не применяется).
Метрики пересчитываются при каждом изменении размеров (поворот, resize, рост контента).

### Матрица состояний (эталон для demo/скриншотов, экран 411×731)

skipCollapsed=false: Content (контент ≤ 60% → высота по контенту) · Collapsed (контент > 60% → фикс 438px=60%)
· ExpandedContent (после разворота, контент влезает; лимит Status Bar) · ExpandedFullScreen (707 · весь экран).
skipCollapsed=true: Content по контенту без лимита 60% (лимит только Status Bar); контент выше экрана → FullScreen.
Loading=true: стейт Content+SkipCollapsed, высота 192, Middle=Loader; закрыть можно, развернуть нельзя;
после загрузки высота анимируется к цели. Предпочтительно грузить данные ДО открытия; Loading — запасной.
Overlay=false: тень Shadow Soft видна; контент под БШ всё равно заблокирован.

### Переходы (стейт-машина высоты)

- Открытие: контент ≤ 60% → Content; > 60% → Collapsed. (skipCollapsed: ≤ экрана → Content; > → FullScreen.)
- Жест вверх из Collapsed: контент помещается → ExpandedContent; нет → ExpandedFullScreen.
- Жест вниз с начала списка: Expanded* → Collapsed; Content/Collapsed → закрытие.
- skipCollapsed: контент вырос выше экрана → авто FullScreen.
- Тап вне области и кнопка закрывают БШ из ЛЮБОГО состояния.

## 5. Вложенный скролл и жесты

- Жест вверх: в Collapsed — СНАЧАЛА разворачиваем лист, ЗАТЕМ скроллим список; в остальных — просто скроллим.
- Жест вниз: пока список не в начале — доскролл к началу; в начале — тянем лист вниз.
- `SheetNestedScrollConnection(state, interactionsEnabled)`: `interactionsEnabled=false`, если dragHandle==null
  или Loading. onPreScroll: available.y<0 и Collapsed → dragBy+поглотить. onPostScroll: available.y>0 → dragBy.
  onPreFling: isDragging → settle(velocity).
- `settle()`: по скорости и ближайшему якорю; вниз из Expanded* → Collapsed; вниз из Content/Collapsed →
  onDismissRequest() (если dismissOnSwipeDown); вверх из Collapsed → expandTarget().
- Сопротивление (ТОЛЬКО Content и ExpandedContent): `resistedOvershoot(raw, max) = max * (1 - exp(-raw/max))`;
  после отпускания — spring к якорю. В Collapsed и FullScreen НЕ применяется.

## 6. Клавиатура

Всегда ПОД листом (реализация — xbet `withAdjustmentForKeyboard`/`withKeyboardShrink`, см. фазу D), перекрывать
не может. Content/Collapsed/ExpandedContent: места хватает — стейт не меняется, лист поднимается; нет —
авто-переход в ExpandedFullScreen. В FullScreen стейт неизменен.
IME скрывается при: закрытии БШ; FullScreen→Collapsed; сворачивании/закрытии из Content; внешнем действии.

**Откат авто-промоушена при скрытии IME (уточнение от юзера).** Если в ExpandedFullScreen попали АВТОМАТИЧЕСКИ
из-за клавиатуры (не хватило места), то при её скрытии лист откатывается к стейту, который был ДО промоушена.
Реализация: `imePromotedFrom: SheetValue?` запоминается в `onImeShown` перед `animateTo(ExpandedFullScreen)`;
`onImeHidden` при `currentValue == ExpandedFullScreen && imePromotedFrom != null` делает `animateTo(imePromotedFrom)`
и сбрасывает флаг. РУЧНОЕ взаимодействие с высотой (`expand()`/`settle()`) сбрасывает `imePromotedFrom` → ручной
разворот НЕ откатывается.

## 7. Корневой composable (референс)

```
BoxWithConstraints(fillMaxSize) {
  1. Scrim: затемнение опционально, блокировка тачей ВСЕГДА (pointerInput + опц. background(ScrimColor));
     тап → onDismissRequest() если dismissOnOutsideTap.
  2. Ширина: maxWidth < 600dp → fillMaxWidth; иначе width(512dp), центр; пересчёт при повороте автоматич.
  3. Column(align BottomCenter, height = state.offsetDp, padding top = statusBar,
            softShadow если !overlayBackground, nestedScroll, imePadding) {
       additionalTop → AdditionalTopLayer(state.additionalTopState)
       Surface(RoundedCornerShape(20,20,0,0), SheetBackground, weight(1f)) {
         Column { top?.invoke(); Column(weight(1f, fill=false).verticalScroll){ isLoading→Loader else middle() }; bottom?.invoke() }
       }
       dragHandle → DragHandle(style, align TopCenter, padding top 8)  // поверх, вёрстку не двигает
     }
  4. SheetImeEffect(state)
}
```

## 8. Структура модуля

```
designsystem/bottomsheet/
├── XBottomSheet.kt            // корневой composable: scrim, ширина, слои
├── XBottomSheetState.kt       // стейт-машина + публичное API show/expand/hide
├── SheetAnchors.kt            // метрики экрана → якоря высоты
├── SheetNestedScroll.kt       // связка скролла Middle с высотой БШ
├── SheetDragGestures.kt       // драг за контент, сопротивление, settle
├── SheetKeyboard.kt           // реакция на IME
├── DragHandle.kt              // Theme / Static
├── AdditionalTop.kt           // слот с состояниями Expanded / Collapsed
├── XBottomSheetDefaults.kt    // вшитые токены геометрии и цвета
└── presets/                   // Title, BodyText, Buttons, MenuCell, Spacing…
```

## 9. Жёсткие правила спеки

- Не входит в API намеренно: цвета фона/скрима/хендла (вшиты); высота листа (вычисляется);
  скелетон (компонент не поддерживает); RTL-зеркалирование (лист не зеркалится).
- Отступы внутри составных частей менять запрещено. Отступы — пресетом Spacing.
- Lottie внутри БШ запрещён.
- Один БШ на экран; открытие нового закрывает предыдущий (наложение — только исключение).
- Анимация — имитация нативной (spring, `NativeSheetSpring`).
- Дизайнерские отступы (рекомендация): бок 16, верх 20, низ 8.

## 10. Пример использования (из спеки)

```kotlin
val sheet = rememberXBottomSheetState(skipCollapsed = false, initialLoading = true)
val scope = rememberCoroutineScope()

LaunchedEffect(Unit) {
    sheet.show()                          // откроется в Loading (192dp, Loader)
    val sports = repository.loadSports()
    sheet.contentReady()                  // высота анимируется к целевому стейту
}

XBottomSheet(
    state = sheet,
    onDismissRequest = { scope.launch { sheet.hide() } },
    top = {
        PresetTitle("Вид спорта")
        PresetSearchField(query, onQueryChange)
    },
    bottom = { Preset1Button("Показать все", onClick = ::showAll) },
) {
    sports.forEach { PresetMenuCell(it.title, it.icon, onClick = { select(it) }) }
}
```

## Figma → API

| Параметр Figma | В коде |
|---|---|
| State (4) | `state.currentValue` — вычисляется, не задаётся |
| Skip Collapsed | `rememberXBottomSheetState(skipCollapsed)` |
| Overlay Background | `overlayBackground: Boolean` |
| Loading | `state.isLoading` / `contentReady()` |
| Drag Handle · Color | `dragHandle: DragHandleStyle?` (null — скрыт) |
| Additional Top | `additionalTop`-слот + AdditionalTopState |
| x / Top · Middle · Bottom | слоты top / middle / bottom |
| Закрытие (3 способа) | dismissOnOutsideTap · dismissOnSwipeDown · hide() |

---

## План реализации (architect, живой чеклист)

Пакет реализации — `com.onexui.bottomsheet` в модуле `:app` (namespace остаётся `com.zorderlab`).
Alva-порт (`com.alva.*`) и z-order-лаба (`com.zorderlab.*`, кнопки 1–8) НЕ трогаются.

### Файловый план (`app/src/main/java/com/onexui/…`)

Компонент — `com/onexui/bottomsheet/`:
- [x] `XBottomSheetDefaults.kt` — вшитые токены (§2), `NativeSheetSpring`, soft-shadow-модификатор.
- [x] `theme/XTheme.kt` — стаб темы: `XColors(backgroundContent, separator)`, `XTheme.colors` как `@Composable get`
      по `isSystemInDarkTheme()`. Light: `#FFFFFF` / `#D8DDE9`; Dark: `#121212` / `#2A2F3A` (значения лабовые).
- [x] `SheetAnchors.kt` — `enum SheetValue` (6), `SheetMetrics`, `openTarget`/`expandTarget`/`anchorPx` (§4).
- [x] `XBottomSheetState.kt` — `@Stable class XBottomSheetState` + `rememberXBottomSheetState(...)`,
      публичное API `show/expand/hide/contentReady`, internal `onContentRemeasured` (§4). Compose-state, не StateFlow.
- [x] `DragHandle.kt` — `enum DragHandleStyle { Theme, Static }` + `DragHandle` (36×4dp, r360, top 8dp, read-only).
- [x] `AdditionalTop.kt` — `AdditionalTopState` (Expanded/Collapsed) + `AdditionalTopLayer` (overlap 32 / peek 20).
- [x] `SheetKeyboard.kt` — (ПЕРЕСМОТРЕНО) `SheetKeyboardAutoFullScreenEffect(state, keyboardState)`: авто-FullScreen
      поверх `KeyboardLiftState`. Подъём/сжатие — xbet-модификаторы (см. ниже), не `WindowInsets.ime`.
- [x] xbet keyboard-пакет (копия из Mobile_Android_OnexBet, KDoc сохранён): `org.xplatform.uikit.compose.modifier.
      keyboard.{lift, lift.utils, adjustment, shrink}` — `rememberKeyboardLiftState`/`KeyboardLiftState`,
      `withAdjustmentForKeyboard`, `withKeyboardShrink`, `SoftInputModeGuard`. Свой пакет, Alva-копию не смешиваем.
- [x] `SheetNestedScroll.kt` — `SheetNestedScrollConnection(state, interactionsEnabled)` (§5).
- [x] `SheetDragGestures.kt` — драг за контент, `resistedOvershoot`, `settle` (§5).
- [x] `XBottomSheet.kt` — корневой composable (§7): ширина через `LocalWindowInfo.containerSize` (<600 fill /
      ≥600 512dp центр), **кастомный `Layout`-измеритель контента** (без BoxWithConstraints/SubcomposeLayout —
      ПЕРЕСМОТРЕНО по требованию юзера), scrim, слои additionalTop/top/middle(scroll)/bottom, DragHandle.

Пресеты — `com/onexui/bottomsheet/presets/` (только нужные demo):
- [x] `PresetTitle.kt` · `PresetBodyText.kt` · `PresetMenuCell.kt` · `Preset1Button.kt` · `PresetSearchField.kt`
      · `PresetLoader.kt` (не Lottie, `LoadingSheetHeight` 192dp) · `PresetSpacing.kt`.

Demo — `com/onexui/demo/`:
- [x] `XbsDemoActivity.kt` — отдельная exported Activity, `enableEdgeToEdge()` + `adjustResize`, единственный ComposeView.
- [x] `XbsDemoScreen.kt` — 7 кнопок-кейсов (a–g), один активный кейс = один XBottomSheet на экран.
- [x] `AndroidManifest.xml` — добавить `<activity android:name="com.onexui.demo.XbsDemoActivity"` (полное имя, вне
      namespace), `exported=true`, свой `LAUNCHER` intent-filter, `label="XBS Demo"`, `windowSoftInputMode="adjustResize"`.
      Правка только additive; блок MainActivity не трогать.

### Решение: измерение `contentHeightPx` — кастомный `Layout` (ПЕРЕСМОТРЕНО по требованию юзера)

> Исходное решение архитектора (SubcomposeLayout) ОТМЕНЕНО по требованию юзера: избегать `BoxWithConstraints`
> и `SubcomposeLayout`. Ниже — актуальная реализация.

- **Размеры экрана**: без `BoxWithConstraints`. Ширина/высота — из `LocalWindowInfo.current.containerSize` (px) +
  `LocalDensity` (как `DeviceStateStub`). Ширина <600dp → `fillMaxWidth`, ≥600dp → `width(512dp)` центр. При повороте
  `containerSize` обновляется сам → пересчёт метрик автоматический.
- **Измеритель**: без `SubcomposeLayout`. `XBottomSheet.kt` меряет тело листа кастомным `Layout` с ОДНИМ
  ребёнком-телом, тело композируется РОВНО ОДИН раз. Натуральную высоту контента берём `maxIntrinsicHeight(width)`
  (unclamped — проходит сквозь `verticalScroll`, нужно чтобы детектить overflow skipCollapsed→FullScreen);
  `measure` тела за проход один — при `Constraints.fixed(width, offset)`, поэтому top/middle/bottom рефлоуятся
  корректно (sticky-низ у нижнего края offset, клип не нужен). `contentHeightPx` репортим в state с guard
  (`updateMetrics` не пишет одинаковое) — snapshot не грязнится каждый layout-пасс.
- Почему НЕ «мерить при потолке + клип по offset» (вариант из первичной постановки): клип прибил бы sticky-низ к
  потолку и срезал его при offset<ceiling; потолочный замер клампится `verticalScroll` и не даёт задетектить
  overflow. Вариант с intrinsic+fixed(offset) закрывает оба пункта одной композицией.

### Решение: хост demo — отдельная exported Activity со своим LAUNCHER

`XbsDemoActivity` с собственным `LAUNCHER`-intent-filter (вторая иконка «XBS Demo»). Обоснование: правка манифеста
чисто additive (новый `<activity>`-блок), НОЛЬ изменений в Kotlin z-order-лабы. Альтернатива «кнопка в HostFragment»
отвергнута — HostFragment это z-order-флоу-код (правило «флоу лабы не трогать»). Запуск на верификации:
`adb shell am start -n com.zorderlab/com.onexui.demo.XbsDemoActivity`.

### Кейсы demo (эталон — матрица §4). 11 кнопок: a, b1, b2, c, d, e, f, g, i, j, k

- [x] (a) Content короткий (logout): `skipCollapsed=false`, Title+BodyText+1Button → стейт Content, высота по контенту.
- [x] (b1) Средний список (контент >60%, но ≤ экрана) → открытие Collapsed 60%, свайп вверх → **ExpandedContent** (по контенту, лимит Status Bar).
- [x] (b2) Огромный список (контент > экрана) → открытие Collapsed 60%, свайп вверх → **ExpandedFullScreen** (весь экран).
- [x] (c) `skipCollapsed=true`: контент без лимита 60% (лимит — Status Bar); контент выше экрана → FullScreen.
- [x] (d) Loading→contentReady: `initialLoading=true`, `show()`→Loading 192dp/Loader, `delay`→`contentReady()`→анимация.
- [x] (e) `overlayBackground=false`: видна тень Shadow Soft, тачи под листом всё равно заблокированы.
- [x] (f) `dragHandle=null`: хендл скрыт, взаимодействие с высотой отключено (нет свайпа/expand), закрытие — тап/`hide()`.
- [x] (g) additionalTop «Добавить в купон/Отслеживать»: слот с Expanded/Collapsed, переключается кнопкой в кейсе.
- [x] (i) IME: `PresetSearchField` в top + короткий список; фокус на поле → подъём (withAdjustmentForKeyboard) и авто-FullScreen + сжатие Middle (withKeyboardShrink); закрытие скрывает IME.
- [x] (j) Закрытия выключены: `dismissOnOutsideTap=false, dismissOnSwipeDown=false` — закрыть только кнопкой (`hide()`).
- [x] (k) `DragHandleStyle.Static` + рост контента: кнопка в Middle «+10» → onContentRemeasured тянет высоту; при `skipCollapsed=true` рост выше экрана → авто-FullScreen.

### Фазы реализации (для xbet-compose-expert) + критерий готовности

- [x] **Фаза A — State/Anchors.** `XBottomSheetDefaults`, `theme/XTheme`, `SheetAnchors`, `XBottomSheetState`.
      Критерий: `:app:assembleDebug` зелёный; API `rememberXBottomSheetState`/`show`/`expand`/`hide`/`contentReady`
      присутствует; `openTarget`/`expandTarget`/`anchorPx` считают по §4 (проверка логикой, без тестов).
- [x] **Фаза B — Root + слоты + измерение.** (ПЕРЕСМОТРЕНО) `XBottomSheet.kt`: ширина через
      `LocalWindowInfo.containerSize` (без BoxWithConstraints), кастомный `Layout`-измеритель (без SubcomposeLayout),
      Surface(20,20,0,0), Column top/middle(scroll)/bottom, DragHandle, scrim. Контент — временные заглушки (Text/Box).
      Критерий: короткий контент открывается Content по высоте, длинный — Collapsed 60% (глазами/скриншот);
      `dragHandle=null` прячет хендл; ширина 512dp по центру на ≥600dp. Проверено на эмуляторе (411dp fill / 512dp центр).
- [x] **Фаза C — NestedScroll + жесты + resistance.** `SheetNestedScroll`, `SheetDragGestures`, `settle`,
      `resistedOvershoot`. Критерий: свайп вверх из Collapsed сначала разворачивает лист, потом скроллит; свайп вниз
      с начала списка → Collapsed (из Expanded*) или закрытие (из Content/Collapsed); resistance виден в Content/ExpandedContent.
- [x] **Фаза D — IME.** (ПЕРЕСМОТРЕНО) xbet-модификаторы клавиатуры (скопирован пакет
      `org.xplatform.uikit.compose.modifier.keyboard`, Alva-копию не трогаем): `withAdjustmentForKeyboard` на Column
      листа (подъём над IME, вместо `imePadding`), `withKeyboardShrink` на Middle (сжатие при нехватке места);
      авто-FullScreen поверх `KeyboardLiftState` (`SheetKeyboardAutoFullScreenEffect`). Гейтинг по стейту: подъём
      только вне FullScreen, сжатие только в FullScreen (иначе двойной ход). Критерий: фокус на SearchField поднимает
      лист / авто-разворачивает в FullScreen. Проверено на эмуляторе (Collapsed→FullScreen + shrink Middle, IME shown).
- [x] **Фаза E — AdditionalTop.** `AdditionalTop.kt` состояния Expanded/Collapsed (overlap 32 / peek 20).
      Критерий: кейс (g) переключает состояния внешней кнопкой; слой sticky, вёрстку листа не двигает.
- [x] **Фаза F — Presets.** 7 пресетов; замена заглушек фазы B на реальные пресеты. Критерий: все кейсы рендерятся
      пресетами; Loader без Lottie; отступы — только пресетом Spacing.
- [x] **Фаза G — Demo.** `XbsDemoActivity` + `XbsDemoScreen` + 7 кейсов + манифест. Критерий: `:app:assembleDebug`
      зелёный; запуск Activity; каждый кейс открывает живой лист с реальными жестами. Один лист на экран.

### Риски / подводные камни

1. **Измерение контента** — (ПЕРЕСМОТРЕНО) кастомный `Layout` с одним ребёнком: `maxIntrinsicHeight` (unclamped) для
   натуральной высоты + `measure(fixed(offset))` для плейсмента, одна композиция. Без SubcomposeLayout. Решение фазы B.
2. **Bootstrap первого открытия** — offset=0 (Hidden) до измерения; `contentHeightPx` считается синхронно из intrinsic
   на первом же layout-пассе (тело композируется до show()), затем `show()` анимирует к цели → «попа» нет.
3. **Поворот/resize** — `SheetMetrics` зависит от screenHeightPx/statusBarPx/contentHeightPx; при повороте
   `LocalWindowInfo.containerSize` и `WindowInsets.statusBars` дают новые значения, ширина меняет reflow контента →
   новый contentHeightPx. `LaunchedEffect(screenHeightPx, statusBarPx)` → `snapToCurrentAnchor` (snap, не animate).
4. **Animatable-offset ↔ verticalScroll** — единственный писатель в offset: drag = `snapTo`, settle = `animateTo`,
   бегущий settle отменяется новым drag'ом (мьютекс Animatable). Все мутации — через suspend-методы стейта.
   `interactionsEnabled=false` при `dragHandle==null`/Loading — жесты высоты выключены полностью.
5. **IME в одном окне (без Dialog)** — (ПЕРЕСМОТРЕНО) вместо `imePadding` — xbet-модификаторы
   `withAdjustmentForKeyboard` (подъём, ставит softInputMode ADJUST_NOTHING) + `withKeyboardShrink` (сжатие Middle),
   источник — `KeyboardLiftState` (ViewTreeObserver над IME-инсетами). Демо-Activity — `ComponentActivity` с
   edge-to-edge + `adjustResize` (модификатор перебивает softInputMode в рантайме). Нехватка места → авто-FullScreen
   поверх KeyboardLiftState. Окно (z-order) тут НЕ нужно.
6. **Shadow Soft** — виден только при `overlayBackground=false`; в Compose нет готового «верхнего мягкого» токена —
   кастомный модификатор в `XBottomSheetDefaults`.
7. **Loader / скелетон** — Lottie запрещён (§9), скелетон компонент не поддерживает; Loader = простой прогресс 192dp.
8. **Изоляция demo** — отдельная exported Activity, полное имя в манифесте, ноль правок z-order-лабы.
