package com.onexui.demo

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.onexui.bottomsheet.NativeSheetSpring
import com.onexui.bottomsheet.XBottomSheet
import com.onexui.bottomsheet.XBottomSheetScope
import com.onexui.bottomsheet.additionaltop.AdditionalTopState
import com.onexui.bottomsheet.config.BottomKeyboardBehavior
import com.onexui.bottomsheet.config.rememberXBottomSheetConfig
import com.onexui.bottomsheet.handle.DragHandleStyle
import com.onexui.bottomsheet.presets.PresetBodyText
import com.onexui.bottomsheet.presets.PresetMenuCell
import com.onexui.bottomsheet.presets.PresetSearchField
import com.onexui.bottomsheet.presets.PresetSingleButton
import com.onexui.bottomsheet.presets.PresetTitle
import com.onexui.bottomsheet.state.rememberXBottomSheetState
import kotlinx.coroutines.delay

// Тема Compose-слоя demo (отдельная от View-темы Activity): XBottomSheet и пресеты читают MaterialTheme.
@Composable
internal fun XbsDemoTheme(content: @Composable () -> Unit) {
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme, content = content)
}

// Кейсы матрицы состояний. Один активный кейс = один XBottomSheet на экран: открытие нового кейса закрывает
// предыдущий (при открытом листе scrim перекрывает кнопки — второй кейс запускается после закрытия).
private enum class DemoCase(val label: String) {
    A("(a) Content — выход из аккаунта"),
    B1("(b1) Expand → ExpandedContent (средний список)"),
    B2("(b2) Expand → ExpandedFullScreen (огромный список)"),
    C("(c) skipCollapsed = true"),
    D("(d) Loading → markContentReady"),
    E("(e) overlayBackground = false (тень)"),
    F("(f) dragHandle = null"),
    G("(g) Additional Top — купон / отслеживать"),
    I("(i) IME — поиск: подъём / авто-FullScreen + shrink"),
    L("(l) IME — поиск + список + bottom ПОД клавиатурой"),
    S("(s) IME — поиск + список + bottom НАД клавиатурой"),
    J("(j) Закрытия выключены — только кнопкой"),
    K("(k) Static handle + рост контента → авто-FullScreen"),
    M("(m) Контент: LazyVerticalGrid (сетка 2 кол.)"),
    N("(n) Контент: LazyRow (гориз., короткий → wrap)"),
    O("(o) Контент: LazyColumn + LazyRow (карусели)"),
    P("(p) Контент: Column + verticalScroll"),
    Q("(q) Контент: LazyColumn + вложенный LazyColumn"),
    R("(r) Кастомный якорь 50% (свайп: peek → 50% → full)"),
    T("(t) AdditionalTop + короткий контент (wrap)"),
    U("(u) Loading + поиск: IME во время Loading"),
    V("(v) predictive back — onBackPress=true"),
    W("(w) live-ручки: peekFraction + anchors"),
}

private val SPORTS: List<String> = listOf(
    "Футбол", "Хоккей", "Баскетбол", "Теннис", "Волейбол", "Гандбол", "Бейсбол",
    "Настольный теннис", "Киберспорт", "Бокс", "MMA", "Регби", "Крикет", "Дартс",
    "Бильярд", "Снукер", "Гольф", "Бадминтон", "Водное поло", "Флорбол", "Футзал",
    "Пляжный волейбол", "Формула-1", "Биатлон", "Лыжные гонки", "Плавание", "Лёгкая атлетика",
    "Шахматы", "Керлинг", "Американский футбол", "Гребля", "Стрельба", "Фехтование",
    "Тяжёлая атлетика", "Спортивная гимнастика", "Художественная гимнастика", "Триатлон",
    "Конный спорт", "Парусный спорт", "Скалолазание", "Сёрфинг", "Скейтбординг", "BMX",
    "Хоккей на траве", "Софтбол", "Лакросс", "Сквош", "Падел", "Пляжный футбол", "Кабадди",
)

