# RESULTS — bs-zorder-lab

Полное ТЗ: `/Users/Victor/.claude/commands/alva-bs-zorder-test.md`.
Прогноз (§0 ТЗ) vs Факт по трём флоу заполняет **фаза 4** (после прогона на устройстве).

> **АКТУАЛЬНОЕ СОСТОЯНИЕ (позже всего ниженаписанного).** Порт `com.alva.*` (AlvaBottomSheet +
> nav3-сцены `AlvaBottomSheetScene`/`SceneStrategy`, device-стабы, копия `KeyboardLiftState`) из проекта
> УДАЛЁН вместе с nav3-проводкой листов (`NavDisplay`/backStack/`Nav3SheetRoute`). Z-order-лаба
> (`com.zorderlab.*`, флоу 1–8) переведена напрямую на **XBottomSheet**: лист управляется
> `XBottomSheetState.show/hide`, как в demo. Оконный режим (эквивалент `hostInWindow` из разделов ниже)
> реализован НЕ в компоненте, а обёрткой уровня лабы `com.zorderlab.WindowedXBottomSheet` — full-screen
> прозрачный `Dialog` (`usePlatformDefaultWidth=false`, `decorFitsSystemWindows=false`) с гашением
> платформенного dim; окно диалога добавляется в WindowManager позже BSD/Dialog → лист ложится сверху
> (правило «одинаковый слой = порядок добавления»). Флоу 1/2/4 (второй оверлей сверху), флоу 3 (BSD
> сверху) и XBS-demo (a/i/u) перепроверены на устройстве после перевода — поведение сохранено.
> Разделы ниже — исторический лог ресёрча Alva-порта (фазы 0–2), оставлен для справки.

---

## Артефакт-стратегия (решение фазы 1)

**Выбрано: пул артефактов Alva 1:1 — JetBrains Compose Multiplatform + JetBrains navigation3 1.1.1,
в обычном `com.android.application` модуле (НЕ KMP).** Google-версии nav3 отклонены.

### Итоговые координаты (скопированы из `Alva/gradle/libs.versions.toml`)

| Слой | Артефакт | Версия |
|------|----------|--------|
| Compose runtime/foundation/ui/animation | `org.jetbrains.compose.*` | `1.11.1` |
| Compose Material3 | `org.jetbrains.compose.material3:material3` | `1.10.0-alpha05` |
| **Navigation 3** | `org.jetbrains.androidx.navigation3:navigation3-ui` | **`1.1.1`** |
| NavigationEvent | `org.jetbrains.androidx.navigationevent:navigationevent-compose` | `1.1.0` |
| Lifecycle (compose) | `org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose` | `2.10.0` |
| Activity bridge | `androidx.activity:activity-compose` | `1.13.0` |
| View-мир | `androidx.fragment:fragment-ktx` | `1.8.9` |
| BottomSheetDialogFragment | `com.google.android.material:material` | `1.14.0` |
| AppCompat host | `androidx.appcompat:appcompat` | `1.7.1` |

Тулчейн: AGP `com.android.application` **9.2.1**, Kotlin **2.4.0** (встроен в AGP 9),
compose-compiler `org.jetbrains.kotlin.plugin.compose` 2.4.0, Gradle wrapper **9.5.0**,
compileSdk/targetSdk **37**, minSdk **30**, JDK 21. Всё — как в Alva.

### Почему JetBrains nav3, а не Google `androidx.navigation3`

Порт `AlvaBottomSheet` написан под **точную форму API JetBrains navigation3 1.1.1**. Из реального
кода Alva (`AlvaBottomSheetScene.kt`, `AlvaBottomSheetSceneStrategy.kt`) порт использует:

- `androidx.navigation3.scene.OverlayScene` (override `key` / `previousEntries` / `overlaidEntries` / `entries` / `content`);
- `androidx.navigation3.scene.SceneStrategy` + `SceneStrategyScope<T>.calculateScene(...)` с членом `onBack`;
- `androidx.navigation3.runtime.NavMetadataKey` + типобезопасный билдер `metadata { put(Key, value) }`;
- `NavEntry.Content()` / `.contentKey` / `.metadata[Key]` (оператор `get`).

