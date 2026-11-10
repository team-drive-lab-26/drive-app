package com.doran.app.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doran.app.MainUiState
import com.doran.app.core.trip.TripEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 정차 중 디버그 빌드에서만 보이는 시연 패널 */
@Composable
fun AgentDebugPanel(
    ui: MainUiState,
    onSubmit: (String) -> Unit,
    onDemoEvent: (String) -> Unit,
    onDemoRoute: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val time = remember { SimpleDateFormat("HH:mm:ss", Locale.KOREA) }
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val navi = ui.navi
        val naviText = when {
            !navi.isConnected -> "내비 미연결"
            !navi.isRouteActive -> "내비 연결됨 · 경로 없음"
            else -> "경로 ${navi.destinationName} · ${navi.etaMinutes ?: "?"}분 남음"
        }
        Text(
            "음성 ${ui.voice.speaker}/${ui.voice.listener} · $naviText",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("AI 시연: ${ui.caption}", maxLines = 1)
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
        // 에뮬레이터에서는 한글 입력이 번거로워 자주 쓰는 문장을 버튼으로 둔다
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DEMO_UTTERANCES.forEach { utterance ->
                OutlinedButton(onClick = { onSubmit(utterance) }) { Text(utterance) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DEMO_EVENTS.forEach { (event, label) ->
                OutlinedButton(onClick = { onDemoEvent(event) }) { Text(label) }
            }
            OutlinedButton(onClick = onDemoRoute) { Text("강릉 경로") }
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 120.dp)) {
            items(ui.conversation.logs) { log ->
                Text(
                    "${time.format(Date(log.atMillis))}  ${log.text}",
                    fontSize = 14.sp,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val DEMO_UTTERANCES = listOf("근처 맛집 추천해 줘", "응 거기 들르자", "휴게소 들를래", "신나는 노래 틀어줘")

private val DEMO_EVENTS = listOf(
    TripEvent.TRIP_STARTED to "출발",
    TripEvent.ARRIVAL_30M to "도착 30분 전",
    TripEvent.REBOARDED to "재탑승",
    TripEvent.CONTINUOUS_DRIVE to "연속 운전",
)
