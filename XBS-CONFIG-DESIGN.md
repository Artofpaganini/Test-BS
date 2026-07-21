# XBS-CONFIG-DESIGN — конфиг-движимая архитектура XBottomSheet (ФИНАЛ)

> Синтез: мой аудит компонента + 4 линзы ресерча (`XBS-CONFIG-RESEARCH.md`) + две корректировки юзера
> (Scope-концепция обязательна; цвета конфигурируемы с дефолтами спеки/темы через `Unspecified`; размеры/
> геометрия жёстко вшиты). Код НЕ меняется — это дизайн под попунктный апрув. Инвариант правило №0:
> 19 demo-кейсов работают 1:1.
>
> Каждое предложение помечено ID (A–K) + вердикт: [STRONG] / [RECOMMEND] / [OPTIONAL]. Апрувить можно
> попунктно. Отвергнутое (§6) — с причинами; вердикты reject линз уважены (принцип «не забивать гвозди»).

---

## ПОПРАВКИ ЮЗЕРА (апрув — реализуются в этом раунде)

Три уточнения к дизайну сверх §2–§6. Приоритет над исходными формулировками там, где расходятся.

1. **`onDismissRequest` → `suspend () -> Unit`.** Сигнатура закрытия становится suspend; компонент вызывает её
   через свой remembered `CoroutineScope` (`scope.launch { onDismissRequest() }` во всех точках: скрим-тап,
   settle→Hidden, BackHandler, `scope.requestDismiss()`). Call-site упрощается до `onDismissRequest = { state.hide() }`
   (в demo — `{ state.hide(); onClose() }`), `scope.launch`/`dismiss`-замыкание из 19 кейсов уходит. Проводка поля
   `state.onDismissRequest` **остаётся не-suspend** (`() -> Unit`): корень записывает в него launcher
   `{ scope.launch { currentOnDismiss() } }` через `SideEffect`, а settle/скрим зовут поле синхронно. Поведение
   settle→dismiss — 1:1; дроп IME (§6.5) срабатывает синхронно с закрытием (`onSheetHidden` на переходе
   `isVisible→false`, т.е. в начале `hide()` до анимации offset) — сохраняется.
2. **Конфиг ОБОРАЧИВАЕМ — composable-фабрика `rememberXBottomSheetConfig`** (решение юзера, заменяет исходную
   формулировку). Вместо голой `xBottomSheetConfig(...)`:
   `@Composable internal fun rememberXBottomSheetConfig(configure) = remember { XBottomSheetConfigBuilder().apply(configure).build() }`.
   `remember` БЕЗ ключей → `configure` исполняется ОДИН раз; конфиг статичен на жизнь composable (динамика цветов
   темы — через `Unspecified`→резолв в корне, НЕ через пересоздание конфига). `rememberSaveable` НЕ делаем (конфиг
   пересоздаётся из кода после process-death, стейт уже в Saver). Голая `xBottomSheetConfig(...)` УДАЛЕНА (ambiguity)
   — единственная форма remembered. Companion `XBottomSheetConfig.Default` (по поправке №3 без companion) → top-level
   `internal val XBottomSheetConfigDefault` (`internal`, не `private`: читается из корневого пакета).
3. **Ноль `object`/`companion`-фабрик в новых сущностях.** `SheetAnchorTable` — обычный `internal class` +
   extension-фабрика `internal fun SheetMetrics.toAnchorTable(skipCollapsed: Boolean): SheetAnchorTable`; вместо
   `SheetAnchorTable.Empty` — nullable-поле в стейте (таблица не построена, пока нет метрик). То же для новых
   резолверов/дефолтов: top-level `fun`/`val` — можно, `companion object`-фабрика — нельзя. Дефолт
   `XBottomSheetColors` — top-level `private val`, не companion. (Существующие `companion Default` у
   `XBottomSheetConfig` не трогаем — правка только новых сущностей.)

---

## 0. Принципы синтеза (три оси «куда прячем логику»)

1. **route-through-config** — поведенческая политика читается из именованных `@Immutable`-конфигов в
   композиции корня; вниз (measure/draw/жесты) идут распакованные примитивы/State (контракт §4.7).
2. **scope-hides-internals** — слоты получают receiver `XBottomSheetScope`: безопасное семантическое API,
   а `offset`/`metrics`/`gestureCommands`/`enqueue*` физически недостижимы из контента.
3. **extract-into-entity** — тяжёлая внутренняя математика (якоря) выносится в чистый immutable-объект
   (`SheetAnchorTable`), стейт-машина худеет до оркестратора.

