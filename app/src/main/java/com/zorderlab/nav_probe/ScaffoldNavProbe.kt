package com.zorderlab.nav_probe

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.get
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope

// Компайл-проба фазы 1. Доказывает, что JetBrains navigation3 1.1.1 предоставляет ровно тот API,
// под который написан порт AlvaBottomSheet (см. AlvaBottomSheetScene / AlvaBottomSheetSceneStrategy):
// OverlayScene, Scene, SceneStrategy, SceneStrategyScope.onBack, NavEntry.Content()/contentKey/metadata,
// NavMetadataKey, билдер metadata { put(...) }. Никакой бизнес-логики — только проверка сигнатур для фазы 2.

internal data class ProbeOverlayScene<T : Any>(
    override val key: T,
    override val previousEntries: List<NavEntry<T>>,
    override val overlaidEntries: List<NavEntry<T>>,
    private val entry: NavEntry<T>,
    val onDismiss: () -> Unit,
) : OverlayScene<T> {

    override val entries: List<NavEntry<T>> = listOf(entry)

    override val content: @Composable () -> Unit = {
        entry.Content()
    }
}

internal class ProbeOverlaySceneStrategy<T : Any> : SceneStrategy<T> {

    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        if (entries.size < 2) return null
        val lastEntry = entries.last()
        lastEntry.metadata[ProbeOverlayKey] ?: return null
        @Suppress("UNCHECKED_CAST")
        return ProbeOverlayScene(
            key = lastEntry.contentKey as T,
            previousEntries = entries.dropLast(1),
            overlaidEntries = entries.dropLast(1),
            entry = lastEntry,
            onDismiss = onBack,
        )
    }

    companion object {

        fun overlay(): Map<String, Any> = metadata {
            put(ProbeOverlayKey, Unit)
        }

        object ProbeOverlayKey : NavMetadataKey<Unit>
    }
}
