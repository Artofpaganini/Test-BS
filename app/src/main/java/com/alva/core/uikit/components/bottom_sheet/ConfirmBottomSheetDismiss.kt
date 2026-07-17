package com.alva.core.uikit.components.bottom_sheet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * Side-effect, регистрирующий runtime-проверку перед изменением состояния sheet'а.
 *
 * Помещай внутрь content-лямбды `AlvaBottomSheet` (или Nav3-роута, обёрнутого
 * `AlvaBottomSheetSceneStrategy.bottomSheet`) — composable получит holder через
 * [LocalAlvaBottomSheetStateHolder] и зарегистрирует [confirm] как новую policy.
 *
 * Лямбда [confirm] вызывается:
 * - при попытке drag/fling в anchor (в первую очередь — `Hidden` со reason
 *   [DismissReason.UserSwipedDown]) — если возвращает `false`, anchor не попадёт
 *   в `DraggableAnchors` и физически недостижим жестом;
 * - перед программным `animateTo`/`snapTo` — если `false`, переход отменяется;
 * - при system back / scrim tap — если `false`, dismiss блокируется.
 *
 * Лямбда захватывает Compose state (`viewModel.hasUnsavedChanges`, формы,
 * валидации) — изменения автоматически переcчитывают filtered anchors через
 * сеттер [AlvaBottomSheetStateHolder.confirmValueChange].
 */
@Composable
fun ConfirmBottomSheetDismiss(
    confirm: (target: AlvaBottomSheetValue, reason: DismissReason?) -> Boolean,
) {
    val holder = LocalAlvaBottomSheetStateHolder.current
    // Контракт: [confirm] читает live snapshot фичи (hasUnsavedChanges и т.п.) и
    // передаётся новой лямбдой на каждую рекомпозицию. Перерегистрация на ключе
    // confirm заставляет сеттер пересчитать filtered anchors — так swipe-guard
    // (наличие/отсутствие Hidden-якоря) остаётся синхронным с наблюдаемым состоянием.
    // Сеттер идемпотентен: updateAnchors не делает ничего, если набор якорей не изменился.
    LaunchedEffect(holder, confirm) {
        holder.confirmValueChange = confirm
    }
}