private val MarkerColors: List<Color> = listOf(
    Color(0xFFEF5350), Color(0xFF42A5F5), Color(0xFF66BB6A), Color(0xFFFFA726), Color(0xFFAB47BC),
)

// LazyColumn сам скроллит. fillMaxWidth (не fillMaxSize): высота wrap'ится → мало айтемов wrap-режим, много fill.
// Клик по строке закрывает лист через scope (requestDismiss), поэтому это extension XBottomSheetScope.
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

@Composable
internal fun XbsDemoScreen() {
    // saveable, чтобы активный кейс (а с ним стейт листа) переживал ротацию — иначе демо-хост сбросился бы в null.
    var activeCase by rememberSaveable(
        stateSaver = Saver(
            save = { case -> case?.name ?: "" },
            restore = { saved ->
                val name = saved as? String
                DemoCase.entries.firstOrNull { entry -> entry.name == name }
            },
        ),
    ) { mutableStateOf<DemoCase?>(null) }
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "XBottomSheet · demo",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            DemoCase.entries.forEach { case ->
                Button(onClick = { activeCase = case }, modifier = Modifier.fillMaxWidth()) {
                    Text(text = case.label)
                }
            }
        }
        activeCase?.let { case ->
            // key(case) — пересоздаём хост при смене кейса (свежий стейт листа).
            key(case) {
                XbsCaseHost(case = case, onClose = { activeCase = null })
            }
        }
    }
}

@Composable
private fun XbsCaseHost(case: DemoCase, onClose: () -> Unit) {
    when (case) {
        DemoCase.A -> CaseContent(onClose)
        DemoCase.B1 -> CaseMediumList(onClose)
        DemoCase.B2 -> CaseHugeList(onClose)
        DemoCase.C -> CaseSkipCollapsed(onClose)
        DemoCase.D -> CaseLoading(onClose)
        DemoCase.E -> CaseNoOverlay(onClose)
        DemoCase.F -> CaseNoHandle(onClose)
        DemoCase.G -> CaseAdditionalTop(onClose)
        DemoCase.I -> CaseImeSearch(onClose)
        DemoCase.L -> CaseImeBottomUnderKeyboard(onClose)
        DemoCase.S -> CaseImeBottomAboveKeyboard(onClose)
        DemoCase.J -> CaseNoDismiss(onClose)
        DemoCase.K -> CaseStaticGrow(onClose)
        DemoCase.M -> CaseGrid(onClose)
        DemoCase.N -> CaseLazyRow(onClose)
        DemoCase.O -> CaseCarousels(onClose)
        DemoCase.P -> CaseVerticalScroll(onClose)
        DemoCase.Q -> CaseNestedLazyColumn(onClose)
        DemoCase.R -> CaseCustomAnchor(onClose)
        DemoCase.T -> CaseAdditionalTopWrap(onClose)
        DemoCase.U -> CaseLoadingImeSearch(onClose)
        DemoCase.V -> CasePredictiveBack(onClose)
        DemoCase.W -> CaseLiveHandles(onClose)
    }
}

// (r) Кастомный якорь: между Collapsed(peek) и Expanded(full) добавлен свой якорь 50% экрана. Свайп по листу
// останавливается на ближайшем из якорей: peek(2/3) / 50% / full. Разраб задаёт свои через customAnchors.
@Composable
private fun CaseCustomAnchor(onClose: () -> Unit) {
    val state = rememberXBottomSheetState { anchors { "half" at 0.5f } }
    LaunchedEffect(Unit) { state.show() }
    XBottomSheet(
        state = state,
        onDismissRequest = { state.hide(); onClose() },
        top = { PresetTitle("Кастомный якорь 50%") },
    ) {
        SportLazyList(SPORTS)
    }
}

