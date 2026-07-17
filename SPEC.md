# SPEC — bs-zorder-lab (живой источник правды)

Проверка z-order: nav3 in-tree bottom sheet (порт AlvaBottomSheet) ⨯ BottomSheetDialogFragment/DialogFragment.
Полное ТЗ: `/Users/Victor/.claude/commands/alva-bs-zorder-test.md`.

## Фазы (роутинг субагентов, §5 ТЗ)

- [x] **Фаза 1 — Скаффолд (architect). ГОТОВО.** Собираемый Android-каркас: gradle, манифест, MainActivity+FragmentContainerView,
      HostFragment, Stub : BottomSheetDialogFragment, nav3-компайл-проба. `:app:assembleDebug` — ЗЕЛЁНЫЙ (APK собран).
      Артефакт-стратегия выбрана и проверена компиляцией (см. RESULTS.md).
- [x] **Фаза 2 — Порт nav-логики (kotlin). ГОТОВО.** Транзитивный перенос AlvaBottomSheet + scene/strategy/state-holder'ов
      из Alva, адаптация KMP→Android (§2 ТЗ). `:app:compileDebugKotlin` + `:app:assembleDebug` — ЗЕЛЁНЫЕ.
      Шаги: (1) прочитано транзитивное замыкание импортов от корней; (2) заглушка core:device (SystemBarsInsets/WindowSizeClass
      вербатим + DeviceStateStub через LocalWindowInfo/WindowInsets) + uikit utils (noRippleEffectClick, rememberKeyboardLiftState);
      (3) порт uikit bottom_sheet (14 файлов); (4) порт nav scene/bottom_sheet (5) + MultiBackStackNavigator + AlvaNav/NavRoute/
      AlvaTabCommand/enumNameSaver + urезанный AlvaSceneStrategies (только bottom sheet); (5) dep viewmodel-navigation3 в каталог;
      (6) compileDebugKotlin + assembleDebug зелёные. Детали заглушки/отклонений — в RESULTS.md §"Порт (фаза 2)".
- [x] **Фаза 3 — UI и флоу (ui). ГОТОВО.** NavDisplay + SceneStrategy проводка, `openAlvaBottomSheet()`, HostFragment 3 кнопки,
      Sample(BottomSheet)DialogFragment, таймеры Handler + cross-open флоу (§3 ТЗ). `:app:assembleDebug` — ЗЕЛЁНЫЙ.
      Единственный warning — вербатим-копия из Alva (DSL-marker на `alvaBottomSheetSceneConfig`), от новых файлов warning'ов нет.
- [ ] **Фаза 4 — Ревью + прогон (review + team lead).** Ревью, запуск, 3 скриншота наложений, RESULTS.md прогноз vs факт.

## Фаза 3 — что сделано (UI и флоу)

Все файлы под `app/src/main/java/com/zorderlab/`. `:app:assembleDebug` — ЗЕЛЁНЫЙ.

1. [x] `LabRoutes.kt` — `BaseRoute` / `Nav3SheetRoute` (`data object : NavRoute`). `contentKey = key.toString()` (String, saveable).
2. [x] `LabScreen.kt` — `LabTheme` (MaterialTheme light/dark), `LabRoot(backStack)`: `NavDisplay(backStack, onBack, sceneStrategies, entryProvider)`
       — backStack-оверлоад JetBrains nav3 1.1.1 (дефолтный decorator = `rememberSaveableStateHolderNavEntryDecorator`, без сериализации;
       база рендерится дефолтным `SinglePaneSceneStrategy`, лист — нашим `AlvaBottomSheetSceneStrategy`). `BaseScreenContent` прозрачен
       (тачи проваливаются сквозь ComposeView на HostFragment). `Nav3SheetContent` — фиолетовый фон + «NAV3 IN-TREE SHEET» + shown/hidden лог.
3. [x] `MainActivity.kt` — `: AppCompatActivity(), ZOrderHost`. `backStack = mutableStateListOf<NavKey>(BaseRoute)` в поле Activity.
       `openAlvaBottomSheet()` = guard + `backStack.add(Nav3SheetRoute)`. `openSheetThenRaiseDialog()` = открыть лист + `Handler.postDelayed(5000)`
       → `findFragmentById` каст к `SheetOverlayDialogRaiser` → `raiseDialogOverSheet()`. Таймер снимается в `onDestroy`.