Пакеты `androidx.navigation3.*` совпадают у JetBrains и Google, но **сигнатуры и набор классов
эволюционируют между версиями** (у Google это `androidx.navigation3` в alpha-нумерации с иной формой
metadata-API и SceneStrategyScope). Взяв **идентичный артефакт**, что и Alva, мы полностью снимаем
риск рассинхрона сигнатур на фазе 2 (порт). Это прямая рекомендация ТЗ §1 («Артефакты — те же, что у Alva»).

### Эмпирическая проверка (сделана, не заявлена)

Стратегия проверена **компиляцией**, а не декларацией. Файл `app/.../nav_probe/ScaffoldNavProbe.kt` —
компайл-проба, зеркалящая ровно те вызовы nav3-API, что нужны порту (`ProbeOverlayScene : OverlayScene`,
`ProbeOverlaySceneStrategy : SceneStrategy` с `onBack`, `metadata { put(NavMetadataKey, ...) }`,
`entry.metadata[Key]`). Проба **успешно скомпилировалась** → JetBrains navigation3 1.1.1 предоставляет
нужный API surface. Фаза 2 может портировать без переписывания под другую форму nav3.

### Замены/отклонения от ТЗ

- **AGP 9 built-in Kotlin.** Первый билд упал: `org.jetbrains.kotlin.android` плагин запрещён при AGP ≥ 9
  (Kotlin встроен). Плагин удалён из root и `:app` — ровно как в `Alva/androidApp/build.gradle.kts`
  (там тоже только `android.application` + `compose.compiler`, без kotlin-плагина). Плагин
  `org.jetbrains.compose` **не понадобился**: JetBrains Compose-артефакты (android-вариант резолвится
  в `androidx.compose.*`) подтянулись в обычный `com.android.application` через один лишь compose-compiler.
- **`androidx.fragment:fragment-ktx:1.8.9`** — версии фрагмента нет в каталоге Alva (Alva чистый CMP,
  фрагменты не использует). Взята актуальная стабильная. Не относится к nav3/compose — риска для порта нет.
- Ничего в Alva не менялось/не удалялось — только чтение и копирование версий.

---

## Сборка (факт)

Команда: `cd /Users/Victor/work/bs-zorder-lab && ./gradlew :app:assembleDebug`

```
> Task :app:compileDebugKotlin
> Task :app:compileDebugJavaWithJavac NO-SOURCE
> Task :app:dexBuilderDebug
> Task :app:packageDebug
> Task :app:assembleDebug

BUILD SUCCESSFUL in 27s
36 actionable tasks: 36 executed
```

Артефакт: `app/build/outputs/apk/debug/app-debug.apk` (~38 MB).
Единственное предупреждение — безвредное: `Unable to strip ... libandroidx.graphics.path.so` (пакуется как есть).

Что доказано зелёной сборкой:
- JetBrains Compose + JetBrains nav3 компилируются в чистом Android-модуле;
- nav3-компайл-проба подтвердила API surface, нужный порту AlvaBottomSheet;
- View-мир (`FragmentContainerView`, `HostFragment`, `Stub : BottomSheetDialogFragment` из google material)
  компилируется в том же модуле → оба мира сосуществуют.

---

## Порт (фаза 2) — AlvaBottomSheet + nav-strategy

Транзитивный перенос из Alva (Alva не менялась — только чтение/копирование). Пакеты `com.alva.*` сохранены
(порт узнаваем). Все файлы под `app/src/main/java/com/alva/…`. `:app:compileDebugKotlin` и `:app:assembleDebug` — ЗЕЛЁНЫЕ.

### Портированные файлы (30 `.kt`)

**UI-компонент** `com.alva.core.uikit.components.bottom_sheet` (вербатим, кроме отмеченного):
`AlvaBottomSheet.kt` (единственная правка — см. заглушку device ниже), `AlvaBottomSheetValue.kt`, `DismissReason.kt`,
`AlvaBottomSheetScrim.kt`, `AlvaBottomSheetStateHolder.kt`, `AlvaBottomSheetStateRetention.kt`, `ConfirmBottomSheetDismiss.kt`,
`LocalAlvaBottomSheetStateHolder.kt`, `LocalAlvaBottomSheetOverlay.kt`, `config/AlvaBottomSheetConfig.kt`,
`config/AlvaBottomSheetConfigBuilder.kt`, `drag/AlvaDragHandle.kt` (без `@Preview`-функции → убрана зависимость на
`utils/preview`), `fling/AlvaBottomSheetFlingBehavior.kt`, `nested_scroll/BottomSheetNestedScrollConnection.kt`.