// (m) Контент = LazyVerticalGrid: сетка заполняет доступную высоту → fill-режим (Collapsed peek / Expanded).
@Composable
private fun CaseGrid(onClose: () -> Unit) {
    val state = rememberXBottomSheetState()
    LaunchedEffect(Unit) { state.show() }
    XBottomSheet(
        state = state,
        onDismissRequest = { state.hide(); onClose() },
        top = { PresetTitle("Сетка (LazyVerticalGrid)") },
    ) {
        LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.fillMaxWidth()) {
            gridItems(SPORTS) { sport -> PresetMenuCell(title = sport, onClick = { requestDismiss() }) }
        }
    }
}

// (n) Контент = LazyRow (горизонтальный, короткий по высоте) → wrap-режим: лист по высоте контента.
@Composable
private fun CaseLazyRow(onClose: () -> Unit) {
    val state = rememberXBottomSheetState()
    LaunchedEffect(Unit) { state.show() }
    XBottomSheet(
        state = state,
        onDismissRequest = { state.hide(); onClose() },
        top = { PresetTitle("Горизонтальный (LazyRow)") },
    ) {
        LazyRow(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
            items(SPORTS) { sport ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .height(96.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                ) { Text(text = sport, color = MaterialTheme.colorScheme.onPrimaryContainer) }
            }
        }
    }
}

// (o) Контент = LazyColumn с вложенными LazyRow (вертикальный список каруселей). Заполняет → fill-режим.
// Вертикальный скролл листа и горизонтальный скролл каруселей не конфликтуют (разные оси).
@Composable
private fun CaseCarousels(onClose: () -> Unit) {
    val state = rememberXBottomSheetState()
    LaunchedEffect(Unit) { state.show() }
    XBottomSheet(
        state = state,
        onDismissRequest = { state.hide(); onClose() },
        top = { PresetTitle("Карусели (LazyColumn + LazyRow)") },
    ) {
        LazyColumn(modifier = Modifier.fillMaxWidth(), state = rememberLazyListState()) {
            items(12) { row ->
                PresetBodyText("Категория ${row + 1}")
                LazyRow(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    items(SPORTS) { sport ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .height(72.dp)
                                .background(MarkerColors[(row) % MarkerColors.size], RoundedCornerShape(12.dp))
                                .padding(16.dp),
                        ) { Text(text = sport, color = Color.White) }
                    }
                }
            }
        }
    }
}

// (p) Контент = Column + verticalScroll (не Lazy). Длинный → заполняет → fill-режим; короткий был бы wrap.
@Composable
private fun CaseVerticalScroll(onClose: () -> Unit) {
    val state = rememberXBottomSheetState()
    LaunchedEffect(Unit) { state.show() }
    XBottomSheet(
        state = state,
        onDismissRequest = { state.hide(); onClose() },
        top = { PresetTitle("Column + verticalScroll") },
    ) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            SPORTS.forEachIndexed { index, sport ->
                PresetMenuCell(title = sport, onClick = { requestDismiss() }, leadingColor = MarkerColors[index % MarkerColors.size])
            }
        }
    }
}

// (q) Контент = LazyColumn с вложенным LazyColumn фиксированной высоты (вложенный вертикальный скролл).
@Composable
private fun CaseNestedLazyColumn(onClose: () -> Unit) {
    val state = rememberXBottomSheetState()
    LaunchedEffect(Unit) { state.show() }
    XBottomSheet(
        state = state,
        onDismissRequest = { state.hide(); onClose() },
        top = { PresetTitle("Вложенный LazyColumn") },
    ) {
        LazyColumn(modifier = Modifier.fillMaxWidth(), state = rememberLazyListState()) {
            item { PresetBodyText("Внешний LazyColumn. Ниже — вложенный LazyColumn фикс. высоты (свой скролл):") }
            item {
                LazyColumn(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                    items(SPORTS) { sport -> PresetMenuCell(title = sport, onClick = { requestDismiss() }) }
                }
            }
            items(SPORTS) { sport -> PresetMenuCell(title = sport, onClick = { requestDismiss() }) }
        }
    }
}

