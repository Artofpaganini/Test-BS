package com.alva.core.navigation.scene

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.SceneStrategy
import com.alva.core.navigation.scene.bottom_sheet.AlvaBottomSheetSceneStrategy

// Урезанная под лабораторию z-order версия rememberAlvaSceneStrategies: из полного набора Alva
// (bottom sheet + dialog + list-detail + two-pane + supporting-pane) портирована ТОЛЬКО bottom-sheet-часть.
// Возвращает список из единственной AlvaBottomSheetSceneStrategy — этого достаточно, чтобы NavDisplay
// поднимал nav3 in-tree лист поверх активной сцены (что и проверяет z-order-тест). Остальные стратегии
// в лабораторию сознательно НЕ тянутся.
@Composable
fun rememberAlvaSceneStrategies(): List<SceneStrategy<NavKey>> {
    val bottomSheetStrategy = remember { AlvaBottomSheetSceneStrategy<NavKey>() }
    return remember(bottomSheetStrategy) {
        listOf(bottomSheetStrategy)
    }
}