**Навигация** `com.alva.core.navigation`:
`scene/bottom_sheet/AlvaBottomSheetScene.kt`, `…SceneStrategy.kt`, `…SceneConfig.kt`, `…SceneConfigBuilder.kt`,
`…SceneRetentionStore.kt` (все вербатим); `scene/AlvaSceneStrategies.kt` — **урезан до bottom-sheet-части** (dialog/two-pane/
list-detail/supporting-pane НЕ портированы, как требует §2); `MultiBackStackNavigator.kt` (вербатим) + его транзитив:
`AlvaNav.kt`, `route/NavRoute.kt`, `tab/AlvaTabCommand.kt`, `saver/EnumNameSaver.kt`.

**Утилиты uikit** (минимальные срезы, чтобы не тянуть весь модуль):
`utils/ModifierExt.kt` — только `noRippleEffectClick` (без `alvaShadow`); `modifier/keyboard/KeyboardLiftState.kt` —
`rememberKeyboardLiftState` без `alvaLog` (убрана зависимость на `core:common`).

### Заглушки (замена транзитива core:device — разрешено §2)

- `com.alva.core.device.model.SystemBarsInsets` и `…WindowSizeClass` — **вербатим** из Alva (только модели, без pipeline).
- `com.alva.core.device.DeviceStateStub` + `rememberDeviceStateStub()` — **новая заглушка** вместо
  `koinInject<DeviceStateProvider>().collectAsState()`. Отдаёт ровно 3 поля, которые читает `AlvaBottomSheet`
  (`systemBarsInsets`, `widthSizeClass`, `screenWidthDp`), считая их напрямую из Compose: insets — из
  `WindowInsets.systemBars`/`displayCutout`, ширина окна — из `LocalWindowInfo.current.containerSize`, класс ширины —
  через вербатим-`WindowSizeClass.fromWidthDp`. Убирает Koin и весь `core:device` (fold-сигнал, ориентация, density —
  для z-order-теста не нужны). Единственная правка внутри `AlvaBottomSheet.kt` — замена 1 строки чтения deviceState.

### Отклонение от указания архитектора по deps

Team lead (со слов архитектора фазы 1) просил добавить `material3.adaptive:adaptive:1.3.0-beta02` (для
`currentWindowAdaptiveInfo`/`VerticalDragHandle`) и `androidx.window:window:1.5.1`. **По факту оба не понадобились:**
`VerticalDragHandle` — только в полном `AlvaSceneStrategies` (list-detail), который урезан; `currentWindowAdaptiveInfo`/
`currentWindowSize` — заменены на нехинтованный, не-deprecated `LocalWindowInfo.current.containerSize` в заглушке device.
Чтобы не держать неиспользуемые зависимости (правило «ровно порт»), `adaptive` и `window` **НЕ добавлены**. Если фаза 3
захочет адаптивные сцены (реальные list-detail/two-pane) — вернуть строки в каталог/`build.gradle.kts` одной правкой.
**Добавлена** только реально используемая `lifecycle-viewmodel-navigation3:2.10.0` (нужна `MultiBackStackNavigator` для
`rememberViewModelStoreNavEntryDecorator`).

### API для фазы 3 (проводка NavDisplay + открытие листа)

Референс проводки — `Alva/feature/root/…/RootScreen.kt` (строки ~119–229). Минимум для лаборатории:

1. **Пометить route листом.** В `entryProvider` для nav3-роута bottom sheet передать metadata:
   ```kotlin
   entry<Nav3SheetRoute>(
       metadata = AlvaBottomSheetSceneStrategy.bottomSheet {
           initialValue = AlvaBottomSheetValue.Expanded          // или PartialExpanded(0.5f)
           scrim = AlvaBottomSheetScrim.Enabled()                 // modal (перехват back/скрим)
           dismissOnScrimTap = true
       },
   ) { /* контент листа: крупная метка «NAV3 IN-TREE SHEET» + цветной фон */ }
   ```
   `Nav3SheetRoute` — реализует `NavRoute` (наш ported `com.alva.core.navigation.route.NavRoute : NavKey`).

2. **Стратегии.** `val sceneStrategies = rememberAlvaSceneStrategies()` (наш урезанный вариант — список из одной
   `AlvaBottomSheetSceneStrategy<NavKey>`). Передать в `NavDisplay(sceneStrategies = sceneStrategies, …)`.