// (a) Короткий Content: Title + BodyText + 1Button. Высота по контенту, стейт Content.
@Composable
private fun CaseContent(onClose: () -> Unit) {
    val state = rememberXBottomSheetState()
    LaunchedEffect(Unit) { state.show() }
    XBottomSheet(
        state = state,
        onDismissRequest = { state.hide(); onClose() },
        top = { PresetTitle("Выйти из аккаунта") },
        bottom = { PresetSingleButton(text = "Выйти", onClick = { requestDismiss() }) },
    ) {
        PresetBodyText("Вы уверены, что хотите выйти из аккаунта? Несохранённые данные будут потеряны.")
    }
}

// (b1) Средний список: контент > 60% (открытие Collapsed), но ≤ экрана → свайп вверх даёт ExpandedContent
// (высота по контенту, лимит Status Bar), а не FullScreen.
@Composable
private fun CaseMediumList(onClose: () -> Unit) {
    val state = rememberXBottomSheetState()
    LaunchedEffect(Unit) { state.show() }
    XBottomSheet(
        state = state,
        onDismissRequest = { state.hide(); onClose() },
        top = { PresetTitle("Вид спорта (средний список)") },
    ) {
        SportLazyList(SPORTS.take(11))
    }
}

// (b2) Огромный список: контент > экрана (открытие Collapsed) → свайп вверх даёт ExpandedFullScreen (весь экран).
@Composable
private fun CaseHugeList(onClose: () -> Unit) {
    val state = rememberXBottomSheetState()
    LaunchedEffect(Unit) { state.show() }
    XBottomSheet(
        state = state,
        onDismissRequest = { state.hide(); onClose() },
        top = { PresetTitle("Вид спорта (огромный список)") },
    ) {
        SportLazyList(SPORTS)
    }
}

// (c) skipCollapsed = true: контент БЕЗ лимита 60% (лимит — Status Bar). Длинный список (>экрана) → открытие
// сразу ExpandedFullScreen, минуя Collapsed (без skip был бы Collapsed 60%) — видна ветка skip.
@Composable
private fun CaseSkipCollapsed(onClose: () -> Unit) {
    val state = rememberXBottomSheetState { skipCollapsed = true }
    LaunchedEffect(Unit) { state.show() }
    XBottomSheet(
        state = state,
        onDismissRequest = { state.hide(); onClose() },
        top = { PresetTitle("Популярное (skipCollapsed)") },
    ) {
        SportLazyList(SPORTS.take(50))
    }
}

// (d) Loading → markContentReady: initialLoading, show() → Loading 192dp/Loader, задержка → markContentReady() → анимация.
@Composable
private fun CaseLoading(onClose: () -> Unit) {
    val state = rememberXBottomSheetState { initialLoading = true }
    LaunchedEffect(Unit) {
        state.show()
        delay(LOADING_DELAY_MS)
        state.markContentReady()
    }
    XBottomSheet(
        state = state,
        onDismissRequest = { state.hide(); onClose() },
        top = { PresetTitle("Виды спорта") },
    ) {
        SportLazyList(SPORTS.take(50))
    }
}

// (e) overlayBackground = false: видна тень Shadow Soft, тачи под листом всё равно заблокированы.
@Composable
private fun CaseNoOverlay(onClose: () -> Unit) {
    val state = rememberXBottomSheetState()
    LaunchedEffect(Unit) { state.show() }
    XBottomSheet(
        state = state,
        onDismissRequest = { state.hide(); onClose() },
        config = rememberXBottomSheetConfig { overlayBackground = false },
        top = { PresetTitle("Без затемнения") },
        bottom = { PresetSingleButton(text = "Понятно", onClick = { requestDismiss() }) },
    ) {
        PresetBodyText("overlayBackground = false: фон не затемняется, виден Shadow Soft. Тачи под листом заблокированы.")
    }
}

