package com.onexui.bottomsheet.presets

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Пресет одиночной кнопки внизу листа (bottom-слот): на всю ширину, side 16, низ 8.
@Composable
internal fun PresetSingleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = PresetSpacing.Horizontal,
                end = PresetSpacing.Horizontal,
                top = PresetSpacing.Bottom,
                bottom = PresetSpacing.Bottom,
            )
            .height(ButtonHeight),
    ) {
        Text(text = text)
    }
}

private val ButtonHeight = 48.dp
