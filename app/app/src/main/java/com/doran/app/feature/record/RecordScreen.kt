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
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.heightIn
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.doran.app.core.designsystem.DoranCharacter
import com.doran.app.core.designsystem.DoranMood
import com.doran.app.core.designsystem.DoranPalette
import com.doran.app.core.designsystem.DoranPrimaryButton
import com.doran.app.core.designsystem.DoranSecondaryButton
import com.doran.app.core.designsystem.DoranText
import com.doran.app.core.designsystem.RibbonButton
import com.doran.app.core.designsystem.RoadScene
import com.doran.app.core.designsystem.RoadSide
import com.doran.app.core.designsystem.RoadSign
import com.doran.app.core.media.CapsuleRecorder

/**
 * 목소리 캡슐 녹음과 남긴 사람 고르기. 끝나거나 취소하면 [onClose].
 * 같은 앞길 위, 지금 세운 곳이 바로 앞 길가에 주황 표지판으로 서 있다. 내 차 자리의 도란이 마이크 소리만큼 출렁이고,
 * 둘레를 따라 15초가 채워진다.
 */
@Composable
fun RecordScreen(onClose: () -> Unit, viewModel: RecordViewModel = viewModel(factory = RecordViewModel.Factory)) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(ui.phase) {
        if (ui.phase == RecordPhase.DONE) onClose()
    }
    val progress = (ui.elapsedMs.toFloat() / CapsuleRecorder.MAX_MS).coerceIn(0f, 1f)
    val place = ui.placeName.ifBlank { "여기" }
    val signs = listOf(RoadSign("here", place, "지금 세운 곳", 0.08f, RoadSide.RIGHT, hot = true))

    Box(Modifier.fillMaxSize().background(DoranPalette.Background)) {
        RoadScene(signs = signs, horizon = 0.40f, orbSize = 210.dp) {
            Box(Modifier.fillMaxSize()) {
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
                    modifier = Modifier.fillMaxSize().padding(14.dp),
                )
            }
        }

        Row(Modifier.fillMaxWidth().padding(start = 48.dp, end = 48.dp, top = 32.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                when (ui.phase) {
                    RecordPhase.RECORDING -> Heading("${place}에서 한마디", recordingHint(ui))
                    RecordPhase.CHOOSE_SPEAKER -> {
                        Heading(
                            "누가 남겼어?",
                            "${ui.elapsedMs.coerceAtMost(CapsuleRecorder.MAX_MS) / 1000}초 녹음했어. 고르면 앨범 지도에 그 사람 색 핀으로 남길게.",
                        )
                        Spacer(Modifier.height(32.dp))
                        ChooseSpeaker(ui.family, onChoose = viewModel::chooseSpeaker)
                    }
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

        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 48.dp, end = 48.dp, bottom = 40.dp)) {
            when (ui.phase) {
                RecordPhase.RECORDING -> {
                    DoranPrimaryButton(text = "다 했어", onClick = viewModel::finishRecording, modifier = Modifier.heightIn(min = 76.dp).width(220.dp))
                    // 되돌릴 수 없는 동작은 주 동작과 떨어뜨려 오른쪽 끝에 둔다
                    DoranSecondaryButton(text = "그만둘래", onClick = viewModel::cancel, modifier = Modifier.align(Alignment.CenterEnd))
                }
                RecordPhase.CHOOSE_SPEAKER -> {
                    DoranSecondaryButton(text = "지우기", onClick = viewModel::cancel, modifier = Modifier.align(Alignment.CenterEnd))
                }
                RecordPhase.FAILED -> DoranPrimaryButton(text = "돌아가기", onClick = viewModel::cancel)
                RecordPhase.DONE -> Unit
            }
        }
    }
}

/** 녹음하는 동안 음악·송풍을 줄였다면 왜 조용해졌는지 같이 말한다 */
private fun recordingHint(ui: RecordUiState): String {
    val quieted = when {
        ui.musicPaused && ui.fanLowered -> "목소리 잘 담기게 음악을 멈추고 바람을 줄였어."
        ui.musicPaused -> "목소리 잘 담기게 음악을 잠깐 멈췄어."
        ui.fanLowered -> "목소리 잘 담기게 바람을 줄였어."
        else -> ""
    }
    return "듣고 있어. 15초 안에 말하면 돼. $quieted".trim()
}

@Composable
private fun Heading(title: String, body: String) {
    // 정차 화면과 같은 크기로 2m 떨어진 뒷자리에서도 읽히게 한다
    Text(title, style = DoranText.say, color = DoranPalette.Ink)
    Spacer(Modifier.height(10.dp))
    Text(body, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 24.sp, lineHeight = 34.sp), color = DoranPalette.InkSoft)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChooseSpeaker(family: List<String>, onChoose: (String) -> Unit) {
    // 질문 바로 아래에 크게 둔다. 뒷자리에서 손을 뻗어도 누르기 쉽게 한 줄에 큰 버튼으로 놓는다
    FlowRow(
        Modifier.width(860.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        family.forEach { name -> RibbonButton(name = name, onClick = { onChoose(name) }, large = true) }
    }
}