// (f) dragHandle = null: хендл скрыт, взаимодействие с высотой отключено (нет свайпа/expand). Закрытие — тап/кнопка.
@Composable
private fun CaseNoHandle(onClose: () -> Unit) {
    val state = rememberXBottomSheetState()
    LaunchedEffect(Unit) { state.show() }
    XBottomSheet(
        state = state,
        onDismissRequest = { state.hide(); onClose() },
        config = rememberXBottomSheetConfig { dragHandle = null },
        top = { PresetTitle("Без Drag Handle") },
        bottom = { PresetSingleButton(text = "Закрыть", onClick = { requestDismiss() }) },
    ) {
        PresetBodyText("dragHandle = null: жесты высоты отключены. Закрыть можно тапом вне листа или кнопкой.")
    }
}

// (g) Additional Top «Добавить в купон / Отслеживать»: слот с Expanded/Collapsed. Состояние живёт на
// state.additionalTopState, переключается кнопкой в листе (внешний фактор).
@Composable
private fun CaseAdditionalTop(onClose: () -> Unit) {
    val state = rememberXBottomSheetState()
    LaunchedEffect(Unit) { state.show() }
    val collapsed = state.additionalTopState == AdditionalTopState.Collapsed
    XBottomSheet(
        state = state,
        onDismissRequest = { state.hide(); onClose() },
        config = rememberXBottomSheetConfig { additionalTop { cornerRadius = 16.dp } },
        additionalTop = { AdditionalTopCard(collapsed = collapsed) },
        top = { PresetTitle("Событие") },
        bottom = {
            PresetSingleButton(
                text = if (collapsed) "Развернуть Additional Top" else "Свернуть Additional Top",
                onClick = {
                    state.additionalTopState = if (collapsed) {
                        AdditionalTopState.Expanded
                    } else {
                        AdditionalTopState.Collapsed
                    }
                },
            )
        },
    ) {
        PresetBodyText("Слот Additional Top закреплён над листом. Кнопка ниже переключает Expanded/Collapsed.")
    }
}

// (i) IME: SearchField в top + короткий список. Фокус на поле → лист поднимается (withAdjustmentForKeyboard),
// а т.к. места короткому листу не хватает → авто-переход в ExpandedFullScreen + сжатие Middle (withKeyboardShrink).
// Закрытие листа скрывает IME (фокус уходит вместе с листом).
@Composable
private fun CaseImeSearch(onClose: () -> Unit) {
    val state = rememberXBottomSheetState()
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { state.show() }
    val filtered = remember(query) { SPORTS.take(50).filter { sport -> sport.contains(query, ignoreCase = true) } }
    XBottomSheet(
        state = state,
        onDismissRequest = { state.hide(); onClose() },
        top = {
            PresetTitle("Поиск + клавиатура")
            PresetSearchField(query = query, onQueryChange = { value -> query = value })
        },
    ) {
        SportLazyList(filtered)
    }
}

// (l) Гибкость подъёма над клавиатурой: bottomKeyboardBehavior = StayUnderKeyboard. При фокусе на поиске лист
// разворачивается в FullScreen, поиск (top) + список (middle) поднимаются над клавиатурой, а bottom-кнопка
// остаётся прижатой к нижней кромке листа и уходит ПОД клавиатуру (появляется снова при её скрытии).
// Для сравнения: кейс (i) с дефолтным Lift поднимает над клавиатурой ВЕСЬ контент, включая bottom.
@Composable
private fun CaseImeBottomUnderKeyboard(onClose: () -> Unit) {
    val state = rememberXBottomSheetState()
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { state.show() }
    val filtered = remember(query) { SPORTS.take(50).filter { sport -> sport.contains(query, ignoreCase = true) } }
    XBottomSheet(
        state = state,
        onDismissRequest = { state.hide(); onClose() },
        config = rememberXBottomSheetConfig { keyboard { bottomBehavior = BottomKeyboardBehavior.StayUnderKeyboard } },
        top = {
            PresetTitle("Вид спорта")
            PresetSearchField(query = query, onQueryChange = { value -> query = value })
        },
        bottom = { PresetSingleButton(text = "Показать все", onClick = { requestDismiss() }) },
    ) {
        SportLazyList(filtered)
    }
}