**Ключевой вывод линз (принят):** конфиг-поверхность УЖЕ изоморфна осям вариативности разработчика.
Ни одна единица НЕ требует подменяемой логики (strategy). Пороги жестов/пружина — вшиты (§9 «имитация
нативной»); per-sheet-физика сделала бы листы приложения разнородными и не нужна ни одному из 19 кейсов.
Поэтому **новых публичных конфиг-ручек мало**: только цвета (директива юзера) + `dismiss.onBackPress` +
косметика группировки. Остальное — инкапсуляция (scope) и внутренняя чистка (anchor-table, token-consolidation).

---

## 1. Итоговая таблица «логика → куда прячем»

Вердикт: **config** (value-конфиг) · **scope** (receiver слотов) · **entity** (внутренний объект) ·
**token** (вшито в `XBottomSheetDefaults`/`NativeSheetSpring`, §9) · **as-is** (уже правильно, не трогаем).

| Поведенческая единица | Где сейчас | Вердикт | Предложение |
|---|---|---|---|
| Scrim: цвет Black 40% | `Defaults.ScrimColor` | **config** | **A** `XBottomSheetColors.scrim` (default→токен) |
| Scrim: fade-дистанция 120dp | `Defaults.ScrimFadeDistance` | **token** | вшито (моушен-визуал §9) |
| Scrim: блок тачей ВСЕГДА / гард !isDragging | `SheetScrim` | **as-is** | инвариант §1/§3 |
| Scrim: закрытие тапом | `DismissConfig.onOutsideTap` | **config** | есть |
| Sheet background (темазависим) | `Defaults.SheetBackground` | **config** | **A** `colors.sheetBackground` (default→тема) |
| Overlay on/off + тень при false | `overlayBackground` + токены тени | **config**+**token** | есть; elevation-16 вшито |
| Ширина 512 / порог 600 | `XBottomSheet.kt` токены | **token** | вшито §2 (геометрия) |
| Инсеты (status/nav/loading 192) | `SheetInsets` | **token** | вшито (edge-to-edge канон) |
| Detect/place замер (`SubcomposeLayout`) | `SheetContainer` | **as-is** | внутр. механика §7; подмена ломает wrap/fill |
| `openTarget/expandTarget/anchorPx` | `SheetAnchorTargets.kt` | **as-is** | ядро §4; в этом раунде не трогаем |
| `isFillMode` | `SheetMetrics` | **as-is**→**scope-read** | наружу как факт: **E** `scope.isFillMode` |
| Набор кандидатов + settle-выбор + floor/ceiling драга + isAtRestAnchor | `XBottomSheetState.buildSettleCandidates/settle/dragBy/кэши` | **entity** | **G** `SheetAnchorTable` (порт 1:1) |
| `FLING_VELOCITY_THRESHOLD=400`, `RESISTANCE_MAX_PX=240` | state companion | **token** | **J** консолидация в `Defaults` (вшито, НЕ конфиг) |
| `ANCHOR_EPS/OVERSHOOT_ENTER_EPS/REMEASURE_TIMEOUT` | state companion | **as-is** | численная гигиена, остаётся в companion/entity |
| Resistance-формула `max*(1−exp)` | `ResistedOvershoot.kt` | **token** | вшито (feel §9); стратегия НЕ вводится |
| FIFO-канал жестов | state | **as-is** | архитектура |
| Nested-scroll связка | `SheetNestedScrollConnection` | **as-is** | §5; не точка расширения |
| Пружина `NativeSheetSpring` | global | **token** | вшито §9 |
| peekFraction / custom-anchors / skipCollapsed / initialLoading | `StateConfig` | **config** | есть; **H** anchors DSL → infix `at` |
| IME Lift/StayUnderKeyboard | `KeyboardConfig.bottomBehavior` | **config** | есть (enum — верная форма) |
| IME авто-FullScreen / откат / жесты-off / drop-on-hide / гейтинг | state + `ObserveSheetState` | **as-is** | §6 обязательные правила; **F** дедуп рецепта дропа |
| AdditionalTop overlap 32 / fraction-анимация | токен + measure | **token**/**as-is** | геометрия §2; Animatable в measure |
| AdditionalTop cornerRadius | плоское `additionalTopCornerRadius` | **config** | **B** группа `additionalTop { }` |
| AdditionalTop Expanded/Collapsed | `state.additionalTopState` | **scope** | **D** `scope.additionalTopState` (var) |
| Закрытие из контента | host-замыкание `dismiss` | **scope** | **D** `scope.requestDismiss()` |
| Дроп IME из контента | тянут Local*-контроллеры сами | **scope** | **F** `scope.hideKeyboard()` |
| Текущий стейт / факт анимации | `currentValue` (public), `offset` (internal) | **scope**/**read** | **E** `scope.sheetValue`; **I** `state.isAnimating` |
| Back-press закрытие | хост пишет `BackHandler` сам | **config** | **C** `dismiss { onBackPress }` (default false) |
| DragHandle Theme/Static/null + связка «null⇒жесты off» | `dragHandle` | **config**+**as-is** | есть; связку не развязываем §1 |
| Handle-цвета Theme/Static | `Defaults.HandleTheme/HandleStatic` | **config** | **A** `colors.handleTheme/handleStatic` |
| Saver (теги/isLoading/additionalTop) | `XBottomSheetStateSaver` | **as-is** | append-only + tag-freeze контракт (§4) |
| «Один БШ на экран» | хост | **as-is** | обязанность хоста §9 |

