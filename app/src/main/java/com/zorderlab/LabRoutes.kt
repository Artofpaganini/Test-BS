package com.zorderlab

import com.alva.core.navigation.route.NavRoute

// Базовый экран под nav3-листом. Держит бэкстек непустым (иначе NavDisplay падает) и рендерится
// через дефолтный SinglePaneScene. Сам по себе прозрачный: сквозь ComposeView виден нижележащий
// FragmentContainerView с кнопками HostFragment — оба живут в одном окне Activity.
data object BaseRoute : NavRoute

// Роут nav3 in-tree bottom sheet. В entryProvider помечается metadata AlvaBottomSheetSceneStrategy
// .bottomSheet → NavDisplay поднимает его как OverlayScene (порт AlvaBottomSheet) внутри окна Activity.
data object Nav3SheetRoute : NavRoute

// Роут листа в СОБСТВЕННОМ окне: тот же bottomSheet-entry, но с hostInWindow = true → scene оборачивает
// chrome в compose Dialog (отдельное top-level окно, слой BSD) → лист ложится поверх Fragment-диалогов.
data object Nav3WindowSheetRoute : NavRoute
