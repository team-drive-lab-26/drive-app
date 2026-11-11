package com.doran.app.feature.record

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.doran.app.core.designsystem.DoranCharacter
import com.doran.app.core.designsystem.DoranMood
import com.doran.app.core.designsystem.DoranPalette
import com.doran.app.core.designsystem.DoranPrimaryButton
import com.doran.app.core.designsystem.DoranSecondaryButton
import com.doran.app.core.designsystem.DoranText
import com.doran.app.core.designsystem.RibbonButton
import com.doran.app.core.media.CapsuleRecorder

/**
 * 목소리 캡슐 녹음과 남긴 사람 고르기. 끝나거나 취소하면 [onClose].
 * 가운데 도란이 마이크 소리만큼 출렁이고, 둘레를 따라 15초가 채워진다.
 */
@Composable
fun RecordScreen(onClose: () -> Unit, viewModel: RecordViewModel = viewModel(factory = RecordViewModel.Factory)) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(ui.phase) {
        if (ui.phase == RecordPhase.DONE) onClose()
    }
    val progress = (ui.elapsedMs.toFloat() / CapsuleRecorder.MAX_MS).coerceIn(0f, 1f)

    Box(
        Modifier
            .fillMaxSize()
            .background(DoranPalette.Background)
            .padding(horizontal = 56.dp, vertical = 44.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                when (ui.phase) {
                    RecordPhase.RECORDING -> Heading(
                        if (ui.placeName.isBlank()) "한마디 남겨 줘" else "${ui.placeName}에서 한마디",
                        "듣고 있어. 15초 안에 말하면 돼.",
                    )
                    RecordPhase.CHOOSE_SPEAKER -> Heading(
                        "누가 남겼어?",
                        "${ui.elapsedMs.coerceAtMost(CapsuleRecorder.MAX_MS) / 1000}초 녹음했어. 고르면 앨범 지도에 그 사람 색 핀으로 남길게.",
                    )
                    RecordPhase.FAILED -> Heading("녹음하지 못했어", "마이크 권한을 확인하거나 조금 더 길게 말해 줘.")
                    RecordPhase.DONE -> Unit
                }
            }
            if (ui.phase == RecordPhase.RECORDING) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${ui.remainingSeconds}", style = DoranText.countdown, color = DoranPalette.Ink)
                    Text("초", style = MaterialTheme.typography.headlineMedium, color = DoranPalette.InkSoft, modifier = Modifier.padding(bottom = 16.dp, start = 6.dp))
                }
            }
        }

        Box(Modifier.align(Alignment.Center).size(320.dp)) {
            // 15초 둘레. 지난 시간만큼 주황으로 찬다
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 6.dp.toPx()
                val inset = stroke / 2f
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(DoranPalette.Line, 0f, 360f, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
                drawArc(DoranPalette.Accent, -90f, 360f * progress, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            DoranCharacter(
                mood = when (ui.phase) {
                    RecordPhase.RECORDING -> DoranMood.LISTENING
                    RecordPhase.CHOOSE_SPEAKER -> DoranMood.HAPPY
                    else -> DoranMood.IDLE
                },
                level = ui.level,
                modifier = Modifier.fillMaxSize().padding(24.dp),
            )
        }

        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
            when (ui.phase) {
                RecordPhase.RECORDING -> {
                    DoranPrimaryButton(text = "다 했어", onClick = viewModel::finishRecording)
                    // 되돌릴 수 없는 동작은 주 동작과 떨어뜨려 오른쪽 끝에 둔다
                    DoranSecondaryButton(text = "그만둘래", onClick = viewModel::cancel, modifier = Modifier.align(Alignment.CenterEnd))
                }
                RecordPhase.CHOOSE_SPEAKER -> {
                    ChooseSpeaker(ui.family, onChoose = viewModel::chooseSpeaker)
                    DoranSecondaryButton(text = "지우기", onClick = viewModel::cancel, modifier = Modifier.align(Alignment.CenterEnd))
                }
                RecordPhase.FAILED -> DoranPrimaryButton(text = "돌아가기", onClick = viewModel::cancel)
                RecordPhase.DONE -> Unit
            }
        }
    }
}

@Composable
private fun Heading(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.headlineLarge, color = DoranPalette.Ink)
    Spacer(Modifier.height(8.dp))
    Text(body, style = MaterialTheme.typography.bodyLarge, color = DoranPalette.InkSoft)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChooseSpeaker(family: List<String>, onChoose: (String) -> Unit) {
    FlowRow(
        Modifier.width(820.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        family.forEach { name -> RibbonButton(name = name, onClick = { onChoose(name) }) }
    }
}