3. **Бэкстек.** Два пути:
   - **Простой (рекомендуется для лаборатории):** `val backStack = rememberNavBackStack(BaseRoute)` из
     `androidx.navigation3.runtime`; `openAlvaBottomSheet()` = `backStack.add(Nav3SheetRoute)`.
     `NavDisplay(backStack = backStack, entries = …, onBack = { backStack.removeLastOrNull() }, sceneStrategies = …)`.
     Условие сцены: `AlvaBottomSheetSceneStrategy` активируется только при `entries.size >= 2`, поэтому в стеке нужен
     базовый экран + лист.
   - **Полный (как в Alva):** `MultiBackStackNavigator` через `rememberMultiBackStackNavigator(configuration) { tab(...) }`,
     `entries = navigator.toDecoratedEntries(entryProvider, bottomSheetSceneRetention)`, `onBack = navigator::popBack`.
     Требует `SavedStateConfiguration` с polymorphic `SerializersModule` для `NavKey` (kotlinx-serialization). Для z-order-
     теста избыточен — но портирован по §2, доступен.

4. **Retention (опц.).** `val retention = rememberAlvaBottomSheetSceneRetentionStore()`, обернуть NavDisplay в
   `CompositionLocalProvider(LocalAlvaBottomSheetSceneRetention provides retention)` — сохраняет анкер листа при forward-back.
   Для простого сценария не обязателен.

5. **Открытие из Activity (§3 ТЗ).** `MainActivity.openAlvaBottomSheet()` = мутировать backStack (`backStack.add(Nav3SheetRoute)`)
   из state, поднятого над `NavDisplay`. Fragment зовёт метод через каст к интерфейсу Activity (без event bus).

**Блокеры для фазы 3:** нет. Компонент и стратегия компилируются и готовы к проводке. Оба пути backStack доступны.
Если выбран полный путь (`MultiBackStackNavigator`) — нужен `SerializersModule`/`@Serializable` route + kotlinx-serialization
(проверить, что тянется транзитивно savedstate-serialization; при простом пути — не нужно вовсе).

---

## Прогноз vs Факт (флоу 1–3) — фаза 4 (эмпирика)

Прогон: `emulator-5554` (Android tablet, 2208×1840), APK `com.zorderlab`. Скриншот в момент наложения (t≈5с+),
когда оба слоя на экране. Логи `adb logcat -s ZORDER` подтвердили, что оба слоя реально показаны.

| # | Сценарий | Прогноз (§0) | Факт | Совпало | Скриншот |
|---|----------|--------------|------|:---:|----------|
| 1 | BSDFragment открыт → +5с nav3-лист поверх | ❌ nav3 позади | ✅ BSDFragment (teal) СВЕРХУ, ярко; nav3 (purple) ПОЗАДИ, притемнён скримом BSD, торчит только верхушка | ✅ | `screenshots/flow1_bsd_then_nav3.png` |
| 2 | DialogFragment открыт → +5с nav3-лист поверх | ❌ nav3 позади | ✅ DialogFragment (orange) СВЕРХУ, ярко; nav3 (purple) весь ПОЗАДИ, притемнён скримом диалога | ✅ | `screenshots/flow2_dialog_then_nav3.png` |
| 3 | nav3-лист открыт → +5с BSDFragment поверх | ✅ диалог поверх | ✅ BSDFragment (teal) СВЕРХУ, ярко; nav3 (purple) ПОЗАДИ, притемнён | ✅ | `screenshots/flow3_nav3_then_bsd.png` |
| 4 | BSDFragment открыт → +5с nav3-лист поверх, но лист поднимает **сам фрагмент** (fragment-hosted ComposeView) | ❌ nav3 позади | ✅ BSDFragment (teal) СВЕРХУ, ярко; fragment-hosted nav3 (blue) ПОЗАДИ — полностью скрыт под BSD (лог `NAV3 fragment-hosted sheet shown` подтверждает композицию) | ✅ | `screenshots/flow4_bsd_then_nav3_fragment_hosted.png` |

**Все 4 прогноза подтверждены эмпирически.**

Флоу 4 добавляет важный контрапункт: nav3-лист поднимает не Activity, а сам Fragment в собственном `ComposeView`.
Результат тот же, что во флоу 1 — потому что `ComposeView` фрагмента живёт внутри `FragmentContainerView`,
то есть всё в том же **единственном окне Activity**. **Кто хостит in-tree лист — Activity или Fragment — на z-order
не влияет.** Определяет только уровень окна: любой Fragment-диалог (отдельное `Window`) перекрывает любой in-tree
Compose-лист.

