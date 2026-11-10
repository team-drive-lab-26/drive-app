package com.doran.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.doran.app.core.designsystem.AgentDebugPanel
import com.doran.app.core.designsystem.DebugVehicleBar
import com.doran.app.core.designsystem.DoranTheme
import com.doran.app.navigation.DoranNavHost

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory }
    private val audioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.onListenClick() else viewModel.onMicPermissionDenied()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DoranTheme {
                val ui by viewModel.uiState.collectAsStateWithLifecycle()
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                        DoranNavHost(
                            isDriving = ui.isDriving,
                            caption = ui.caption,
                            isListening = ui.isListening,
                            onListen = ::onListenClick,
                        )
                    }
                    if (BuildConfig.DEBUG) {
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

    private fun onListenClick() {
        val needsPermission = !viewModel.uiState.value.isListening &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) audioPermission.launch(Manifest.permission.RECORD_AUDIO) else viewModel.onListenClick()
    }
}
