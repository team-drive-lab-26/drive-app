package com.doran.app.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class PlaceholderLink(val label: String, val onClick: () -> Unit)

/** 화면 구현 전 경로 연결 확인용 빈 화면 */
@Composable
fun PlaceholderScreen(
    title: String,
    links: List<PlaceholderLink> = emptyList(),
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = title, fontSize = 48.sp, color = MaterialTheme.colorScheme.onBackground)
        links.forEach { link ->
            Button(onClick = link.onClick, modifier = Modifier.height(76.dp)) {
                Text(text = link.label, fontSize = 28.sp)
            }
        }
    }
}
