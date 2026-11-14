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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
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
import com.doran.app.core.data.Preferences
import com.doran.app.core.designsystem.BackBar
import com.doran.app.core.designsystem.DoranButtonShape
import com.doran.app.core.designsystem.RoadScene
import com.doran.app.core.designsystem.Rule
import com.doran.app.core.designsystem.SayLine
import com.doran.app.core.designsystem.panel
import com.doran.app.core.designsystem.DoranPalette
import com.doran.app.core.designsystem.DoranSecondaryButton
import com.doran.app.core.designsystem.RibbonButton
import com.doran.app.core.trip.TripCoordinator

class SettingsViewModel(private val preferences: AppPreferences, private val coordinator: TripCoordinator) : ViewModel() {
    val state = preferences.state

    fun setWakeWord(enabled: Boolean) {
        preferences.update { it.copy(wakeWord = enabled) }
        if (enabled) coordinator.enableHandsFree() else coordinator.disableHandsFree()
    }

    fun setScreen(mode: String) = preferences.update { it.copy(screen = mode) }

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

/**
 * 호출어, 화면 밝기, 가족 이름, 데이터 사용 범위. 정차 중에만 연다.
 * 같은 앞길 위에 설정 패널이 붙는다. 화면(자동·낮·밤)을 바꾸면 뒤의 길이 바로 그 시간대로 바뀐다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)) {
    val prefs by viewModel.state.collectAsStateWithLifecycle()
    var newName by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize().background(DoranPalette.Background)) {
        RoadScene(horizon = 0.40f, showRoute = false, orbLight = false)
        Column(Modifier.width(560.dp)) {
            BackBar(back = "도란", title = "설정", onBack = onBack)
            SayLine("설정은 이 기기에만 남아.", maxLines = 3, modifier = Modifier.padding(start = 48.dp, top = 8.dp, end = 24.dp))
        }
        Column(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 28.dp, end = 32.dp, bottom = 32.dp)
                .width(700.dp)
                .fillMaxHeight()
                .panel()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 36.dp, vertical = 12.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp)
                    .clickable { viewModel.setWakeWord(!prefs.wakeWord) }
                    .padding(vertical = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionText(Modifier.weight(1f), "\"도란아\"로 부르기", "끄면 도란을 눌러야만 들어요. 켜 두면 마이크가 계속 켜져 있지만, \"도란아\"로 시작한 말만 서버로 보내요.")
                Spacer(Modifier.width(32.dp))
                Switch(
                    checked = prefs.wakeWord,
                    onCheckedChange = viewModel::setWakeWord,
                    modifier = Modifier.scale(1.35f),
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = DoranPalette.Accent,
                        checkedThumbColor = DoranPalette.OnAccent,
                        checkedBorderColor = DoranPalette.Accent,
                        uncheckedTrackColor = DoranPalette.SurfaceHigh,
                        uncheckedBorderColor = DoranPalette.LineStrong,
                        uncheckedThumbColor = DoranPalette.InkSoft,
                    ),
                )
            }
            Rule()

            Column(Modifier.padding(vertical = 24.dp)) {
                SectionText(Modifier, "화면", "자동이면 차 전조등이 켜질 때 어두운 화면으로 바뀌어요. 전조등 값을 모르는 차는 아침 7시부터 저녁 7시까지 밝은 화면이에요.")
                Spacer(Modifier.height(18.dp))
                Segmented(
                    options = listOf(Preferences.SCREEN_AUTO to "자동", Preferences.SCREEN_DAY to "낮", Preferences.SCREEN_NIGHT to "밤"),
                    selected = prefs.screen,
                    onSelect = viewModel::setScreen,
                )
            }
            Rule()

            Column(Modifier.padding(vertical = 24.dp)) {
                SectionText(Modifier, "가족 이름", "녹음한 뒤 \"누가 남겼어?\"에 나오는 이름이에요. 사람마다 앨범 지도 핀 색이 정해져 있어요. 눌러서 빼요.")
                Spacer(Modifier.height(18.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    prefs.family.forEach { name -> RibbonButton(name = name, trailing = "빼기", onClick = { viewModel.removeFamily(name) }) }
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it.take(10) },
                        singleLine = true,
                        placeholder = { Text("새 이름", style = MaterialTheme.typography.titleLarge) },
                        textStyle = MaterialTheme.typography.titleLarge,
                        shape = DoranButtonShape,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DoranPalette.Ink,
                            unfocusedBorderColor = DoranPalette.LineStrong,
                            cursorColor = DoranPalette.Accent,
                            focusedTextColor = DoranPalette.Ink,
                            unfocusedTextColor = DoranPalette.Ink,
                            focusedPlaceholderColor = DoranPalette.InkMute,
                            unfocusedPlaceholderColor = DoranPalette.InkMute,
                        ),
                        modifier = Modifier.weight(1f).heightIn(min = 72.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    DoranSecondaryButton(text = "추가", modifier = Modifier.heightIn(min = 72.dp), onClick = {
                        viewModel.addFamily(newName)
                        newName = ""
                    })
                }
            }
            Rule()
            SectionText(Modifier.padding(vertical = 24.dp), "서버로 보내는 것", "질문 문장, 주행·정차 상태, 남은 시간, 들른 곳 이름과 시각. 목소리 녹음 파일과 대기 중에 들린 말은 보내지 않아요.")
            Rule()
            SectionText(Modifier.padding(vertical = 24.dp), "앱 정보", "도란 ${BuildConfig.VERSION_NAME}")
        }
    }
}

@Composable
private fun SectionText(modifier: Modifier, title: String, body: String) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = DoranPalette.Ink)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge, color = DoranPalette.InkSoft)
    }
}

/** 하나만 고르는 칸. 고른 칸은 글자색으로 채워 뒤집는다 */
@Composable
private fun Segmented(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(76.dp)
            .border(2.dp, DoranPalette.LineStrong, DoranButtonShape)
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) DoranPalette.Ink else Color.Transparent)
                    .clickable { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) DoranPalette.Surface else DoranPalette.Ink)
            }
        }
    }
}
