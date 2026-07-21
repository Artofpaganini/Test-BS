package com.onexui.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

// Отдельный exported-хост demo со своим LAUNCHER. edge-to-edge + adjustResize (манифест) обязательны, иначе
// WindowInsets.ime = 0 и лист in-tree (без Dialog/окна) не реагирует на клавиатуру.
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
