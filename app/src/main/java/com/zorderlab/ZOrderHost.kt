package com.zorderlab

// Контракт Activity для View-мира. HostFragment поднимает nav3 in-tree лист, кастуя requireActivity()
// к этому интерфейсу (связь fragment→activity без event bus).
interface ZOrderHost {

    // Открывает nav3-лист (пуш Nav3SheetRoute в бэкстек). Сценарии кнопок 1 и 2 — фрагмент зовёт напрямую.
    fun openAlvaBottomSheet()

    // Сценарий кнопки 3: Activity открывает лист и САМА заводит таймер, через 5с пиная фрагмент поднять
    // BottomSheetDialogFragment поверх листа (связь activity→fragment через SheetOverlayDialogRaiser).
    fun openSheetThenRaiseDialog()

    // Сценарий кнопки 5: пуш Nav3WindowSheetRoute (hostInWindow) → лист в собственном compose Dialog-окне.
    fun openWindowHostedSheet()
}

// Контракт HostFragment. Activity пинает фрагмент поднять BottomSheetDialogFragment поверх nav3-листа
// (направление activity→fragment, сценарий кнопки 3). Диалог поднимает именно фрагмент.
interface SheetOverlayDialogRaiser {

    fun raiseDialogOverSheet()
}
