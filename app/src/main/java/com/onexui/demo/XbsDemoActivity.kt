package com.onexui.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

// Отдельный exported-хост demo XBottomSheet со своим LAUNCHER (вторая иконка «XBS Demo»). Изоляция от
// z-order-лабы: ноль правок её Kotlin-кода, только additive-блок в манифесте. edge-to-edge + adjustResize
// (в манифесте) — обязательны, иначе WindowInsets.ime = 0 и лист не реагирует на клавиатуру (лист in-tree,
// без Dialog/окна). Единственный ComposeView через setContent.
class XbsDemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Иначе система кладёт полупрозрачную контрастную подложку на 3-button nav поверх листа (SDK 29+).
        window.isNavigationBarContrastEnforced = false
        setContent {
            XbsDemoTheme {
                XbsDemoScreen()
            }
        }
    }
}
