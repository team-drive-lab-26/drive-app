package com.doran.app.feature.record

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.doran.app.core.designsystem.DoranCharacter
import com.doran.app.core.designsystem.DoranMood
import com.doran.app.core.designsystem.DoranPalette
import com.doran.app.core.designsystem.DoranPrimaryButton
import com.doran.app.core.designsystem.DoranSecondaryButton
import com.doran.app.core.designsystem.DoranText
import com.doran.app.core.designsystem.RecordTape
import com.doran.app.core.designsystem.RibbonButton
import com.doran.app.core.designsystem.doranFeetOffset
import com.doran.app.core.media.CapsuleRecorder

// 마이크 크기를 이 간격으로 적어 테이프에 파형을 쓴다
private const val SAMPLE_MS = 100L
private const val CAPACITY = (CapsuleRecorder.MAX_MS / SAMPLE_MS).toInt()

/**
 * 목소리 캡슐 녹음과 남긴 사람 고르기. 끝나거나 취소하면 [onClose].
 * 화면을 가로지르는 테이프에 목소리가 파형으로 적히고, 도란이 쓰기 헤드 위에 앉아 함께 움직인다.
 */
@Composable
fun RecordScreen(onClose: () -> Unit, viewModel: RecordViewModel = viewModel(factory = RecordViewModel.Factory)) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(ui.phase) {
        if (ui.phase == RecordPhase.DONE) onClose()
    }
    val levels = remember { mutableStateListOf<Float>() }
    // 느린 기기에서도 테이프 길이가 실제 시간과 맞게, 지난 시간만큼 칸을 채운다
    LaunchedEffect(ui.elapsedMs) {
        if (ui.phase != RecordPhase.RECORDING) return@LaunchedEffect
        val due = (ui.elapsedMs / SAMPLE_MS).toInt().coerceAtMost(CAPACITY)
        while (levels.size < due) levels += ui.level
    }
    val progress = (ui.elapsedMs.toFloat() / CapsuleRecorder.MAX_MS).coerceIn(0f, 1f)

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(DoranPalette.Background),
    ) {
        val band = 220.dp
        val bandTop = maxHeight * 0.50f
        val doran = minOf(maxHeight * 0.30f, 250.dp)
        val inset = 40.dp
        val headX = inset + (maxWidth - inset * 2) * progress

        Row(
            Modifier.fillMaxWidth().padding(start = 56.dp, end = 56.dp, top = 48.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                when (ui.phase) {
                    RecordPhase.RECORDING -> Heading(
                        if (ui.placeName.isBlank()) "한마디 남겨 줘" else "${ui.placeName}에서 한마디",
                        "듣고 있어. 15초 안에 말하면 돼.",
                    )
                    RecordPhase.CHOOSE_SPEAKER -> Heading(
                        "누가 남겼어?",
                        "${ui.elapsedMs.coerceAtMost(CapsuleRecorder.MAX_MS) / 1000}초 녹음했어. 고르면 그 사람 색으로 테이프에 남길게.",
                    )
                    RecordPhase.FAILED -> Heading("녹음하지 못했어", "마이크 권한을 확인하거나 조금 더 길게 말해 줘.")
                    RecordPhase.DONE -> Unit
                }
            }
            if (ui.phase == RecordPhase.RECORDING) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${ui.remainingSeconds}", style = DoranText.countdown, color = DoranPalette.Ink)
                    Text(
                        "초",
                        style = MaterialTheme.typography.headlineMedium,
                        color = DoranPalette.InkSoft,
                        modifier = Modifier.padding(bottom = 18.dp, start = 6.dp),
                    )
                }
            }
        }

        RecordTape(
            levels = levels,
            capacity = CAPACITY,
            progress = progress,
            color = DoranPalette.Head,
            bandHeight = band,
            modifier = Modifier.offset(y = bandTop),
        )
        Box(Modifier.offset(x = (headX - doran / 2).coerceIn(0.dp, maxWidth - doran), y = bandTop - doranFeetOffset(doran))) {
            DoranCharacter(
                mood = when (ui.phase) {
                    RecordPhase.RECORDING -> DoranMood.LISTENING
                    RecordPhase.CHOOSE_SPEAKER -> DoranMood.HAPPY
                    else -> DoranMood.IDLE
                },
                level = ui.level,
                modifier = Modifier.size(doran),
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                .offset(y = bandTop + band + 36.dp)
                .padding(horizontal = 56.dp),
        ) {
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
