package com.zorderlab

import android.util.Log
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.onexui.bottomsheet.XBottomSheet
import com.onexui.bottomsheet.state.rememberXBottomSheetState

private const val ZORDER_TAG = "ZORDER"

// Оконный хост XBottomSheet НА УРОВНЕ ЛАБЫ. Компонент XBS оконного режима не имеет и не получит (anti-scope
// дизайна), поэтому обёртка живёт здесь, в com.zorderlab. Паттерн портирован из удалённого
// AlvaBottomSheetScene.WindowHostedSheetChrome: full-screen прозрачный Dialog (usePlatformDefaultWidth=false,
// decorFitsSystemWindows=false) + гашение платформенного dim через DialogWindowProvider. Окно диалога
// добавляется в WindowManager позже уже показанного BSD/Dialog → по правилу «одинаковый слой = порядок
// добавления» лист ложится ПОВЕРХ (флоу 1/2/4/5). Компонент XBS не трогается — им управляем напрямую (show/hide).
@Composable
fun WindowedXBottomSheet(
    background: Color,
    label: String,
    logName: String,
    contentDescriptionText: String,
    onClosed: () -> Unit,
) {
    Dialog(
        onDismissRequest = onClosed,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false, // тап по скриму обрабатывает сам XBS
            usePlatformDefaultWidth = false, // MATCH_PARENT вместо платформенной ширины диалога
            decorFitsSystemWindows = false, // edge-to-edge: окно в MATCH_PARENT×MATCH_PARENT
        ),
    ) {
        UndimDialogWindow()
        val sheet = rememberXBottomSheetState()
        LaunchedEffect(Unit) { sheet.show() }
        XBottomSheet(
            state = sheet,
            onDismissRequest = {
                sheet.hide()
                onClosed()
            },
        ) {
            SheetLayer(
                background = background,
                label = label,
                logName = logName,
                contentDescriptionText = contentDescriptionText,
            )
        }
    }
}

// Гасим платформенный dim и оконную анимацию диалога: иначе поверх плавного скрима XBS лёг бы мгновенный
// оконный dim (двоение). Появление рисует сам XBS.
@Composable
private fun UndimDialogWindow() {
    val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect {
        dialogWindow?.setDimAmount(0f)
        dialogWindow?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        dialogWindow?.setWindowAnimations(0)
    }
}

// Контент листа: контрастный фон + крупная метка (на скриншоте наложения видно, какой слой сверху).
// ZORDER-лог показа/скрытия слоя сохранён.
@Composable
private fun SheetLayer(
    background: Color,
    label: String,
    logName: String,
    contentDescriptionText: String,
) {
    DisposableEffect(Unit) {
        Log.d(ZORDER_TAG, "$logName shown")
        onDispose { Log.d(ZORDER_TAG, "$logName hidden") }
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