**Итог:** новых публичных конфигов — цвета (A, директива юзера) + `onBackPress` (C) + группировка
`additionalTop` (B). Инкапсуляция — scope (D/E/F). Внутренняя чистка — anchor-table (G), token-consolidation (J),
DSL-косметика (H), read-getter (I). **Ноль strategy-интерфейсов. Ноль gesture/motion-config.**

---

## 2. Сигнатуры новых/изменённых сущностей

### A. [RECOMMEND · директива юзера] Appearance — `XBottomSheetColors`

Цвета конфигурируемы; дефолты = спека/тема через `Color.Unspecified` (прецедент Alva
`AlvaBottomSheetConfig.containerColor`). Резолв — в композиции корня, вниз идут распакованные `Color`
(контракт §4.7: конфиг читается в корне, примитивы — вглубь).

```kotlin
// config/XBottomSheetColors.kt (реализовано без companion — поправка №3; дефолт даёт билдер, Unspecified×4)
@Immutable
internal data class XBottomSheetColors(
    val scrim: Color,            // Unspecified → Defaults.ScrimColor (Black 40%)
    val sheetBackground: Color,  // Unspecified → XTheme.colors.backgroundContent (темазависим)
    val handleTheme: Color,      // Unspecified → XTheme.colors.separator (темазависим)
    val handleStatic: Color,     // Unspecified → Defaults.HandleStatic (White 40%)
)

// config/XBottomSheetColorsBuilder.kt
@XBottomSheetDsl
internal class XBottomSheetColorsBuilder {
    var scrim: Color = Color.Unspecified
    var sheetBackground: Color = Color.Unspecified
    var handleTheme: Color = Color.Unspecified
    var handleStatic: Color = Color.Unspecified
    internal fun build() = XBottomSheetColors(scrim, sheetBackground, handleTheme, handleStatic)
}

// config/XBottomSheetColorsResolve.kt — резолв ТОЛЬКО в композиции (тема доступна); Unspecified→дефолт.
@Composable internal fun XBottomSheetColors.resolveScrim() = scrim.takeOrElse { XBottomSheetDefaults.ScrimColor }
@Composable internal fun XBottomSheetColors.resolveSheetBackground() = sheetBackground.takeOrElse { XTheme.colors.backgroundContent }
@Composable internal fun XBottomSheetColors.resolveHandleTheme() = handleTheme.takeOrElse { XTheme.colors.separator }
@Composable internal fun XBottomSheetColors.resolveHandleStatic() = handleStatic.takeOrElse { XBottomSheetDefaults.HandleStatic }
```

Проводка: корень `XBottomSheet.kt` резолвит 4 цвета в композиции и передаёт готовыми `Color` в
`SheetScrim(scrimColor=…)`, `SheetContainer→SheetSurface(background=…)`, `DragHandle(themeColor=…, staticColor=…)`.
`XBottomSheetColors` — value-equality (`Color` — value-класс; `Unspecified==Unspecified`) → skip не ломается.
`XBottomSheetDefaults` остаётся единым источником дефолтов (резолверы ссылаются на него — цвета в токенах не
дублируются).

**Развилка (open Q1):** отдельный тип `XBottomSheetColors`+`colors { }` (рекомендую, M3-style) — ОК?
Механизм: use-site/root-резолв `takeOrElse` (рекомендую) vs Alva-`rememberXBottomSheetConfig`-префилл.

### B. [RECOMMEND · линза 2] `additionalTop { cornerRadius }` — группировка (последний prefix-named флаг)

