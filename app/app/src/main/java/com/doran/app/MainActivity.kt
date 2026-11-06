package com.doran.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.doran.app.core.data.FakeVehicleStateSource
import com.doran.app.core.designsystem.DebugVehicleBar
import com.doran.app.core.designsystem.DoranTheme
import com.doran.app.navigation.DoranNavHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DoranTheme {
                // 실제 Vehicle SDK 연동 전까지 가짜 값을 쓴다
                val vehicle = remember { FakeVehicleStateSource() }
                val state by vehicle.state.collectAsStateWithLifecycle()
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                        DoranNavHost(isDriving = state.isDriving)
                    }
                    if (BuildConfig.DEBUG) {
                        DebugVehicleBar(gear = state.gear, onGearChange = vehicle::setGear)
                    }
                }
            }
        }
    }
}
