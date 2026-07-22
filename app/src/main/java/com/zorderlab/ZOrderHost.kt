package com.zorderlab

// Контракт Activity для View-мира. HostFragment открывает лист XBS (в собственном окне), кастуя
// requireActivity() к этому интерфейсу (связь fragment→activity без event bus).
interface ZOrderHost {

    // Открывает activity-hosted XBS-лист (фиолетовый) в собственном окне. Сценарии кнопок 1 и 2.
    fun openActivitySheet()

    // Сценарий кнопки 3: Activity открывает лист и САМА заводит таймер, через 5с пиная фрагмент поднять
    // BottomSheetDialogFragment поверх листа (связь activity→fragment через SheetOverlayDialogRaiser).
    fun openSheetThenRaiseDialog()

    // Сценарий кнопки 5: XBS-лист (красный) в собственном окне — тот же windowed-хелпер, другой цвет.
    fun openWindowSheet()
}

// Контракт HostFragment. Activity пинает фрагмент поднять BottomSheetDialogFragment поверх XBS-листа
// (направление activity→fragment, сценарий кнопки 3). Диалог поднимает именно фрагмент.
interface SheetOverlayDialogRaiser {

    fun raiseDialogOverSheet()
}