```kotlin
// config/AdditionalTopConfig.kt (переезд из additionaltop/; там остаётся AdditionalTopState)
@Immutable internal data class AdditionalTopConfig(val cornerRadius: Dp)
// config/AdditionalTopConfigBuilder.kt
@XBottomSheetDsl internal class AdditionalTopConfigBuilder {
    var cornerRadius: Dp = 0.dp
    internal fun build() = AdditionalTopConfig(cornerRadius)
}
```
`XBottomSheet.kt:140` `AdditionalTopConfig(cornerRadius = config.additionalTopCornerRadius)` → `config.additionalTop`
(готовый инстанс, пересборка на рекомпозицию уходит). Call-site g: `additionalTop { cornerRadius = 16.dp }`.

### C. [RECOMMEND · линза 3] `dismiss { onBackPress }` — прячет host BackHandler-вайринг

```kotlin
@Immutable internal data class DismissConfig(val onOutsideTap: Boolean, val onSwipeDown: Boolean, val onBackPress: Boolean)
@XBottomSheetDsl internal class DismissConfigBuilder {
    var onOutsideTap = true; var onSwipeDown = true
    var onBackPress = false   // дефолт false → 19 кейсов 1:1 (back остаётся у хоста)
    internal fun build() = DismissConfig(onOutsideTap, onSwipeDown, onBackPress)
}
// XBottomSheet.kt, рядом с ObserveSheetState:
BackHandler(enabled = state.isVisible && config.dismiss.onBackPress) { currentOnDismiss() }
```
Закрытие — через тот же `onDismissRequest` (дроп IME §6.5 переиспользуется). `BackHandler(enabled=false)`
регистрирует выключенный колбэк → диспетчер пропускает → строгое 1:1 при дефолте.

### D+E+F. [STRONG(D)/RECOMMEND(E,F) · линза 1] `XBottomSheetScope` — единый receiver 4 слотов

```kotlin
// XBottomSheetScope.kt
@Stable
interface XBottomSheetScope {
    val sheetValue: SheetValue                 // E: вычисленный стейт (дискретно, НЕ покадрово; offset не отдаём)
    val isFillMode: Boolean                    // E: режим якорей по замеру (до замера — false)
    var additionalTopState: AdditionalTopState // D: sticky-слой (внешний фактор)
    fun requestDismiss()                       // D: единственная точка закрытия ИЗ контента → onDismissRequest хоста
    fun hideKeyboard()                         // F: дроп IME без закрытия (канон §6.5: hide + clearFocus)
}

@Stable
internal class XBottomSheetScopeImpl(private val state: XBottomSheetState) : XBottomSheetScope {
    internal var keyboardController: SoftwareKeyboardController? = null
    internal var focusManager: FocusManager? = null
    override val sheetValue get() = state.currentValue
    override val isFillMode get() = state.metrics?.isFillMode ?: false
    override var additionalTopState
        get() = state.additionalTopState
        set(value) { state.additionalTopState = value }
    override fun requestDismiss() = state.onDismissRequest()
    override fun hideKeyboard() { keyboardController?.hide(); focusManager?.clearFocus(force = true) }
}
```
Слот-сигнатуры: `additionalTop/top/bottom: (@Composable XBottomSheetScope.() -> Unit)?`,
`middle: @Composable XBottomSheetScope.() -> Unit`. Вайринг — расширение СУЩЕСТВУЮЩЕГО SideEffect:
```kotlin
val sheetScope = remember(state) { XBottomSheetScopeImpl(state) }
SideEffect {
    state.dismissOnSwipeDown = config.dismiss.onSwipeDown
    state.onDismissRequest = { currentOnDismiss() }
    sheetScope.keyboardController = keyboardController
    sheetScope.focusManager = focusManager
}
```
Дедуп §6.5: `ObserveSheetState(onSheetHidden = { sheetScope.hideKeyboard() })` — рецепт дропа в одной точке.
Detect/place-слоты получают ОДИН инстанс scope (detect-копия не placed → `requestDismiss` недостижим).

**Что scope НЕ даёт (осознанно, линзы reject):** `offset`/`metrics`/`anchorPx`/пиксели; suspend
`show/expand/hide` (команды высоты — у хоста, иначе петля ответственности); `isKeyboardVisible`;
`ColumnScope`-возможности (`weight`/`align` сломали бы detect wrap/fill); сахар `dismiss{none()}`/`toggle*`.

### G. [RECOMMEND · линза 3 · отделимо] `SheetAnchorTable` — вынос якорной математики

Внутренний immutable-объект по образцу foundation `DraggableAnchors`+`updateAnchors`. Прячет из стейт-машины
~80 строк (`buildSettleCandidates`/`computeLowest/HighestAllowedAnchorPx`/кэши/`AnchorCandidate`).

