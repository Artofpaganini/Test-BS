package com.onexui.demo

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.onexui.bottomsheet.AdditionalTopState
import com.onexui.bottomsheet.DragHandleStyle
import com.onexui.bottomsheet.KeyboardBottomBehavior
import com.onexui.bottomsheet.XBottomSheet
import com.onexui.bottomsheet.rememberXBottomSheetState
import com.onexui.bottomsheet.presets.Preset1Button
import com.onexui.bottomsheet.presets.PresetBodyText
import com.onexui.bottomsheet.presets.PresetMenuCell
import com.onexui.bottomsheet.presets.PresetSearchField
import com.onexui.bottomsheet.presets.PresetTitle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Тема Compose-слоя demo (отдельная от View-темы Activity): XBottomSheet и пресеты читают MaterialTheme.
@Composable
internal fun XbsDemoTheme(content: @Composable () -> Unit) {
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme, content = content)
}

// Кейсы матрицы состояний (§4). Один активный кейс = один XBottomSheet на экран: открытие нового кейса
// закрывает предыдущий (при открытом листе scrim перекрывает кнопки — второй кейс запускается после закрытия).
private enum class DemoCase(val label: String) {
    A("(a) Content — выход из аккаунта"),
    B1("(b1) Expand → ExpandedContent (средний список)"),
    B2("(b2) Expand → ExpandedFullScreen (огромный список)"),
    C("(c) skipCollapsed = true"),
    D("(d) Loading → contentReady"),
    E("(e) overlayBackground = false (тень)"),
    F("(f) dragHandle = null"),
    G("(g) Additional Top — купон / отслеживать"),
    I("(i) IME — поиск: подъём / авто-FullScreen + shrink"),
    L("(l) IME — поиск + список + bottom ПОД клавиатурой"),
    J("(j) Закрытия выключены — только кнопкой"),
    K("(k) Static handle + рост контента → авто-FullScreen"),
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

@Composable
internal fun XbsDemoScreen() {
    var activeCase by remember { mutableStateOf<DemoCase?>(null) }
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
        DemoCase.J -> CaseNoDismiss(onClose)
        DemoCase.K -> CaseStaticGrow(onClose)
    }
}

// (a) Короткий Content: Title + BodyText + 1Button. Высота по контенту, стейт Content.
@Composable
private fun CaseContent(onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val state = rememberXBottomSheetState()
    val dismiss: () -> Unit = { scope.launch { state.hide(); onClose() } }
    LaunchedEffect(Unit) { state.show() }
    XBottomSheet(
        state = state,
        onDismissRequest = dismiss,
        top = { PresetTitle("Выйти из аккаунта") },
        bottom = { Preset1Button(text = "Выйти", onClick = dismiss) },
    ) {
        PresetBodyText("Вы уверены, что хотите выйти из аккаунта? Несохранённые данные будут потеряны.")
    }
}

// (b1) Средний список: контент > 60% (открытие Collapsed), но ≤ экрана → свайп вверх даёт ExpandedContent
// (высота по контенту, лимит Status Bar), а не FullScreen.
@Composable
private fun CaseMediumList(onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val state = rememberXBottomSheetState()
    val dismiss: () -> Unit = { scope.launch { state.hide(); onClose() } }
    LaunchedEffect(Unit) { state.show() }
    XBottomSheet(
        state = state,
        onDismissRequest = dismiss,
        top = { PresetTitle("Вид спорта (средний список)") },
    ) {
        SPORTS.take(11).forEachIndexed { index, sport ->
            PresetMenuCell(
                title = sport,
                onClick = dismiss,
                leadingColor = MarkerColors[index % MarkerColors.size],
            )
        }
    }
}

// (b2) Огромный список: контент > экрана (открытие Collapsed) → свайп вверх даёт ExpandedFullScreen (весь экран).
@Composable
private fun CaseHugeList(onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val state = rememberXBottomSheetState()
    val dismiss: () -> Unit = { scope.launch { state.hide(); onClose() } }
    LaunchedEffect(Unit) { state.show() }
    XBottomSheet(
        state = state,
        onDismissRequest = dismiss,
        top = { PresetTitle("Вид спорта (огромный список)") },
    ) {
        SPORTS.forEachIndexed { index, sport ->
            PresetMenuCell(
                title = sport,
                onClick = dismiss,
                leadingColor = MarkerColors[index % MarkerColors.size],
            )
        }
    }
}

