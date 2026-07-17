package com.onexui.bottomsheet.presets

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight

// Пресет заголовка листа: side 16, top 20 (дизайнерская рекомендация §9).
@Composable
internal fun PresetTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = PresetSpacing.Horizontal,
                end = PresetSpacing.Horizontal,
                top = PresetSpacing.Top,
                bottom = PresetSpacing.Bottom,
            ),
    )
}