```kotlin
// state/SheetAnchorTable.kt — чистый объект (реализовано без @Immutable — «без Compose-зависимостей»; поправка №3:
// без companion, конструктор internal под extension-фабрику; вместо Empty — nullable anchorTable в стейте).
internal class SheetAnchorTable internal constructor(private val restEntries: List<AnchorEntry>) {
    internal data class AnchorEntry(val value: SheetValue, val anchorPx: Int)
    val lowestRestAnchorPx: Int = restEntries.firstOrNull()?.anchorPx ?: 0
    val highestRestAnchorPx: Int = restEntries.lastOrNull()?.anchorPx ?: 0
    // Порт settle-выбора 1:1 (fling→якорь по направлению, иначе ближайший); Hidden(0) — только при
    // isDismissAllowed И отсутствии rest-якоря на 0px (семантика distinctBy). null = нет кандидатов.
    fun settleTarget(offsetPx: Float, velocityPxPerSec: Float, isDismissAllowed: Boolean): SheetValue?
    fun isAtRestAnchor(offsetPx: Float): Boolean
}

// state/SheetAnchorTableFactory.kt — extension-фабрика (поправка №3, не companion). Дословный порт rest-части;
// FlingVelocityThresholdPxPerSec (Defaults) / ANCHOR_EPS без смены значений.
internal fun SheetMetrics.toAnchorTable(skipCollapsed: Boolean): SheetAnchorTable
```
Пересчёт — в `updateMetrics` (updateAnchors-паттерн, тот же guard). `settle`/`dragBy`/`isOffsetAtRestAnchor`
читают таблицу. `dismissOnSwipeDown` мутабелен → в таблицу НЕ вшивать, передавать параметром. `openTarget/
expandTarget/anchorPx` в этом раунде НЕ трогаем. **Отделимо:** можно апрувить/фазировать независимо от A–F.

### H. [RECOMMEND · линза 3] anchors-DSL: infix `at` (канон `DraggableAnchors`)

```kotlin
// state/XSheetAnchorsBuilder.kt
@XBottomSheetDsl internal class XSheetAnchorsBuilder {
    private val anchors = mutableListOf<XSheetAnchor>()
    infix fun String.at(heightFraction: Float) { anchors.add(XSheetAnchor(this, heightFraction)) }  // member-ext, скоуп билдера
    internal fun build(): List<XSheetAnchor> = anchors.toList()
}
```
`anchor(key, fraction)` удаляется (не две формы одного действия). Call-site r: `anchors { "half" at 0.5f }`.
Возвращает тот же `XSheetAnchor` в тот же список → рантайм 1:1.

### I. [OPTIONAL · линза 3] `isAnimating` — аддитивный факт бегущей анимации

```kotlin
// state/XBottomSheetState.kt
val isAnimating: Boolean get() = offset.isRunning   // snapshot-state; наблюдаемо из snapshotFlow
```
Прячет internal `offset` за булевым фактом (без него единственная альтернатива — открыть offset целиком).
`currentValue`→`targetValue` НЕ переименовываем (сломало бы контракт §4).

### J. [OPTIONAL · линза 2] Feel-токены жестов → `XBottomSheetDefaults` (вшито, НЕ конфиг)

```kotlin
internal object XBottomSheetDefaults {
    // Вшитая физика жестов (§9, НЕ публичный конфиг; менять только с полным прогоном 19). px намеренно.
    const val FlingVelocityThresholdPxPerSec = 400f
    const val ResistanceMaxPx = 240f
}
```
Стейт/`SheetAnchorTable` читают из `Defaults` (const → compile-inline, ноль аллокаций). `ANCHOR_EPS/
OVERSHOOT_ENTER_EPS/REMEASURE_TIMEOUT` остаются в companion (гигиена, не «феел»). **НЕ делать:** перевод
400/240 из px в dp — поведенческое изменение на не-базовой плотности (нарушит 1:1).

### K. [RECOMMEND · линза 2 · doc-only] Контракт проводки конфига — в `XBOTTOMSHEET.md`

Зафиксировать правилом (страховка от протечки будущих полей в measure/жесты): (1) конфиг читается только
в композиции корня, вниз — примитивы/value-объекты/State; (2) флаги suspend-логики копируются `SideEffect`'ом
в поля стейта; (3) логика-объекты `remember`ятся один раз и получают State-ссылки. + инвариант: конфиг-группы —
`@Immutable data class` без лямбд и без `AnimationSpec`/`Shape`-полей (дешёвая value-equality в skip-пути).

### Изменённый `XBottomSheetConfig` (итог A+B+C)

