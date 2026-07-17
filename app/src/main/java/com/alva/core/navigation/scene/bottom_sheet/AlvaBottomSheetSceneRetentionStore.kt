package com.alva.core.navigation.scene.bottom_sheet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import com.alva.core.uikit.components.bottom_sheet.AlvaBottomSheetValue

// In-memory store анкеров листов overlay-сцен, поднятый НАД NavDisplay. Ключ — contentKey записи листа,
// значение — последний осевший анкер. Store живёт СНАРУЖИ overlay-контента, поэтому переживает dispose
// листа при forward-nav (пуш экрана поверх) и отдаёт анкер обратно на возврате назад — snap без appear.
// Ключевое отличие от прежнего SaveableStateHolder-подхода: запись анкера EAGER (по изменению значения),
// а не ленивое сохранение на dispose. Прежний подход зависел от того, успеет ли nav3 захватить состояние
// chrome в момент teardown overlay (AnimatedContent exit + scene removeState + movableContent) — не успевал,
// поэтому анкер терялся. Eager-запись делает retention детерминированной. Process death тут НЕ покрываем
// (in-memory): его обслуживает rememberSaveable-Saver самого holder'а листа.
@Suppress("MatchingDeclarationName")
@Stable
class AlvaBottomSheetSceneRetentionStore internal constructor(
    private val anchorsByKey: SnapshotStateMap<Any, AlvaBottomSheetValue>,
) {
    // Сохранённый анкер записи с данным contentKey, либо null (свежее открытие / после eviction).
    fun retainedAnchor(contentKey: Any): AlvaBottomSheetValue? = anchorsByKey[contentKey]

    // Записать текущий осевший анкер листа. Hidden не пишем: закрытие чистится eviction на финальном pop.
    fun storeAnchor(contentKey: Any, anchor: AlvaBottomSheetValue) {
        anchorsByKey[contentKey] = anchor
    }

    // Стереть анкер — на окончательном pop записи листа (лист убран из back stack), чтобы следующее
    // СВЕЖЕЕ открытие того же route проигрывало appear, а не восстанавливалось на старом анкере.
    fun evictAnchor(contentKey: Any) {
        anchorsByKey.remove(contentKey)
    }
}

val LocalAlvaBottomSheetSceneRetention: ProvidableCompositionLocal<AlvaBottomSheetSceneRetentionStore?> =
    staticCompositionLocalOf { null }

@Composable
fun rememberAlvaBottomSheetSceneRetentionStore(): AlvaBottomSheetSceneRetentionStore {
    val anchorsByKey: SnapshotStateMap<Any, AlvaBottomSheetValue> = remember { mutableStateMapOf() }
    return remember(anchorsByKey) { AlvaBottomSheetSceneRetentionStore(anchorsByKey) }
}

// Декоратор в цепочке entryDecorators того же NavDisplay: на окончательном pop записи стирает её анкер
// из store. При null store (сайт retention не подключил) — прозрачный pass-through без побочных эффектов.
@Composable
fun <T : NavKey> rememberAlvaBottomSheetSceneRetentionEvictionDecorator(
    store: AlvaBottomSheetSceneRetentionStore?,
): NavEntryDecorator<T> = remember(store) {
    NavEntryDecorator(
        onPop = { contentKey -> store?.evictAnchor(contentKey) },
    ) { entry: NavEntry<T> ->
        entry.Content()
    }
}