### Вывод

Порядок открытия **не имеет значения** — Fragment-диалог (`BottomSheetDialogFragment` / `DialogFragment`)
**всегда** ложится поверх nav3 in-tree листа (`AlvaBottomSheet` через `OverlayScene`/`SceneStrategy`).

Причина — уровень оконной системы, а не Compose z-index:
- Fragment-диалог = отдельное top-level `Window` в `WindowManager`, поверх окна Activity, со своим dim-скримом.
- nav3-лист = рендерится **in-tree** внутри `ComposeView` Activity, то есть в **том же окне Activity**, которое
  всегда ниже окна любого Dialog.

Практическое следствие для Alva: **нельзя перекрыть `AlvaBottomSheet`-ом любой активный `DialogFragment`/
`BottomSheetDialogFragment`** — Compose/nav3-лист уйдёт под скрим диалога. Обратное работает: View-диалог
перекроет nav3-лист. Наблюдаемо: слой поверх — яркий; слой под диалогом — притемнён скримом верхнего окна.
Флоу 1 и 3 визуально идентичны (оба заканчиваются teal BSD сверху) — прямое доказательство независимости от порядка.

### Метод воспроизведения
1. `./gradlew :app:assembleDebug`
2. `adb install -r app/build/outputs/apk/debug/app-debug.apk`
3. `adb shell am start -n com.zorderlab/.MainActivity`
4. `adb logcat -s ZORDER` (пары shown/hidden)
5. Кнопки: 1 (BSD→nav3 activity-hosted), 2 (Dialog→nav3), 3 (nav3→BSD), 4 (BSD→nav3 **fragment-hosted**). Скриншот на t≈5с после нажатия.

---

## Управление z-order «когда требуется» (флоу 5, 8) — эмпирика

Ресерч (source-verified по sources.jar из Gradle-кэша): Compose `Dialog{}` на Android — реальное
платформенное окно (`DialogWrapper : ComponentDialog`, `TYPE_APPLICATION`) — тот же слой и token,
что у окна BottomSheetDialogFragment → **z-order между ними определяется порядком добавления окон
в WindowManager, последнее сверху**. `AlvaDialogScene` в Alva уже использует этот паттерн (Dialog
composable → отдельное окно); стоковый nav3 `DialogScene` — тоже.

### Флоу 5 — `hostInWindow`: nav3-лист в собственном Dialog-окне (грамотное решение)

Прототип изменения для Alva: `AlvaBottomSheetSceneConfig` + поле `hostInWindow` (дефолт `false`);
при `true` `AlvaBottomSheetScene.content` оборачивает существующий `SheetChrome()` в full-screen
прозрачный `Dialog` (usePlatformDefaultWidth=false, decorFitsSystemWindows=false) + `UndimDialogWindow()`
(гасит принудительный dim темы `FloatingDialogWindowTheme` и оконную анимацию). API вызывающего кода
не меняется — флаг в metadata через DSL: `bottomSheet { hostInWindow = true }`.

| Проверка | Факт |
|---|---|
| BSD открыт → +5с nav3-лист `hostInWindow=true` | ✅ КРАСНЫЙ лист (own window) СВЕРХУ BSD, со своим скримом — инверсия флоу 1 | 
| `dumpsys window windows` | ✅ три окна com.zorderlab, окно листа добавлено последним (верхнее) |
| Back при открытом листе | ✅ закрывает ЛИСТ (`NAV3 window-hosted sheet hidden`), BSD остаётся жив — LIFO-риск не реализовался |
| Скрим | ✅ одинарный (UndimDialogWindow отработал), анимация листа штатная |

Скриншоты: `screenshots/flow5_bsd_then_nav3_own_window.png`, `screenshots/flow5back_back_closes_sheet_bsd_alive.png`.

### Флоу 8 — динамический цикл: «сверху тот, чьё окно добавлено последним»

Голый `ComponentDialog` (Theme_Translucent_NoTitleBar) с ComposeView-листом. Цикл: t=0 лист-окно →
t=+5с `BSD.show()` → t=+10с `dialog.dismiss(); dialog.show()` (re-add того же инстанса, без пере-inflate).