```kotlin
@Immutable internal data class XBottomSheetConfig internal constructor(
    val overlayBackground: Boolean,
    val dragHandle: DragHandleStyle?,
    val additionalTop: AdditionalTopConfig,   // B (было: additionalTopCornerRadius: Dp)
    val dismiss: DismissConfig,               // C (+ onBackPress)
    val keyboard: KeyboardConfig,
    val colors: XBottomSheetColors,           // A
)
// Дефолт — top-level `internal val XBottomSheetConfigDefault` (поправка №3, без companion); строится
// composable-фабрикой `rememberXBottomSheetConfig { }` (поправка №2, remember без ключей).
```
**`XBottomSheetStateConfig` — БЕЗ изменений** (skipCollapsed/initialLoading/peekFraction/anchors). Это снимает
главный миграционный риск: ключ `rememberSaveable` байт-идентичен, стейт не пересоздаётся (см. §5 R1).

---

## 3. Call-site: минимальный / типовой / продвинутый

```kotlin
// ── МИНИМАЛЬНЫЙ (a/b1/b2/m/n/o/p/q) — 0 конфига, scope невидим ──
val state = rememberXBottomSheetState()
XBottomSheet(state = state, onDismissRequest = { scope.launch { state.hide() } }) {
    SportLazyList(SPORTS)                       // extension XBottomSheetScope.SportLazyList → requestDismiss() внутри
}

// ── ТИПОВОЙ (e/f/g/j/l + colors) ──
XBottomSheet(
    state = rememberXBottomSheetState { skipCollapsed = true },
    onDismissRequest = { scope.launch { state.hide() } },
    config = xBottomSheetConfig {
        overlayBackground = false                                   // e
        dragHandle = DragHandleStyle.Static                         // k
        additionalTop { cornerRadius = 16.dp }                     // B (g)
        dismiss { onOutsideTap = false; onSwipeDown = false; onBackPress = true }  // C (j)
        keyboard { bottomBehavior = BottomKeyboardBehavior.StayUnderKeyboard }     // l
        colors { scrim = Color(0x66000000); handleTheme = Color(0xFF9AA4B2) }      // A; фон/handleStatic — дефолт темы
    },
    top = { PresetTitle("…"); PresetSearchField(query, onQueryChange) },
    bottom = { PresetSingleButton("Закрыть") { requestDismiss() } },              // D scope
) { SportLazyList(filtered) }

// ── ПРОДВИНУТЫЙ (r + scope read-layer) ──
val state = rememberXBottomSheetState { peekFraction = 0.5f; anchors { "half" at 0.5f } }  // H
XBottomSheet(state = state, onDismissRequest = { scope.launch { state.hide() } }) {
    if (sheetValue == SheetValue.Collapsed) SwipeUpHintRow()        // E scope.sheetValue
    LazyColumn(Modifier.fillMaxWidth()) {
        items(items) { PresetMenuCell(it.title, onClick = { onPicked(it); hideKeyboard() }) }  // F
    }
}
// isAnimating (I): PresetSingleButton("Показать все", isEnabled = !state.isAnimating, onClick = ::showAll)
```

---

## 4. Фазы миграции + регрессия-маппинг на 19 кейсов

Фазировка M0–M4 (аддитивное ядро + атомарные switch по образцу P4-DSL). Каждый M-коммит: `assembleDebug`
зелёный, прогон затронутой зоны по матрице. Дефолты новых полей = текущим значениям бит-в-бит.

- **M0 — база.** Полный прогон 19 + baseline-скрины (`adb wm size 1080x1919`) + git-точка. Всё сравнивается с базой.
- **M1 — аддитивные конфиги (сигнатуры слотов не меняются).** A (colors + резолв в корне + проводка в
  SheetScrim/Surface/DragHandle), C (`onBackPress`+BackHandler), B (`additionalTop{}` — правит call-site g),
  J (feel-токены→Defaults). Серия мелких коммитов; demo не трогается, кроме g.
- **M2 — внутренние сущности (internal, публичная поверхность не меняется).** G (`SheetAnchorTable`, порт 1:1),
  `XBottomSheetScopeImpl`, I (`isAnimating`). Demo не трогается.
- **M3 — атомарные switch-точки (одна форма за раз, компилятор перечисляет call-sites).** D/E/F (слот-сигнатуры
  → `XBottomSheetScope` receiver — source-прозрачно для 19 инлайн-лямбд), H (anchors infix — правит call-site r).
  Опционально миграция demo j/k/g на scope-API.
