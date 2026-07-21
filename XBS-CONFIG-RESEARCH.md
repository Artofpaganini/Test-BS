# XBS-CONFIG-RESEARCH — сырые результаты 4 дизайн-линз (вход для архитектора)


# Линза 2 — CONFIG vs STRATEGY по подсистемам. Исчерпывающий проход по всем поведенческим единицам кода (app/src/main/java/com/onexui/bottomsheet/**), каждой присвоен вердикт: (a) value-конфиг, (b) strategy-интерфейс, (c) вшито по спеке §9.

ИНВЕНТАРЬ (19 единиц):
1. SCRIM (layout/SheetScrim.kt): цвет Black 40% — ВШИТО §9 (Defaults.ScrimColor); фейд альфы по offset/ScrimFadeDistance 120dp — ВШИТО (моушен-токен, уже в Defaults после линзы 4); блокировка тачей под листом ВСЕГДА — ВШИТО §1; тап-снаружи-закрытие — КОНФИГ есть (DismissConfig.onOutsideTap); гард !isDragging — ВШИТО.
2. ШИРИНА (XBottomSheet.kt:63-64): MaxWidth 512 / WideScreenThreshold 600, fillMaxWidth vs центр — ВШИТО §2.
3. ИНСЕТЫ: потолок = screen − statusBar (SheetMetrics.maxHeightPx); navBar-паддинг внутри Surface (bottomInsetModifier LiftContent / reserve StayUnderKeyboardMeasurePolicy); Loading-якорь = 192dp + navBarPx — всё ВШИТО (edge-to-edge канон, ПЕРЕСМОТРЕНО-блок спеки).
4. ЗАМЕР detect/place (layout/SheetContainer.kt: SubcomposeLayout, ContentMeasureSlot при maxHeight=ceiling → contentHeightPx/isFillMode, VisibleContentSlot при fixed(offset); updateMetrics с primitive-guard) — ВШИТО, внутренняя механика, легализована юзером под Lazy; подмена = слом wrap/fill.
5. ДРАГ (XBottomSheetState.dragBy + gesture/): FIFO-канал GestureCommand — ВШИТО; floor при dismissOnSwipeDown=false (кэш lowestAllowedAnchorPx) — производное от КОНФИГА dismiss.onSwipeDown; overshoot-резина max*(1−exp(−raw/max)), RESISTANCE_MAX_PX=240px — ВШИТО (feel-токен, предложение №2); OVERSHOOT_ENTER_EPS_PX=0.5 и потолок обычного драга highestAllowedAnchorPx — ВШИТО (численная гигиена/кэш перфа).
6. SETTLE (XBottomSheetState.settle/buildSettleCandidates): FLING_VELOCITY_THRESHOLD=400px/s — ВШИТО (feel-токен, предложение №2); выбор якоря по скорости/ближайшему, ANCHOR_EPS=1px — ВШИТО; НО состав кандидатов — уже ДАННЫЕ: isFillMode(замер) × skipCollapsed × peekFraction × anchors{} × dismiss.onSwipeDown — вся осмысленная вариативность settle покрыта value-конфигом; SettleStrategy НЕ НУЖЕН (см. rejected).
7. NESTED-SCROLL (gesture/SheetNestedScrollConnection.kt): вверх-в-Collapsed — сначала лист до expandTarget-якоря; вниз — остаток после доскролла тянет лист; onPreFling-гейты isDragging + isOffsetAtRestAnchor — ВШИТО §5; enabled-гейт (derivedStateOf: dragHandle≠null ∧ !isLoading ∧ !imeVisible) — производное от конфига + юзер-правил §6. Это НЕ точка расширения, а внутренняя связка — статус-кво верный.
8. IME-ПОЛИТИКИ: bottomBehavior Lift/StayUnderKeyboard — КОНФИГ есть (KeyboardConfig, enum — правильная форма: будущие режимы = новые значения enum, не новые флаги); авто-FullScreen при liftedTop>maxHeight или alwaysFullScreen (StayUnderKeyboard+bottom) — ВШИТО §6; откат imePromotedFrom при скрытии IME + сброс ручным expand()/settle() — ВШИТО (юзер-правило 3); жесты высоты при IME отключены — ВШИТО (юзер-правило 4); drop-on-hide (isVisible→false ⇒ keyboardController.hide + clearFocus, ObserveSheetState) — ВШИТО (юзер-правило 5); гейтинг withAdjustmentForKeyboard(!FullScreen) / withKeyboardShrink(FullScreen) — ВШИТО.
9. ADDITIONAL TOP: overlap 32dp — ВШИТО §2; Collapsed = полное скрытие (peek-20 отменён юзером) — ВШИТО ПЕРЕСМОТРЕНО; анимация фракции — Animatable + NativeSheetSpring, .value в measure — ВШИТО; переключение состояния — публичный state.additionalTopState (внешние факторы) — API есть; cornerRadius — КОНФИГ, но плоским полем additionalTopCornerRadius → предложение №1: вложенная группа additionalTop{}.
10. ПРУЖИНА NativeSheetSpring (NoBouncy/MediumLow) — ВШИТО §9 («имитация нативной»).
11. PEEK 2/3 — value-КОНФИГ есть (StateConfig.peekFraction, дефолт Defaults.PeekFraction в билдере).
12. CUSTOM-ЯКОРЯ — value-КОНФИГ есть (StateConfig.anchors{} DSL, XSheetAnchor(key, heightFraction)); fill-only + Log.w вместо require — решение юзера, ВШИТО.
13. REMEASURE_TIMEOUT_MS=500 (markContentReady) — ВШИТО (робастность; у разработчика нет осмысленного выбора).
14. LOADING: высота 192dp — ВШИТО §2; interactions off в Loading — ВШИТО; вход через initialLoading — value-КОНФИГ есть (StateConfig).
15. skipCollapsed — value-КОНФИГ есть (StateConfig).
16. DRAG HANDLE: Theme/Static/null — КОНФИГ есть; геометрия 36×4/r360/top8 и цвета — ВШИТО §2; связка «null ⇒ жесты высоты off» — ВШИТО §1 (не развязывать).
17. overlayBackground + тень Shadow Soft при false — КОНФИГ есть + вшитые токены тени (elevation-16 аппроксимация задокументирована).
18. SAVER/restore (теги SheetValue/isLoading/additionalTopState) — ВШИТО.
19. «Один БШ на экран» — обязанность хоста, вне компонента (§9).

ВЫВОД: конфиг-поверхность уже изоморфна осям вариативности разработчика; НИ ОДНА единица не требует подменяемой логики (strategy). Горячие пути чисты: конфиг читается только в композиции корня; в measure/draw/жесты уходят распакованные примитивы (SheetInsets, scrimFadeDistancePx), State-ссылки (interactionsEnabledState) и SideEffect-копии в поля стейта (dismissOnSwipeDown/onDismissRequest); в dragBy/settle — только const и кэшированные поля, нуль аллокаций и непрямых вызовов. XBottomSheetConfig.equals — 5 дешёвых полей; предложение №1 сохраняет эту цену.

## [STRONGLY-RECOMMEND] additionalTop { } — продолжить иерархию вложенных конфиг-групп (dismiss/keyboard → additionalTop)

### design
Плоское поле additionalTopCornerRadius — единственный конфиг с группой-в-префиксе имени; внутренний AdditionalTopConfig уже существует (additionaltop/AdditionalTopConfig.kt) и пересобирается в XBottomSheet.kt:140 на каждой рекомпозиции корня. Делаем его продуктом DSL-группы, симметрично dismiss/keyboard.

```kotlin
// config/AdditionalTopConfig.kt (переезд из additionaltop/; в additionaltop/ остаётся AdditionalTopState)
@Immutable
internal data class AdditionalTopConfig(
    val cornerRadius: Dp,
)

// config/AdditionalTopConfigBuilder.kt
@XBottomSheetDsl
internal class AdditionalTopConfigBuilder {
    var cornerRadius: Dp = 0.dp
    internal fun build(): AdditionalTopConfig = AdditionalTopConfig(cornerRadius = cornerRadius)
}

// config/XBottomSheetConfig.kt
@Immutable
internal data class XBottomSheetConfig internal constructor(
    val overlayBackground: Boolean,
    val dragHandle: DragHandleStyle?,
    val additionalTop: AdditionalTopConfig,   // было: additionalTopCornerRadius: Dp
    val dismiss: DismissConfig,
    val keyboard: KeyboardConfig,
) {
    internal companion object { val Default = xBottomSheetConfig() }
}

// config/XBottomSheetConfigBuilder.kt
@XBottomSheetDsl
internal class XBottomSheetConfigBuilder {
    var overlayBackground: Boolean = true
    var dragHandle: DragHandleStyle? = DragHandleStyle.Theme
    private val additionalTopBuilder = AdditionalTopConfigBuilder()
    private val dismissBuilder = DismissConfigBuilder()
    private val keyboardBuilder = KeyboardConfigBuilder()
    // Повторные вызовы мёржатся (last-write-wins) — тот же контракт, что dismiss{}/keyboard{}.
    fun additionalTop(configure: AdditionalTopConfigBuilder.() -> Unit) { additionalTopBuilder.configure() }
    fun dismiss(configure: DismissConfigBuilder.() -> Unit) { dismissBuilder.configure() }
    fun keyboard(configure: KeyboardConfigBuilder.() -> Unit) { keyboardBuilder.configure() }
    internal fun build(): XBottomSheetConfig = XBottomSheetConfig(
        overlayBackground = overlayBackground,
        dragHandle = dragHandle,
        additionalTop = additionalTopBuilder.build(),
        dismiss = dismissBuilder.build(),
        keyboard = keyboardBuilder.build(),
    )
}
```

XBottomSheet.kt:140: `additionalTopConfig = AdditionalTopConfig(cornerRadius = config.additionalTopCornerRadius)` → `additionalTopConfig = config.additionalTop` (готовый инстанс из конфига, пересборка на рекомпозицию уходит).

Call-site (кейс g, единственный затронутый):
```kotlin
config = xBottomSheetConfig {
    additionalTop { cornerRadius = 16.dp }   // было: additionalTopCornerRadius = 16.dp
}
```
Коллизии имён с composable-слотом additionalTop = {...} нет — разные скоупы (билдер vs параметры XBottomSheet); прецедент dismiss (локальный val vs DSL-метод) уже проверен в P4-DSL.

### hides_from_component
Из корня XBottomSheet уходит знание «как из плоского Dp собрать AdditionalTopConfig» (пересборка на каждой рекомпозиции) — конфиг проходит сквозь корень готовым value-объектом. Из публичной поверхности уходит последний prefix-named флаг; будущие факты Additional Top (напр. стартовое состояние карточки) получают готовую группу и не раздувают плоскую часть конфига и его equals (остаётся 5 полей, вложенный data class из одного Dp — та же цена сравнения, что у голого Dp).

### developer_cost
Минимальный: без изменений — config не задан (Default), слот additionalTop не используется. Типовой: xBottomSheetConfig { additionalTop { cornerRadius = 16.dp } } — одна вложенная группа, дефолты в билдере. Продвинутый (будущее): новые поля Additional Top добавляются в группу без source-break плоской части конфига.

### break_risk
Рискует: (1) пропущенный call-site — компилятор перечислит (грепнуть additionalTopCornerRadius: ровно XBottomSheet.kt + XbsDemoScreen кейс g + доки); (2) skip-путь — value-equality сохраняется (data class из data class), проверить печатью в кейсе (i): лист не пересоздаётся/не моргает, как в приёмке P4-DSL; (3) поведение карточки — прогнать (g): скругление 16dp, тоггл Expanded/Collapsed, контент листа неподвижен («Событие» на том же y — эталон baseline/bug_g_expanded.png / bug_g_collapsed.png), свайп-вниз закрывает. Горячие пути не затронуты: cornerRadius читается в композиции (additionalTopBody-лямбда), в measure по-прежнему только additionalTopFraction.value и overlapPx.

## [OPTIONAL] Вшитые feel-токены жестов → XBottomSheetDefaults (консолидация «вшито», НЕ конфиг)

### design
FLING_VELOCITY_THRESHOLD=400f и RESISTANCE_MAX_PX=240f — токены ощущения жестов (fling-порог settle и глубина rubber-band). Решение линзы: это ВШИТО (в конфиг не выносить — per-sheet физика сделала бы жесты приложения разнородными), но токены должны жить там, где разработчик 1XUI Core ищет все вшитые числа — в XBottomSheetDefaults, рядом с ScrimFadeDistance (перенесённым туда линзой 4 по тому же аргументу).

