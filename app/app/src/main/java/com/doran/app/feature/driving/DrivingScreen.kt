package com.doran.app.feature.driving

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 주행 중에는 음성 버튼(듣는 중에는 정지) 하나와 자막 한 줄만 둔다 */
@Composable
fun DrivingScreen(caption: String, isListening: Boolean, onListen: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        IconButton(
            onClick = onListen,
            modifier = Modifier.size(48.dp).semantics {
                contentDescription = if (isListening) "듣기 멈추기" else "도란 음성 입력"
            },
        ) {
            Text(if (isListening) "■" else "●", fontSize = 24.sp, color = MaterialTheme.colorScheme.primary)
        }
        Text(
            caption.ifBlank { "도란에게 말해 보세요" },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 24.sp,
        )
    }
}