- **M4 — регрессия + доки.** Матрица + канарейки + новые кейсы (t)/(u); K (контракт проводки), Saver-контракт
  комментарием, XBOTTOMSHEET.md/XBS-SPEC.md с пометкой «ПЕРЕСМОТРЕНО (раунд N)».

### Матрица «зона правки → кейсы» (прогон после каждого коммита)

| Зона | Обязательный прогон | Канарейки |
|---|---|---|
| **A цвета** | ВСЕ (фон/скрим/хендл 1:1) | dark+light скрины a/e/g/k; дрейф дефолтов (Unspecified→токен/тема) |
| **B additionalTop** | g | скругление 16dp, тоггл, контент листа неподвижен |
| **C onBackPress** | j + любой open-лист | back при дефолте false уходит хосту/Activity (1:1) |
| **D/E/F scope** | j, k, g, a, i/l/s | печать в (i): лист не моргает/не пересоздаётся; requestDismiss==скрим-тап |
| **G anchor-table** | a, b1, b2, j, r | b1 L6-клампинг; b1/b2 L8-инерция списка; j floor при закрытиях-off |
| **H anchors DSL** | r | свайп peek→50%→full |
| **J feel-токены** | a, b1, b2, r | смоук свайпов (перенос const — байткод-эквивалент) |
| **I isAnimating** | — | сборка (чисто read-only) |

### Два новых кейса (строго на слепые зоны, не на ручки)

```kotlin
T("(t) AdditionalTop + короткий контент (wrap)")  // E1-зона: открытие Content-ПО-КОНТЕНТУ, не peek; карточка+тоггл
U("(u) Loading + поиск: IME во время Loading")     // L5-зона: markContentReady под IME → авто-FullScreen, не «верх за потолком»
```
Итого 21 кейс. Новые ручки (colors/onBackPress) проверяются РАСШИРЕНИЕМ существующих кейсов, не новыми.

### Saver-контракт (инвариант process-death, комментарием в `XBottomSheetStateSaver.kt`)

Append-only список `[tag, isLoading, additionalTopName, …новое-в-конец]`; чтение `getOrNull(i) as? T ?: default`;
tag-freeze (`h/c/col/ec/efs/l/cu:` — персистентный контракт, ре-неймы `SheetValue` тегов не меняют); config
НЕ сохраняется (пересоздаётся DSL, мягкая деградация: `Custom(key)` при удалённом якоре → `peekFraction`).
**В этом раунде Saver не меняется** (StateConfig не растёт) — контракт фиксируется как страховка на будущее.
Канарейка M4: process-death-скрипт на b2/d/g/r.

---

## 5. Риск-топ-5

1. **Дрейф дефолтов цветов / feel-токенов** (A/J): `Unspecified`-резолв или перенос const дал не тот цвет/порог.
   → таблица «старое место → новое место»; резолверы ссылаются на те же `Defaults`/`XTheme`; dark+light скрины
   a/e/g/k; смоук свайпов a/b1/b2 (J — байткод-эквивалент).
2. **Неточный порт формул в `SheetAnchorTable`** (G): расхождение settle/floor/ceiling. → дословный порт;
   distinctBy-семантика «Hidden только если нет rest на 0px»; empty-фолбэк через nullable; прогон a/b1(+L6)/b2/j/r/L8.
3. **Незавершённый атомарный switch слотов/anchors** (D–F/H): сосуществование форм → ambiguity; пропущенный
   call-site. → одна форма за раз; компилятор перечисляет 19 call-sites (+r для H); grep модуля (alva/zorderlab
   чисты — подтверждено); критерий «assembleDebug без правок demo» (кроме g/r).
4. **Утечка конфига/аллокаций в measure** (A): цвета прочитаны глубоко/по-кадрово. → резолв в композиции корня,
   вниз — готовые `Color`-примитивы (контракт §4.7/K); ревью diff measure-лямбд; драг-прогон b2/g без jank.
5. **Скрытый регресс skip/Saver** (общий): не-value поле в конфиге → пересоздание. → StateConfig НЕ растёт
   (ключ Saver байт-идентичен — риск ретирован для этого раунда); `XBottomSheetConfig` — только value-типы
   (`Color`/`Dp`/`Boolean`/enum/вложенные data); канарейка «печать в (i)» на каждый конфиг-коммит.

DoD (обязателен): assembleDebug зелёный, diff не касается `com.alva.*`/`com.zorderlab.*`/`org/xplatform/**`;
прогон 21 кейса против baseline; канарейки (печать-в-i, ротация c/d/g/r, process-death b2/d/g/r, dark/light);
грепы (`require(` нет, лямбд в data class нет, `animate*AsState` нет, геометрия-токены в конфигах нет);
доки обновлены.