| t | Факт | Скриншот |
|---|---|---|
| ≈7с | ✅ teal BSD СВЕРХУ (его окно позже), зелёная панель позади | `flow8a_compose_window_then_bsd_on_top.png` |
| ≈12с | ✅ после re-raise ЗЕЛЁНАЯ панель СВЕРХУ, BSD скрыт (жив — «hidden» в логе нет) | `flow8b_after_reraise_compose_on_top.png` |

Лог: `COMPOSE dialog-sheet shown → BSD-FRAGMENT shown → COMPOSE dialog-sheet hidden + shown`
(пара hidden/shown при re-raise = композиция пересоздаётся: ComponentDialog в onStop шлёт ON_DESTROY).
`hide()/show()` окна НЕ переупорядочивает (source-verified); `bringToFront` для окон не существует —
единственный примитив подъёма = remove+add окна.

### Итоговая матрица «требование → механизм»

| Требование | Механизм | Статус |
|---|---|---|
| Compose-лист поверх уже открытого DialogFragment | `hostInWindow = true` в конфиге сцены (лист в своём окне, добавлен позже → сверху) | ✅ флоу 5 |
| View-диалог поверх Compose-листа | in-tree лист — автоматически (флоу 1–4); windowed-лист — показать диалог позже | ✅ флоу 1–4, 8a |
| Вернуть Compose-лист наверх после нового диалога | re-add окна листа: `key(counter){ Dialog(...) }` / `dismiss()+show()` | ✅ флоу 8b |
| «Диалог» подчиняется z-order Compose/nav3 | демоция: `setShowsDialog(false)` + add в контейнер (view уходит в окно Activity) | ресерч R3 (не прототипировано) |
| Детерминированный z-order всех оверлеев | single overlay owner: все оверлеи через nav3 backstack, ВСЕ сцены in-tree | архитектурная цель (ресерч R4) |

### Рекомендация для Alva

1. **Канон — in-tree** (текущий AlvaBottomSheetScene): внутри чистого nav3-мира z-order детерминирован
   порядком backstack (`NavDisplay` рисует overlay-сцены в порядке стека — проверено по исходникам 1.1.1).
2. **`hostInWindow` — переходный escape hatch** для экранов, где лист обязан перекрывать legacy
   Fragment-диалоги. KMP: в commonMain у `DialogProperties` только 3 параметра → нужны
   `expect fun windowedSheetDialogProperties()` + `@Composable expect fun DisablePlatformDialogDim()`
   (androidMain: decorFits+undim; iosMain: usePlatformInsets=false, scrimColor=Transparent).
3. **Внимание: `AlvaDialogScene` уже windowed** → диалог из nav3-стека ВСЕГДА перекрывает in-tree лист
   независимо от порядка entries. Для полного детерминизма его стоит перевести на in-tree рендер
   (скрим + Surface в Box по образцу листа).
4. Деградации windowed-листа (задокументировать в KDoc флага): предиктив-back-превью нижнего экрана
   не работает пока фокус у окна листа; shared elements через границу окон не работают; два windowed-оверлея
   упорядочены порядком окон, не стеком. Back при этом работает корректно (проверено флоу 5: лист закрылся
   с pop, BSD жив).

---

## Фикс флоу 1–4: «второй оверлей всегда поверх» (по запросу юзера)

Оба nav3-листа лабы (фиолетовый activity-hosted и синий fragment-hosted) переведены на
`hostInWindow = true` — один разделяемый `entry<Nav3SheetRoute>`, флаг в одном месте.
Теперь работает естественное UX-правило: **кто открыт позже — тот сверху** (все оверлеи — окна,
z-order = порядок добавления).

| # | Флоу | До фикса | После фикса | Скриншот |
|---|---|---|---|---|
| 1 | BSD → +5с лист | лист ПОД BSD | ✅ лист СВЕРХУ | `screenshots/flow1_fixed_windowed.png` |
| 2 | Dialog → +5с лист | лист ПОД | ✅ лист СВЕРХУ | `screenshots/flow2_fixed_windowed.png` |
| 3 | лист → +5с BSD | BSD сверху | ✅ BSD сверху (окно позже — семантика сохранена) | `screenshots/flow3_fixed_windowed.png` |
| 4 | BSD → +5с fragment-hosted лист | лист ПОД | ✅ лист СВЕРХУ | `screenshots/flow4_fixed_windowed.png` |

Метки листов: «NAV3 SHEET (windowed)» / «NAV3 SHEET (fragment-hosted, windowed)».
Логи: `NAV3 windowed sheet shown` / `NAV3 fragment-hosted windowed sheet shown`.
