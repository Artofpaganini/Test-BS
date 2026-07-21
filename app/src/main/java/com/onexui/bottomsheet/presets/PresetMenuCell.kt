package com.onexui.bottomsheet.presets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun PresetMenuCell(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingColor: Color? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(CellHeight)
            .clickable(onClick = onClick)
            .padding(horizontal = PresetSpacing.Horizontal),
    ) {
        if (leadingColor != null) {
            Box(
                modifier = Modifier
                    .size(MarkerSize)
                    .background(color = leadingColor, shape = CircleShape),
            )
            Box(modifier = Modifier.size(width = PresetSpacing.ItemGap, height = 0.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private val CellHeight = 52.dp
private val MarkerSize = 20.dp
