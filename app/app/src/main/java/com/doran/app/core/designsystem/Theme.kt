package com.doran.app.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 임시 값. 그림체 확정 때 바꾼다
private val DoranColors = darkColorScheme(
    background = Color(0xFF07090C),
    surface = Color(0xFF13171C),
    onBackground = Color(0xFFF2F4F7),
    onSurface = Color(0xFFF2F4F7),
    onSurfaceVariant = Color(0xFFA7B0BC),
    primary = Color(0xFFE8B26A),
    onPrimary = Color(0xFF07090C),
)

@Composable
fun DoranTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DoranColors, content = content)
}
