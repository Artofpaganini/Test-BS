package com.alva.core.navigation.scene.bottom_sheet

import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.rememberLifecycleOwner
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.OverlayScene
import com.alva.core.uikit.components.bottom_sheet.AlvaBottomSheet
import com.alva.core.uikit.components.bottom_sheet.AlvaBottomSheetValue
import com.alva.core.uikit.components.bottom_sheet.config.rememberAlvaBottomSheetConfig
import com.alva.core.uikit.components.bottom_sheet.rememberRetainedAlvaBottomSheetStateHolder

internal data class AlvaBottomSheetScene<T : Any>(
    override val key: T,
    override val previousEntries: List<NavEntry<T>>,
    override val overlaidEntries: List<NavEntry<T>>,
    private val entry: NavEntry<T>,
    private val sceneConfig: AlvaBottomSheetSceneConfig,
    private val onBack: () -> Unit,
) : OverlayScene<T> {

    override val entries: List<NavEntry<T>> = listOf(entry)

    override val content: @Composable () -> Unit = {
        // Кейуем по стабильному contentKey записи: entryProvider пересоздаёт sceneConfig каждый кадр
        // (не data class), поэтому key(sceneConfig) пересоздавал бы holder и рестартовал анимации.
        // contentKey стабилен всё время жизни BS.
        key(entry.contentKey) {
            when (sceneConfig.hostInWindow) {
                true -> WindowHostedSheetChrome()
                false -> SheetChrome()
            }
        }
    }

    // Chrome в собственном окне. Dialog = реальный ComponentDialog-Window (TYPE_APPLICATION),
    // добавляется в WindowManager позже окна любого уже показанного Fragment-диалога и по правилу
    // «одинаковый слой → порядок добавления» ложится ПОВЕРХ него.
    @Composable
    private fun WindowHostedSheetChrome() {
        Dialog(
            // Fallback-путь back: штатно back первым перехватывает NavigationBackHandler самого
            // AlvaBottomSheet — внутри окна он регистрируется в диспетчер ДИАЛОГА (view-tree fallback
            // LocalNavigationEventDispatcherOwner) ПОЗЖЕ always-enabled колбэка DialogWrapper и по LIFO
            // выигрывает → сохраняется анимация скрытия. Сюда попадём только если лист back не взял.
            onDismissRequest = { onBack() },
            properties = DialogProperties(
                dismissOnBackPress = true,
                // Окно фуллскрин: «снаружи» не существует, тап по скриму обрабатывает сам лист.
                dismissOnClickOutside = false,
                // MATCH_PARENT по ширине вместо платформенной ширины диалога.
                usePlatformDefaultWidth = false,
                // Edge-to-edge: fitInsets(0), FLAG_LAYOUT_IN_SCREEN, cutout ALWAYS; вместе с
                // fillMaxSize-контентом DialogLayout переключит окно в MATCH_PARENT×MATCH_PARENT.
                decorFitsSystemWindows = false,
            ),
        ) {
            UndimDialogWindow()
            SheetChrome()
        }
    }

    // Гасим платформенный dim и оконную анимацию: тема FloatingDialogWindowTheme (decorFits=false)
    // принудительно включает backgroundDimEnabled=true → без этого скрим двоится (мгновенный оконный
    // dim поверх плавного AlvaBottomSheetScrimLayer). Появление рисует appear-анимация самого листа.
    @Composable
    private fun UndimDialogWindow() {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.setDimAmount(0f)
            dialogWindow?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            dialogWindow?.setWindowAnimations(0)
        }
    }

    @Composable
    private fun SheetChrome() {
        val retentionStore = LocalAlvaBottomSheetSceneRetention.current
        // Читаем сохранённый анкер на ПЕРВОЙ композиции chrome. На возврате forward-back это свежая
        // композиция overlay-сцены → store уже содержит анкер → holder стартует restored (snap, без appear).
        // Свежее открытие — store пуст → retainedAnchor == null → holder проигрывает appear на initialValue.
        // withoutReadObservation: значение нужно лишь как seed для holder'а (rememberSaveable закеширует
        // на первой композиции), поэтому НЕ подписываемся на SnapshotStateMap — иначе eager-запись анкера
        // рекомпозила бы chrome на каждом settle.
        val retainedAnchor: AlvaBottomSheetValue? = Snapshot.withoutReadObservation {
            retentionStore?.retainedAnchor(entry.contentKey)
        }
        val visualConfig = rememberAlvaBottomSheetConfig {
            sceneConfig.shape?.let { sceneShape -> shape = sceneShape }
            if (sceneConfig.containerColor != Color.Unspecified) containerColor = sceneConfig.containerColor
            scrim = sceneConfig.scrim
            draggable = sceneConfig.draggable
            dismissOnScrimTap = sceneConfig.dismissOnScrimTap
        }
        val holder = rememberRetainedAlvaBottomSheetStateHolder(
            initial = sceneConfig.initialValue,
            anchors = sceneConfig.anchors,
            animationSpec = sceneConfig.animationSpec,
            retainedValue = retainedAnchor,
        )
        // Eager-запись анкера в store по каждому изменению осевшего значения: store всегда актуален к моменту
        // forward-nav, не зависим от того, захватит ли nav3 состояние chrome при teardown overlay. Hidden
        // пропускаем — закрытие чистится eviction'ом на финальном pop.
        when (retentionStore) {
            null -> Unit
            else -> LaunchedEffect(holder, retentionStore) {
                snapshotFlow { holder.currentValue }.collect { anchor ->
                    when (anchor) {
                        is AlvaBottomSheetValue.Hidden -> Unit
                        else -> retentionStore.storeAnchor(entry.contentKey, anchor)
                    }
                }
            }
        }
        val lifecycleOwner = rememberLifecycleOwner()
        AlvaBottomSheet(
            state = holder,
            onDismissRequest = { onBack() },
            config = visualConfig,
        ) {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                entry.Content()
            }
        }
    }
}
