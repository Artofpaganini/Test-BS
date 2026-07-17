package com.onexui.bottomsheet.presets

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// Пресет поля поиска в top-слоте: одна строка, плейсхолдер, side 16.
@Composable
internal fun PresetSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Поиск",
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = { Text(text = placeholder) },
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
