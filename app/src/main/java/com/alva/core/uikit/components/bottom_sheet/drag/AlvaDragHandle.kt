package com.alva.core.uikit.components.bottom_sheet.drag

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private val TOUCH_TARGET_SIZE = 48.dp
private val PILL_WIDTH = 32.dp
private val PILL_HEIGHT = 4.dp
private const val PILL_ALPHA = 0.4f
private const val PILL_DESCRIPTION = "Drag to resize"

@Composable
fun AlvaDragHandle(modifier: Modifier = Modifier) {
    val pillColor = MaterialTheme.colorScheme.outline.copy(alpha = PILL_ALPHA)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(TOUCH_TARGET_SIZE)
            .semantics {
                contentDescription = PILL_DESCRIPTION
                role = Role.Button
            },
    ) {
        Box(
            modifier = Modifier
                .size(width = PILL_WIDTH, height = PILL_HEIGHT)
                .background(color = pillColor, shape = CircleShape),
        )
    }
}