4. [x] `ZOrderHost.kt` — 2 интерфейса: `ZOrderHost` (fragment→activity), `SheetOverlayDialogRaiser` (activity→fragment, кнопка 3).
5. [x] `HostFragment.kt` — 3 кнопки (программный LinearLayout). Таймеры кнопок 1/2 в фрагменте (`flowHandler`, снимаются в `onDestroyView`).
       Кнопка 3 — только `openSheetThenRaiseDialog()`, таймер на стороне Activity. Реализует `SheetOverlayDialogRaiser`.
6. [x] `SampleBottomSheetDialogFragment.kt : BottomSheetDialogFragment` — «BSD-FRAGMENT (View)», лог shown/hidden.
7. [x] `SampleDialogFragment.kt : DialogFragment` — «DIALOG-FRAGMENT (View)», лог shown/hidden.

### Кнопка 4 — fragment-hosted nav3-лист (дополнение)
- `HostFragment` хостит СВОЙ `ComposeView` (fragment-scoped `sheetBackStack = mutableStateListOf<NavKey>(BaseRoute)`),
  без обращения к Activity. Root фрагмента переведён на `FrameLayout` (кнопки + on-demand ComposeView поверх).
- Поток кнопки 4: показать `SampleBottomSheetDialogFragment` → `Handler.postDelayed(5000)` → `openFragmentHostedSheet()`
  (ComposeView добавляется в иерархию, `sheetBackStack.add(Nav3SheetRoute)`). Таймер снимается в `onDestroyView`.
- **pass-through решён так:** ComposeView добавляется ТОЛЬКО на время флоу; при закрытии листа `Nav3SheetContent.onGone`
  (из `onDispose`) через `view.post{}` снимает ComposeView с иерархии → пока лист закрыт, поверх кнопок нет ComposeView.
- `LabScreen.kt`: выделен общий `ZOrderNavDisplay(backStack, sheetContent)`; `LabRoot` (activity, фиолетовый
  `NAV3 IN-TREE SHEET`) и `FragmentHostedSheetRoot` (fragment, синий `NAV3 SHEET (fragment-hosted)`) — оба через него.
  Логи: `NAV3 IN-TREE SHEET shown/hidden` (activity) и `NAV3 fragment-hosted sheet shown/hidden` (fragment).
- Кнопки 1–3 не менялись.

### Кнопка 5 — nav3-лист в собственном окне (hostInWindow, прототип для Alva)
Правка ПОРТА (в лабе разрешено): `AlvaBottomSheetSceneConfig` +поле `hostInWindow: Boolean`; builder +`var hostInWindow=false`;
`AlvaBottomSheetScene.content` — ветка `when(hostInWindow){ true -> WindowHostedSheetChrome(); false -> SheetChrome() }`.
`WindowHostedSheetChrome` оборачивает `SheetChrome()` в compose `Dialog` (fullscreen, `usePlatformDefaultWidth=false`,
`decorFitsSystemWindows=false`, `dismissOnClickOutside=false`) → лист получает своё top-level окно (слой BSD); `UndimDialogWindow`
гасит оконный dim/анимацию (скрим рисует сам лист). `decorFitsSystemWindows` в JetBrains compose 1.11.1 — стабилен, opt-in не нужен.
- `LabRoutes.kt` +`data object Nav3WindowSheetRoute`. `LabScreen.kt` — новый entry с `hostInWindow=true`, красный лист «NAV3 SHEET (own window)».
- `ZOrderHost.openWindowHostedSheet()` → `MainActivity` пушит `Nav3WindowSheetRoute`. `HostFragment` кнопка 5: BSD → +5с → `openWindowHostedSheet()`.

