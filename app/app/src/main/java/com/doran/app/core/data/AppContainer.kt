package com.doran.app.core.data

import android.content.Context
import com.doran.app.BuildConfig
import com.doran.app.core.media.MusicPlayer
import com.doran.app.core.trip.TripCoordinator
import com.doran.app.core.voice.DoranVoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 앱 전체에서 하나씩 쓰는 객체를 만든다(공식 가이드의 수동 의존성 주입).
 * 차량·내비·음성 SDK 연결은 화면 수명과 상관없이 앱이 살아 있는 동안 유지한다.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    // SDK 콜백과 상태 갱신이 메인 스레드 기준이라 Main.immediate를 쓴다. 네트워크는 Retrofit이 따로 처리한다
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val agentRepository = AgentRepository(BuildConfig.AGENT_BASE_URL)
    val vehicleStateSource: VehicleStateSource = PleosVehicleStateSource(appContext)
    val navi = PleosNavi(appContext)
    val voice = DoranVoice(appContext)
    val musicPlayer = MusicPlayer(appContext)

    val tripCoordinator = TripCoordinator(
        scope = applicationScope,
        agent = agentRepository,
        vehicle = vehicleStateSource,
        navi = navi,
        voice = voice,
        music = musicPlayer,
    )
}