// (s) Дефолтный подъём над клавиатурой: bottomKeyboardBehavior = Lift (значение по умолчанию). При фокусе на
// поиске лист разворачивается в FullScreen и над клавиатурой поднимается ВЕСЬ контент, включая bottom-кнопку —
// она едет вверх вместе с top+middle и остаётся видимой прямо над клавиатурой. Противоположность кейсу (l),
// где bottom, наоборот, прижат к нижней кромке листа и уходит ПОД клавиатуру.
@Composable
private fun CaseImeBottomAboveKeyboard(onClose: () -> Unit) {
    val state = rememberXBottomSheetState()
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { state.show() }
    val filtered = remember(query) { SPORTS.take(50).filter { sport -> sport.contains(query, ignoreCase = true) } }
    XBottomSheet(
        state = state,
        onDismissRequest = { state.hide(); onClose() },
        config = rememberXBottomSheetConfig { keyboard { bottomBehavior = BottomKeyboardBehavior.Lift } },
        top = {
            PresetTitle("Вид спорта")
            PresetSearchField(query = query, onQueryChange = { value -> query = value })
        },
        bottom = { PresetSingleButton(text = "Показать все", onClick = { requestDismiss() }) },
    ) {
        SportLazyList(filtered)
    }
}

// (j) Все способы закрытия опциональны: dismissOnOutsideTap=false, dismissOnSwipeDown=false — закрыть можно
// ТОЛЬКО кнопкой (requestDismiss).
@Composable
private fun CaseNoDismiss(onClose: () -> Unit) {
    val state = rememberXBottomSheetState()
    LaunchedEffect(Unit) { state.show() }
    XBottomSheet(
        state = state,
        onDismissRequest = { state.hide(); onClose() },
        config = rememberXBottomSheetConfig { dismiss { onOutsideTap = false; onSwipeDown = false } },
        top = { PresetTitle("Закрытия выключены") },
        bottom = { PresetSingleButton(text = "Закрыть", onClick = { requestDismiss() }) },
    ) {
        PresetBodyText("dismissOnOutsideTap = false, dismissOnSwipeDown = false. Тап вне листа и свайп вниз не закрывают — только кнопка.")
    }
}

// (k) DragHandle.Static + рост контента: кнопка в Middle добавляет элементы → onContentRemeasured тянет высоту
// за контентом; при skipCollapsed=true и росте выше экрана срабатывает ветка «контент вырос выше экрана» →
// авто-переход в ExpandedFullScreen.
@Composable
private fun CaseStaticGrow(onClose: () -> Unit) {
    val state = rememberXBottomSheetState { skipCollapsed = true }
    var itemCount by remember { mutableStateOf(4) }
    LaunchedEffect(Unit) { state.show() }
    XBottomSheet(
        state = state,
        onDismissRequest = { state.hide(); onClose() },
        config = rememberXBottomSheetConfig { dragHandle = DragHandleStyle.Static },
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
}

// (t) AdditionalTop + короткий контент: лист открывается по высоте контента (Content), а не peek. Карточка + тоггл
// через scope.additionalTopState (var). Слепая зона E1: sticky-слой поверх wrap-контента.
@Composable
private fun CaseAdditionalTopWrap(onClose: () -> Unit) {
    val state = rememberXBottomSheetState()
    LaunchedEffect(Unit) { state.show() }
    XBottomSheet(
        state = state,
        onDismissRequest = { state.hide(); onClose() },
        config = rememberXBottomSheetConfig { additionalTop { cornerRadius = 16.dp } },
        additionalTop = { AdditionalTopCard(collapsed = additionalTopState == AdditionalTopState.Collapsed) },
        top = { PresetTitle("Событие (короткий)") },
        bottom = {
            PresetSingleButton(
                text = if (additionalTopState == AdditionalTopState.Collapsed) "Развернуть" else "Свернуть",
                onClick = {
                    additionalTopState = if (additionalTopState == AdditionalTopState.Collapsed) {
                        AdditionalTopState.Expanded
                    } else {
                        AdditionalTopState.Collapsed
                    }
                },
            )
        },
    ) {
        PresetBodyText("Короткий контент → лист открывается по высоте контента (Content), не peek. Карточка additionalTop сверху; кнопка переключает Expanded/Collapsed через scope.")
    }
}

// (u) Loading + поиск: IME во время Loading. Слепая зона L5: фокус в поиске во время Loading, markContentReady
// под открытой клавиатурой → авто-FullScreen (верх листа не уходит за потолок).
@Composable
private fun CaseLoadingImeSearch(onClose: () -> Unit) {
    val state = rememberXBottomSheetState { initialLoading = true }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        state.show()
        delay(LOADING_DELAY_MS)
        state.markContentReady()
    }
    val filtered = remember(query) { SPORTS.take(50).filter { sport -> sport.contains(query, ignoreCase = true) } }
    XBottomSheet(
        state = state,
        onDismissRequest = { state.hide(); onClose() },
        top = {
            PresetTitle("Поиск (Loading + IME)")
            PresetSearchField(query = query, onQueryChange = { value -> query = value })
        },
    ) {
        SportLazyList(filtered)
    }
}

