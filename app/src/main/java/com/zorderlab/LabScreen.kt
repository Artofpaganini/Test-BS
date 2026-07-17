package com.zorderlab

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.alva.core.navigation.scene.bottom_sheet.AlvaBottomSheetSceneStrategy
import com.alva.core.navigation.scene.rememberAlvaSceneStrategies
import com.alva.core.uikit.components.bottom_sheet.AlvaBottomSheetScrim
import com.alva.core.uikit.components.bottom_sheet.AlvaBottomSheetValue

private const val ZORDER_TAG = "ZORDER"

// Все листы лабы теперь в собственном окне (hostInWindow) — второй оверлей во флоу 1/2/4 ложится поверх.
// Цвета различают триггер: фиолетовый — activity (кнопки 1–3), синий — fragment-hosted (кнопка 4),
// красный — отдельный роут кнопки 5. Разные цвета, чтобы на скриншоте наложения отличать слои.
private val SHEET_BACKGROUND_ACTIVITY = Color(0xFF6A1B9A)
private val SHEET_BACKGROUND_FRAGMENT = Color(0xFF1565C0)
private val SHEET_BACKGROUND_WINDOW = Color(0xFFC62828)

// Тема Compose-слоя. Отдельная от View-темы Activity: AlvaBottomSheet читает MaterialTheme.colorScheme,
// поэтому Compose нужен собственный провайдер. Поддержка светлой/тёмной темы через isSystemInDarkTheme.
@Composable
fun LabTheme(content: @Composable () -> Unit) {
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme, content = content)
}

// Корень Compose-слоя Activity. backStack поднят в поле Activity (переживает рекомпозицию, доступен из
// openAlvaBottomSheet() в не-composable контексте). Лист хостит Activity → фиолетовый контент.
@Composable
fun LabRoot(backStack: SnapshotStateList<NavKey>) {
    ZOrderNavDisplay(backStack = backStack) {
        Nav3SheetContent(
            background = SHEET_BACKGROUND_ACTIVITY,
            label = "NAV3 SHEET (windowed)",
            logName = "NAV3 windowed sheet",
            contentDescriptionText = "Nav3 windowed bottom sheet layer, activity-hosted",
        )
    }
}

// Корень Compose-слоя, который хостит сам HostFragment в собственном ComposeView (кнопка 4). Тот же nav3
// (NavDisplay + rememberAlvaSceneStrategies + fragment-scoped backStack), лист — синий, чтобы отличать от
// activity-hosted. onSheetGone дёргается из onDispose контента листа → фрагмент снимает свой ComposeView
// с иерархии, когда лист закрыт (иначе прозрачная база перекрывала бы кнопки).
@Composable
fun FragmentHostedSheetRoot(
    backStack: SnapshotStateList<NavKey>,
    onSheetGone: () -> Unit,
) {
    ZOrderNavDisplay(backStack = backStack) {
        Nav3SheetContent(
            background = SHEET_BACKGROUND_FRAGMENT,
            label = "NAV3 SHEET (fragment-hosted, windowed)",
            logName = "NAV3 fragment-hosted windowed sheet",
            contentDescriptionText = "Nav3 windowed bottom sheet layer, fragment-hosted",
            onGone = onSheetGone,
        )
    }
}

// Общая проводка nav3 для обоих хостов. При size < 2 AlvaBottomSheetSceneStrategy отдаёт null → база
// рендерится дефолтным SinglePaneScene; add(Nav3SheetRoute) поднимает лист как OverlayScene. sheetContent —
// слот контента листа (разный цвет/метка у Activity и Fragment).
@Composable
private fun ZOrderNavDisplay(
    backStack: SnapshotStateList<NavKey>,
    sheetContent: @Composable () -> Unit,
) {
    val sceneStrategies = rememberAlvaSceneStrategies()
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        sceneStrategies = sceneStrategies,
        entryProvider = entryProvider {
            entry<BaseRoute> { _ -> BaseScreenContent() }
            entry<Nav3SheetRoute>(
                metadata = AlvaBottomSheetSceneStrategy.bottomSheet {
                    initialValue = AlvaBottomSheetValue.Expanded
                    scrim = AlvaBottomSheetScrim.Enabled()
                    dismissOnScrimTap = true
                    // Оба листа лабы (activity/фиолетовый и fragment-hosted/синий) хостятся в собственном
                    // окне: второй оверлей флоу 1/2/4 добавлен в WindowManager позже → ложится ПОВЕРХ первого.
                    hostInWindow = true
                },
            ) { _ -> sheetContent() }
            entry<Nav3WindowSheetRoute>(
                metadata = AlvaBottomSheetSceneStrategy.bottomSheet {
                    initialValue = AlvaBottomSheetValue.Expanded
                    scrim = AlvaBottomSheetScrim.Enabled()
                    dismissOnScrimTap = true
                    hostInWindow = true
                },
            ) { _ ->
                Nav3SheetContent(
                    background = SHEET_BACKGROUND_WINDOW,
                    label = "NAV3 SHEET (own window)",
                    logName = "NAV3 window-hosted sheet",
                    contentDescriptionText = "Nav3 bottom sheet layer in its own window",
                )
            }
        },
    )
}

// Прозрачный базовый экран: без фона и без pointer-input, чтобы тачи проваливались сквозь ComposeView
// на нижележащий HostFragment (кнопки View-мира).
@Composable
private fun BaseScreenContent() {
    Box(modifier = Modifier.fillMaxSize())
}

// Контент nav3 in-tree листа. Chrome листа (surface, scrim, drag handle) рисует AlvaBottomSheetScene —
// здесь только контрастный фон + крупная метка, чтобы на скриншоте наложения было видно, лежит ли лист
// поверх Fragment-диалогов или позади. DisposableEffect логирует показ/скрытие слоя; onGone дёргается на
// скрытии (нужен fragment-hosted варианту, чтобы снять свой ComposeView).
@Composable
private fun Nav3SheetContent(
    background: Color,
    label: String,
    logName: String,
    contentDescriptionText: String,
    onGone: () -> Unit = {},
) {
    DisposableEffect(Unit) {
        Log.d(ZORDER_TAG, "$logName shown")
        onDispose {
            Log.d(ZORDER_TAG, "$logName hidden")
            onGone()
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            modifier = Modifier.semantics { contentDescription = contentDescriptionText },
            text = label,
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

// Панель листа для кнопки 8 (голое ComponentDialog-окно, без nav3/AlvaBottomSheet). Прозрачный fillMaxSize
// с цветной нижней панелью высотой panelHeight (выше 320dp BSD → торчит полоса, видно кто кого перекрывает)
// и крупной меткой. DisposableEffect логирует показ/скрытие слоя.
@Composable
fun ResearchSheetPanel(
    background: Color,
    panelHeight: Dp,
    label: String,
    logName: String,
) {
    DisposableEffect(Unit) {
        Log.d(ZORDER_TAG, "$logName shown")
        onDispose { Log.d(ZORDER_TAG, "$logName hidden") }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(panelHeight)
                .background(background),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                modifier = Modifier.semantics { contentDescription = label },
                text = label,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}