### Кнопка 8 — динамический цикл top-level окон (ComponentDialog)
`HostFragment.composeSheetDialog: ComponentDialog?` (голый `ComponentDialog`, `Theme_Translucent_NoTitleBar`, внутри ComposeView с
`ResearchSheetPanel` — зелёная панель 400dp). Флоу: t0 `dialog.show()` → +5с `BSD.show()` (BSD сверху) → +10с `dismiss(); show()`
(re-add того же инстанса → окно листа снова последнее → лист сверху). Re-show работает: `ComponentDialog.onStop` шлёт ON_DESTROY,
но `LifecycleRegistry` запрещает лишь INITIALIZED→DESTROYED, поэтому второй `show()` (onStart→ON_RESUME) поднимает registry
DESTROYED→RESUMED и ComposeView пере-компонуется. Cleanup: `onDestroyView` делает `composeSheetDialog?.dismiss(); =null`.
- `LabScreen.ResearchSheetPanel(background, panelHeight, label, logName)` — public, лог `COMPOSE dialog-sheet shown/hidden`.

Кнопки 1–4 не менялись. `:app:assembleDebug` — ЗЕЛЁНЫЙ (единственный warning — вербатим Alva DSL-marker).

### Фикс флоу 1–4: оба лаб-листа переведены на `hostInWindow = true`
Правка юзера: во флоу 1/2/4 второй оверлей (nav3-лист, открытый через 5с) уходил ПОД диалог. Фикс — единственный
разделяемый `entry<Nav3SheetRoute>` в `ZOrderNavDisplay` получил `hostInWindow = true` в metadata → и activity-, и
fragment-hosted лист теперь рендерятся в собственном compose Dialog-окне (слой BSD) и, будучи добавленными позже,
ложатся ПОВЕРХ первого оверлея. Метки/логи: activity — «NAV3 SHEET (windowed)» / `NAV3 windowed sheet`; fragment —
«NAV3 SHEET (fragment-hosted, windowed)» / `NAV3 fragment-hosted windowed sheet`. Кнопки/флоу/тексты 1–4 и кнопки 5/8 —
без изменений (менялся ТОЛЬКО хостинг листов + метки). Флоу 3 семантику сохраняет (BSD-окно добавлено позже листа-окна → BSD сверху).

Фаза 1-артефакты не тронуты (`StubBottomSheet.kt`, `nav_probe/ScaffoldNavProbe.kt` — оставлены как есть, компилируются).
Запуск (фаза 4): `adb shell am start -n com.zorderlab/.MainActivity`, мониторинг `adb logcat -s ZORDER`.

## Текущий шаг (фаза 2 закрыта)

1. [x] Прочитано транзитивное замыкание импортов от корней (nav scene/bottom_sheet + uikit bottom_sheet + core:device + uikit utils).
2. [x] Заглушка core:device: `SystemBarsInsets`/`WindowSizeClass` вербатим, `DeviceStateStub` через `LocalWindowInfo` + `WindowInsets`.
3. [x] Порт uikit `components/bottom_sheet` (14 файлов, пакеты `com.alva.*` сохранены).
4. [x] Порт nav `scene/bottom_sheet` (5) + `AlvaSceneStrategies` (урезан до bottom sheet) + `MultiBackStackNavigator` + контракты
       (`AlvaNav`, `NavRoute`, `AlvaTabCommand`, `enumNameSaver`).
5. [x] Dep `lifecycle-viewmodel-navigation3:2.10.0` в каталог + `app/build.gradle.kts`.
6. [x] `:app:compileDebugKotlin` + `:app:assembleDebug` — ЗЕЛЁНЫЕ. Единственный warning — вербатим-копия из Alva (DSL-marker на
       `alvaBottomSheetConfig`). Отчёт возвращён team lead.

Фаза 2 закрыта. Дальше — фаза 3 (UI и флоу: NavDisplay-проводка + openAlvaBottomSheet + Fragment-диалоги, alva-android-ui-expert).
Публичный API для фазы 3 — в RESULTS.md §"Порт (фаза 2)" → "API для фазы 3".

## Жёсткие правила (напоминание)
Alva не трогать (только читать/копировать) · тесты не писать · KDoc к файлам не генерировать ·
комментарии по-русски · ast-index для поиска · ровно каркас, без бонус-фич.