// (c) skipCollapsed = true: контент БЕЗ лимита 60% (лимит — Status Bar). Длинный список (>экрана) → открытие
// сразу ExpandedFullScreen, минуя Collapsed (без skip был бы Collapsed 60%) — видна ветка skip.
@Composable
private fun CaseSkipCollapsed(onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val state = rememberXBottomSheetState(skipCollapsed = true)
    val dismiss: () -> Unit = { scope.launch { state.hide(); onClose() } }
    LaunchedEffect(Unit) { state.show() }
    XBottomSheet(
        state = state,
        onDismissRequest = dismiss,
        top = { PresetTitle("Популярное (skipCollapsed)") },
    ) {
        SPORTS.take(50).forEachIndexed { index, sport ->
            PresetMenuCell(
                title = sport,
                onClick = dismiss,
                leadingColor = MarkerColors[index % MarkerColors.size],
            )
        }
    }
}

// (d) Loading → contentReady: initialLoading, show() → Loading 192dp/Loader, задержка → contentReady() → анимация.
@Composable
private fun CaseLoading(onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val state = rememberXBottomSheetState(initialLoading = true)
    val dismiss: () -> Unit = { scope.launch { state.hide(); onClose() } }
    LaunchedEffect(Unit) {
        state.show()
        delay(LOADING_DELAY_MS)
        state.contentReady()
    }
    XBottomSheet(
        state = state,
        onDismissRequest = dismiss,
        top = { PresetTitle("Виды спорта") },
    ) {
        SPORTS.take(50).forEachIndexed { index, sport ->
            PresetMenuCell(
                title = sport,
                onClick = dismiss,
                leadingColor = MarkerColors[index % MarkerColors.size],
            )
        }
    }
}

// (e) overlayBackground = false: видна тень Shadow Soft, тачи под листом всё равно заблокированы.
@Composable
private fun CaseNoOverlay(onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val state = rememberXBottomSheetState()
    val dismiss: () -> Unit = { scope.launch { state.hide(); onClose() } }
    LaunchedEffect(Unit) { state.show() }
    XBottomSheet(
        state = state,
        onDismissRequest = dismiss,
        overlayBackground = false,
        top = { PresetTitle("Без затемнения") },
        bottom = { Preset1Button(text = "Понятно", onClick = dismiss) },
    ) {
        PresetBodyText("overlayBackground = false: фон не затемняется, виден Shadow Soft. Тачи под листом заблокированы.")
    }
}

// (f) dragHandle = null: хендл скрыт, взаимодействие с высотой отключено (нет свайпа/expand). Закрытие — тап/кнопка.
@Composable
private fun CaseNoHandle(onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val state = rememberXBottomSheetState()
    val dismiss: () -> Unit = { scope.launch { state.hide(); onClose() } }
    LaunchedEffect(Unit) { state.show() }
    XBottomSheet(
        state = state,
        onDismissRequest = dismiss,
        dragHandle = null,
        top = { PresetTitle("Без Drag Handle") },
        bottom = { Preset1Button(text = "Закрыть", onClick = dismiss) },
    ) {
        PresetBodyText("dragHandle = null: жесты высоты отключены. Закрыть можно тапом вне листа или кнопкой.")
    }
}

