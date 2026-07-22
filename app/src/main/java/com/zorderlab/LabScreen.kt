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

private const val ZORDER_TAG = "ZORDER"

// Цвета листов лабы: фиолетовый — activity-hosted (кнопки 1–3), синий — fragment-hosted (кнопка 4),
// красный — own-window (кнопка 5). Разные цвета, чтобы на скриншоте наложения отличать слои.
private val SHEET_BACKGROUND_ACTIVITY = Color(0xFF6A1B9A)
private val SHEET_BACKGROUND_FRAGMENT = Color(0xFF1565C0)
private val SHEET_BACKGROUND_WINDOW = Color(0xFFC62828)

// Тема Compose-слоя. Отдельная от View-темы Activity: XBottomSheet читает MaterialTheme.colorScheme, поэтому
// Compose нужен собственный провайдер. Поддержка светлой/тёмной темы через isSystemInDarkTheme.
@Composable
fun LabTheme(content: @Composable () -> Unit) {
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme, content = content)
}

// Корень Compose-слоя Activity: прозрачная база (тачи проваливаются на кнопки HostFragment) + оверлей-лист XBS
// в собственном окне (WindowedXBottomSheet), когда активен. Лист управляется напрямую (show/hide в хелпере), без
// nav3. activeSheet поднят в поле Activity (доступен из openActivitySheet() в не-composable контексте).
@Composable
fun LabRoot(activeSheet: LabSheetKind?, onSheetClosed: () -> Unit) {
    BaseScreenContent()
    when (activeSheet) {
        LabSheetKind.ACTIVITY -> WindowedXBottomSheet(
            background = SHEET_BACKGROUND_ACTIVITY,
            label = "XBS SHEET (windowed)",
            logName = "XBS windowed sheet",
            contentDescriptionText = "XBS windowed bottom sheet layer, activity-hosted",
            onClosed = onSheetClosed,
        )
        LabSheetKind.WINDOW -> WindowedXBottomSheet(
            background = SHEET_BACKGROUND_WINDOW,
            label = "XBS SHEET (own window)",
            logName = "XBS window-hosted sheet",
            contentDescriptionText = "XBS bottom sheet layer in its own window",
            onClosed = onSheetClosed,
        )
        null -> Unit
    }
}

// Корень Compose-слоя, который хостит сам HostFragment в собственном ComposeView (кнопка 4). Тот же
// WindowedXBottomSheet, но синий, чтобы отличать от activity-hosted. onSheetGone дёргается на закрытии листа →
// фрагмент снимает свой ComposeView с иерархии (иначе прозрачная база перекрывала бы кнопки).
@Composable
fun FragmentHostedSheetRoot(onSheetGone: () -> Unit) {
    WindowedXBottomSheet(
        background = SHEET_BACKGROUND_FRAGMENT,
        label = "XBS SHEET (fragment-hosted, windowed)",
        logName = "XBS fragment-hosted windowed sheet",
        contentDescriptionText = "XBS windowed bottom sheet layer, fragment-hosted",
        onClosed = onSheetGone,
    )
}

// Прозрачный базовый экран: без фона и без pointer-input, чтобы тачи проваливались сквозь ComposeView на
// нижележащий HostFragment (кнопки View-мира).
@Composable
private fun BaseScreenContent() {
    Box(modifier = Modifier.fillMaxSize())
}

// Панель листа для кнопки 8 (голое ComponentDialog-окно, без XBS). Прозрачный fillMaxSize с цветной нижней
// панелью высотой panelHeight (выше 320dp BSD → торчит полоса, видно кто кого перекрывает) и крупной меткой.
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
