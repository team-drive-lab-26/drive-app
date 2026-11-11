package com.doran.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.doran.app.core.designsystem.AgentDebugPanel
import com.doran.app.core.designsystem.DebugVehicleBar
import com.doran.app.core.designsystem.DoranMood
import com.doran.app.core.designsystem.DoranPalette
import com.doran.app.core.designsystem.DoranTheme
import com.doran.app.core.model.Gear
import com.doran.app.navigation.DoranActions
import com.doran.app.navigation.DoranNavHost

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory }

    // 버튼을 눌러 권한을 물은 경우에만 허용 뒤 바로 듣는다
    private var listenAfterPermission = false
    private val audioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            viewModel.enableHandsFree()
            if (listenAfterPermission) viewModel.onListenClick()
        } else {
            viewModel.onMicPermissionDenied()
        }
        listenAfterPermission = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 처음 켤 때 마이크 권한을 받아 "도란아" 호출어 대기를 시작한다
        if (hasMicPermission()) viewModel.enableHandsFree() else audioPermission.launch(Manifest.permission.RECORD_AUDIO)
        handleDebugIntent(intent)

        val actions = DoranActions(
            onCall = ::onListenClick,
            onToggleDebug = { if (BuildConfig.DEBUG) viewModel.toggleDebug() },
            onRouteHandled = viewModel::onRouteHandled,
        )
        setContent {
            DoranTheme {
                val ui by viewModel.uiState.collectAsStateWithLifecycle()
                Column(Modifier.fillMaxSize().background(DoranPalette.Background)) {
                    Box(Modifier.weight(1f)) {
                        DoranNavHost(ui = ui, actions = actions)
                    }
                    // 디버그 빌드에서도 평소에는 숨긴다. 도란을 길게 누르면 열린다
                    if (BuildConfig.DEBUG && ui.local.showDebug) {
                        if (!ui.isDriving) {
                            AgentDebugPanel(
                                ui = ui,
                                onSubmit = viewModel::submitUtterance,
                                onDemoEvent = viewModel::sendDemoEvent,
                                onDemoRoute = viewModel::startDemoRoute,
                            )
                        }
                        DebugVehicleBar(
                            vehicle = ui.vehicle,
                            onGearChange = viewModel::overrideGear,
                            onDoorChange = viewModel::overrideDoorOpen,
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDebugIntent(intent)
    }

    /**
     * 디버그 빌드에서 adb로 상태를 바꾼다. 화면 촬영 스크립트(app/scripts/capture_screens.sh)가 쓴다.
     * 예: adb shell am start -n com.doran.app/.MainActivity --es gear D --es mood speaking
     */
    private fun handleDebugIntent(intent: Intent?) {
        if (!BuildConfig.DEBUG || intent == null) return
        intent.getStringExtra("gear")?.let { name -> runCatching { Gear.valueOf(name) }.getOrNull()?.let(viewModel::overrideGear) }
        intent.getStringExtra("door")?.let { viewModel.overrideDoorOpen(it == "open") }
        intent.getStringExtra("route")?.let(viewModel::requestRoute)
        intent.getStringExtra("mood")?.let { name ->
            viewModel.forceMood(DoranMood.entries.firstOrNull { it.name.equals(name, ignoreCase = true) })
        }
        intent.getStringExtra("debug")?.let { viewModel.setDebugVisible(it == "on") }
        intent.getStringExtra("say")?.let(viewModel::submitUtterance)
        intent.getStringExtra("event")?.let(viewModel::sendDemoEvent)
    }

    private fun onListenClick() {
        val needsPermission = !viewModel.uiState.value.isListening && !hasMicPermission()
        if (needsPermission) {
            listenAfterPermission = true
            audioPermission.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            viewModel.onListenClick()
        }
    }

    private fun hasMicPermission() =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
}
