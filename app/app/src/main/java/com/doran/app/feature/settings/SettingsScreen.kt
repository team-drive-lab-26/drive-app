package com.doran.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.doran.app.BuildConfig
import com.doran.app.DoranApplication
import com.doran.app.core.data.AppPreferences
import com.doran.app.core.designsystem.DoranPalette
import com.doran.app.core.designsystem.DoranSecondaryButton
import com.doran.app.core.trip.TripCoordinator

class SettingsViewModel(private val preferences: AppPreferences, private val coordinator: TripCoordinator) : ViewModel() {
    val state = preferences.state

    fun setWakeWord(enabled: Boolean) {
        preferences.update { it.copy(wakeWord = enabled) }
        if (enabled) coordinator.enableHandsFree() else coordinator.disableHandsFree()
    }

    fun addFamily(name: String) {
        val clean = name.trim().take(MAX_NAME)
        if (clean.isEmpty()) return
        preferences.update { if (clean in it.family || it.family.size >= MAX_FAMILY) it else it.copy(family = it.family + clean) }
    }

    fun removeFamily(name: String) = preferences.update { prefs ->
        // 녹음 뒤 고를 이름이 하나는 남아 있어야 한다
        if (prefs.family.size <= 1) prefs else prefs.copy(family = prefs.family - name)
    }

    companion object {
        private const val MAX_NAME = 10
        private const val MAX_FAMILY = 8

        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as DoranApplication).container
                SettingsViewModel(container.preferences, container.tripCoordinator)
            }
        }
    }
}

/** 호출어, 가족 이름, 데이터 사용 범위. 정차 중에만 연다 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)) {
    val prefs by viewModel.state.collectAsStateWithLifecycle()
    var newName by remember { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxSize()
            .background(DoranPalette.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 64.dp, vertical = 24.dp),
    ) {
        Text(
            "← 돌아가기",
            style = MaterialTheme.typography.titleLarge,
            color = DoranPalette.Ink,
            modifier = Modifier.clickable(onClick = onBack).padding(vertical = 8.dp),
        )
        Text("설정", style = MaterialTheme.typography.headlineMedium, color = DoranPalette.Ink)
        Spacer(Modifier.height(24.dp))

        Section("\"도란아\"로 부르기", "끄면 도란을 눌러야만 들어요. 켜 두면 마이크가 계속 켜져 있지만, \"도란아\"로 시작한 말만 서버로 보내요.") {
            Switch(
                checked = prefs.wakeWord,
                onCheckedChange = viewModel::setWakeWord,
                colors = SwitchDefaults.colors(checkedTrackColor = DoranPalette.Body),
            )
        }
        HorizontalDivider(color = DoranPalette.Line)

        Column(Modifier.padding(vertical = 24.dp)) {
            Text("가족 이름", style = MaterialTheme.typography.titleLarge, color = DoranPalette.Ink)
            Text("녹음한 뒤 \"누가 남겼어?\"에 나오는 이름이에요. 눌러서 지워요.", style = MaterialTheme.typography.bodyMedium, color = DoranPalette.InkSoft)
            Spacer(Modifier.height(16.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                prefs.family.forEach { name -> DoranSecondaryButton(text = "$name  ✕", onClick = { viewModel.removeFamily(name) }) }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it.take(10) },
                    singleLine = true,
                    label = { Text("이름 추가") },
                    modifier = Modifier.width(320.dp),
                )
                Spacer(Modifier.width(12.dp))
                DoranSecondaryButton(text = "추가", onClick = {
                    viewModel.addFamily(newName)
                    newName = ""
                })
            }
        }
        HorizontalDivider(color = DoranPalette.Line)

        Section("서버로 보내는 것", "질문 문장, 주행·정차 상태, 남은 시간, 들른 곳 이름과 시각. 목소리 녹음 파일과 대기 중에 들린 말은 보내지 않아요.") {}
        HorizontalDivider(color = DoranPalette.Line)
        Section("앱 정보", "도란 ${BuildConfig.VERSION_NAME}") {}
    }
}

@Composable
private fun Section(title: String, body: String, trailing: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 96.dp).padding(vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 24.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = DoranPalette.Ink)
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = DoranPalette.InkSoft)
        }
        trailing()
    }
}