// (v) predictive back: dismiss.onBackPress=true → back-жест (Android 14+ gesture nav) визуально двигает лист за
// прогрессом. Кнопочный back и API<34 закрывают мгновенно как раньше.
@Composable
private fun CasePredictiveBack(onClose: () -> Unit) {
    val state = rememberXBottomSheetState()
    LaunchedEffect(Unit) { state.show() }
    XBottomSheet(
        state = state,
        onDismissRequest = { state.hide(); onClose() },
        config = rememberXBottomSheetConfig { dismiss { onBackPress = true } },
        top = { PresetTitle("Predictive back") },
    ) {
        PresetBodyText("dismiss.onBackPress = true: back-жест (Android 14+) визуально двигает лист за пальцем. Отпустить — лист закрывается; отменить — возвращается на место.")
    }
}

// (w) Живые ручки стейта: peekFraction и anchors меняются прямо в композиции — покоящийся лист доезжает к новому
// якорю сам, без жеста. Кнопка «peek» двигает Collapsed-якорь (лист на нём — переезжает сразу). Кнопка «средний
// якорь» правит кастомный rest-стоп: свайпни лист на него, затем меняй высоту — лист переедет между 50% и 33%.
@Composable
private fun CaseLiveHandles(onClose: () -> Unit) {
    val state = rememberXBottomSheetState { anchors { "mid" at 0.5f } }
    var isWidePeek by remember { mutableStateOf(false) }
    var isLowMid by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { state.show() }
    XBottomSheet(
        state = state,
        onDismissRequest = { state.hide(); onClose() },
        top = {
            PresetTitle("Живые ручки стейта")
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        isWidePeek = !isWidePeek
                        state.peekFraction = if (isWidePeek) 0.5f else 2f / 3f
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(text = if (isWidePeek) "peek → 2/3" else "peek → 1/2") }
                Button(
                    onClick = {
                        isLowMid = !isLowMid
                        state.anchors { "mid" at if (isLowMid) 0.33f else 0.5f }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(text = if (isLowMid) "средний якорь → 50%" else "средний якорь → 33%") }
            }
        },
    ) {
        SportLazyList(SPORTS)
    }
}

// Внешний контент слота Additional Top: кросс-фейд текстов (alpha 0 в Collapsed), чтобы гасли синхронно с
// усадкой высоты, а не обрезались резко.
@Composable
private fun AdditionalTopCard(collapsed: Boolean) {
    // alpha через Animatable: .value читается в graphicsLayer (draw-фаза) → кросс-фейд без покадровой рекомпозиции.
    val contentAlpha = remember { Animatable(if (collapsed) 0f else 1f) }
    LaunchedEffect(collapsed) {
        contentAlpha.animateTo(if (collapsed) 0f else 1f, NativeSheetSpring)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .graphicsLayer { alpha = contentAlpha.value }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Добавить в купон",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            text = "Отслеживать",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

private const val LOADING_DELAY_MS = 2000L