```kotlin
// XBottomSheetDefaults.kt
internal object XBottomSheetDefaults {
    // ... существующие токены ...
    // Вшитая физика жестов (§9: не в публичном конфиге). Значения в px НАМЕРЕННО:
    // менять только с полным прогоном 19 кейсов.
    const val FlingVelocityThresholdPxPerSec: Float = 400f
    const val ResistanceMaxPx: Float = 240f
}

// XBottomSheetState.kt: companion худеет до численной гигиены/робастности
private companion object {
    const val ANCHOR_EPS = 1f
    const val OVERSHOOT_ENTER_EPS_PX = 0.5f
    const val REMEASURE_TIMEOUT_MS = 500L
}
// dragBy/settle читают XBottomSheetDefaults.ResistanceMaxPx / .FlingVelocityThresholdPxPerSec
// (const → compile-time инлайн, ноль непрямых вызовов и аллокаций в горячем пути).
```

ANCHOR_EPS / OVERSHOOT_ENTER_EPS_PX / REMEASURE_TIMEOUT_MS остаются в companion стейта: это не «феел», а внутренняя численная гигиена — их переезд в Defaults создал бы ложное ощущение настраиваемости.

### hides_from_component
Из стейт-машины уходят два бренд-токена ощущения жестов — стейт-машина остаётся чистой логикой над числами, а «что можно крутить при переносе в 1XUI Core» собрано в одном объекте Defaults. Публичный API не растёт ни на поле.

### developer_cost
Нулевой для потребителя API — сигнатуры не меняются. Для сопровождающего компонент: все вшитые числа (геометрия, цвета, моушен, физика жестов) — один объект XBottomSheetDefaults.

### break_risk
Рискует минимально: чистый перенос const (значения бит-в-бит, const-инлайн — байткод-эквивалент). Гарантия 1:1: компиляция + смоук свайпов (a) закрытие, (b1) expand/collapse, (b2) FullScreen, (r) остановка на custom-якоре. ВАЖНО НЕ ДЕЛАТЬ в этом раунде: перевод 400/240 из px в dp-нормировку — это поведенческое изменение на плотностях ≠ базовой (240px ≈ 91dp на 420dpi, вдвое больше физически на mdpi); зафиксировать как кандидата отдельного решения при переносе в Core, с пометкой в комментарии токена.

## [RECOMMEND] Контракт проводки конфига (композиция → примитивы/State/SideEffect) — зафиксировать как правило в XBOTTOMSHEET.md

### design
Компонент уже держит конфиг вне горячих путей тремя механизмами — возвести их в явный контракт для каждого будущего конфиг-поля (иначе следующая же группа «удобно» прочитается в measure и добавит инвалидаций):

1. **Конфиг читается только в композиции корня XBottomSheet.** В measure/draw/жесты уходят распакованные примитивы и value-объекты: `SheetInsets(screenHeightPx, statusBarPx, navBarPx, loadingSheetHeightPx)`, `scrimFadeDistancePx: Float`, `AdditionalTopConfig`. Прецеденты: SheetContainer/SheetScrim.
2. **Флаги, нужные suspend-логике стейта, копируются SideEffect'ом в var-поля стейта**, а не протаскиваются параметрами глубоких вызовов:
```kotlin
SideEffect {
    state.dismissOnSwipeDown = config.dismiss.onSwipeDown
    state.onDismissRequest = { currentOnDismiss() }
}
```
(XBottomSheet.kt:105-108 — канонический образец; так dragBy/settle читают поле без снапшот-чтений конфига.)
3. **Логика-объекты создаются один раз и получают State-ссылки, не значения:** `remember(state) { SheetNestedScrollConnection(state, enabledState = interactionsEnabledState) }`, `remember { StayUnderKeyboardMeasurePolicy(keyboardState, navBarState) }` — доступность/IME читаются в колбэках/measure через State, инстанс не пересоздаётся.

Плюс два инварианта конфиг-типов: (а) каждая группа — @Immutable data class без лямбд и без AnimationSpec/Shape-полей (value-equality обязана оставаться дешёвой и корректной — XBottomSheetConfig в skip-пути); (б) дефолты только в билдерах, повторный вызов группы мёржится last-write-wins.

Оформление: раздел «Проводка конфига (обязательна для новых полей)» в XBOTTOMSHEET.md после блока API + по одной строке-ссылке на канонические точки (XBottomSheet.kt SideEffect, SheetContainer/SheetInsets, SheetNestedScrollConnection).

### hides_from_component
Ничего нового не прячет — фиксирует УЖЕ достигнутое разделение (конфиг = данные в композиции; горячие пути = примитивы/State/кэши стейта) как контракт, чтобы будущие конфиг-группы не протекли в measure/жесты и не раздули equals. Это страховка результата P1-P4 рефактора.

### developer_cost
Нулевой для потребителя API. Для контрибьютора — чеклист из 3 пунктов при добавлении конфиг-поля вместо реверс-инжиниринга, почему dismissOnSwipeDown живёт копией в стейте.

### break_risk
Кода не трогает — риск нулевой, поведение 1:1 по определению. Единственная стоимость — дисциплина ревью: новое конфиг-поле проверяется по чеклисту из трёх пунктов.

### ОТВЕРГНУТО линзой
- SettleStrategy (подменяемый выбор якоря в settle) — settle это идентичность стейт-машины §4/§5, а вся осмысленная вариативность уже задаётся ДАННЫМИ: isFillMode (замер) × skipCollapsed × peekFraction × anchors{} × dismiss.onSwipeDown формируют кандидатов, стратегия лишь выбирает по скорости/близости. Подменяемая логика = легальный обход спеки и конец гарантии 1:1; ни одному из 19 кейсов не нужна. Не забивать гвозди: у разработчика нет вопроса, на который отвечал бы SettleStrategy.
- AnchorResolver (подменяемое вычисление anchorPx/openTarget/expandTarget) — якоря уже декларативны (anchors { anchor(key, fraction) } + SheetMetrics); резолвер создал бы второй источник правды для высот и рассинхронизировал кэши lowest/highestAllowedAnchorPx, буфер settle-кандидатов и Saver-теги Custom(key).
- MotionConfig (пружина/скрим-фейд в публичный конфиг) — §9: «анимация — имитация нативной (NativeSheetSpring)», моушен = бренд-феел, не per-sheet выбор. Технически: AnimationSpec в data class сломал бы дешёвую value-equality XBottomSheetConfig (skip-путь) — SpringSpec не даёт гарантированного структурного equals.
- GestureConfig (FLING_VELOCITY_THRESHOLD/RESISTANCE_MAX_PX наружу) — per-sheet физика жестов делает листы одного приложения разнородными на ощупь; запроса нет ни в одном кейсе. Решение: вшито, консолидация токенов в Defaults (предложение №2).
- ScrimConfig (цвет/альфа/дистанция фейда) — §9: цвета вшиты, API цветов не принимает; ScrimFadeDistance — моушен-токен визуальной идентичности.
- Расширение KeyboardConfig тогглами (hideImeOnDismiss / autoFullScreen / disableGesturesUnderIme) — правила IME §6 + уточнения юзера помечены ОБЯЗАТЕЛЬНЫ; тоггл дал бы выключить обязательное (лист «улетает» от keyboard-lift, верх за статус-баром). Единственная легальная ось — bottomBehavior, и она уже enum: будущие режимы = новые значения enum в существующей группе keyboard{}, не новые флаги.
- Развязка dragHandle-видимости и жестов высоты (отдельный isHeightInteractionEnabled) — §1: «взаимодействие с высотой отключено ⇒ хендл скрывается» — связка спековая, двойной флаг породил бы запрещённые комбинации (хендл виден, жесты мертвы).
- LoaderContent-слот или LoaderStrategy — Loader вшит (192dp, PresetLoader), Lottie запрещён, скелетон компонент не поддерживает (§9); слот открыл бы дорогу обоим запретам.
- WidthConfig (512/600) и настройка инсетов — геометрия §2 вшита; инсеты — edge-to-edge канон (ПЕРЕСМОТРЕНО-блок), не предмет выбора разработчика.
- Стратегия замера (подмена detect/place-механики SubcomposeLayout) — внутренняя механика, легализована юзером под Lazy-контент (intrinsics с LazyColumn не работают); подмена ломает wrap/fill-детекцию — фундамент всех якорей.
- Вынос REMEASURE_TIMEOUT_MS/ANCHOR_EPS/OVERSHOOT_ENTER_EPS_PX куда-либо из companion стейта — это численная гигиена и робастность, не настройка; перенос в Defaults создал бы ложное ощущение настраиваемости.
- Перевод px-констант жестов (400/240) в dp-нормировку в этом раунде — поведенческое изменение на плотностях ≠ базовой (нарушает «19 кейсов 1:1»); допустимо только как отдельное решение при переносе в 1XUI Core с полной регрессией на разных density.


# Линза 4 — БЕЗОПАСНОСТЬ МИГРАЦИИ (правило №0: 19 кейсов 1:1). База изучена: /Users/Victor/work/bs-zorder-lab, компонент app/src/main/java/com/onexui/bottomsheet/** после P0→P4-DSL (XBS-QUALITY-RESEARCH.md, статус DONE), demo — 19 кейсов A,B1,B2,C,D,E,F,G,I,L,S,J,K,M,N,O,P,Q,R в com/onexui/demo/XbsDemoScreen.kt. Ключевые точки бинарного поведения: (1) rememberSaveable(config, saver=xBottomSheetStateSaver(config)) в state/RememberXBottomSheetState.kt — ключ = value-equality XBottomSheetStateConfig, configure-лямбда исполняется каждую рекомпозицию; (2) Saver — позиционный List<Any> [tag, isLoading, additionalTopName] с getOrNull-дефолтами и tag-строками "h/c/col/ec/efs/l/cu:"; (3) горячий measure-путь — SubcomposeLayout-лямбда SheetContainer.kt:100-135 (каждый кадр drag/settle), updateMetrics с примитив-сравнением до аллокации (L1), кэши lowest/highestAllowedAnchorPx только в updateMetrics; (4) конфиг разбирается в корне XBottomSheet.kt:118-140 — вниз идут примитивы/State. ОЦЕНКА СУЩЕСТВУЮЩЕЙ МАТРИЦЫ: клик-прогон 19 кейсов уверенно ловит ~2/3 классов правок целевого раунда — каждая конфиг-ручка имеет именной кейс (e/f/g/j/k/l/s — sheet-config; c/d/k/r — state-config), жесты (a/b1/b2/j/r), IME (i/l/s), measure wrap/fill (a,n/b2,m,g,d,k). СЛЕПЫЕ ЗОНЫ (кликами НЕ ловятся): 1) Saver/process-death — вообще не проверяется кнопками; 2) сброс стейта от нестабильного ключа rememberSaveable — ловится только печатью в кейсе (i); 3) additionalTop+короткий контент (E1-зона, верифицирована разовым скрином, кейса нет); 4) Loading+IME (L5-сценарий); 5) скрытый смонтированный лист + фокус хоста (П2 drop(1)); 6) широкий экран ≥600dp/512dp-центр (только ручной wm size); 7) markContentReady на фоне/таймаут (L4); 8) fill-режим + custom-якорь ниже peek при dismissOnSwipeDown=false (кейс j — wrap). Зоны 1–2 закрываются канарейками (предложения 2–3), 3–4 — двумя новыми кейсами (t)/(u) (предложение 5), 5–8 остаются ручными сценариями чек-листа DoD.

## [STRONGLY-RECOMMEND] Фазировка M0–M4: аддитивное ядро + атомарные switch-точки (по образцу P4-DSL)

### design
M0 «замороженная база»: полный ручной прогон 19 кейсов + baseline-скрины (каталог baseline/ уже в практике: p1_*, bug_g_*) + git-точка; всё дальнейшее сравнивается с базой, не с памятью.

M1 «аддитивные конфиги» (сигнатуры не меняются, серия мелких коммитов). Новые ручки входят ТОЛЬКО как поля существующих билдеров и новые вложенные группы. Механика совместимости: конструкторы data class уже internal (`XBottomSheetConfig internal constructor` — config/XBottomSheetConfig.kt), call-sites строят конфиг только DSL-ем → добавление поля не трогает ни один из 19 кейсов. Образец (если раунд вводит группу жестов):