---

## 6. Anti-scope (отвергнуто и почему)

**Строго отвергнуто линзами (уважаем reject, «не забивать гвозди»):**
- **`GestureConfig`/`MotionConfig` в публичный конфиг** (spring/fling/resistance наружу) — §9 «имитация
  нативной»; per-sheet-физика делает листы приложения разнородными; технически `AnimationSpec` в data class
  рискует дешёвой value-equality (skip-путь); ни одному из 19 кейсов не нужно. Решение: вшито (J — консолидация токенов).
- **Strategy-интерфейсы** (`SettleStrategy`/`AnchorResolver`/`ResistanceCurve`/`SheetTransitionPolicy`
  /`confirmValueChange`) — settle/якоря — идентичность стейт-машины §4/§5; вся осмысленная вариативность уже
  задаётся ДАННЫМИ (isFillMode×skipCollapsed×peekFraction×anchors×dismiss); подменяемая логика = легальный обход
  спеки и конец гарантии 1:1. Наш `onDismissRequest` (владение) сильнее M3-веты. Нет 2-й реализации → YAGNI.
- **`ScrimConfig` fade-дистанция / `WidthConfig` 512-600 / настройка инсетов** — геометрия §2 вшита; инсеты —
  edge-to-edge канон. (Цвет scrim — исключение, вынесен в A по директиве юзера.)
- **Тоглы `KeyboardConfig`** (`hideImeOnDismiss`/`autoFullScreen`/`disableGesturesUnderIme`) — §6-правила
  ОБЯЗАТЕЛЬНЫ; тоггл дал бы выключить обязательное (лист «улетает»). Единственная ось — `bottomBehavior` (enum).
- **Слот `dragHandle`/`Loader` composable; развязка handle-видимости и жестов** — §1/§9 инварианты; слот
  породил бы зоопарк хендлов / обход запрета Lottie-скелетона.
- **Scope: `ColumnScope`-receiver / scoped-модификаторы / LazyListScope-DSL / 4 раздельных scope / offset-metrics
  в scope / suspend show-expand-hide в scope / `isKeyboardVisible` / сахар `dismiss{none()}`/`toggle*`** —
  `weight/align` сломали бы detect wrap/fill; команды высоты — у хоста (петля ответственности); сырые px
  спровоцировали бы самодельную геометрию; сахар = церемония.
- **M3 оконное** (`securePolicy`/appearance-bars/`contentPadding`/`ModalBottomSheetProperties`) — лист in-tree,
  своего окна нет; инсеты вшиты внутрь (отдать PaddingValues = утечка §9-отступов).
- **Переименование `currentValue→targetValue`; `requireOffset`-стиль** — сломало бы §4/доки; require запрещён.

**Отвергнуто по миграции:**
- **@Deprecated-мост / версионированный Saver / сохранение конфига в Saver / runtime-мутабельный конфиг /
  автотесты / новый кейс на каждую ручку / binary-compat-validator** — API internal (deprecation-цикл невозможен);
  append-only+getOrNull уже даёт forward-tolerance; мутабельность открыла бы гонки кэшей якорей; тесты запрещены
  правилом проекта; новый кейс — только на видимую степень свободы/слепую зону (ровно t/u).
- **Перевод px-констант 400/240 в dp** — поведенческое изменение на не-базовой плотности (нарушает 1:1);
  только как отдельное Core-решение с регрессией на разных density.

---

## 7. Открытые вопросы (развилки для юзера — до правок)

1. **Colors (A):** отдельный `XBottomSheetColors`+`colors { }` (рекомендую, M3-style) — ОК? Механизм дефолтов —
   root-резолв `takeOrElse` (рекомендую) vs Alva-`rememberXBottomSheetConfig`-префилл?
2. **Scope-набор (D/E/F):** `sheetValue`/`isFillMode`/`additionalTopState`/`requestDismiss`/`hideKeyboard` —
   достаточно? (линзы reject `expand`/`isKeyboardVisible`/suspend-команд — согласен.)
3. **`SheetAnchorTable` (G):** делаем сейчас (−80 строк, чистая математика, риск порта 1:1) или откладываем как
   отдельный чисто-внутренний рефактор? Отделимо от A–F.
4. **`onBackPress` (C):** дефолт `false` (1:1) — ОК? (в Core можно осознанно флипнуть в `true`.)
5. **anchors DSL (H):** infix `"half" at 0.5f` (рекомендую, канон M3) vs оставить `anchor(key, fraction)`?
6. **Объём:** обязательно A/D/E/F (+B/C косметика); G/H/I/J — по решению; K — doc-only.
