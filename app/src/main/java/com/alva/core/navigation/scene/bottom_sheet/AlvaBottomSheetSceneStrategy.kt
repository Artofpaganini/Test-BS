package com.alva.core.navigation.scene.bottom_sheet

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.get
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope

/**
 * [SceneStrategy] отображающий записи с metadata [AlvaBottomSheetSceneStrategy.AlvaBottomSheetKey]
 * в виде кастомного modal bottom sheet ([AlvaBottomSheetScene]).
 *
 * Должна регистрироваться **первой** среди overlay-стратегий в [rememberAlvaSceneStrategies],
 * так как modal bottom sheet — наивысший приоритет среди overlay-сцен.
 *
 * @see AlvaBottomSheetSceneStrategy.bottomSheet — DSL фабрика для разметки [NavEntry] как bottom sheet.
 */
class AlvaBottomSheetSceneStrategy<T : Any> : SceneStrategy<T> {

    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        if (entries.size < 2) return null
        val lastEntry = entries.last()
        val sceneConfig = lastEntry.metadata[AlvaBottomSheetKey] ?: return null
        @Suppress("UNCHECKED_CAST")
        return AlvaBottomSheetScene(
            key = lastEntry.contentKey as T,
            previousEntries = entries.dropLast(1),
            overlaidEntries = entries.dropLast(1),
            entry = lastEntry,
            sceneConfig = sceneConfig,
            onBack = onBack,
        )
    }

    companion object {
        /**
         * DSL фабрика для регистрации [NavEntry] как bottom sheet.
         *
         * Пример:
         * ```kotlin
         * entry<PaymentDetailsRoute>(
         *     metadata = AlvaBottomSheetSceneStrategy.bottomSheet {
         *         initialValue = AlvaBottomSheetValue.PartialExpanded(0.5f)
         *         draggable = true
         *         dismissOnScrimTap = false
         *     },
         * ) { PaymentDetailsScreen() }
         * ```
         */
        fun bottomSheet(
            receiver: AlvaBottomSheetSceneConfigBuilder.() -> Unit = {},
        ): Map<String, Any> = metadata {
            put(AlvaBottomSheetKey, alvaBottomSheetSceneConfig(receiver))
        }

        object AlvaBottomSheetKey : NavMetadataKey<AlvaBottomSheetSceneConfig>
    }
}