```kotlin
@Immutable
internal data class GestureConfig(
    val flingVelocityThreshold: Float,
    val resistanceMaxPx: Float,
)

@XBottomSheetDsl
internal class GestureConfigBuilder {
    var flingVelocityThreshold: Float = 400f   // = текущий companion-const XBottomSheetState
    var resistanceMaxPx: Float = 240f
    internal fun build(): GestureConfig = GestureConfig(flingVelocityThreshold, resistanceMaxPx)
}

// в XBottomSheetConfigBuilder — только добавление:
private val gestureBuilder = GestureConfigBuilder()
fun gesture(configure: GestureConfigBuilder.() -> Unit) { gestureBuilder.configure() }
// build(): ..., gesture = gestureBuilder.build()
```
Критерий каждого M1-коммита: assembleDebug зелёный БЕЗ правок demo; дефолт нового поля = текущему вшитому значению (сверка по таблице «старое место → новое место», риск №3); дефолт-восьмёрка (a,b1,b2,m,n,o,p,q) рендерится 1:1.

M2 «внутренние сущности/Scope'ы»: новые классы (стратегии, скоупы, measure-политики) вводятся internal и вайрятся внутри компонента при неизменной публичной поверхности; demo не трогается; прогон — по матрице затронутой зоны (предложение 5).

M3 «атомарные switch-точки»: любая смена сигнатуры — один коммит, старая и новая формы НЕ сосуществуют (ambiguity дефолтных форм доказана в P4-DSL: обе применимы без аргументов). Компилятор — перечислитель call-sites; их полный список = 19 в XbsDemoScreen.kt (строки 194 r, 206 m, 220 n, 243 o, 270 p, 286 q, 306 a, 324 b1, 340 b2, 357 c, 373 d, 393 e, 411 f, 430 g, 463 i, 487 l, 513 s, 537 j, 558 k) + ноль в com.alva.*/com.zorderlab.* (подтверждено grep-ом P4-DSL). Нюанс-лазейка: добавление receiver'а слоту —
```kotlin
@Stable
interface XBottomSheetContentScope { val sheetValue: SheetValue }

middle: @Composable XBottomSheetContentScope.() -> Unit
```
— для inline trailing-лямбд source-совместимо (все 19 кейсов передают инлайн-лямбды) → такой switch фактически аддитивен; ломает только val-типизированные лямбды (в demo их нет). Scope-объект — один инстанс `remember(state) { ... }`, иначе слоты теряют skip.

M4 «регрессия + доки»: прогон по матрице (предложение 5) + канарейки (предложения 2–3); XBOTTOMSHEET.md и XBS-SPEC.md с пометками «ПЕРЕСМОТРЕНО (раунд N)». Порядок жёсткий: M1/M2 чередуются, M3 — только после зелёных M1+M2, по одной switch-точке на коммит.

### hides_from_component
Миграционная механика прячется от фичевого разработчика: минимальный call-site (`rememberXBottomSheetState()` + `XBottomSheet(state, onDismiss) { ... }`) не меняется ни в одной фазе; вся эволюция живёт в билдерах и internal-обвязке.

### developer_cost
Минимальный call-site: 0 правок во всех фазах. Типовой (с конфигом): 0 правок в M1/M2; в M3 — механическая правка только если switch-точка коснулась его формы (образец P4: 11 правок на 19 кейсов). Продвинутый: новые группы (gesture{} и т.п.) доступны сразу после соответствующего M1-коммита.

### break_risk
Риск — пропущенный call-site или сосуществование форм при switch. Гарантия 1:1: одна форма за раз (компилятор перечисляет правки), grep модуля перед M3 (alva/zorderlab компонент не используют — уже проверено), критерий «assembleDebug без правок demo» для каждого M1-коммита, прогон затронутой зоны после каждого коммита.

## [STRONGLY-RECOMMEND] Saver-контракт: append-only список + tag-freeze + мягкая деградация (инвариант process-death)

### design
Зафиксировать (комментарий в state/XBottomSheetStateSaver.kt + пункт ревью) четыре инварианта:

1) Форма — append-only. Сегодня сохраняется `List<Any>`: `[tag: String, isLoading: Boolean, additionalTopName: String]`. Новые сохраняемые факты — ТОЛЬКО в конец списка; чтение — только `saved.getOrNull(i) as? T ?: default` (текущий стиль restore сохранить). Переупорядочивание/удаление позиций запрещено: Bundle со старой формой обязан восстановиться, недостающие хвостовые позиции — дефолтами.
```kotlin
internal fun xBottomSheetStateSaver(config: XBottomSheetStateConfig): Saver<XBottomSheetState, List<Any>> = Saver(
    save = { state -> listOf(
        sheetValueTag(state.currentValue),   // [0] — контракт заморожен
        state.isLoading,                     // [1]
        state.additionalTopState.name,       // [2]
        /* НОВОЕ — только сюда, [3+] */
    ) },
    restore = { saved -> /* только getOrNull(i) + дефолт */ },
)
```
2) Tag-freeze: строки "h"/"c"/"col"/"ec"/"efs"/"l"/"cu:" — персистентный контракт; любые будущие ре-неймы SheetValue НЕ меняют теги; новые SheetValue получают новые теги; незнакомый тег уже падает в Hidden (else-ветка sheetValueFromTag) — сохранить.

3) Config НЕ сохраняется: он пересоздаётся DSL-ем на call-site. Несовместимость «старый сохранённый стейт × новый конфиг» деградирует мягко и без require: Custom(key) при удалённом якоре — фолбэк customAnchorPx → peekFraction (SheetMetrics.customAnchorPx — уже так); Loading при initialLoading=false — восстановится и корректно снимется markContentReady.

4) Ключ rememberSaveable: `rememberSaveable(config, saver = xBottomSheetStateSaver(config))`. Рост полей XBottomSheetStateConfig безопасен, пока каждое поле value-equal (см. предложение 3). Против process-death inputs не персистятся — восстановление идёт по позиционному registry-ключу композиции, рост конфига restore НЕ ломает; ломает только смена формы списка (п.1).

Канарейка (не тест — тесты в проекте запрещены), в обязательный прогон M4:
```bash
adb shell am start -n com.zorderlab/com.onexui.demo.XbsDemoActivity
# руками: (b2) развернуть в FullScreen / (d) оставить в Loading / (g) карточка Collapsed / (r) стать на якорь "half"
adb shell input keyevent KEYCODE_HOME && adb shell am kill com.zorderlab
adb shell am start -n com.zorderlab/com.onexui.demo.XbsDemoActivity
# ожидание: activeCase восстановлен (он сам saveable, XbsDemoScreen.kt:125), лист в том же SheetValue,
# additionalTopFraction стартует из восстановленного additionalTopState (XBottomSheet.kt:76-78), offset доводит snapToCurrentAnchor
```

### hides_from_component
Персистентность полностью прячется в файле Saver'а: ни стейт-машина, ни конфиги, ни layout не знают про Bundle; restore() остаётся единственной internal-точкой входа восстановления.

### developer_cost
Нулевой для потребителя API: контракт целиком внутренний. Для автора раунда — дисциплина «новые позиции только в конец, теги не трогать» + 5 минут скрипта на прогоне.

### break_risk
Нарушение инварианта — самый тихий класс регрессов: после process-death лист молча восстанавливается в Hidden или не в тот стейт, клик-матрица 19 кейсов этого НЕ видит (слепая зона №1). Гарантия 1:1 — process-death-скрипт на b2/d/g/r в обязательном чек-листе + append-only/tag-freeze как пункт ревью любого diff'а, трогающего Saver или SheetValue.

## [STRONGLY-RECOMMEND] Инвариант стабильности конфигов: value-equality в skip-путях, стратегии — только sealed value-типы

### design
Два правила на весь целевой раунд.

(1) В XBottomSheetConfig / XBottomSheetStateConfig кладутся только типы со структурным equals: примитивы, enum, Dp, sealed data object/data class, ограниченный List value-типов (только anchors). Стратегии (правило «стратегии = интерфейсы/классы, не лямбды») в конфиге допустимы ТОЛЬКО как sealed-иерархии value-объектов:
```kotlin
sealed interface SettleStrategy {
    data object NearestAnchor : SettleStrategy
    data class VelocityBiased(val bias: Float) : SettleStrategy
}
// в билдере: var settleStrategy: SettleStrategy = SettleStrategy.NearestAnchor
```
Почему это инвариант бинарного поведения: configure-лямбда исполняется КАЖДУЮ рекомпозицию хоста → каждый раз новый config-инстанс; спасает только value-equality. Открытый (не-sealed) интерфейс, реализованный на call-site анонимным классом без equals, даёт config != config → (а) для sheet-config: XBottomSheet перестаёт скипаться — перф-регресс; (б) для state-config: input rememberSaveable «меняется» каждую рекомпозицию → СТЕЙТ ЛИСТА ПЕРЕСОЗДАЁТСЯ, лист моргает в Hidden при каждой печати в поле. Если раунду понадобится открытая (расширяемая снаружи) стратегия — её место в параметрах XBottomSheet рядом со слотами, НЕ в state-config и вне ключа rememberSaveable.

(2) «Конфиг разбирается в корне»: вложенные конфиг-объекты не путешествуют ниже XBottomSheet — как сейчас (XBottomSheet.kt:118-140: вниз идут overlayBackground/dragHandle/bottomBehavior примитивами; AdditionalTopConfig — плоский data class с одним Dp). Тогда рост конфига оплачивается ОДНИМ equals на рекомпозицию хоста (O(полей), примитивы — наносекунды), а SheetContainer/SheetBody сохраняют skip по примитивам. Запрещено включать по-кадровые величины в конфиг-объекты (additionalTopFraction остаётся отдельным Animatable-параметром — канон P3-Б/бага additionalTop).

Верификация: кейс (i), серия печати в поле поиска — лист не пересоздаётся и не моргает (канон верификации P4-DSL); при желании Layout Inspector: composition count XBottomSheet не растёт от символа.

### hides_from_component
Прячет от компонента и от call-site вопрос «стабилен ли конфиг»: стабильность гарантируется системой типов (sealed value-иерархии), а не дисциплиной разработчика фичи.

### developer_cost
Минимальный/типовой call-site: без изменений. Продвинутый: выбор стратегии — одна строка DSL (`settleStrategy = SettleStrategy.VelocityBiased(bias = 0.3f)`); собственную реализацию стратегии написать нельзя (sealed) — это осознанная цена за невозможность сломать skip/Saver.

### break_risk
Риск №1 всего раунда: одно не-value поле в state-config → сброс стейта листа на каждую рекомпозицию. Ловится ТОЛЬКО печатью в (i) — закрепить её обязательным шагом чек-листа для любой правки конфигов. Sealed-ограничение гарантирует, что сломать инвариант с call-site невозможно в принципе.

## [STRONGLY-RECOMMEND] Zero-alloc measure-контракт для конфиг-чтений (горячий путь SheetContainer)

### design
Measure-лямбда SubcomposeLayout (layout/SheetContainer.kt:100-135) исполняется каждый кадр drag/settle/rubber-band — это самый горячий путь компонента. Контракт для ЛЮБЫХ новых конфиг-значений, нужных в measure, три разрешённые формы:

(а) примитив, вычисленный в композиции и захваченный лямбдой (как ceilingPx, insets-поля, overlapPx) — стабильная ссылка, ноль аллокаций на кадр;
(б) State<T>/Animatable, чей .value читается ВНУТРИ measure (как additionalTopFraction.value в SheetContainer.kt:124 и keyboardState.value в StayUnderKeyboardMeasurePolicy) — snapshot-чтение в measure инвалидирует только layout-фазу;
(в) стабильный объект, созданный в композиции `remember { ... }` БЕЗ ключей, значения — через State внутри (образец: StayUnderKeyboardMeasurePolicy(keyboardState, navBarState) — итог раунда A/B).

Запрещено в measure-лямбде: создание объектов (кроме Constraints — inline value class), чтение полей identity-нестабильного конфига, вызовы DSL-билдеров, boxing.

