package com.doran.app.feature.record

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.doran.app.core.designsystem.DoranCharacter
import com.doran.app.core.designsystem.DoranMood
import com.doran.app.core.designsystem.DoranPalette
import com.doran.app.core.designsystem.DoranPrimaryButton
import com.doran.app.core.designsystem.DoranSecondaryButton
import com.doran.app.core.media.CapsuleRecorder

/** 목소리 캡슐 녹음과 남긴 사람 고르기. 끝나거나 취소하면 [onClose] */
@Composable
fun RecordScreen(onClose: () -> Unit, viewModel: RecordViewModel = viewModel(factory = RecordViewModel.Factory)) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(ui.phase) {
        if (ui.phase == RecordPhase.DONE) onClose()
    }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(DoranPalette.Background)
            .padding(horizontal = 64.dp, vertical = 40.dp),
    ) {
        val height = maxHeight
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(0.9f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                DoranCharacter(
                    mood = when (ui.phase) {
                        RecordPhase.RECORDING -> DoranMood.LISTENING
                        RecordPhase.CHOOSE_SPEAKER -> DoranMood.HAPPY
                        else -> DoranMood.IDLE
                    },
                    level = ui.level,
                    modifier = Modifier.size(minOf(height * 0.62f, 400.dp)),
                )
            }
            Column(Modifier.weight(1.1f), verticalArrangement = Arrangement.Center) {
                when (ui.phase) {
                    RecordPhase.RECORDING -> Recording(ui, onFinish = viewModel::finishRecording, onCancel = viewModel::cancel)
                    RecordPhase.CHOOSE_SPEAKER -> ChooseSpeaker(ui, onChoose = viewModel::chooseSpeaker, onCancel = viewModel::cancel)
                    RecordPhase.FAILED -> Failed(onClose = viewModel::cancel)
                    RecordPhase.DONE -> Unit
                }
            }
        }
    }
}

@Composable
private fun Recording(ui: RecordUiState, onFinish: () -> Unit, onCancel: () -> Unit) {
    val title = if (ui.placeName.isBlank()) "한마디 남겨 줘" else "${ui.placeName}에서 한마디"
    Text(title, style = MaterialTheme.typography.headlineMedium, color = DoranPalette.Ink)
    Spacer(Modifier.height(8.dp))
    Text("듣고 있어. 15초 안에 말하면 돼.", style = MaterialTheme.typography.bodyLarge, color = DoranPalette.InkSoft)
    Spacer(Modifier.height(40.dp))
    Text("${ui.remainingSeconds}", style = MaterialTheme.typography.displayMedium.copy(fontSize = MaterialTheme.typography.displayMedium.fontSize * 2.4f), color = DoranPalette.Ink)
    Spacer(Modifier.height(16.dp))
    val progress by animateFloatAsState((ui.elapsedMs.toFloat() / CapsuleRecorder.MAX_MS).coerceIn(0f, 1f), label = "progress")
    Box(Modifier.fillMaxWidth(0.8f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(DoranPalette.Line)) {
        Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(DoranPalette.Body))
    }
    Spacer(Modifier.height(40.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        DoranPrimaryButton(text = "다 했어", onClick = onFinish)
        DoranSecondaryButton(text = "그만둘래", onClick = onCancel)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChooseSpeaker(ui: RecordUiState, onChoose: (String) -> Unit, onCancel: () -> Unit) {
    Text("누가 남겼어?", style = MaterialTheme.typography.headlineMedium, color = DoranPalette.Ink)
    Spacer(Modifier.height(8.dp))
    Text("${ui.elapsedMs.coerceAtMost(CapsuleRecorder.MAX_MS) / 1000}초 녹음했어. 고르면 앨범에 저장할게.", style = MaterialTheme.typography.bodyLarge, color = DoranPalette.InkSoft)
    Spacer(Modifier.height(32.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ui.family.forEach { name -> DoranSecondaryButton(text = name, onClick = { onChoose(name) }) }
    }
    Spacer(Modifier.height(32.dp))
    DoranSecondaryButton(text = "지우기", onClick = onCancel)
}

@Composable
private fun Failed(onClose: () -> Unit) {
    Text("녹음하지 못했어", style = MaterialTheme.typography.headlineMedium, color = DoranPalette.Ink)
    Spacer(Modifier.height(8.dp))
    Text("마이크 권한을 확인하거나 조금 더 길게 말해 줘.", style = MaterialTheme.typography.bodyLarge, color = DoranPalette.InkSoft)
    Spacer(Modifier.height(32.dp))
    DoranPrimaryButton(text = "돌아가기", onClick = onClose)
}
