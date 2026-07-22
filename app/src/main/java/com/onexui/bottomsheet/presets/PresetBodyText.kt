package com.onexui.bottomsheet.presets

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Пресет: абзац второстепеного текста в теле листа (отступы из PresetSpacing). */
@Composable
internal fun PresetBodyText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = PresetSpacing.Horizontal,
                end = PresetSpacing.Horizontal,
                top = PresetSpacing.Bottom,
                bottom = PresetSpacing.Bottom,
            ),
    )
}