Смежные инварианты того же пути:
- updateMetrics сохраняет примитив-сравнение ДО аллокации SheetMetrics (state/XBottomSheetState.kt:169-179, фикс L1). Правило роста: новое поле метрик добавляется ОДНОВРЕМЕННО в data class SheetMetrics И в это сравнение — рассинхрон даёт либо «метрики не обновляются» (поле не в сравнении не триггерит пересоздание), либо «snapshot грязнится каждый пасс» (поле в data class, но сравнение не дополнено — вечное неравенство).
- Кэши lowestAllowedAnchorPx/highestAllowedAnchorPx пересчитываются только в updateMetrics (фиксы П1/L6); любые новые якорные сущности целевой модели обязаны идти тем же путём — не считать в dragBy/settle на каждое move-событие (там 60–120 Гц).
- Слот-лямбды detectBody/placeBody/additionalTopBody остаются в композиции (SheetContainer.kt:73-90, фикс #5) — новые слоты целевой модели не создавать внутри measure.

Верификация: драг-прогон (b2) и анимация карточки (g) без jank; ревью diff'а measure-лямбд на аллокации — обязательный пункт DoD (профайлер — по желанию).

### hides_from_component
Покадровая механика остаётся инкапсулированной в layout-файлах; конфиги и Scope'ы наружу отдают только дискретные значения/State — разработчик фичи физически не может попасть кодом в кадровый путь.

### developer_cost
Нулевой для потребителя API. Для автора раунда — таблица из трёх разрешённых форм в голове (или в XBOTTOMSHEET.md, секция «Технические решения»), сверка при каждом новом параметре measure-политик.

### break_risk
Нарушение НЕ ломает поведение 1:1 мгновенно — проявляется как GC-давление/jank/потеря skip, т.е. тихий регресс, который клик-матрица формально «проходит». Страховка: пункт ревью «diff measure-лямбд» + драг-прогон (b2)/(g) глазами в каждом M-коммите, затрагивающем layout/.

## [STRONGLY-RECOMMEND] Регрессионная матрица «зона правки → кейсы» + два новых кейса (t)/(u) — минимум

### design
Маппинг для прогона после каждого M-коммита (полный прогон 19 — только перед финалом):

| Зона правки | Обязательный прогон | Канарейки |
|---|---|---|
| state-config (skipCollapsed/initialLoading/peekFraction/anchors), Saver, ключ rememberSaveable | c, d, k, r | ротация открытых c/d/r; process-death-скрипт b2/d/g/r (предл. 2); печать в (i) (предл. 3) |
| sheet-config (overlay/dragHandle/cornerRadius/dismiss/keyboard) | e, f, g, j, k, l, s | дефолт-восьмёрка a,b1,b2,m,n,o,p,q — дрейф дефолтов |
| жесты: settle/resistance/floor/каналы | a, b1, b2, j, r | b1 — L6-клампинг (held-драг вверх без пустого стрейча); b1/b2 — L8 (инерция списка после дороста) |
| IME | i, l, s | скрытие IME в (i) — откат авто-FullScreen; кейс (u) ниже |
| measure/detect/слоты (SubcomposeLayout, AdditionalTop, StayUnderKeyboard) | a, n (wrap), b2, m (fill), g, d, k | кейс (t) ниже; ротация открытого (g) |
| наблюдатели ObserveSheetState | d, k, i, g | скрим-тап закрывает (П2-зона); рост (k) под открытой клавиатурой |
| пресеты/тема | a, d | — |

Новые кейсы — строго по слепым зонам, не по новым ручкам (правило: «новый кейс только на новую ВИДИМУЮ степень свободы или слепую зону»):
```kotlin
private enum class DemoCase(val label: String) {
    // ... существующие 19 ...
    T("(t) AdditionalTop + короткий контент (wrap)"),
    U("(u) Loading + поиск: IME во время Loading"),
}

@Composable
private fun CaseAdditionalTopShort(onClose: () -> Unit) {
    val state = rememberXBottomSheetState()
    // additionalTop-карточка + Title + BodyText + кнопка (контент < peek)
    // ожидание: открытие Content-ПО-КОНТЕНТУ (не peek 2/3 — E1-зона), карточка над листом, тоггл работает
}

@Composable
private fun CaseLoadingIme(onClose: () -> Unit) {
    val state = rememberXBottomSheetState { initialLoading = true }
    // PresetSearchField в top, delay → markContentReady
    // ожидание: фокус в поле во время Loading; markContentReady под открытой IME → авто-FullScreen,
    // а не «верх листа за потолком» (L5-зона; коллектор IME ключуется парой keyboardState.value to isLoading)
}
```
Итого 21 кейс; новые ручки конфига из M1 проверяются РАСШИРЕНИЕМ существующих кейсов (например, gesture{} — прогоном b1/b2 c нестандартным порогом в отладочной ветке, без нового кейса). Оценка покрытия и полный список слепых зон — в сводке линзы; зоны 5–8 (скрытый лист+фокус хоста, wide-screen 512dp, markContentReady на фоне, fill+custom-якорь ниже peek без свайп-закрытия) остаются ручными сценариями чек-листа — по частоте/стоимости кейсы им не положены.

### hides_from_component
Ничего не прячет — это процессная сущность раунда: прячет от архитектора необходимость каждый раз заново решать «что гонять», превращая регрессию в детерминированный маппинг.

### developer_cost
Для потребителя API — ноль. Для раунда: ~30–60 строк demo-кода на два кейса + 10–15 минут прогона по матрице на коммит вместо полного часа на все 19.

### break_risk
Главный риск самой матрицы — иллюзия покрытия: зелёный клик-прогон 19 кнопок НЕ эквивалентен «ничего не сломано» (слепые зоны 1–2 ловятся только канарейками). Митиг: DoD требует канарейки наравне с кнопками; новые кейсы t/u принимаются скриншотом против ожидания, зафиксированного в описании кейса.

## [STRONGLY-RECOMMEND] Definition of Done раунда + риск-топ-5 с митигами

### design
DoD (все пункты обязательны, порядок = порядок проверки):
1. `./gradlew :app:assembleDebug` зелёный; diff не касается com.alva.*, com.zorderlab.* и org/xplatform/** (verbatim-копия xbet).
2. Полный прогон 19 (21 с t/u) кейсов против baseline-скринов (эталон 411×731: `adb shell wm size 1080x1919`) — 1:1; по ходу раунда — прогоны затронутых зон по матрице после каждого M-коммита.
3. Канарейки: печать в (i) — лист не моргает/не пересоздаётся; ротация открытых c/d/g/r; process-death-скрипт b2/d/g/r; дефолт-восьмёрка a,b1,b2,m,n,o,p,q — дрейф дефолтов.
4. Новые кейсы (t)/(u) зелёные; других новых кейсов НЕТ (минимум — правило раунда).
5. Грепы по diff (жёсткие правила спеки §9 и юзера): `require(` не появился; лямбд в data class нет; цвета/геометрия-токены не появились в конфигах; `animate*AsState` не появился; `remember(key) { создание-сущности }` не появился; булеаны is/has.
6. Доки: XBOTTOMSHEET.md (блок API), XBS-SPEC.md («ПЕРЕСМОТРЕНО (раунд N)» — пометки главнее исходного текста), таблица «старое место дефолта → новое место», Saver-контракт комментарием в XBottomSheetStateSaver.kt.
7. Каждая правка — с ответом «зачем это разработчику» и functionalRisk (принцип deliberate fixes); сомнительные предложения других линз отклонены с причиной, не исполнены механически.

РИСК-ТОП-5:
1. Нестабильный ключ rememberSaveable (не-value поле в state-config) → стейт листа пересоздаётся каждую рекомпозицию, лист моргает в Hidden. МИТИГ: sealed value-типы стратегий (предл. 3) + канарейка «печать в (i)» на каждый конфиг-коммит.
2. Дрейф формы Saver (переупорядочен list, переименованы теги) → после process-death лист молча в Hidden/не в том стейте. МИТИГ: append-only + tag-freeze (предл. 2) + process-death-скрипт b2/d/g/r в DoD.
3. Дрейф дефолтов при переносе значений в новые билдеры (прецедент уже был: peekFraction 2/3 юзера против спековских 0.60 — два источника правды до P3-В) → тихо меняются все 8 дефолт-кейсов. МИТИГ: таблица переноса дефолтов «вшитая константа → var билдера» в один экран, ревью diff'ом, прогон дефолт-восьмёрки.
4. Конфиг-объекты/аллокации просачиваются в measure и по-кадровые пути → jank, потеря skip, грязный snapshot каждый layout-пасс. МИТИГ: zero-alloc контракт (предл. 4), пункт ревью «diff measure-лямбд», драг-прогон (b2)/(g).
5. Незавершённый атомарный switch (сосуществование форм → ambiguity; или пропущенный call-site). МИТИГ: одна форма за раз, компилятор-перечислитель, greп модуля перед M3 (alva/zorderlab чисты — подтверждено P4-DSL), полный список 19 call-sites зафиксирован в предл. 1.

### hides_from_component
Процессная сущность: прячет от архитектора и исполнителей раунда риск «забыли проверить» — критерий завершения объективен и не зависит от памяти сессии (preserve-task-context).

### developer_cost
Для потребителя API — ноль. Для раунда: ~1–1.5 часа финальной верификации (полный прогон + канарейки + грепы) — фиксированная цена правила №0.

### break_risk
Сам DoD ничего не ломает; его отсутствие — ломает: без пунктов 3 и 5 тихие регрессы (Saver, skip, дефолты) проходят зелёную сборку и зелёный клик-прогон. Гарантия 1:1 достигается только полным чек-листом.

### ОТВЕРГНУТО линзой
- @Deprecated-мост / сосуществование старой и новой сигнатур при switch — отвергнуто: доказанная в P4-DSL ambiguity дефолтных форм (обе применимы без аргументов); API internal, внешних потребителей нет — deprecation-цикл не нужен и невозможен. Атомарный switch дешевле и безопаснее.
- Версионированный Saver (explicit version-поле / Bundle-map вместо списка) — отвергнуто как «забивание гвоздей»: append-only список + getOrNull-дефолты уже дают forward-tolerance; у лабы нет юзеров со старыми сохранёнными стейтами поверх обновления — версия была бы кодом без потребителя. Пересмотреть только при переносе в 1XUI Core.
- Сохранение конфига в Saver («восстановить как было») — отвергнуто: конфиг всегда пересоздаётся DSL-ем на call-site; сохранение создало бы два источника правды и конфликт «сохранённый конфиг × новый код». Мягкая деградация несовместимого стейта уже встроена (Custom-фолбэк, Loading+markContentReady).
- Runtime-мутабельный конфиг (var-поля в стейте, «менять на лету» без пересоздания) — отвергнуто: контракт «смена конфига пересоздаёт стейт» прост, задокументирован и верифицирован P4-DSL; мутабельность открыла бы класс гонок (metrics/кэши якорей/floor посреди жеста) — ровно то, что кэш-инварианты updateMetrics запрещают.
- Автотесты / скриншот-тесты для регрессии миграции — отвергнуто: правило проекта «тесты НЕ писать»; регрессия — ручной прогон по матрице + baseline-скрины + adb-канарейки (process-death), что уже практика раундов P0–P4.
- Новый demo-кейс на каждую новую ручку конфига — отвергнуто: раздувает матрицу и стоимость полного прогона; новые ручки проверяются расширением существующих кейсов, новый кейс — только на новую ВИДИМУЮ степень свободы или слепую зону (итог: ровно 2 новых кейса t/u на 8 слепых зон, остальные — ручные сценарии чек-листа).
- binary-compatibility-validator / API-dump инструментарий на лабе — отвергнуто: модуль single-module, весь API internal — валидатору нечего охранять; осмысленно только при снятии internal в 1XUI Core (упомянуть в доках переноса, не внедрять сейчас).
- Открытые (не-sealed) интерфейсы стратегий в конфигах «для максимальной гибкости» — отвергнуто как прямое нарушение правила №0: анонимная реализация без equals на call-site разрушает value-equality конфига → сброс стейта листа каждую рекомпозицию (худший тихий регресс раунда). Гибкость снаружи — только через параметры XBottomSheet вне ключа rememberSaveable.


# Линза 3 — ПРЕЦЕДЕНТЫ (M3 ModalBottomSheet/SheetState/ModalBottomSheetProperties, BottomSheetScaffold, foundation AnchoredDraggable/DraggableAnchors, Scaffold contentPadding, LazyListScope, Modifier.Node). Метод: сверка актуального API androidx по deepwiki (rememberModalBottomSheetState(skipPartiallyExpanded, confirmValueChange); SheetState: currentValue/targetValue/isAnimationRunning/requireOffset; ModalBottomSheetProperties(securePolicy, shouldDismissOnBackPress=true, shouldDismissOnClickOutside=true, isAppearanceLight*Bars; isFocusable удалён); DraggableAnchors { value at position } + AnchoredDraggableState.updateAnchors, вызываемый из layout-фазы через Modifier.draggableAnchors/onSizeChanged) против текущего кода /Users/Victor/work/bs-zorder-lab/app/src/main/java/com/onexui/bottomsheet/**. Главный вывод: XBS уже ВПЕРЕДИ M3 в трёх пунктах, копировать их не надо: (1) config-DSL с value-equality (@Immutable data + билдер-дефолты) сильнее M3-простыни из 12+ параметров и properties-класса; (2) peekFraction + anchors-DSL конфигурируемы — а захардкоженный 50%-partial у M3 это исторически главная причина форков ModalBottomSheet; (3) suspend awaitMetrics() вместо кидающегося requireOffset() — bootstrap без require. Переносимы три идеи: DraggableAnchors/updateAnchors-паттерн как ВНУТРЕННЯЯ таблица якорей (P1), infix `at` в anchors-DSL (P2), shouldDismissOnBackPress как dismiss-флаг (P3). Слепое копирование отвергается: confirmValueChange-лямбда (P4, у нас запрет лямбд + onDismissRequest уже сильнее веты), оконные свойства properties, contentPadding-паттерн, слот dragHandle, вынос порогов/анимации. Урок дублирования BottomSheetScaffold vs ModalBottomSheet (каждый реализует settle сам, ядро не извлечено) закрывается P1: якорная математика становится переиспользуемым чистым объектом до того, как появится второй вариант листа.

## [STRONGLY-RECOMMEND] P1. SheetAnchorTable — внутренняя immutable-таблица якорей по образцу DraggableAnchors + updateAnchors-паттерн

### design
Образец: foundation DraggableAnchors (immutable снапшот позиций с запросами positionOf/closestAnchor/minPosition/maxPosition) + AnchoredDraggableState.updateAnchors, вызываемый при смене размеров из layout-фазы. У нас точка пересчёта уже есть — updateMetrics (зовётся из measure с guard'ом по примитивам). Новый файл state/SheetAnchorTable.kt:

```kotlin
// Чистый immutable-объект без Compose-зависимостей: вся математика якорей settle/drag в одном месте.
@Immutable
internal class SheetAnchorTable private constructor(
    // Rest-якоря (без Hidden): отсортированы по anchorPx, дедуп по px (первый добавленный побеждает — семантика distinctBy).
    private val restEntries: List<AnchorEntry>,
) {
    @Immutable
    internal data class AnchorEntry(val value: SheetValue, val anchorPx: Int)

    val lowestRestAnchorPx: Int = restEntries.firstOrNull()?.anchorPx ?: 0
    val highestRestAnchorPx: Int = restEntries.lastOrNull()?.anchorPx ?: 0

    // Порт settle-выбора: fling через порог → следующий якорь по направлению (velocity < -FLING → выше,
    // > FLING → ниже), иначе ближайший по |px - offset|. Hidden(0) добавляется в кандидаты только при
    // isDismissAllowed И отсутствии rest-якоря на 0px (сегодня Hidden кладётся последним и выпадает в distinctBy).
    // null — кандидатов нет (парность с текущим empty-фолбэком settle).
    fun settleTarget(offsetPx: Float, velocityPxPerSec: Float, isDismissAllowed: Boolean): SheetValue?

    // offset стоит на rest-якоре (|px - offset| < ANCHOR_EPS)? Для onPreFling: не съедать инерцию списка (фикс L8).
    fun isAtRestAnchor(offsetPx: Float): Boolean

    internal companion object {
        val Empty = SheetAnchorTable(emptyList())
        // Единственная точка построения — дословный порт buildSettleCandidates:
        // fill → [Collapsed?peek] + FullScreen + Custom(key)*; skipCollapsed → Content; wrap-короткий → Content;
        // wrap-длинный → Collapsed + expandTarget. Константы FLING_VELOCITY_THRESHOLD=400f / ANCHOR_EPS=1f переезжают сюда без изменения значений.
        fun resolve(metrics: SheetMetrics, skipCollapsed: Boolean): SheetAnchorTable
    }
}
```

Стейт-машина (/Users/Victor/work/bs-zorder-lab/app/src/main/java/com/onexui/bottomsheet/state/XBottomSheetState.kt) худеет:

```kotlin
internal class XBottomSheetState internal constructor(config: XBottomSheetStateConfig) {
    private var anchorTable: SheetAnchorTable = SheetAnchorTable.Empty

    internal fun updateMetrics(...) {
        // guard по примитивам как сейчас (L1); при фактической смене:
        metrics = updated
        anchorTable = SheetAnchorTable.resolve(updated, skipCollapsed)   // updateAnchors-паттерн M3
    }

    private suspend fun settle(velocity: Float) {
        isDragging = false
        accumulatedOvershootPx = 0f
        imePromotedFrom = null
        val target = anchorTable.settleTarget(offset.value, velocity, dismissOnSwipeDown)
        when (target) {
            null -> animateTo(currentValue)              // парность с текущим empty-фолбэком
            SheetValue.Hidden -> onDismissRequest()
            else -> animateTo(target)
        }
    }

    private suspend fun dragBy(deltaHeightPx: Float) {
        val floorPx = if (dismissOnSwipeDown) 0f else anchorTable.lowestRestAnchorPx.toFloat()
        val overshootBase = if (atTop) measured.maxHeightPx.toFloat() else anchorTable.highestRestAnchorPx.toFloat()
        // остальная механика overshoot/resistance — без изменений
    }

    internal fun isOffsetAtRestAnchor(): Boolean = anchorTable.isAtRestAnchor(offset.value)
}
```

Удаляются: buildSettleCandidates, computeLowestAllowedAnchorPx, computeHighestAllowedAnchorPx, приватные кэши lowestAllowedAnchorPx/highestAllowedAnchorPx, private data class AnchorCandidate, выбор chosen внутри settle (~80 строк). openTarget/expandTarget/anchorPx (state/SheetAnchorTargets.kt) в этом раунде НЕ трогать — таблица покрывает только settle/drag-границы/preFling; минимальный дифф. Бонус M3-урока про BottomSheetScaffold: будущий persistent-вариант листа переиспользует SheetMetrics + SheetAnchorTable без форка settle-логики. Зачем разработчику (сопровождающему): settle() читается в 5 строк, вся якорная математика — тестируемый чистый объект; заодно уходят аллокации mutableList+Pair+distinctBy+sortedBy на каждый settle и каждый preFling (isOffsetAtRestAnchor сегодня перестраивает список кандидатов на каждый fling).

### hides_from_component
Из стейт-машины прячется ВСЯ якорная математика (набор кандидатов по режимам wrap/fill/skipCollapsed, floor/ceiling драга, midpoint+velocity-выбор цели, проверка «на якоре») — в чистый immutable-объект state/SheetAnchorTable.kt без Compose-зависимостей. XBottomSheetState остаётся оркестратором (когда пересчитать, когда анимировать), таблица — вычислителем (куда).

### developer_cost
Для пользователя компонента — ноль (всё internal, публичное API не меняется, 19 call-sites не трогаются). Для сопровождающего: −80 строк в стейт-машине, +1 маленький чистый файл; отладка settle сводится к одному объекту.

### break_risk
Риск — неточный порт формул. Гарантия 1:1: (1) distinctBy-семантика «первый добавленный побеждает»: Hidden кладётся последним, значит при гипотетическом rest-якоре на 0px Hidden выпадает — в settleTarget включать Hidden только если нет rest-записи с anchorPx==0; (2) сохранить empty-фолбэк settle → animateTo(currentValue) через nullable-результат; (3) константы FLING_VELOCITY_THRESHOLD=400f / ANCHOR_EPS=1f перенести без изменения значений; (4) dismissOnSwipeDown мутабелен (пишется SideEffect'ом из конфига) — в таблицу НЕ вшивать, передавать параметром запроса. Верификация: (a) свайп-закрытие, (b1) expand/collapse + L6-клампинг хендл-драга на ExpandedContent, (b2) FullScreen, (j) floor при выключенном закрытии, (r) кастомный якорь 0.5f, L8 — инерция списка после составного жеста.

## [RECOMMEND] P2. Infix `at` в anchors-DSL — канон DraggableAnchors { value at position }

### design
Образец: foundation `DraggableAnchors { Start at 0f; End at 100f }` — единственный общепринятый Compose-словарь для «ключ на позиции». Правка /Users/Victor/work/bs-zorder-lab/app/src/main/java/com/onexui/bottomsheet/state/XSheetAnchorsBuilder.kt:

```kotlin
@XBottomSheetDsl
internal class XSheetAnchorsBuilder {
    private val anchors = mutableListOf<XSheetAnchor>()

    // Канон M3 DraggableAnchors: «ключ на доле высоты экрана». Member-extension на String
    // доступен ТОЛЬКО внутри anchors { } (скоуп билдера), глобально String не загрязняется.
    infix fun String.at(heightFraction: Float) {
        anchors.add(XSheetAnchor(key = this, heightFraction = heightFraction))
    }

    internal fun build(): List<XSheetAnchor> = anchors.toList()
}
```

Метод anchor(key, heightFraction) УДАЛЯЕТСЯ (не оставлять две формы одного действия — иначе API-шум). Call-site минимальный — без anchors вовсе (дефолт пустой). Типовой (кейс r, единственный в demo):

```kotlin
val sheet = rememberXBottomSheetState {
    anchors { "half" at 0.5f }
}
```

Продвинутый:

```kotlin
val sheet = rememberXBottomSheetState {
    skipCollapsed = false
    anchors {
        "third" at 0.33f
        "twoThirds" at 0.66f
    }
}
```

Зачем разработчику: якорная запись читается как в платформенном AnchoredDraggable — нулевой порог входа для любого, кто видел M3-код; согласуется с DSL-first предпочтением проекта.

### hides_from_component
Логики не прячет — выравнивает публичный словарь якорей с платформенным каноном (узнаваемость > самодельный anchor()). Фабрика XSheetAnchor остаётся спрятанной в билдере, как сейчас.

### developer_cost
Минимальный call-site: ничего (anchors опциональны). Типовой: одна строка `"half" at 0.5f`. Продвинутый: несколько строк-якорей без запятых и скобок вызова.

### break_risk
Compile-time only: один call-site (кейс r, XbsDemoScreen) правится атомарно anchor("half", 0.5f) → "half" at 0.5f. Рантайм-поведение идентично: тот же XSheetAnchor в тот же список. Верификация: сборка + кейс (r) — свайп peek → 50% → full.

## [RECOMMEND] P3. dismiss { onBackPress } — перенос shouldDismissOnBackPress из ModalBottomSheetProperties в dismiss-группу конфига

### design
Образец: ModalBottomSheetProperties(securePolicy, shouldDismissOnBackPress = true, shouldDismissOnClickOutside = true) — M3 сгруппировал поведение закрытия в properties-объект; наш dismiss{} — та же группа, но лучше (value-equality + билдер-дефолты). Из тройки переносим ТОЛЬКО back-press (остальные два — оконные, см. rejected). Правки config/DismissConfig.kt + config/DismissConfigBuilder.kt:

```kotlin
@Immutable
internal data class DismissConfig(
    val onOutsideTap: Boolean,
    val onSwipeDown: Boolean,
    val onBackPress: Boolean,
)

@XBottomSheetDsl
internal class DismissConfigBuilder {
    var onOutsideTap: Boolean = true
    var onSwipeDown: Boolean = true
    // Дефолт false: back остаётся у хоста — 19 кейсов 1:1. M3-дефолт true объясняется Dialog-окном,
    // которое и так перехватывает back; наш лист in-tree. При переносе в 1XUI Core архитектор может
    // осознанно флипнуть дефолт в true (UX-канон «back закрывает лист») — отдельным решением, не здесь.
    var onBackPress: Boolean = false
    internal fun build(): DismissConfig =
        DismissConfig(onOutsideTap = onOutsideTap, onSwipeDown = onSwipeDown, onBackPress = onBackPress)
}
```

Компонент (/Users/Victor/work/bs-zorder-lab/app/src/main/java/com/onexui/bottomsheet/XBottomSheet.kt, рядом с ObserveSheetState):

```kotlin
import androidx.activity.compose.BackHandler

BackHandler(enabled = state.isVisible && config.dismiss.onBackPress) { currentOnDismiss() }
```

Закрытие идёт через тот же единственный onDismissRequest → hide() → snapshotFlow{isVisible} в ObserveSheetState роняет IME — весь существующий пайплайн закрытия (включая принудительный дроп клавиатуры §6.5) переиспользуется без новой логики. Call-site типовой:

```kotlin
XBottomSheet(
    state = sheet,
    onDismissRequest = { scope.launch { sheet.hide() } },
    config = xBottomSheetConfig { dismiss { onBackPress = true } },
) { ... }
```

Зачем разработчику: сегодня каждый хост реального экрана обязан сам писать BackHandler + синхронизировать его enabled с isVisible (и легко забыть — back закрывает экран под открытым листом). Один флаг прячет этот вайринг; при открытой IME системный приоритет IME-back отрабатывает раньше колбэка сам — хосту думать не нужно.

### hides_from_component
Прячет в компонент хост-вайринг «BackHandler + enabled по isVisible + маршрут в единственную точку закрытия». Ничего из текущей логики компонента наружу не выносится.

### developer_cost
Минимальный: ничего (дефолт false = текущее поведение). Типовой: dismiss { onBackPress = true } — одна строка вместо самописного BackHandler-блока. Продвинутый: комбинация с onOutsideTap=false/onSwipeDown=false — «закрыть только кнопкой или системным back».

### break_risk
BackHandler(enabled = false) регистрирует ВЫКЛЮЧЕННЫЙ OnBackPressedCallback — диспетчер его пропускает, back-поведение всех 19 кейсов не меняется (строгое 1:1 при дефолте false). Проверить: (j) закрытия выключены — back по-прежнему уходит хосту/Activity; любой кейс с open-листом — back закрывает Activity как сейчас. Задокументировать порядок вложенных BackHandler'ов: включённый обработчик, зарегистрированный в композиции ПОЗЖЕ листа, перехватит раньше (стандартная семантика dispatcher'а). Зависимость androidx.activity:activity-compose уже в модуле (ComponentActivity + enableEdgeToEdge).

## [REJECT] P4. confirmValueChange из M3 SheetState — НЕ переносить (ни лямбдой, ни интерфейсом)

### design
Что в M3: rememberModalBottomSheetState(skipPartiallyExpanded, confirmValueChange: (SheetValue) -> Boolean = { true }) — гейткипер, опрашиваемый на КАЖДОМ переходе (hide/expand/partialExpand/свайп/тап по скриму); вернул false — переход не стартует, лист спружинивает к якорю. Рассмотренная замена под наш запрет лямбд в конфигах (НЕ принята):

```kotlin
fun interface SheetTransitionPolicy {
    fun isTransitionAllowed(from: SheetValue, to: SheetValue): Boolean
}
// в стейт-билдер: var transitionPolicy: SheetTransitionPolicy = SheetTransitionPolicy { _, _ -> true }
```

Почему reject, по пунктам «зачем это разработчику» — незачем: (1) для закрытия наш контракт УЖЕ сильнее M3-веты: settle при цели Hidden и тап по скриму НЕ закрывают лист сами — зовут onDismissRequest, финальное слово у хоста (не вызвал hide() — лист не закрыт). M3 «спрашивает разрешение», мы «отдаём владение» — второй механизм веты поверх первого = два источника правды о закрытии. (2) Для программных show/expand/hide veto бессмыслен: их вызывает сам хост — он может просто не вызвать. (3) Для запрета промежуточных остановок (например «нельзя стоять в Collapsed») — ноль кейсов из 19 и ноль требований спеки; вводить сущность впрок = забивать гвозди. Осознанная и фиксируемая в доке щель против M3: при вето хоста (onDismissRequest без hide()) лист остаётся на высоте отпускания, а не спружинивает к якорю. Если реальный кейс появится — закрывать БЕЗ публичного API: в settle после onDismissRequest() наблюдать «isVisible остался true» и анимировать к ближайшему rest-якорю (одно internal-изменение, при P1 — anchorTable.settleTarget(offset.value, 0f, isDismissAllowed = false)).

### hides_from_component
Ничего не прячет и не добавляет — фиксирует, что механизм веты уже существует в форме владения (onDismissRequest — единственная точка закрытия) и дублировать его конфиг-политикой нельзя.

### developer_cost
n/a — API не меняется. Паттерн для разработчика в доке: «веток закрытия» достигается не-вызовом hide() внутри onDismissRequest.

### break_risk
Нулевой: предложение — НЕ делать изменение. Риск возник бы при переносе: лямбда в стейте нарушает правило проекта, интерфейс-политика создаёт второй источник правды о закрытии и конфликт с dismiss-флагами (какой из них главнее при onSwipeDown=true + policy=false — неразрешимо без документации-костыля).

## [OPTIONAL] P5. isAnimating — аддитивный факт бегущей анимации по канону SheetState (currentValue/targetValue/isAnimationRunning)

### design
Что в M3: SheetState различает currentValue (устаканенное), targetValue (цель анимации/свайпа) и isAnimationRunning. У нас currentValue имеет target-семантику (пишется в НАЧАЛЕ animateTo — это зафиксировано спекой и не меняется), но факт «едем/приехали» снаружи недоступен вовсе: offset internal. Аддитивный геттер в /Users/Victor/work/bs-zorder-lab/app/src/main/java/com/onexui/bottomsheet/state/XBottomSheetState.kt:

```kotlin
@Stable
internal class XBottomSheetState internal constructor(config: XBottomSheetStateConfig) {
    // M3-канон различает currentValue/targetValue/isAnimationRunning. Наш currentValue — target-семантика,
    // дополняем фактом бегущей анимации высоты. Animatable.isRunning — snapshot-state: наблюдаем из
    // composition/snapshotFlow без опроса.
    val isAnimating: Boolean get() = offset.isRunning
}
```

Call-site типовой:

```kotlin
val sheet = rememberXBottomSheetState()
// задизейблить действие, пока лист доезжает до якоря:
PresetSingleButton("Показать все", isEnabled = !sheet.isAnimating, onClick = ::showAll)
// аналитика «лист довёл открытие»:
LaunchedEffect(sheet) {
    snapshotFlow { sheet.currentValue to sheet.isAnimating }
        .collect { (value, isAnimating) -> if (value == SheetValue.Collapsed && !isAnimating) trackSheetOpened() }
}
```

Зачем разработчику: без этого геттера факт завершения анимации открытия/разворота извне не получить никак (offset internal) — пришлось бы городить delay-хаки. Взят именно минимум канона: targetValue НЕ добавляем (у нас его роль уже играет currentValue), переименования нет.

### hides_from_component
Прячет от разработчика внутренний offset: Animatable остаётся internal, наружу выходит только булев факт. Без него единственная «дырка» наружу была бы хуже — просьбы открыть offset целиком.

### developer_cost
Минимальный: ничего (геттер не обязывает). Типовой: одно чтение sheet.isAnimating в условии. Продвинутый: snapshotFlow-связка для аналитики/секвенирования действий после анимации.

### break_risk
Чисто аддитивный read-only getter, ни один переход/анимация не меняется — 19 кейсов не затронуты по построению. Явный запрет в рамках предложения: НЕ переименовывать currentValue → targetValue ради парности с M3 (сломало бы контракт §4, доки и все call-sites). Верификация: сборка; поведенчески проверять нечего.

### ОТВЕРГНУТО линзой
- securePolicy и isAppearanceLightStatusBars/NavigationBars из ModalBottomSheetProperties — это свойства ОКНА Dialog'а; XBottomSheet живёт in-tree, собственного окна нет: FLAG_SECURE и окраска систем-баров — зона Activity-хоста. Перенос дал бы мёртвые конфиг-поля (и цвета в API запрещены спекой §9).
- shouldDismissOnClickOutside как отдельный перенос — не нужен: уже существует как dismiss { onOutsideTap } (эквивалент, реализован раундом P4-DSL); отмечено как «взято ранее», не как новая работа.
- Scaffold contentPadding-паттерн (PaddingValues в слоты) — философия XBS обратная: инсеты вшиты внутрь компонента (фон под nav bar, контент паддится внутри Surface, bottom всегда над баром, замер идёт с паддингом). Отдать PaddingValues контенту = утечка инсет-логики наружу и легальный способ сломать §9-отступы. Текущая форма — фича, не пробел.
- LazyListScope-подобный scope-receiver для middle/слотов — middle сам предоставляет скролл (LazyColumn со СВОИМ scope, надеть второй receiver поверх нельзя), а эксклюзивных данных для scope нет: всё нужное контенту (currentValue, isLoading, additionalTopState) уже public в state. Scope без данных = пустая церемония — не забивать гвозди.
- Вынос positionalThreshold / velocityThreshold / snapAnimationSpec в конфиг (канон конструктора AnchoredDraggableState) — спека §9: анимация — имитация нативной (NativeSheetSpring вшит), пороги — вшитые константы (FLING_VELOCITY_THRESHOLD=400f и др.). Конфигурируемость здесь = приглашение каждой фиче разъехаться с нативным ощущением листа.
- dragHandle как composable-слот по образцу M3 (dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() }) — спека фиксирует хендл 36×4dp/r360/top 8dp/read-only; enum DragHandleStyle (Theme/Static/null) охраняет инвариант на уровне типа, слот позволил бы его нарушить. M3-урок тут отрицательный: слот породил зоопарк кастомных хендлов, дизайн-системе это вредно.
- XBottomSheetScaffold / persistent-вариант листа сейчас — преждевременно (нет требований). Урок M3 (ModalBottomSheet и BottomSheetScaffold дублируют settle/anchor-логику, потому что ядро не извлечено) закрывается предложением P1: SheetAnchorTable + SheetMetrics образуют переиспользуемое ядро ДО появления второго варианта.
- Перевод жестов/конфига на Modifier.Node (element + node.update с пофилдовым diff'ом без рекомпозиции) — цель паттерна уже достигнута другим способом: measure-фазные State-чтения (StayUnderKeyboardMeasurePolicy(keyboardState), Animatable-фракция в measure, derivedStateOf для interactionsEnabled — раунды P1/P2/A/B). Переписывание pointerInput на Node — микро-перф без API-выгоды и с риском жестовых регрессий.
- requireOffset()-стиль доступа (бросок из геттера до первого замера, как в M3 SheetState) — прямое противоречие правилу «без require»; наш suspend awaitMetrics()/awaitContentMetrics уже решает bootstrap корректнее: вызвавший show() просто дождётся метрик.
- Переименование currentValue → targetValue ради парности с M3 — сломало бы контракт §4, доки и все call-sites ради номенклатуры; семантика «состояние ВЫЧИСЛЯЕТСЯ» зафиксирована спекой. Вместо этого — аддитивный isAnimating (P5).


# Линза 1 — SCOPE-КОНЦЕПЦИЯ XBottomSheet (bs-zorder-lab, read-only исследование). Изучено: XBottomSheet.kt, layout/SheetContainer.kt+SheetBody.kt, state/XBottomSheetState.kt (+Config/Builder/Saver/Anchors), config/* (DSL P4-раунда), observe/ObserveSheetState.kt, demo/XbsDemoScreen.kt (19 кейсов), доки XBOTTOMSHEET.md / XBS-SPEC.md (ПЕРЕСМОТРЕНО-пометки) / XBS-QUALITY-RESEARCH.md.

ГЛАВНЫЙ ВЫВОД: нужен ОДИН общий receiver-скоуп `XBottomSheetScope` для всех четырёх слотов (additionalTop/top/bottom/middle), а не четыре отдельных: способности слотов совпадают, а кейс (g) кросс-слотный — кнопка в bottom управляет additionalTop-карточкой. Скоуп — небольшой интерфейс (5-6 членов), не god-object: критерий роста — новый член добавляется только если он выражает инвариант, которым ВЛАДЕЕТ компонент (закрытие, IME-рецепт, additionalTop, вычисленные факты стейт-машины); всё остальное (данные экрана, команды show/expand, геометрия) остаётся у хоста/внутри.

ПРЕЦЕДЕНТЫ: ColumnScope/RowScope существуют ради per-child layout-параметров (weight/align) — у слотов XBS их нет, жесты/nested-scroll компонент вешает на контейнер и они работают с любым скроллером (доказано кейсами m..q) → scoped-модификаторы типа Modifier.sheetScrollable() не нужны. LazyListScope — DSL-описание контента — противоречит контракту «middle сам предоставляет скролл» → отклонено. BoxWithConstraintsScope — прецедент «скоуп отдаёт данные измерения», но отдаём не сырые px (геометрия вшита, дух §9), а производные ФАКТЫ: sheetValue / isFillMode. M3 SheetState — канон «suspend-команды у хоста через его coroutineScope» сохраняем; M3 ModalBottomSheet отдаёт контенту ColumnScope — сознательно НЕ повторяем (weight() против внутренней Column сломал бы detect-замер wrap/fill).

ФОРМА: interface (не abstract class — нечего шарить, Compose-прецеденты интерфейсы; не sealed — лёгкий fake для @Preview extension-контента), @Stable, единственная internal-реализация. Стабильность: инстанс создаётся `remember(state) { XBottomSheetScopeImpl(state) }` — identity-ключ, домашний идиом (как SheetNestedScrollConnection и Animatable фракции); меняющиеся значения читаются ЧЕРЕЗ state внутри get()-свойств, пересоздания по value-ключам нет (rule-3 соблюдён). Композиционные контроллеры (keyboardController/focusManager) проводятся в impl через уже существующий SideEffect — тот же паттерн, что state.onDismissRequest.

СОВМЕСТИМОСТЬ: смена типа слотов `@Composable () -> Unit` → `@Composable XBottomSheetScope.() -> Unit` source-прозрачна для лямбд-литералов — все 19 кейсов компилируются БЕЗ правок (receiver неиспользуем), поведение 1:1 гарантируется тем, что геометрия/жесты/анимации не трогаются вовсе. Конфиг-DSL (б): текущая структура (dismiss{}/keyboard{}/anchors{}) признана правильной; добавка — только перегрузка anchor() под готовый XSheetAnchor-хендл (optional); motion{} отклонён по §9.

## [STRONGLY-RECOMMEND] P1. XBottomSheetScope — единый receiver-скоуп четырёх слотов: requestDismiss() + additionalTopState

### design
Новый файл app/src/main/java/com/onexui/bottomsheet/XBottomSheetScope.kt:

```kotlin
package com.onexui.bottomsheet

import androidx.compose.runtime.Stable
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.SoftwareKeyboardController
import com.onexui.bottomsheet.additionaltop.AdditionalTopState
import com.onexui.bottomsheet.state.XBottomSheetState

// Возможности контента внутри листа. Не sealed: fake-реализация допустима для @Preview extension-контента.
@Stable
interface XBottomSheetScope {
    // Состояние sticky-слоя Additional Top; переключается только внешними факторами (кнопка/логика экрана).
    var additionalTopState: AdditionalTopState

    // Единственная точка закрытия ИЗ контента: маршрутизируется в onDismissRequest хоста
    // (тот же путь, что тап по скриму и свайп-settle в Hidden).
    fun requestDismiss()
}

@Stable
internal class XBottomSheetScopeImpl(
    private val state: XBottomSheetState,
) : XBottomSheetScope {
    internal var keyboardController: SoftwareKeyboardController? = null
    internal var focusManager: FocusManager? = null

    override var additionalTopState: AdditionalTopState
        get() = state.additionalTopState
        set(value) {
            state.additionalTopState = value
        }

    override fun requestDismiss() {
        state.onDismissRequest()
    }
}
```

Сигнатура компонента (слоты становятся receiver-лямбдами, остальное без изменений):

```kotlin
@Composable
fun XBottomSheet(
    state: XBottomSheetState,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    config: XBottomSheetConfig = XBottomSheetConfig.Default,
    additionalTop: (@Composable XBottomSheetScope.() -> Unit)? = null,
    top: (@Composable XBottomSheetScope.() -> Unit)? = null,
    bottom: (@Composable XBottomSheetScope.() -> Unit)? = null,
    middle: @Composable XBottomSheetScope.() -> Unit,
)
```

Вайринг в корне XBottomSheet.kt (расширение СУЩЕСТВУЮЩЕГО SideEffect, строки 105-108):

```kotlin
val sheetScope = remember(state) { XBottomSheetScopeImpl(state) }
SideEffect {
    state.dismissOnSwipeDown = config.dismiss.onSwipeDown
    state.onDismissRequest = { currentOnDismiss() }
    sheetScope.keyboardController = keyboardController
    sheetScope.focusManager = focusManager
}
```

SheetContainer/SheetBody получают один stable-параметр `sheetScope: XBottomSheetScope` и инвочат слоты с receiver'ом: `top?.let { slot -> slot(sheetScope) }`; в middleContent-обёртке SheetBody: `if (state.isLoading) PresetLoader() else middle(sheetScope)`; обёртка additionalTopBody в SheetContainer: `card(sheetScope)`. Слот-лямбды detect/place НЕ меняются — та же композиция, тот же инстанс скоупа в обоих сабкомпоуз-слотах (detect-копия не placed → не hit-testable, requestDismiss оттуда недостижим).

Кейс (j) ЧИЩЕ (bottom-кнопка закрытия без протаскивания замыкания; заодно исчезает локаль `val dismiss` — а с ней известная гоча P4-раунда «коллизия имён dismiss vs DSL-метод dismiss{}»):

```kotlin
@Composable
private fun CaseNoDismiss(onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val state = rememberXBottomSheetState()
    LaunchedEffect(Unit) { state.show() }
    XBottomSheet(
        state = state,
        onDismissRequest = { scope.launch { state.hide(); onClose() } },
        config = xBottomSheetConfig { dismiss { onOutsideTap = false; onSwipeDown = false } },
        top = { PresetTitle("Закрытия выключены") },
        bottom = { PresetSingleButton(text = "Закрыть", onClick = { requestDismiss() }) },
    ) {
        PresetBodyText("Тап вне листа и свайп вниз не закрывают — только кнопка.")
    }
}
```

Кейс (k) ЧИЩЕ (строки списка закрывают лист через скоуп, host-замыкание в контент не течёт; itemCount — легитимный host-стейт, остаётся):

```kotlin
XBottomSheet(
    state = state,
    onDismissRequest = { scope.launch { state.hide(); onClose() } },
    config = xBottomSheetConfig { dragHandle = DragHandleStyle.Static },
    top = { PresetTitle("Static handle + рост контента") },
) {
    LazyColumn(modifier = Modifier.fillMaxWidth(), state = rememberLazyListState()) {
        item {
            PresetSingleButton(
                text = "Добавить 10 элементов (сейчас $itemCount)",
                onClick = { itemCount += 10 },
            )
        }
        items(itemCount) { index ->
            PresetMenuCell(
                title = "Элемент ${index + 1}",
                onClick = { requestDismiss() },
                leadingColor = MarkerColors[index % MarkerColors.size],
            )
        }
    }
}
```
(Внутри items{} внешний receiver доступен: у LazyListScope свой @DslMarker — LazyScopeMarker, он не блокирует XBottomSheetScope.)

Кейс (g) ЧИЩЕ (вычисление `collapsed` уезжает из композиции хоста внутрь слотов — рекомпозиция сужается до слота):

```kotlin
additionalTop = { AdditionalTopCard(isCollapsed = additionalTopState == AdditionalTopState.Collapsed) },
bottom = {
    val isCollapsed = additionalTopState == AdditionalTopState.Collapsed
    PresetSingleButton(
        text = if (isCollapsed) "Развернуть Additional Top" else "Свернуть Additional Top",
        onClick = {
            additionalTopState = if (isCollapsed) AdditionalTopState.Expanded else AdditionalTopState.Collapsed
        },
    )
},
```

Общий контент demo (SportLazyList в 8+ кейсах) становится extension-композаблом без параметра-замыкания:

```kotlin
@Composable
private fun XBottomSheetScope.SportLazyList(sports: List<String>) {
    LazyColumn(modifier = Modifier.fillMaxWidth(), state = rememberLazyListState()) {
        itemsIndexed(sports) { index, sport ->
            PresetMenuCell(
                title = sport,
                onClick = { requestDismiss() },
                leadingColor = MarkerColors[index % MarkerColors.size],
            )
        }
    }
}
// call-site: XBottomSheet(...) { SportLazyList(SPORTS) }
```

### hides_from_component
Прячется ФОРМА доступа к внутренностям, которую visibility сама по себе не закрывает: (1) контент больше не получает `state` и host-замыкания — из слота НЕЛЬЗЯ дотянуться до offset (Animatable), metrics (SheetMetrics), gesture-канала (enqueueDrag/enqueueSettle/processGestures), updateMetrics, onImeShown/onImeHidden, snapToCurrentAnchor — их просто нет на скоупе; (2) инвариант спеки «единственная точка закрытия» из соглашения превращается в форму API: requestDismiss() ВСЕГДА идёт через onDismissRequest хоста (state.onDismissRequest, уже проведённый SideEffect'ом) — исчезает класс багов «контент вызвал state.hide() напрямую, минуя onClose()/cleanup хоста» (в demo это замыкание dismiss утекало во все 19 кейсов); (3) внутренняя вёрстка (Column top/middle/bottom, weight, детект-слоты SubcomposeLayout) остаётся секретом — receiver сознательно НЕ ColumnScope; (4) рецепт переключения additionalTop инкапсулируется: слоту доступен только enum-факт, не фракция/Animatable/overlap. Куда прячется: интерфейс XBottomSheetScope (публичный контракт) + XBottomSheetScopeImpl (internal, делегат в state). При переносе в 1XUI Core публикуются только XBottomSheetScope, SheetValue, AdditionalTopState — impl и state-внутренности остаются internal модуля.

### developer_cost
Минимальный call-site — НУЛЕВОЙ дифф против сегодняшнего: `XBottomSheet(state = state, onDismissRequest = { scope.launch { state.hide() } }) { PresetBodyText("...") }` — скоуп невидим, пока не нужен. Типовой: `bottom = { PresetSingleButton(text = "Закрыть", onClick = { requestDismiss() }) }` — минус одно протаскиваемое замыкание на каждый лист. Продвинутый: библиотека переиспользуемого контента `@Composable fun XBottomSheetScope.ConfirmSheetContent(title: String)` — контент листа переносится между экранами без threading'а state/dismiss через параметры.

### break_risk
Риски: (1) смена типа слотов ломает call-site, если туда передавали val/method-reference типа `@Composable () -> Unit` — в demo все 19 кейсов передают лямбды-литералы (проверено чтением XbsDemoScreen.kt), для страховки перед миграцией grep по `top =|bottom =|additionalTop =|middle =` на не-литералы; (2) requestDismiss() до первого SideEffect — теоретически no-op (state.onDismissRequest дефолт `{}`), практически недостижимо: SideEffect применяется в конце первой композиции, раньше клика; (3) двойная композиция detect/place — оба слота получают ОДИН инстанс скоупа, поведение идентично текущему middle(); (4) проводка одного stable-параметра сквозь SheetContainer/SheetBody — skip-поведение не меняется (инстанс стабилен per state). Гарантия 1:1: геометрия/жесты/IME/анимации НЕ трогаются вообще — меняется только способ инвока слот-лямбд (добавка receiver-аргумента) и два поля в существующем SideEffect; requestDismiss() вызывает ровно ту же лямбду, что тап по скриму (state.onDismissRequest -> currentOnDismiss). Верификация: assembleDebug БЕЗ правок demo (source-прозрачность) -> полный прогон матрицы 19 кейсов -> точечно (j) кнопка + отключённые тап/свайп, (k) +10 -> авто-FullScreen, (g) тоггл карточки, (a) свайп-закрытие, скрим-тап в (b1), ротация открытого (c)/(d).

## [RECOMMEND] P2. Read-слой скоупа: sheetValue и isFillMode (вычисленные факты вместо metrics)

### design
Расширение интерфейса (продолжение P1):

```kotlin
@Stable
interface XBottomSheetScope {
    // Текущий вычисленный стейт листа. Меняется на ДИСКРЕТНЫХ переходах стейт-машины
    // (не покадрово — offset скоуп не отдаёт принципиально).
    val sheetValue: SheetValue

    // Режим якорей по замеру контента: true — fill (Collapsed/Custom/FullScreen),
    // false — wrap (Content/ExpandedContent). До первого замера — false.
    val isFillMode: Boolean

    var additionalTopState: AdditionalTopState
    fun requestDismiss()
}
```

Реализация — прямые snapshot-чтения через state (без кэшей и derivedStateOf на скоупе: чтение происходит в месте вызова, инвалидируется только читающий RecomposeScope слота):

```kotlin
override val sheetValue: SheetValue get() = state.currentValue
override val isFillMode: Boolean get() = state.metrics?.isFillMode ?: false
```

Для этого SheetValue уже публичен (state/SheetValue.kt), metrics остаётся internal — наружу уходит только производный Boolean.

Call-site (прод-паттерн; в связке с P4 — реакция на кастомный якорь):

```kotlin
XBottomSheet(state = state, onDismissRequest = { scope.launch { state.hide() } }) {
    if (sheetValue == SheetValue.Collapsed) {
        SwipeUpHintRow()   // подсказка «потяни вверх» видна только в peek-состоянии
    }
    SportLazyList(SPORTS)
}
```

Для кейса (k) — прод-развитие (в demo НЕ вносить, кейс остаётся 1:1): кнопке подгрузки можно гасить смысл после auto-FullScreen — `if (sheetValue != SheetValue.ExpandedFullScreen) item { LoadMoreButton(...) }`.

### hides_from_component
Прячет SheetMetrics целиком: контенту нужны решения («какой стейт», «какой режим якорей»), а не пиксели (screenHeightPx/statusBarPx/contentHeightPx/peekPx/anchorPx) — сырые числа остаются internal, наружу два производных факта. Это BoxWithConstraintsScope-прецедент, но без нарушения духа §9 (геометрия вшита/вычисляется): по sheetValue/isFillMode невозможно построить параллельную геометрию листа. Также прячет nullable-природу metrics (до первого замера) за честным дефолтом false.

### developer_cost
Минимальный — не пользоваться (ничего не платишь: чтения нет — инвалидации нет). Типовой — одно условие в слоте: `if (sheetValue == SheetValue.Collapsed) Hint()`. Продвинутый — адаптивный контент: разные шапки для Collapsed/Expanded*, включение пагинации только при isFillMode, реакция на SheetValue.Custom(key) в связке с кастомными якорями.

### break_risk
Поведенческий риск нулевой — чисто читающее API, ни одна ветка компонента не меняется. Риск использования: разработчик читает sheetValue высоко в дереве слота и получает рекомпозицию слота на каждый переход стейт-машины — приемлемо (переходы дискретные: show/expand/settle, не кадры анимации; offset намеренно не экспонирован). Задокументировать в KDoc скоупа: «sheetValue меняется на переходах, не покадрово». Гарантия 1:1: demo-кейсы эти свойства не используют — прогон матрицы без изменений; смоук на отсутствие лишних рекомпозиций — печать в поиске кейса (i) не должна дёргать счётчик композиций листа (как в верификации P4-DSL).

## [RECOMMEND] P3. hideKeyboard() на скоупе — канонический дроп IME из контента + дедуп рецепта §6.5 внутри компонента

### design
Расширение интерфейса и impl (продолжение P1):

```kotlin
@Stable
interface XBottomSheetScope {
    // Принудительно скрыть клавиатуру, НЕ закрывая лист: clearFocus + hide (канон §6.5).
    // Авто-развёрнутый из-за IME лист откатится к прежнему стейту сам (imePromotedFrom).
    fun hideKeyboard()
}

// XBottomSheetScopeImpl — тот же рецепт, что onSheetHidden в корне (порядок сохранён):
override fun hideKeyboard() {
    keyboardController?.hide()
    focusManager?.clearFocus(force = true)
}
```

Дедуп внутри компонента — XBottomSheet.kt, вызов ObserveSheetState (строки 92-100) сводится к единой точке рецепта:

```kotlin
ObserveSheetState(
    state = state,
    keyboardState = keyboardState,
    alwaysFullScreenOnIme = config.keyboard.bottomBehavior == BottomKeyboardBehavior.StayUnderKeyboard && bottom != null,
    onSheetHidden = { sheetScope.hideKeyboard() },
)
```

Call-site (прод-паттерн пикера на базе кейсов i/l/s — выбор элемента скрывает IME, лист остаётся):

```kotlin
XBottomSheet(
    state = state,
    onDismissRequest = { scope.launch { state.hide() } },
    top = {
        PresetTitle("Вид спорта")
        PresetSearchField(query = query, onQueryChange = { value -> query = value })
    },
) {
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(filtered) { sport ->
            PresetMenuCell(
                title = sport,
                onClick = {
                    onSportPicked(sport)
                    hideKeyboard()   // IME упала -> onImeHidden откатит авто-FullScreen, жесты снова активны
                },
            )
        }
    }
}
```

### hides_from_component
Прячет ПРАВИЛЬНЫЙ порядок дропа IME (keyboardController.hide + focusManager.clearFocus(force)) — сегодня разработчик, желающий скрыть клавиатуру после выбора в списке, тянет LocalSoftwareKeyboardController/LocalFocusManager сам и легко забывает clearFocus (IME всплывает обратно на следующем тапе) — ровно та грабля, ради которой компонент завёл own-рецепт в onSheetHidden. Плюс прячет связку с IME-стейт-машиной листа: после hideKeyboard() откат авто-FullScreen (imePromotedFrom) и реактивация жестов происходят сами — контенту не нужно знать, что они существуют. Внутри компонента рецепт сводится к одной точке (scope.hideKeyboard == onSheetHidden) — двух расходящихся копий не появится.

### developer_cost
Минимальный — не вызывать (ничего не меняется). Типовой — одна строка в onClick элемента: `hideKeyboard()`. Продвинутый — комбинирование с P2: скрыть IME при уходе из поисковой секции (`if (sheetValue == SheetValue.Collapsed) hideKeyboard()` в LaunchedEffect слота).

### break_risk
Риски: (1) дедуп onSheetHidden -> sheetScope.hideKeyboard() обязан сохранить порядок вызовов 1:1 (hide, затем clearFocus(force=true)) — переносится дословно; (2) вызов hideKeyboard() из контента триггерит штатную цепочку KeyboardLiftState -> onImeHidden -> откат промоушена — это УЖЕ существующее поведение системной кнопки «назад»/IME-кнопки, новых веток нет; (3) незаполненные контроллеры до SideEffect — no-op (nullable), недостижимо до первого клика. Гарантия 1:1: demo hideKeyboard() не использует — прогон (i)/(l)/(s): фокус -> авто-FullScreen -> тап по скриму: клавиатура падает, лист закрывается прижатым к низу (путь onSheetHidden через новую точку); back-скрытие IME -> откат FullScreen.

## [OPTIONAL] P4. anchors{}-DSL: перегрузка anchor(XSheetAnchor) + возврат хендла — якорь-константа вместо magic string

### design
Правка state/XSheetAnchorsBuilder.kt (source-совместимая: возврат Unit -> XSheetAnchor игнорируем; поведение build() не меняется):

```kotlin
@XBottomSheetDsl
class XSheetAnchorsBuilder internal constructor() {
    private val anchors = mutableListOf<XSheetAnchor>()

    fun anchor(key: String, heightFraction: Float): XSheetAnchor =
        XSheetAnchor(key = key, heightFraction = heightFraction)
            .also { created -> anchors.add(created) }

    fun anchor(anchor: XSheetAnchor) {
        anchors.add(anchor)
    }

    internal fun build(): List<XSheetAnchor> = anchors.toList()
}
```

Call-site (прод-паттерн: якорь объявляется констант­ой файла — XSheetAnchor уже публичный @Immutable data class — и переиспользуется в DSL и в сравнении стейта через P2):

```kotlin
private val HalfAnchor = XSheetAnchor(key = "half", heightFraction = 0.5f)

val state = rememberXBottomSheetState { anchors { anchor(HalfAnchor) } }

XBottomSheet(state = state, onDismissRequest = { scope.launch { state.hide() } }) {
    if (sheetValue == SheetValue.Custom(HalfAnchor.key)) {
        HalfStateHeader()   // контент реагирует на свой якорь без строкового литерала в двух местах
    }
    SportLazyList(SPORTS)
}
```

Кейс (r) demo остаётся дословно как есть: `rememberXBottomSheetState { anchors { anchor("half", 0.5f) } }` — компилируется и ведёт себя 1:1.

motion{}-скоуп НЕ добавлять (см. отвергнутые): анимация по §9 вшита (NativeSheetSpring), конфиг-ручки для неё противоречат спеке.

### hides_from_component
Прячет строковую связку «ключ в конфиге ↔ ключ в SheetValue.Custom»: сегодня разработчик обязан продублировать литерал "half" в anchors{} и в каждом сравнении currentValue — опечатка молча даёт фолбэк на peekFraction (customAnchorPx firstOrNull). Хендл-константа делает связку единственным объявлением. Внутренности не затрагиваются: список якорей так же уезжает в XBottomSheetStateConfig -> SheetMetrics.customAnchors.

### developer_cost
Минимальный — прежний `anchor("half", 0.5f)` (ноль изменений). Типовой — файловая константа + `anchor(HalfAnchor)`. Продвинутый — набор якорей фичи одним объектом: `object EventSheetAnchors { val Half = XSheetAnchor("half", 0.5f); val Preview = XSheetAnchor("preview", 0.33f) }` и переиспользование в контенте через sheetValue (P2).

### break_risk
Риск минимальный: смена возвращаемого типа anchor() Unit -> XSheetAnchor source-совместима (результат игнорируем), перегрузка не создаёт ambiguity (разные параметры). Дубль ключа через оба варианта anchor() ведёт себя как сегодняшний дубль (distinctBy по anchorPx в buildSettleCandidates, молча) — контракт не меняем (require запрещён юзером, L7-варнинг уже есть). Гарантия 1:1: кейс (r) без правок — свайп peek -> 50% -> full; компиляция demo.

### ОТВЕРГНУТО линзой
- motion{}-конфиг-скоуп (настройка spring/анимаций): §9 — анимация есть «имитация нативной», NativeSheetSpring вшит как токен; ручка анимации приглашает фичи разъезжаться с дизайн-системой. Разработчику это не нужно — значит не строим.
- Scoped-модификаторы а-ля ColumnScope (Modifier.sheetScrollable(), Modifier.sheetDraggable()): у слотов XBS нет per-child layout-параметров — жесты и nested-scroll компонент вешает на КОНТЕЙНЕР и они работают с любым скроллером контента (доказано кейсами m/n/o/p/q). Scoped-модификатор создал бы второй путь вайринга и ложно намекал бы на его обязательность.
- LazyListScope-стиль DSL для middle (items{}/cell{} вместо composable-контента): сузил бы контракт «middle сам предоставляет скролл» до одной реализации списка — сломает проверенную матрицу контентов (LazyVerticalGrid/LazyRow/Column+verticalScroll/вложенные Lazy).
- Четыре отдельных scope-типа (TopScope/BottomScope/MiddleScope/AdditionalTopScope): наборы способностей совпадают, а кейс (g) кросс-слотный — кнопка в bottom управляет additionalTop-карточкой; четыре интерфейса = церемония без дополнительного прятания.
- ColumnScope как receiver middle (по образцу M3 ModalBottomSheet): открыл бы weight()/align() против ВНУТРЕННЕЙ Column листа — сломал бы detect-замер wrap/fill и sticky-механику bottom; внутренняя вёрстка — секрет компонента.
- Экспонировать в скоупе offset/metrics/anchorPx/пиксельные якоря: геометрия по §9 вшита/вычисляется; сырые px спровоцируют параллельную самодельную геометрию в фичах. Наружу — только производные факты sheetValue/isFillMode (P2).
- suspend-команды show()/expand()/hide() в скоупе: канон M3 SheetState — команды высоты живут у хоста через его coroutineScope; в скоупе они дали бы контенту управлять листом, внутри которого он лежит (петля ответственности). Закрытие из контента — только requestDismiss() через хостовый onDismissRequest.
- Сахар dismiss { none() } / toggleAdditionalTop(): два boolean-флага и enum-присваивание читаются сами; сахар ради краткости — «забивание гвоздя», не прятание логики.
- additionalTop{}-группа в конфиг-DSL ради одного additionalTopCornerRadius: группировка одного поля = церемония (решение линзы 5 подтверждено); вернуться, когда появится второй параметр слоя.
- val isKeyboardVisible в скоупе: IME-политика — зона компонента (config.keyboard + авто-FullScreen/откат/гейтинг жестов); факт видимости IME в контенте провоцирует самодельную логику подъёма поверх штатной. Для канонических нужд достаточно hideKeyboard() (P3).
- abstract class вместо interface для скоупа: нечего шарить/защищать (impl — чистый делегат в state), Compose-прецеденты (ColumnScope/LazyListScope/BoxWithConstraintsScope) — интерфейсы; interface дешевле для ABI и оставляет свободу реализации + fake для @Preview.
- remember(config, ...) / value-ключи для создания скоупа: запрещённый rule-3 паттерн — скоуп создаётся ОДИН раз на инстанс стейта (remember(state), домашний идиом SheetNestedScrollConnection/Animatable-фракции), меняющиеся значения читаются через state внутри get()-свойств.
