package com.doran.app.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AgentDebugPanel(caption: String, onSubmit: (String) -> Unit, onDemoEvent: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("AI 시연: $caption", maxLines = 1)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("도란에게 말하기") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { if (text.isNotBlank()) { onSubmit(text); text = "" } }) {
                Text("전송")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onDemoEvent("trip_started") }) { Text("출발 시연") }
            Button(onClick = { onDemoEvent("continuous_drive") }) { Text("휴식 시연") }
        }
    }
}
