package com.doran.app.ui.record

import androidx.compose.runtime.Composable
import com.doran.app.ui.components.PlaceholderLink
import com.doran.app.ui.components.PlaceholderScreen

@Composable
fun RecordScreen(
    onRecorded: () -> Unit,
) {
    PlaceholderScreen(
        title = "한마디 남길래?",
        links = listOf(
            PlaceholderLink("녹음 끝", onRecorded),
        ),
    )
}