// (g) Additional Top «Добавить в купон / Отслеживать»: слот с Expanded/Collapsed. Состояние живёт на
// state.additionalTopState (§7), переключается кнопкой в листе (внешний фактор).
@Composable
private fun CaseAdditionalTop(onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val state = rememberXBottomSheetState()
    val dismiss: () -> Unit = { scope.launch { state.hide(); onClose() } }
    LaunchedEffect(Unit) { state.show() }
    val collapsed = state.additionalTopState == AdditionalTopState.Collapsed
    XBottomSheet(
        state = state,
        onDismissRequest = dismiss,
        additionalTop = { AdditionalTopCard(collapsed = collapsed) },
        top = { PresetTitle("Событие") },
        bottom = {
            Preset1Button(
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
    val scope = rememberCoroutineScope()
    val state = rememberXBottomSheetState()
    var query by remember { mutableStateOf("") }
    val dismiss: () -> Unit = { scope.launch { state.hide(); onClose() } }
    LaunchedEffect(Unit) { state.show() }
    val filtered = SPORTS.take(50).filter { sport -> sport.contains(query, ignoreCase = true) }
    XBottomSheet(
        state = state,
        onDismissRequest = dismiss,
        top = {
            PresetTitle("Поиск + клавиатура")
            PresetSearchField(query = query, onQueryChange = { value -> query = value })
        },
    ) {
        filtered.forEachIndexed { index, sport ->
            PresetMenuCell(
                title = sport,
                onClick = dismiss,
                leadingColor = MarkerColors[index % MarkerColors.size],
            )
        }
    }
}

// (l) Гибкость подъёма над клавиатурой: bottomKeyboardBehavior = StayUnderKeyboard. При фокусе на поиске лист
// разворачивается в FullScreen, поиск (top) + список (middle) поднимаются над клавиатурой, а bottom-кнопка
// остаётся прижатой к нижней кромке листа и уходит ПОД клавиатуру (появляется снова при её скрытии).
// Для сравнения: кейс (i) с дефолтным Lift поднимает над клавиатурой ВЕСЬ контент, включая bottom.
@Composable
private fun CaseImeBottomUnderKeyboard(onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val state = rememberXBottomSheetState()
    var query by remember { mutableStateOf("") }
    val dismiss: () -> Unit = { scope.launch { state.hide(); onClose() } }
    LaunchedEffect(Unit) { state.show() }
    val filtered = SPORTS.take(50).filter { sport -> sport.contains(query, ignoreCase = true) }
    XBottomSheet(
        state = state,
        onDismissRequest = dismiss,
        bottomKeyboardBehavior = KeyboardBottomBehavior.StayUnderKeyboard,
        top = {
            PresetTitle("Вид спорта")
            PresetSearchField(query = query, onQueryChange = { value -> query = value })
        },
        bottom = { Preset1Button(text = "Показать все", onClick = dismiss) },
    ) {
        filtered.forEachIndexed { index, sport ->
            PresetMenuCell(
                title = sport,
                onClick = dismiss,
                leadingColor = MarkerColors[index % MarkerColors.size],
            )
        }
    }
}

// (j) Все способы закрытия опциональны: dismissOnOutsideTap=false, dismissOnSwipeDown=false — закрыть можно
// ТОЛЬКО кнопкой (hide()).
@Composable
private fun CaseNoDismiss(onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val state = rememberXBottomSheetState()
    val dismiss: () -> Unit = { scope.launch { state.hide(); onClose() } }
    LaunchedEffect(Unit) { state.show() }
    XBottomSheet(
        state = state,
        onDismissRequest = dismiss,
        dismissOnOutsideTap = false,
        dismissOnSwipeDown = false,
        top = { PresetTitle("Закрытия выключены") },
        bottom = { Preset1Button(text = "Закрыть", onClick = dismiss) },
    ) {
        PresetBodyText("dismissOnOutsideTap = false, dismissOnSwipeDown = false. Тап вне листа и свайп вниз не закрывают — только кнопка.")
    }
}

// (k) DragHandle.Static + рост контента: кнопка в Middle добавляет элементы → onContentRemeasured тянет высоту
// за контентом; при skipCollapsed=true и росте выше экрана срабатывает правило «контент вырос выше экрана» →
// авто-переход в ExpandedFullScreen.
@Composable
private fun CaseStaticGrow(onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val state = rememberXBottomSheetState(skipCollapsed = true)
    var itemCount by remember { mutableStateOf(4) }
    val dismiss: () -> Unit = { scope.launch { state.hide(); onClose() } }
    LaunchedEffect(Unit) { state.show() }
    XBottomSheet(
        state = state,
        onDismissRequest = dismiss,
        dragHandle = DragHandleStyle.Static,
        top = { PresetTitle("Static handle + рост контента") },
    ) {
        Preset1Button(text = "Добавить 10 элементов (сейчас $itemCount)", onClick = { itemCount += 10 })
        repeat(itemCount) { index ->
            PresetMenuCell(
                title = "Элемент ${index + 1}",
                onClick = dismiss,
                leadingColor = MarkerColors[index % MarkerColors.size],
            )
        }
    }
}

// Карточка Additional Top: ФОН слота (не красим составные части листа — это внешний контент). Фон рисуется
// всегда (в Collapsed остаётся видимой полоской peek 20dp), а КОНТЕНТ (тексты) уходит в Alpha 0 — раздельно
// фон/контент, чтобы полоска не была прозрачной. graphicsLayer с alpha — на контент внутри фона.
@Composable
private fun AdditionalTopCard(collapsed: Boolean) {
    val contentAlpha by animateFloatAsState(
        targetValue = if (collapsed) 0f else 1f,
        label = "additionalTopContentAlpha",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .graphicsLayer { alpha = contentAlpha }
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
