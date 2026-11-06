package com.doran.app.ui.record

import androidx.compose.runtime.Composable
import com.doran.app.ui.components.PlaceholderLink
import com.doran.app.ui.components.PlaceholderScreen

@Composable
fun SpeakerSelectScreen(
    onDone: () -> Unit,
) {
    PlaceholderScreen(
        title = "누가 말했나요?",
        links = listOf(
            PlaceholderLink("저장", onDone),
        ),
    )
}
